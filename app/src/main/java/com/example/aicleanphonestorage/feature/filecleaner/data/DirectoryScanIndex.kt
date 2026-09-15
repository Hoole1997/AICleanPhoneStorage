package com.example.aicleanphonestorage.feature.filecleaner.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase

internal data class ScanDirectory(
    val document: String,
    val parent: String? = null,
    val name: String = "",
    val folder: String = "",
    val modified: Long = 0,
    val depth: Int = 0,
    val nonempty: Boolean = false,
)

/** SAF 文档树队列和空目录后序归并保存在临时 SQLite 中，不保存全量树或打开多层 Cursor。 */
internal class DirectoryScanIndex(private val index: ScanIndex) {
    private val db get() = index.writableDatabase

    fun enqueue(scan: Long, entry: ScanDirectory): Boolean = db.insertWithOnConflict(
        "directories", null, ContentValues().apply {
            put("scan", scan); put("document", entry.document); put("parent", entry.parent)
            put("name", entry.name); put("folder", entry.folder); put("modified", entry.modified)
            put("depth", entry.depth)
        }, SQLiteDatabase.CONFLICT_IGNORE,
    ) != -1L

    fun take(scan: Long, completed: Boolean = false): ScanDirectory? {
        val state = if (completed) 1 else 0
        return db.rawQuery(
            "SELECT document,parent,name,folder,modified,depth,nonempty FROM directories WHERE scan=? AND done=? ORDER BY depth ${if (completed) "DESC" else "ASC"} LIMIT 1",
            arrayOf(scan.toString(), state.toString()),
        ).use {
            if (!it.moveToFirst()) return@use null
            ScanDirectory(it.getString(0), it.getString(1), it.getString(2), it.getString(3), it.getLong(4), it.getInt(5), it.getInt(6) != 0).also { entry ->
                db.execSQL("UPDATE directories SET done=? WHERE scan=? AND document=?", arrayOf(state + 1, scan, entry.document))
            }
        }
    }

    fun markNonempty(scan: Long, document: String) {
        db.execSQL("UPDATE directories SET nonempty=1 WHERE scan=? AND document=?", arrayOf(scan, document))
    }

    companion object {
        fun create(db: SQLiteDatabase) {
            db.execSQL("""CREATE TABLE directories(scan INTEGER NOT NULL, document TEXT NOT NULL,
                done INTEGER NOT NULL DEFAULT 0, parent TEXT, name TEXT NOT NULL DEFAULT '', folder TEXT NOT NULL DEFAULT '',
                modified INTEGER NOT NULL DEFAULT 0, depth INTEGER NOT NULL DEFAULT 0, nonempty INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(scan,document))""")
            db.execSQL("CREATE INDEX directories_work ON directories(scan,done,depth)")
        }
    }
}
