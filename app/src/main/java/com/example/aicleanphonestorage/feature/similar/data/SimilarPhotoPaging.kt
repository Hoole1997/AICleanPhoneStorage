package com.example.aicleanphonestorage.feature.similar.data

import androidx.paging.*
import com.example.aicleanphonestorage.core.coroutines.TaskExecutor
import com.example.aicleanphonestorage.feature.filecleaner.data.*
import kotlinx.coroutines.CancellationException

internal sealed interface SimilarRow {
    val key: String

    data class Header(
        override val key: String,
        val count: Int,
        val bytes: Long,
        val selected: Int,
        val deletable: Int,
    ) : SimilarRow

    data class Photos(override val key: String, val files: List<ScannedFile>) : SimilarRow

    data class Footer(override val key: String) : SimilarRow
}

/** 固定最多三张的小行分页，一个超大组也不创建嵌套列表或一次装入全部成员。跨页不打断网格行。 */
internal class SimilarPhotoPaging(
    private val index: ScanIndex,
    private val executor: TaskExecutor,
) {
    fun pager(scan: Long, columns: Int) =
        Pager(
            PagingConfig(
                pageSize = 20,
                initialLoadSize = 40,
                prefetchDistance = 4,
                maxSize = 80,
                enablePlaceholders = false,
            )
        ) {
            Source(scan, columns.coerceIn(2, 3))
        }

    internal fun page(scan: Long, columns: Int, offset: Int, limit: Int): List<SimilarRow> {
        val sql =
            """SELECT group_key,bytes,modified,count,0 AS kind,0 AS line FROM similar_groups WHERE scan=?
            UNION ALL SELECT g.group_key,g.bytes,g.modified,g.count,1,s.position/? FROM similar_groups g
            JOIN files f ON f.scan=g.scan AND f.group_key=g.group_key JOIN similar_signatures s ON s.file=f.id
            WHERE g.scan=? GROUP BY g.group_key,s.position/?
            UNION ALL SELECT group_key,bytes,modified,count,2,0 FROM similar_groups WHERE scan=?
            ORDER BY bytes DESC,modified DESC,group_key,kind,line LIMIT ? OFFSET ?"""
        val args =
            arrayOf(
                    scan,
                    columns.toLong(),
                    scan,
                    columns.toLong(),
                    scan,
                    limit.toLong(),
                    offset.toLong(),
                )
                .map { it.toString() }
                .toTypedArray()
        return index.readableDatabase.rawQuery(sql, args).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val group = c.getString(0)
                    when (c.getInt(4)) {
                        0 -> {
                            val t =
                                index.totals(
                                    ScanHandle(scan, CleanupFeature.SIMILAR_PHOTOS, 0, ""),
                                    CleanupFilter(bucket = group),
                                )
                            add(
                                SimilarRow.Header(
                                    group,
                                    c.getInt(3),
                                    c.getLong(1),
                                    t.selectedCount,
                                    t.count,
                                )
                            )
                        }
                        1 -> {
                            val line = c.getInt(5)
                            val files =
                                index.readableDatabase
                                    .rawQuery(
                                        """SELECT f.* FROM files f JOIN similar_signatures s ON s.file=f.id
                            WHERE f.scan=? AND f.group_key=? AND s.position>=? AND s.position<? ORDER BY s.position LIMIT ?""",
                                        arrayOf(
                                            scan.toString(),
                                            group,
                                            (line * columns).toString(),
                                            ((line + 1) * columns).toString(),
                                            columns.toString(),
                                        ),
                                    )
                                    .use { cursor ->
                                        buildList {
                                            while (cursor.moveToNext()) add(index.row(cursor))
                                        }
                                    }
                            add(SimilarRow.Photos("photos:$group:$line", files))
                        }
                        else -> add(SimilarRow.Footer("footer:$group"))
                    }
                }
            }
        }
    }

    private inner class Source(val scan: Long, val columns: Int) : PagingSource<Int, SimilarRow>() {
        init {
            index.register(this)
        }

        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, SimilarRow> =
            try {
                val offset = params.key ?: 0
                val rows = executor.io { page(scan, columns, offset, params.loadSize) }
                LoadResult.Page(
                    rows,
                    if (offset == 0) null else (offset - 20).coerceAtLeast(0),
                    if (rows.size < params.loadSize) null else offset + rows.size,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LoadResult.Error(e)
            }

        override fun getRefreshKey(state: PagingState<Int, SimilarRow>): Int? =
            state.anchorPosition?.let { a ->
                val page = state.closestPageToPosition(a)
                page?.prevKey?.plus(20) ?: page?.nextKey?.minus(page.data.size) ?: 0
            }
    }
}
