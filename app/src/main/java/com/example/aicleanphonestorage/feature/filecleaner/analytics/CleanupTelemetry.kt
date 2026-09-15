package com.example.aicleanphonestorage.feature.filecleaner.analytics

import com.example.aicleanphonestorage.core.analytics.*
import com.example.aicleanphonestorage.core.ui.completion.*
import com.example.aicleanphonestorage.feature.filecleaner.data.*
import com.example.aicleanphonestorage.feature.junkcleaner.data.*

/** 业务语义到飞书字段的唯一映射；不上传文件名、URI、路径、包名或扫描索引 ID。 */
internal class CleanupTelemetry(private val sink: EventSink = BusinessTelemetry) {
    fun scan(feature: CleanupFeature, totals: SelectionTotals) {
        val event = when (feature) {
            CleanupFeature.SCREENSHOTS -> MetricEvent.SHOT_SCAN_RESULT
            CleanupFeature.PHOTO_COMPRESS -> MetricEvent.PHOTO_SCAN_RESULT
            CleanupFeature.LARGE_FILES -> MetricEvent.LARGE_SCAN_RESULT
            CleanupFeature.UNUSED_FILES -> {
                // 扫描完成事实可上报；当前没有文档中的三分组统计，不能填 0 冒充。
                sink.send(MetricEvent.UNUSED_SCAN_RESULT, emptyMap())
                return
            }
            else -> return
        }
        sink.send(event, mapOf("count" to totals.count, "total_size" to mb(totals.bytes)))
    }
    fun junkScan(categories: List<JunkCategorySummary>) {
        // 四类分别上报，空目录按 0B 计；旧分类仍保留代码但不进入当前扫描。
        sink.send(MetricEvent.JUNK_SCAN_RESULT, mapOf(
            "result" to if (categories.any { it.count > 0 }) "has_junk" else "empty",
            "total_size" to mb(categories.sumOf { it.bytes }),
            "apk_size" to mb(categories.filter { it.kind == JunkKind.INSTALLERS }.sumOf { it.bytes }),
            "temp_size" to mb(categories.filter { it.kind == JunkKind.TEMPORARY }.sumOf { it.bytes }),
            "empty_folder_size" to mb(categories.filter { it.kind == JunkKind.EMPTY_FOLDERS }.sumOf { it.bytes }),
            "ad_file_size" to mb(categories.filter { it.kind == JunkKind.AD_FILES }.sumOf { it.bytes }),
        ))
    }
    fun junkGroup(kind: JunkKind) {
        group(kind)?.let { sink.send(MetricEvent.JUNK_GROUP_CLICK, mapOf("group" to it)) }
    }
    fun selection(feature: CleanupFeature, bucket: String?, selected: Boolean, totals: SelectionTotals) {
        val params = mutableMapOf<String, Any>("action" to if (selected) "check" else "uncheck")
        val event = when (feature) {
            CleanupFeature.SMART_CLEAN -> {
                params["group"] = group(JunkKind.from(bucket)) ?: return
                MetricEvent.JUNK_DETAIL_CHECK
            }
            CleanupFeature.SCREENSHOTS -> MetricEvent.SHOT_CHECK
            CleanupFeature.PHOTO_COMPRESS -> MetricEvent.PHOTO_CHECK
            CleanupFeature.LARGE_FILES -> MetricEvent.LARGE_FILE_CHECK
            // 现有平铺列表可以记录勾选；尚无分组时省略 group，覆盖报告明确列出。
            CleanupFeature.UNUSED_FILES -> MetricEvent.UNUSED_CHECK
        }
        if (feature == CleanupFeature.PHOTO_COMPRESS) params["selected_count"] = totals.selectedCount
        else params["selected_size"] = mb(totals.selectedBytes)
        sink.send(event, params)
    }
    fun cleanClick(feature: CleanupFeature, totals: SelectionTotals) {
        val event = when (feature) {
            CleanupFeature.SMART_CLEAN -> MetricEvent.JUNK_CLEAN_CLICK
            CleanupFeature.SCREENSHOTS -> MetricEvent.SHOT_CLEAN_CLICK
            CleanupFeature.PHOTO_COMPRESS -> MetricEvent.PHOTO_COMPRESS_CLICK
            CleanupFeature.LARGE_FILES -> MetricEvent.LARGE_CLEAN_CLICK
            CleanupFeature.UNUSED_FILES -> MetricEvent.UNUSED_CLEAN_CLICK
        }
        val params = if (feature == CleanupFeature.PHOTO_COMPRESS)
            mutableMapOf<String, Any>("selected_count" to totals.selectedCount)
        else mutableMapOf("selected_size" to mb(totals.selectedBytes) as Any)
        if (feature == CleanupFeature.SMART_CLEAN) params["button"] = if (totals.count == 0) "got_it" else "smart_clean"
        sink.send(event, params)
    }
    fun filter(filter: CleanupFilter) {
        val type = when (filter.category) {
            FileCategory.ALL -> "all"; FileCategory.PHOTOS -> "photos"; FileCategory.VIDEOS -> "videos"
            FileCategory.AUDIO -> "audio"; FileCategory.DOCUMENTS -> "document"; FileCategory.ARCHIVES -> "archives"
            FileCategory.APK -> "apk"; FileCategory.OTHER -> "other"
        }
        val size = when (filter.minimumBytes) { 10_000_000L -> "10mb"; 50_000_000L -> "50mb"; 100_000_000L -> "100mb"; 500_000_000L -> "500mb"; 1_000_000_000L -> "1gb"; else -> return }
        val time = when (filter.recentDays) { 0 -> "all"; 7 -> "1w"; 30 -> "1m"; 90 -> "3m"; 180 -> "6m"; 365 -> "1y"; else -> return }
        sink.send(MetricEvent.LARGE_FILTER_CHANGE, mapOf("filter_type" to type, "filter_size" to size, "filter_time" to time))
    }
    fun result(feature: CleanupFeature, report: CompletionReport) {
        if (feature == CleanupFeature.PHOTO_COMPRESS && report.kind == CompletionKind.COMPRESSION) {
            sink.send(MetricEvent.PHOTO_RESULT_SHOW, mapOf("compressed_count" to report.completed, "saved_size" to mb(report.reducedBytes)))
            return
        }
        val event = when (feature) {
            CleanupFeature.SMART_CLEAN -> MetricEvent.JUNK_RESULT_SHOW
            CleanupFeature.SCREENSHOTS -> MetricEvent.SHOT_RESULT_SHOW
            CleanupFeature.LARGE_FILES -> MetricEvent.LARGE_RESULT_SHOW
            CleanupFeature.UNUSED_FILES -> MetricEvent.UNUSED_RESULT_SHOW
            else -> return // 压缩后另行删除原图不是一次新的压缩完成。
        }
        val params = mutableMapOf<String, Any>("deleted_size" to mb(report.freedBytes))
        if (feature == CleanupFeature.SMART_CLEAN) {
            if (report.completed > 0) params["type"] = "cleaned"
            // 当前没有空扫描进入完成页的入口；不能仅凭 deleted=0 把取消/失败称为本来很干净。
        }
        sink.send(event, params)
    }
    companion object {
        fun mb(bytes: Long): Double = bytes.coerceAtLeast(0) / 1_000_000.0
        fun group(kind: JunkKind?): String? = when (kind) { JunkKind.INSTALLERS -> "apk"; JunkKind.TEMPORARY -> "temp"; JunkKind.EMPTY_FOLDERS -> "empty_folder"; JunkKind.AD_FILES -> "ad_file"; else -> null }
        fun page(feature: CleanupFeature): String = when (feature) {
            CleanupFeature.SMART_CLEAN -> "junk"; CleanupFeature.SCREENSHOTS -> "screenshots"; CleanupFeature.PHOTO_COMPRESS -> "photo"; CleanupFeature.LARGE_FILES -> "large"; CleanupFeature.UNUSED_FILES -> "unused"
        }
    }
}
