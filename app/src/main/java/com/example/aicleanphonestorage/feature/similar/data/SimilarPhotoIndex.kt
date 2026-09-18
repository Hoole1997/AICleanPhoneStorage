package com.example.aicleanphonestorage.feature.similar.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.core.database.sqlite.transaction
import com.example.aicleanphonestorage.core.media.PhotoSignature
import com.example.aicleanphonestorage.feature.filecleaner.data.*

/** 数字特征与分组汇总在磁盘索引；所有方法由仓库在 I/O 线程调用，UI 不持有全量成员。 */
internal class SimilarPhotoIndex(val index: ScanIndex) {
    private val db
        get() = index.writableDatabase

    fun candidates(scan: Long, after: Long, ungrouped: Boolean = false): List<ScannedFile> =
        db.rawQuery(
                "SELECT * FROM files WHERE scan=? AND id>? ${if(ungrouped) "AND group_key='' AND available=1" else ""} ORDER BY id LIMIT 40",
                arrayOf(scan.toString(), after.toString()),
            )
            .use { c -> buildList { while (c.moveToNext()) add(index.row(c)) } }

    fun hasSameMetadata(scan: Long, file: ScannedFile): Boolean =
        db.rawQuery(
                "SELECT 1 FROM files WHERE scan=? AND id<>? AND size=? AND mime=? AND width=? AND height=? LIMIT 1",
                arrayOf(
                    scan.toString(),
                    file.id.toString(),
                    file.size.toString(),
                    file.mime,
                    file.width.toString(),
                    file.height.toString(),
                ),
            )
            .use { it.moveToFirst() }

    fun signature(file: ScannedFile, value: PhotoSignature) {
        val scan =
            db.rawQuery("SELECT scan FROM files WHERE id=?", arrayOf(file.id.toString())).use {
                it.moveToFirst()
                it.getLong(0)
            }
        db.insertOrThrow(
            "similar_signatures",
            null,
            ContentValues().apply {
                put("file", file.id)
                put("scan", scan)
                put("hash", value.hash)
                put("ratio", value.ratio)
                put("mean", value.mean)
                put("contrast", value.contrast)
                put("sharpness", value.sharpness)
                put("pixels", value.pixels)
                put("red", value.red)
                put("green", value.green)
                put("blue", value.blue)
                put("capture", SimilarPolicy.time(file))
                SimilarPolicy.bands(value.hash).forEachIndexed { i, band -> put("b$i", band) }
            },
        )
    }

    fun signature(id: Long): PhotoSignature? =
        db.rawQuery("SELECT * FROM similar_signatures WHERE file=?", arrayOf(id.toString())).use {
            if (it.moveToFirst()) metrics(it) else null
        }

    private fun metrics(c: Cursor): PhotoSignature {
        fun l(key: String) = c.getLong(c.getColumnIndexOrThrow(key))
        fun d(key: String) = c.getDouble(c.getColumnIndexOrThrow(key))
        return PhotoSignature(
            l("hash"),
            d("ratio"),
            l("mean").toInt(),
            d("contrast"),
            d("sharpness"),
            l("pixels"),
            l("red").toInt(),
            l("green").toInt(),
            l("blue").toInt(),
        )
    }

    fun hash(file: Long, value: String) =
        db.execSQL("UPDATE files SET fingerprint=? WHERE id=?", arrayOf(value, file.toString()))

    fun exactHashes(scan: Long, after: String): List<String> =
        db.rawQuery(
                "SELECT fingerprint FROM files WHERE scan=? AND available=1 AND length(fingerprint)=64 AND fingerprint>? GROUP BY fingerprint HAVING COUNT(*)>1 ORDER BY fingerprint LIMIT 40",
                arrayOf(scan.toString(), after),
            )
            .use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }

    fun exactGroup(scan: Long, hash: String) {
        val group = "exact:$hash"
        db.transaction {
            execSQL(
                "UPDATE files SET group_key=?,bucket='SIMILAR',selected=0,retained=0 WHERE scan=? AND fingerprint=? AND available=1",
                arrayOf(group, scan.toString(), hash),
            )
            // 完全重复已逐个解码校验；排序仍沿用统一推荐优先级。
            val keeper =
                rawQuery(
                        """SELECT f.id FROM files f JOIN similar_signatures s ON s.file=f.id WHERE f.scan=? AND f.group_key=?
                ORDER BY s.pixels DESC,s.sharpness DESC,MIN(f.size,s.pixels*32) DESC,
                CASE WHEN s.capture>0 THEN s.capture ELSE 9223372036854775807 END,f.id LIMIT 1""",
                        arrayOf(scan.toString(), group),
                    )
                    .use {
                        it.moveToFirst()
                        it.getLong(0)
                    }
            execSQL("UPDATE files SET retained=1 WHERE id=?", arrayOf(keeper))
            execSQL(
                "UPDATE similar_signatures SET anchor=0 WHERE file IN (SELECT id FROM files WHERE scan=? AND group_key=?)",
                arrayOf(scan.toString(), group),
            )
        }
    }

    fun references(
        scan: Long,
        file: ScannedFile,
        signature: PhotoSignature,
        after: Long,
    ): List<Pair<ScannedFile, PhotoSignature>> {
        val b = SimilarPolicy.bands(signature.hash)
        // 五个哈希分段至少一个相同才能在汉明距离 <=4 内；索引先过滤，再做颜色/比例/亮度检查。
        val args =
            arrayOf(
                scan.toString(),
                after.toString(),
                file.id.toString(),
                (SimilarPolicy.time(file) - 7L * 86_400_000).toString(),
                (SimilarPolicy.time(file) + 7L * 86_400_000).toString(),
                (file.size / 8).toString(),
                (file.size.coerceAtMost(Long.MAX_VALUE / 8) * 8).toString(),
                *b.map { it.toString() }.toTypedArray(),
            )
        return db.rawQuery(
                """SELECT f.*,s.hash,s.ratio,s.mean,s.contrast,s.sharpness,s.pixels,s.red,s.green,s.blue
            FROM similar_signatures s JOIN files f ON f.id=s.file WHERE s.scan=? AND s.file>? AND s.file<? AND s.anchor=1
            AND s.capture BETWEEN ? AND ? AND f.size BETWEEN ? AND ? AND f.available=1
            AND (s.b0=? OR s.b1=? OR s.b2=? OR s.b3=? OR s.b4=?) ORDER BY s.file LIMIT 40""",
                args,
            )
            .use { c -> buildList { while (c.moveToNext()) add(index.row(c) to metrics(c)) } }
    }

    fun join(reference: ScannedFile, file: ScannedFile, signature: PhotoSignature) {
        val group = reference.groupKey.ifBlank { "similar:${reference.id}" }
        db.transaction {
            execSQL(
                "UPDATE files SET group_key=?,bucket='SIMILAR',retained=1 WHERE id=? AND group_key=''",
                arrayOf(group, reference.id.toString()),
            )
            val keeper =
                index.retainedPeer(reference.copy(groupKey = group)) ?: error("Missing original")
            val better =
                SimilarPolicy.better(
                    file,
                    signature,
                    keeper,
                    requireNotNull(this@SimilarPhotoIndex.signature(keeper.id)),
                )
            if (better) execSQL("UPDATE files SET retained=0 WHERE id=?", arrayOf(keeper.id))
            execSQL(
                "UPDATE files SET group_key=?,bucket='SIMILAR',retained=?,selected=0 WHERE id=?",
                arrayOf<Any>(group, if (better) 1 else 0, file.id),
            )
            execSQL("UPDATE similar_signatures SET anchor=0 WHERE file=?", arrayOf(file.id))
        }
    }

    fun rebuild(scan: Long) = db.transaction {
        // 持久化组内位置，让 UI 以固定小行分页；避免巨大单组跨页错列与逐格 COUNT 查询。
        var lastGroup = ""
        var position = 0
        rawQuery(
                "SELECT id,group_key FROM files WHERE scan=? AND group_key<>'' ORDER BY group_key,retained DESC,id",
                arrayOf(scan.toString()),
            )
            .use { cursor ->
                while (cursor.moveToNext()) {
                    val group = cursor.getString(1)
                    if (group != lastGroup) {
                        lastGroup = group
                        position = 0
                    }
                    execSQL(
                        "UPDATE similar_signatures SET position=? WHERE file=?",
                        arrayOf<Any>(position++, cursor.getLong(0)),
                    )
                }
            }
        delete("similar_groups", "scan=?", arrayOf(scan.toString()))
        execSQL(
            """INSERT INTO similar_groups(scan,group_key,count,bytes,modified)
            SELECT scan,group_key,COUNT(*),TOTAL(CASE WHEN retained=0 AND available=1 THEN size ELSE 0 END),
            MAX(CASE WHEN size=(SELECT MAX(m.size) FROM files m WHERE m.scan=f.scan AND m.group_key=f.group_key) THEN modified ELSE 0 END)
            FROM files f WHERE scan=? AND group_key<>'' GROUP BY group_key HAVING COUNT(*)>=2""",
            arrayOf(scan),
        )
    }

    fun makeOriginal(scan: Long, id: Long) {
        db.transaction {
        val file = index.get(id) ?: return@transaction
        if (!file.available || file.groupKey.isEmpty() || file.retained) return@transaction
        val belongs =
            rawQuery(
                    "SELECT 1 FROM files WHERE id=? AND scan=?",
                    arrayOf(id.toString(), scan.toString()),
                )
                .use { it.moveToFirst() }
        if (!belongs) return@transaction
        execSQL(
            "UPDATE files SET retained=0 WHERE scan=? AND group_key=?",
            arrayOf(scan.toString(), file.groupKey),
        )
        execSQL("UPDATE files SET retained=1,selected=0 WHERE id=?", arrayOf(id))
        rebuild(scan)
        }
        // 提交后再通知 Paging 和总量，观察者不会读到过渡状态。
        index.refresh()
    }

    fun unavailable(id: Long, notify: Boolean = true) {
        val file = index.get(id) ?: return
        if (!file.available) return
        db.transaction {
            execSQL("UPDATE files SET available=0,selected=0 WHERE id=?", arrayOf(id))
            if (file.retained)
                execSQL(
                    "UPDATE files SET selected=0 WHERE group_key=? AND scan=(SELECT scan FROM files WHERE id=?)",
                    arrayOf(file.groupKey, id.toString()),
                )
            if (file.groupKey.isNotEmpty()) execSQL(
                """UPDATE similar_groups SET bytes=(SELECT TOTAL(size) FROM files f WHERE f.scan=similar_groups.scan
                AND f.group_key=similar_groups.group_key AND f.retained=0 AND f.available=1
                AND EXISTS (SELECT 1 FROM files o WHERE o.scan=f.scan AND o.group_key=f.group_key AND o.retained=1 AND o.available=1))
                WHERE group_key=? AND scan=(SELECT scan FROM files WHERE id=?)""", arrayOf(file.groupKey,id.toString()))

        }
        if (notify) index.refresh()
    }

    companion object {
        fun create(db: SQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE similar_signatures(file INTEGER PRIMARY KEY,scan INTEGER NOT NULL,hash INTEGER,ratio REAL,mean INTEGER,
                contrast REAL,sharpness REAL,pixels INTEGER,red INTEGER,green INTEGER,blue INTEGER,capture INTEGER,
                b0 INTEGER,b1 INTEGER,b2 INTEGER,b3 INTEGER,b4 INTEGER,anchor INTEGER NOT NULL DEFAULT 1, position INTEGER NOT NULL DEFAULT 0)"""
            )
            for (i in 0..4) db.execSQL(
                "CREATE INDEX similar_band_$i ON similar_signatures(scan,anchor,b$i,file)"
            )
            db.execSQL(
                "CREATE TABLE similar_groups(scan INTEGER NOT NULL,group_key TEXT NOT NULL,count INTEGER NOT NULL,bytes INTEGER NOT NULL,modified INTEGER NOT NULL,PRIMARY KEY(scan,group_key))"
            )
            db.execSQL(
                "CREATE INDEX similar_group_order ON similar_groups(scan,bytes DESC,modified DESC,group_key)"
            )
        }
    }
}
