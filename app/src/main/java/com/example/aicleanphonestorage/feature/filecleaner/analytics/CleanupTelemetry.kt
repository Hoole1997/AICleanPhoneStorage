package com.example.aicleanphonestorage.feature.filecleaner.analytics

import com.example.aicleanphonestorage.core.analytics.*
import com.example.aicleanphonestorage.core.ui.completion.*
import com.example.aicleanphonestorage.feature.filecleaner.data.*
import com.example.aicleanphonestorage.feature.junkcleaner.data.*
import com.example.aicleanphonestorage.feature.unused.data.*

/** 业务语义到飞书字段的唯一映射；不上传文件名、URI、路径、包名或扫描索引 ID。 */
internal class CleanupTelemetry(private val sink: EventSink = BusinessTelemetry) {
    fun scan(feature: CleanupFeature, totals: SelectionTotals) {
        val event = when (feature) {
            CleanupFeature.SCREENSHOTS -> MetricEvent.SHOT_SCAN_RESULT
            CleanupFeature.PHOTO_COMPRESS -> MetricEvent.PHOTO_SCAN_RESULT
            CleanupFeature.LARGE_FILES -> MetricEvent.LARGE_SCAN_RESULT
            CleanupFeature.UNUSED_FILES -> return // 由实际三分组汇总上报，不使用总量伪装分组值。
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
    fun unusedScan(groups: List<UnusedGroup>) {
        sink.send(MetricEvent.UNUSED_SCAN_RESULT, mapOf(
            "installed_apk_size" to mb(groups.filter { it.kind == UnusedKind.INSTALLED_APK }.sumOf { it.totals.bytes }),
            "residue_size" to mb(groups.filter { it.kind == UnusedKind.RESIDUE }.sumOf { it.totals.bytes }),
            "download_size" to mb(groups.filter { it.kind == UnusedKind.DOWNLOAD }.sumOf { it.totals.bytes }),
        ))
    }
    fun unusedGroup(kind: UnusedKind) = sink.send(MetricEvent.UNUSED_GROUP_CLICK, mapOf("group" to kind.wire))
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
            CleanupFeature.UNUSED_FILES -> {
                params["group"] = UnusedKind.from(bucket)?.wire ?: return
                MetricEvent.UNUSED_CHECK
            }
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
            // 空扫描由结果摘要明确标记；删除失败、取消或删除 0B 空目录都不能据此推断。
            when {
                report.emptyScan -> params["type"] = "already_clean"
                report.completed > 0 -> params["type"] = "cleaned"
            }
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
