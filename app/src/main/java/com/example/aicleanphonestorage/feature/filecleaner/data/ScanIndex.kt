package com.example.aicleanphonestorage.feature.filecleaner.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.paging.PagingSource
import java.util.Collections
import java.util.WeakHashMap

/** 仅缓存元数据/选择状态的临时索引。所有方法由Repository在I/O线程调用，不存文件内容或Bitmap。 */
internal class ScanIndex(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "cleanup_index.db", null, 1) {
    private val sources = Collections.newSetFromMap(WeakHashMap<PagingSource<*, *>, Boolean>())

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE scans(id INTEGER PRIMARY KEY AUTOINCREMENT, feature TEXT NOT NULL, created INTEGER NOT NULL, count INTEGER NOT NULL DEFAULT 0, label TEXT NOT NULL DEFAULT '', partial INTEGER NOT NULL DEFAULT 0, ready INTEGER NOT NULL DEFAULT 0)"
        )
        db.execSQL(
            """CREATE TABLE files(id INTEGER PRIMARY KEY AUTOINCREMENT, scan INTEGER NOT NULL, uri TEXT NOT NULL,
            name TEXT NOT NULL, mime TEXT NOT NULL, size INTEGER NOT NULL, modified INTEGER NOT NULL, category TEXT NOT NULL,
            backend TEXT NOT NULL, scope TEXT NOT NULL, path TEXT NOT NULL, selected INTEGER NOT NULL DEFAULT 0,
            quality INTEGER NOT NULL DEFAULT 75, UNIQUE(scan,uri))"""
        )
        db.execSQL("CREATE INDEX files_scan_sort ON files(scan,size DESC,id)")
        db.execSQL("CREATE INDEX files_selection ON files(scan,selected)")
        db.execSQL(
            "CREATE TABLE directories(scan INTEGER NOT NULL, document TEXT NOT NULL, done INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(scan,document))"
        )
        db.execSQL(
            "CREATE TABLE operations(id INTEGER PRIMARY KEY AUTOINCREMENT, feature TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'prepared')"
        )
        db.execSQL(
            """CREATE TABLE operation_items(op INTEGER NOT NULL, file INTEGER NOT NULL, state TEXT NOT NULL DEFAULT 'pending',
            file_bytes INTEGER NOT NULL DEFAULT 0, output TEXT, output_bytes INTEGER, output_sha TEXT, PRIMARY KEY(op,file))"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        error("Define a migration before changing cleanup index version")
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
        writableDatabase.update(
            "scans",
            ContentValues().apply {
                put("count", handle.scannedCount)
                put("label", handle.scopeLabel)
                put("partial", if (handle.partial) 1 else 0)
                put("ready", 1)
            },
            "id=?",
            arrayOf(handle.id.toString()),
        )
        writableDatabase.delete("directories", "scan=?", arrayOf(handle.id.toString()))
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
                    )
            }

    fun insert(scan: Long, batch: List<ScannedFile>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            batch.forEach { item ->
                db.insertWithOnConflict(
                    "files",
                    null,
                    values(item).apply { put("scan", scan) },
                    SQLiteDatabase.CONFLICT_IGNORE,
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
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
            .query("files", null, selection, args, null, null, "size DESC,id", "$offset,$limit")
            .use { cursor -> buildList { while (cursor.moveToNext()) add(row(cursor)) } }
    }

    fun get(id: Long): ScannedFile? =
        readableDatabase
            .query("files", null, "id=?", arrayOf(id.toString()), null, null, null)
            .use { if (it.moveToFirst()) row(it) else null }

    fun totals(handle: ScanHandle, filter: CleanupFilter): SelectionTotals {
        val (selection, args) = where(handle.id, handle.feature, filter)
        return readableDatabase
            .rawQuery(
                """SELECT COUNT(*),TOTAL(size),TOTAL(selected),TOTAL(CASE WHEN selected=1 THEN size ELSE 0 END),
            TOTAL(size*(100-quality)/100.0) FROM files WHERE $selection""",
                args,
            )
            .use {
                it.moveToFirst()
                SelectionTotals(
                    it.getInt(0),
                    it.getDouble(1).toLong(),
                    it.getInt(2),
                    it.getDouble(3).toLong(),
                    it.getDouble(4).toLong(),
                )
            }
    }

    fun select(id: Long, value: Boolean) {
        writableDatabase.update(
            "files",
            ContentValues().apply { put("selected", if (value) 1 else 0) },
            "id=?",
            arrayOf(id.toString()),
        )
        invalidate()
    }

    fun selectAll(handle: ScanHandle, filter: CleanupFilter, value: Boolean) {
        val (selection, args) = where(handle.id, handle.feature, filter)
        writableDatabase.update(
            "files",
            ContentValues().apply { put("selected", if (value) 1 else 0) },
            selection,
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

    fun remove(id: Long, notify: Boolean = true) {
        writableDatabase.delete("files", "id=?", arrayOf(id.toString()))
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

    fun enqueueDirectory(scan: Long, document: String) =
        writableDatabase.insertWithOnConflict(
            "directories",
            null,
            ContentValues().apply {
                put("scan", scan)
                put("document", document)
            },
            SQLiteDatabase.CONFLICT_IGNORE,
        )

    fun takeDirectory(scan: Long): String? =
        writableDatabase
            .rawQuery(
                "SELECT document FROM directories WHERE scan=? AND done=0 LIMIT 1",
                arrayOf(scan.toString()),
            )
            .use {
                if (!it.moveToFirst()) null
                else
                    it.getString(0).also { id ->
                        writableDatabase.update(
                            "directories",
                            ContentValues().apply { put("done", 1) },
                            "scan=? AND document=?",
                            arrayOf(scan.toString(), id),
                        )
                    }
            }

    fun register(source: PagingSource<*, *>) {
        synchronized(sources) { sources.add(source) }
    }

    private fun invalidate() {
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
                "INSERT INTO operation_items(op,file,file_bytes) SELECT ?,id,size FROM files WHERE $selection AND selected=1",
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
        )
    }
}
