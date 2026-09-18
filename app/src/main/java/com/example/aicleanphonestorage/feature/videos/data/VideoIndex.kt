package com.example.aicleanphonestorage.feature.videos.data

import android.database.sqlite.SQLiteDatabase
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.example.aicleanphonestorage.core.coroutines.TaskExecutor
import com.example.aicleanphonestorage.feature.filecleaner.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal sealed interface VideoRow {
    data class Month(val month: String, val count: Int, val bytes: Long, val selected: Int, val collapsed: Boolean) : VideoRow
    data class Video(val file: ScannedFile) : VideoRow
}

/** 标题和条目一起从 SQL 分页，月份也不全量放入 StateFlow。折叠仅影响展示，绝不改变删除快照。 */
internal class VideoIndex(private val index: ScanIndex, private val executor: TaskExecutor) {
    fun pager(scan: Long) = Pager(PagingConfig(pageSize = 60, initialLoadSize = 120, prefetchDistance = 12,
        maxSize = 240, enablePlaceholders = false)) { Source(scan) }

    suspend fun collapse(scan: Long, month: String, collapsed: Boolean) = executor.io {
        if (collapsed) index.writableDatabase.execSQL("INSERT OR IGNORE INTO video_collapsed(scan,month) VALUES (?,?)", arrayOf<Any>(scan, month))
        else index.writableDatabase.delete("video_collapsed", "scan=? AND month=?", arrayOf(scan.toString(), month))
        index.refresh()
    }

    internal fun page(scan: Long, offset: Int, limit: Int): List<VideoRow> {
        // 月份标题占一行；同月文件按修改时间倒序且以 id 作为稳定次序，跨页不会重复插入标题。
        val sql = """SELECT bucket, 0 AS kind, 0 AS file_id, 0 AS modified, COUNT(*) AS count,
            TOTAL(size) AS bytes, TOTAL(selected) AS selected,
            EXISTS(SELECT 1 FROM video_collapsed c WHERE c.scan=files.scan AND c.month=files.bucket) AS collapsed
            FROM files WHERE scan=? GROUP BY bucket
            UNION ALL SELECT bucket,1,id,modified,0,0,0,0 FROM files WHERE scan=?
            AND NOT EXISTS(SELECT 1 FROM video_collapsed c WHERE c.scan=files.scan AND c.month=files.bucket)
            ORDER BY bucket DESC,kind,modified DESC,file_id DESC LIMIT ? OFFSET ?"""
        return index.readableDatabase.rawQuery(sql, arrayOf(scan.toString(), scan.toString(), limit.toString(), offset.toString())).use { c ->
            buildList {
                while (c.moveToNext()) {
                    if (c.getInt(1) == 0) add(VideoRow.Month(c.getString(0), c.getInt(4), c.getDouble(5).toLong(), c.getInt(6), c.getInt(7) != 0))
                    else index.get(c.getLong(2))?.let { add(VideoRow.Video(it)) }
                }
            }
        }
    }

    fun reconcile(scan: Long, staging: Long) {
        val db = index.writableDatabase
        db.beginTransaction()
        try {
            // 先去除消失/内容元数据变化的旧项；变化项作为新项，默认不选中。
            db.execSQL("""DELETE FROM files WHERE scan=? AND NOT EXISTS
                (SELECT 1 FROM files n WHERE n.scan=? AND n.uri=files.uri AND n.size=files.size AND n.modified=files.modified)""", arrayOf(scan, staging))
            db.execSQL("""INSERT OR IGNORE INTO files(scan,uri,name,mime,size,modified,category,backend,scope,path,bucket)
                SELECT ?,uri,name,mime,size,modified,category,backend,scope,path,bucket FROM files WHERE scan=?""", arrayOf(scan, staging))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        index.refresh()
    }

    private inner class Source(private val scan: Long) : PagingSource<Int, VideoRow>() {
        init { index.register(this) }
        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, VideoRow> = try {
            val offset = params.key ?: 0
            val rows = executor.io { currentCoroutineContext().ensureActive(); page(scan, offset, params.loadSize) }
            LoadResult.Page(rows, if (offset == 0) null else (offset - 60).coerceAtLeast(0),
                if (rows.size < params.loadSize) null else offset + rows.size)
        } catch (e: CancellationException) { throw e } catch (e: Exception) { LoadResult.Error(e) }
        override fun getRefreshKey(state: PagingState<Int, VideoRow>): Int? = state.anchorPosition?.let { anchor ->
            val page = state.closestPageToPosition(anchor)
            page?.prevKey?.plus(60) ?: page?.nextKey?.minus(page.data.size) ?: 0
        }
    }

    companion object {
        fun create(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE video_collapsed(scan INTEGER NOT NULL, month TEXT NOT NULL, PRIMARY KEY(scan,month))")
            db.execSQL("CREATE INDEX files_video_month ON files(scan,bucket DESC,modified DESC,id DESC)")
        }
    }
}
