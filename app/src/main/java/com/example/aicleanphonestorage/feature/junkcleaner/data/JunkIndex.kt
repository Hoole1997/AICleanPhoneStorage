package com.example.aicleanphonestorage.feature.junkcleaner.data

import android.content.ContentValues
import com.example.aicleanphonestorage.feature.filecleaner.data.*
import kotlinx.coroutines.ensureActive

/** 垃圾分类与照片分析的 SQL 查询集中在此；只复用共享索引，不改变扫描/删除的所有权。 */
internal class JunkIndex(private val index: ScanIndex) {
    private val db
        get() = index.writableDatabase

    fun photos(scan: Long, after: Long, hashCandidates: Boolean = false): List<ScannedFile> {
        val clause =
            if (hashCandidates)
                "AND size IN (SELECT size FROM files WHERE scan=? AND category='PHOTOS' GROUP BY size HAVING COUNT(*)>1)"
            else ""
        val args =
            if (hashCandidates) arrayOf(scan.toString(), after.toString(), scan.toString())
            else arrayOf(scan.toString(), after.toString())
        return db.rawQuery(
                "SELECT * FROM files WHERE scan=? AND id>? AND category='PHOTOS' AND bucket='' $clause ORDER BY id LIMIT 40",
                args,
            )
            .use { c -> buildList { while (c.moveToNext()) add(index.row(c)) } }
    }

    fun photosByTime(scan: Long, modified: Long, id: Long): List<ScannedFile> =
        db.rawQuery(
                "SELECT * FROM files WHERE scan=? AND category='PHOTOS' AND bucket='' AND fingerprint<>'!' AND (modified>? OR (modified=? AND id>?)) ORDER BY modified,id LIMIT 40",
                arrayOf(scan.toString(), modified.toString(), modified.toString(), id.toString()),
            )
            .use { c -> buildList { while (c.moveToNext()) add(index.row(c)) } }

    fun setHash(id: Long, hash: String) {
        db.update(
            "files",
            ContentValues().apply { put("fingerprint", hash) },
            "id=?",
            arrayOf(id.toString()),
        )
    }

    fun mark(id: Long, kind: JunkKind, group: String = "", retained: Boolean = false) {
        db.update(
            "files",
            ContentValues().apply {
                put("bucket", kind.name)
                put("group_key", group)
                put("retained", if (retained) 1 else 0)
                put("selected", 0)
            },
            "id=?",
            arrayOf(id.toString()),
        )
    }

    suspend fun duplicateGroups(scan: Long) {
        db.rawQuery(
                "SELECT fingerprint FROM files WHERE scan=? AND length(fingerprint)=64 GROUP BY fingerprint HAVING COUNT(*)>1",
                arrayOf(scan.toString()),
            )
            .use { groups ->
                while (groups.moveToNext()) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    val hash = groups.getString(0)
                    db.execSQL(
                        "UPDATE files SET bucket='DUPLICATES',group_key=?,selected=0 WHERE scan=? AND fingerprint=?",
                        arrayOf("exact:$hash", scan.toString(), hash),
                    )
                    db.execSQL(
                        "UPDATE files SET retained=1 WHERE id=(SELECT id FROM files WHERE scan=? AND fingerprint=? ORDER BY CASE WHEN modified>0 THEN modified ELSE 9223372036854775807 END,id LIMIT 1)",
                        arrayOf(scan.toString(), hash),
                    )
                }
            }
    }

    fun categories(scan: Long): List<JunkCategorySummary> {
        val values = mutableMapOf<String, JunkCategorySummary>()
        db.rawQuery(
                "SELECT bucket,COUNT(*),TOTAL(size),TOTAL(selected) FROM files WHERE scan=? AND bucket<>'' AND retained=0 GROUP BY bucket",
                arrayOf(scan.toString()),
            )
            .use { c ->
                while (c.moveToNext()) {
                    val kind = JunkKind.from(c.getString(0)) ?: continue
                    values[kind.name] =
                        JunkCategorySummary(kind, c.getInt(1), c.getDouble(2).toLong(), c.getInt(3))
                }
            }
        return JunkKind.entries.map { values[it.name] ?: JunkCategorySummary(it) }
    }

    fun latest(): Pair<ScanHandle, Long>? =
        db.rawQuery(
                "SELECT id,created FROM scans WHERE feature='SMART_CLEAN' AND ready=1 ORDER BY id DESC LIMIT 1",
                null,
            )
            .use {
                if (it.moveToFirst())
                    index.handle(it.getLong(0))?.let { handle -> handle to it.getLong(1) }
                else null
            }
}
