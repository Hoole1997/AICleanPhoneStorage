package com.example.aicleanphonestorage.feature.filecleaner.data

import com.example.aicleanphonestorage.feature.junkcleaner.data.JunkKind
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.paging.PagingSource
import java.util.Collections
import java.util.WeakHashMap
import kotlinx.coroutines.flow.update

/** 仅缓存元数据/选择状态的临时索引。所有方法由Repository在I/O线程调用，不存文件内容或Bitmap。 */
internal class ScanIndex(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "cleanup_index.db", null, 4) {
    private val revision = kotlinx.coroutines.flow.MutableStateFlow(0L)
    val changes: kotlinx.coroutines.flow.StateFlow<Long> = revision
    private val sources = Collections.newSetFromMap(WeakHashMap<PagingSource<*, *>, Boolean>())

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE scans(id INTEGER PRIMARY KEY AUTOINCREMENT, feature TEXT NOT NULL, created INTEGER NOT NULL, count INTEGER NOT NULL DEFAULT 0, label TEXT NOT NULL DEFAULT '', partial INTEGER NOT NULL DEFAULT 0, ready INTEGER NOT NULL DEFAULT 0, analysis_skipped INTEGER NOT NULL DEFAULT 0)"
        )
        db.execSQL(
            """CREATE TABLE files(id INTEGER PRIMARY KEY AUTOINCREMENT, scan INTEGER NOT NULL, uri TEXT NOT NULL,
            name TEXT NOT NULL, mime TEXT NOT NULL, size INTEGER NOT NULL, modified INTEGER NOT NULL, category TEXT NOT NULL,
            backend TEXT NOT NULL, scope TEXT NOT NULL, path TEXT NOT NULL, selected INTEGER NOT NULL DEFAULT 0,
            quality INTEGER NOT NULL DEFAULT 75, bucket TEXT NOT NULL DEFAULT '', group_key TEXT NOT NULL DEFAULT '', retained INTEGER NOT NULL DEFAULT 0, fingerprint TEXT NOT NULL DEFAULT '', UNIQUE(scan,uri))"""
        )
        db.execSQL("CREATE INDEX files_scan_sort ON files(scan,size DESC,id)")
        db.execSQL("CREATE INDEX files_selection ON files(scan,selected)")
        createGroupIndexes(db)
        createIdentityIndexes(db)
        DirectoryScanIndex.create(db)
        db.execSQL(
            "CREATE TABLE operations(id INTEGER PRIMARY KEY AUTOINCREMENT, feature TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'prepared')"
        )
        db.execSQL(
            """CREATE TABLE operation_items(op INTEGER NOT NULL, file INTEGER NOT NULL, state TEXT NOT NULL DEFAULT 'pending',
            file_bytes INTEGER NOT NULL DEFAULT 0, output TEXT, output_bytes INTEGER, output_sha TEXT, PRIMARY KEY(op,file))"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE scans ADD COLUMN analysis_skipped INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE files ADD COLUMN bucket TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE files ADD COLUMN group_key TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE files ADD COLUMN retained INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE files ADD COLUMN fingerprint TEXT NOT NULL DEFAULT ''")
            createGroupIndexes(db)
        }
        if (oldVersion < 3) createIdentityIndexes(db)
        if (oldVersion < 4) {
            // 仅重建未完成扫描的临时队列，文件选择与已完成操作不受影响。
            db.execSQL("DROP TABLE IF EXISTS directories")
            DirectoryScanIndex.create(db)
        }
    }

    private fun createIdentityIndexes(db: SQLiteDatabase) {
        db.execSQL("CREATE INDEX files_uri ON files(uri)")
        db.execSQL("CREATE INDEX files_path ON files(path)")
    }

    private fun createGroupIndexes(db: SQLiteDatabase) {
        db.execSQL("CREATE INDEX files_groups ON files(scan,bucket,group_key,retained)")
        db.execSQL("CREATE INDEX files_photo_sizes ON files(scan,category,size)")
        db.execSQL("CREATE INDEX files_fingerprints ON files(scan,fingerprint)")
    }

    fun start(feature: CleanupFeature): Long {
        // 临时索引保留三天供页面重建；清理的只是本应用元数据，不访问任何原始文件。
        writableDatabase.execSQL(
            "DELETE FROM operation_items WHERE file IN (SELECT files.id FROM files JOIN scans ON files.scan=scans.id WHERE created<?)",
            arrayOf(System.currentTimeMillis() - 3 * 86_400_000L),
        )
        writableDatabase.execSQL(
            "DELETE FROM operations WHERE id NOT IN (SELECT op FROM operation_items)"
        )
        readableDatabase
            .rawQuery(
                "SELECT id FROM scans WHERE created<?",
                arrayOf((System.currentTimeMillis() - 3 * 86_400_000L).toString()),
            )
            .use { while (it.moveToNext()) discard(it.getLong(0)) }
        return writableDatabase.insertOrThrow(
            "scans",
            null,
            ContentValues().apply {
                put("feature", feature.name)
                put("created", System.currentTimeMillis())
            },
        )
    }

    fun finishScan(handle: ScanHandle) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            if (handle.feature == CleanupFeature.SMART_CLEAN) {
                // 只全选当前展示的四类候选，覆盖所有分页；旧分类不会被隐藏后悄悄清理。
                // 仅对未发布的新扫描执行；重复完成、页面恢复不能覆盖用户手动取消的选择。
                val (selection, args) = where(handle.id, handle.feature, CleanupFilter())
                db.execSQL(
                    """UPDATE files SET selected=1 WHERE $selection AND retained=0
                    AND EXISTS (SELECT 1 FROM scans WHERE id=? AND ready=0)""",
                    arrayOf(*args, handle.id.toString()),
                )
            }
            db.update(
                "scans",
                ContentValues().apply {
                    put("count", handle.scannedCount)
                    put("label", handle.scopeLabel)
                    put("partial", if (handle.partial) 1 else 0)
                    put("ready", 1)
                    put("analysis_skipped", handle.analysisSkipped)
                },
                "id=?",
                arrayOf(handle.id.toString()),
            )
            db.delete("directories", "scan=?", arrayOf(handle.id.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        // 提交选择和扫描状态后统一通知，总览和详情首次读取即保持一致。
        invalidate()
    }

    fun handle(id: Long): ScanHandle? =
        readableDatabase
            .query("scans", null, "id=? AND ready=1", arrayOf(id.toString()), null, null, null)
            .use {
                if (!it.moveToFirst()) null
                else
                    ScanHandle(
                        id,
                        CleanupFeature.valueOf(it.getString(it.getColumnIndexOrThrow("feature"))),
                        it.getInt(it.getColumnIndexOrThrow("count")),
                        it.getString(it.getColumnIndexOrThrow("label")),
                        it.getInt(it.getColumnIndexOrThrow("partial")) != 0,
                        it.getInt(it.getColumnIndexOrThrow("analysis_skipped")),
                    )
            }

    /** 返回本批实际新增的已分类候选容量；忽略重复 URI，不重复累计扫描数字。 */
    fun insert(scan: Long, batch: List<ScannedFile>): Long {
        val db = writableDatabase
        var candidateBytes = 0L
        db.beginTransaction()
        try {
            batch.forEach { item ->
                val inserted = db.insertWithOnConflict(
                    "files",
                    null,
                    values(item).apply { put("scan", scan) },
                    SQLiteDatabase.CONFLICT_IGNORE,
                )
                if (inserted != -1L && item.bucket.isNotEmpty() && !item.retained)
                    candidateBytes += item.size
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return candidateBytes
    }

    private fun values(item: ScannedFile) =
        ContentValues().apply {
            put("uri", item.uri)
            put("name", item.name)
            put("mime", item.mime)
            put("size", item.size)
            put("modified", item.modifiedMillis)
            put("category", item.category.name)
            put("backend", item.backend.name)
            put("scope", item.scope)
            put("path", item.path)
            put("bucket", item.bucket)
            put("group_key", item.groupKey)
            put("retained", if (item.retained) 1 else 0)
        }

    private fun where(
        scan: Long,
        feature: CleanupFeature,
        filter: CleanupFilter,
    ): Pair<String, Array<String>> {
        val clauses = mutableListOf("scan=?")
        val args = mutableListOf(scan.toString())
        if (feature == CleanupFeature.LARGE_FILES) {
            clauses += "size>=?"
            args += filter.minimumBytes.toString()
            if (filter.category != FileCategory.ALL) {
                clauses += "category=?"
                args += filter.category.name
            }
            if (filter.recentDays > 0) {
                clauses += "modified>=?"
                args += (filter.referenceMillis - filter.recentDays * 86_400_000L).toString()
            }
        }
        if (feature == CleanupFeature.UNUSED_FILES) {
            clauses += "modified>0 AND modified<=?"
            args += (filter.referenceMillis - filter.unusedDays * 86_400_000L).toString()
        }
        if (feature == CleanupFeature.SMART_CLEAN) {
            clauses += "bucket<>''"
            if (filter.bucket != null) {
                clauses += "bucket=?"
                args += filter.bucket
            } else {
                clauses += "bucket IN (${JunkKind.visible.joinToString { "?" }})"
                args += JunkKind.visible.map { it.name }
            }
        }
        return clauses.joinToString(" AND ") to args.toTypedArray()
    }

    fun page(
        handle: ScanHandle,
        filter: CleanupFilter,
        offset: Int,
        limit: Int,
    ): List<ScannedFile> {
        val (selection, args) = where(handle.id, handle.feature, filter)
        return readableDatabase
            .query(
                "files",
                null,
                selection,
                args,
                null,
                null,
                if (
                    handle.feature == CleanupFeature.SMART_CLEAN &&
                        filter.bucket in listOf("DUPLICATES", "SIMILAR")
                )
                    "group_key,retained DESC,id"
                else "size DESC,id",
                "$offset,$limit",
            )
            .use { cursor -> buildList { while (cursor.moveToNext()) add(row(cursor)) } }
    }

    fun expectedFingerprint(id: Long): String? =
        readableDatabase
            .rawQuery("SELECT fingerprint FROM files WHERE id=?", arrayOf(id.toString()))
            .use {
                if (it.moveToFirst()) it.getString(0).takeIf { hash -> hash.length == 64 } else null
            }

    fun retainedPeer(file: ScannedFile): ScannedFile? =
        readableDatabase
            .rawQuery(
                "SELECT * FROM files WHERE scan=(SELECT scan FROM files WHERE id=?) AND group_key=? AND retained=1 LIMIT 1",
                arrayOf(file.id.toString(), file.groupKey),
            )
            .use { if (it.moveToFirst()) row(it) else null }

    fun get(id: Long): ScannedFile? =
        readableDatabase
            .query("files", null, "id=?", arrayOf(id.toString()), null, null, null)
            .use { if (it.moveToFirst()) row(it) else null }

    fun totals(handle: ScanHandle, filter: CleanupFilter): SelectionTotals {
        val (selection, args) = where(handle.id, handle.feature, filter)
        return readableDatabase
            .rawQuery(
                """SELECT COUNT(*),TOTAL(size),TOTAL(selected),TOTAL(CASE WHEN selected=1 THEN size ELSE 0 END) FROM files WHERE $selection AND retained=0""",
                args,
            )
            .use {
                it.moveToFirst()
                SelectionTotals(
                    it.getInt(0),
                    it.getDouble(1).toLong(),
                    it.getInt(2),
                    it.getDouble(3).toLong(),
                )
            }
    }

    fun select(id: Long, value: Boolean) {
        writableDatabase.update(
            "files",
            ContentValues().apply { put("selected", if (value) 1 else 0) },
            "id=? AND retained=0",
            arrayOf(id.toString()),
        )
        invalidate()
    }

    fun selectAll(handle: ScanHandle, filter: CleanupFilter, value: Boolean) {
        val (selection, args) = where(handle.id, handle.feature, filter)
        writableDatabase.update(
            "files",
            ContentValues().apply { put("selected", if (value) 1 else 0) },
            "$selection AND retained=0",
            args,
        )
        invalidate()
    }

    fun quality(id: Long, value: Int) {
        writableDatabase.update(
            "files",
            ContentValues().apply { put("quality", value) },
            "id=?",
            arrayOf(id.toString()),
        )
        invalidate()
    }

    fun rememberPath(id: Long, path: String) {
        if (path.isNotBlank())
            writableDatabase.update(
                "files",
                ContentValues().apply { put("path", path) },
                "id=?",
                arrayOf(id.toString()),
            )
    }

    fun remove(id: Long, notify: Boolean = true) {
        val file = get(id) ?: return
        val args = if (file.path.isBlank()) arrayOf(file.uri) else arrayOf(file.uri, file.path)
        val identity = if (file.path.isBlank()) "uri=?" else "uri=? OR (path<>'' AND path=?)"
        val db = writableDatabase
        db.beginTransaction()
        try {
            // 其他旧快照中的待处理项已不存在，标记跳过；已完成的当前操作保留其删除结果。
            val groups =
                db.rawQuery(
                        "SELECT DISTINCT scan,group_key FROM files WHERE ($identity) AND retained=1 AND group_key<>''",
                        args,
                    )
                    .use { c ->
                        buildList { while (c.moveToNext()) add(c.getLong(0) to c.getString(1)) }
                    }
            db.execSQL(
                "UPDATE operation_items SET state='skipped' WHERE state='pending' AND file IN (SELECT id FROM files WHERE $identity)",
                args,
            )
            db.delete("files", identity, args)
            // 参考图从其他清理入口被删除时，剩余组重新保留一张，防止旧分组将最后副本当垃圾。
            for ((scan, group) in groups) {
                val keeper =
                    db.rawQuery(
                            "SELECT id FROM files WHERE scan=? AND group_key=? ORDER BY size DESC,id LIMIT 1",
                            arrayOf(scan.toString(), group),
                        )
                        .use { if (it.moveToFirst()) it.getLong(0) else null } ?: continue
                db.execSQL(
                    "UPDATE files SET retained=1,selected=0 WHERE id=?",
                    arrayOf(keeper.toString()),
                )
                db.execSQL(
                    "UPDATE operation_items SET state='skipped' WHERE file=? AND state='pending'",
                    arrayOf(keeper.toString()),
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        if (notify) invalidate()
    }

    fun unselect(id: Long) {
        writableDatabase.execSQL("UPDATE files SET selected=0 WHERE id=?", arrayOf(id.toString()))
    }

    fun refresh() = invalidate()

    fun discard(scan: Long) {
        writableDatabase.delete("files", "scan=?", arrayOf(scan.toString()))
        writableDatabase.delete("directories", "scan=?", arrayOf(scan.toString()))
        writableDatabase.delete("scans", "id=?", arrayOf(scan.toString()))
        invalidate()
    }

    fun register(source: PagingSource<*, *>) {
        synchronized(sources) { sources.add(source) }
    }

    private fun invalidate() {
        revision.update { it + 1 }
        val current = synchronized(sources) { sources.toList() }
        current.forEach { it.invalidate() }
    }

    fun prepareOperation(handle: ScanHandle, filter: CleanupFilter): Long {
        val db = writableDatabase
        val (selection, args) = where(handle.id, handle.feature, filter)
        db.beginTransaction()
        try {
            val id =
                db.insertOrThrow(
                    "operations",
                    null,
                    ContentValues().apply {
                        put("feature", handle.feature.name)
                        put("status", "prepared")
                    },
                )
            db.execSQL(
                "INSERT INTO operation_items(op,file,file_bytes) SELECT ?,id,size FROM files WHERE $selection AND selected=1 AND retained=0",
                arrayOf(id.toString(), *args),
            )
            db.setTransactionSuccessful()
            return id
        } finally {
            db.endTransaction()
        }
    }

    fun operationFiles(
        operation: Long,
        state: String = "pending",
        limit: Int = 100,
    ): List<ScannedFile> =
        readableDatabase
            .rawQuery(
                "SELECT files.* FROM files JOIN operation_items ON files.id=operation_items.file WHERE op=? AND state=? ORDER BY files.id LIMIT ?",
                arrayOf(operation.toString(), state, limit.toString()),
            )
            .use { cursor -> buildList { while (cursor.moveToNext()) add(row(cursor)) } }

    fun mark(
        operation: Long,
        id: Long,
        state: String,
        output: String? = null,
        outputBytes: Long? = null,
        outputSha: String? = null,
    ) {
        writableDatabase.update(
            "operation_items",
            ContentValues().apply {
                put("state", state)
                output?.let { put("output", it) }
                outputBytes?.let { put("output_bytes", it) }
                outputSha?.let { put("output_sha", it) }
            },
            "op=? AND file=?",
            arrayOf(operation.toString(), id.toString()),
        )
    }

    fun operationCount(operation: Long, state: String? = null): Int =
        readableDatabase
            .rawQuery(
                "SELECT COUNT(*) FROM operation_items WHERE op=?" +
                    if (state == null) "" else " AND state=?",
                if (state == null) arrayOf(operation.toString())
                else arrayOf(operation.toString(), state),
            )
            .use {
                it.moveToFirst()
                it.getInt(0)
            }

    fun finishOperation(operation: Long, state: String) =
        writableDatabase.update(
            "operations",
            ContentValues().apply { put("status", state) },
            "id=?",
            arrayOf(operation.toString()),
        )

    fun operationBytes(operation: Long): Long =
        readableDatabase
            .rawQuery(
                "SELECT TOTAL(file_bytes) FROM operation_items WHERE op=?",
                arrayOf(operation.toString()),
            )
            .use {
                it.moveToFirst()
                it.getDouble(0).toLong()
            }

    fun operationStatus(operation: Long): String =
        readableDatabase
            .rawQuery("SELECT status FROM operations WHERE id=?", arrayOf(operation.toString()))
            .use { if (it.moveToFirst()) it.getString(0) else "missing" }

    /** 基于持久操作快照统计；原文件删除后仍可读取，副本占用从已删除字节中扣除。 */
    fun operationStorage(operation: Long): OperationStorage =
        readableDatabase.rawQuery(
            """SELECT COALESCE(SUM(CASE WHEN state='deleted' THEN file_bytes ELSE 0 END),0)
                - COALESCE(SUM(output_bytes),0),
                COALESCE(SUM(CASE WHEN output IS NOT NULL THEN MAX(file_bytes-output_bytes,0) ELSE 0 END),0),
                COALESCE(SUM(file_bytes),0),
                COALESCE(SUM(CASE WHEN output IS NOT NULL THEN file_bytes ELSE 0 END),0),
                COALESCE(SUM(output_bytes),0)
                FROM operation_items WHERE op=?""",
            arrayOf(operation.toString()),
        ).use {
            it.moveToFirst()
            OperationStorage(it.getLong(0).coerceAtLeast(0), it.getLong(1).coerceAtLeast(0),
                it.getLong(2).coerceAtLeast(0), it.getLong(3).coerceAtLeast(0), it.getLong(4).coerceAtLeast(0))
        }

    fun cancelPending(operation: Long) {
        writableDatabase.execSQL(
            "UPDATE operation_items SET state='skipped' WHERE op=? AND state IN ('pending','awaiting')",
            arrayOf(operation.toString()),
        )
    }

    fun copyCount(operation: Long): Int =
        readableDatabase
            .rawQuery(
                "SELECT COUNT(*) FROM operation_items WHERE op=? AND output IS NOT NULL",
                arrayOf(operation.toString()),
            )
            .use {
                it.moveToFirst()
                it.getInt(0)
            }

    fun output(operation: Long, file: Long): Pair<String, String>? =
        readableDatabase
            .rawQuery(
                "SELECT output,output_sha FROM operation_items WHERE op=? AND file=? AND output IS NOT NULL",
                arrayOf(operation.toString(), file.toString()),
            )
            .use { if (it.moveToFirst()) it.getString(0) to it.getString(1).orEmpty() else null }

    fun promoteOriginalsForDeletion(operation: Long) {
        writableDatabase.execSQL(
            "UPDATE operation_items SET state='pending' WHERE op=? AND state='copied'",
            arrayOf(operation.toString()),
        )
    }

    fun row(c: Cursor): ScannedFile {
        fun string(name: String) = c.getString(c.getColumnIndexOrThrow(name))
        fun number(name: String) = c.getLong(c.getColumnIndexOrThrow(name))
        return ScannedFile(
            number("id"),
            string("uri"),
            string("name"),
            string("mime"),
            number("size"),
            number("modified"),
            FileCategory.valueOf(string("category")),
            FileBackend.valueOf(string("backend")),
            string("scope"),
            string("path"),
            number("selected") == 1L,
            number("quality").toInt(),
            string("bucket"),
            string("group_key"),
            number("retained") == 1L,
        )
    }
}
