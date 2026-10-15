package com.example.aicleanphonestorage.feature.speedtest.ui

import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.databinding.ItemSpeedDetailBinding
import com.example.aicleanphonestorage.databinding.ItemSpeedHeaderValueBinding
import com.example.aicleanphonestorage.databinding.ItemSpeedMetricBinding
import com.example.aicleanphonestorage.databinding.ScreenSpeedTestBinding
import com.example.aicleanphonestorage.feature.speedtest.data.SpeedTestMetrics
import com.example.aicleanphonestorage.feature.speedtest.data.SpeedTestNetwork
import com.example.aicleanphonestorage.feature.speedtest.data.SpeedTestNetworkType
import com.example.aicleanphonestorage.feature.speedtest.data.SpeedTestPhase
import com.example.aicleanphonestorage.feature.speedtest.data.SpeedTestProgress
import com.example.aicleanphonestorage.feature.speedtest.data.SpeedTestServer
import java.text.NumberFormat
import kotlin.math.floor
import kotlin.math.roundToLong

/** 状态到视图的纯映射：测速中与结果页共用指标卡/节点卡，未测得的值显示占位符而非 0。 */
internal class SpeedTestRenderer(private val binding: ScreenSpeedTestBinding) {
    private val context = binding.root.context
    private val locale = context.resources.configuration.locales[0]
    private val integers = NumberFormat.getIntegerInstance(locale)
    private val decimals = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }
    private val percent = NumberFormat.getPercentInstance(locale)
    private val unknown = context.getString(R.string.home_unknown_value)

    init {
        with(binding.speedMetrics) {
            label(metricsDownload, R.drawable.ic_speed_download, R.string.speed_test_download)
            label(metricsUpload, R.drawable.ic_speed_download, R.string.speed_test_upload)
            // 稿件中上传图标是下载图标的垂直镜像（6554:842 -scale-y），不另做资源。
            metricsUpload.headerIcon.scaleY = -1f
            label(metricsPing, R.drawable.ic_speed_ping, R.string.speed_test_ping)
            label(metricsDownloadJitter, R.drawable.ic_speed_download_jitter, R.string.speed_test_download_jitter)
            label(metricsUploadJitter, R.drawable.ic_speed_upload_jitter, R.string.speed_test_upload_jitter)
        }
        binding.speedDetails.detailsNetwork.detailIcon.setImageResource(R.drawable.ic_speed_network)
        binding.speedDetails.detailsServer.detailIcon.setImageResource(R.drawable.ic_speed_server)
        // 长节点名/运营商和放大字体须自然换行；窄屏或大字号不挤在两个半宽列中。
        val configuration = context.resources.configuration
        if (configuration.fontScale >= 1.5f || configuration.screenWidthDp < 360) {
            with(binding.speedDetails.detailsColumns) {
                orientation = LinearLayout.VERTICAL
                for (index in 0 until childCount) {
                    getChildAt(index).layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply { topMargin = if (index == 0) 0 else (16 * resources.displayMetrics.density).roundToLong().toInt() }
                }
            }
        }
    }

    fun render(state: SpeedTestUiState) {
        when (state) {
            is SpeedTestUiState.Testing -> {
                val progress = state.progress
                binding.speedTesting.isVisible = true
                binding.speedFooter.isVisible = false
                binding.speedRing.setProgress(progress.fraction)
                binding.speedPercent.text = percent.format(floor(progress.fraction * 100.0) / 100.0)
                binding.speedPhase.setText(phaseText(progress.phase))
                metrics(progress.metrics)
                details(state.network, progress.server, testId = null, showTestId = false)
            }
            // 等待扫描完成插屏期间保持真实 100% 测速画面与最终数值，不提前露出结果页。
            is SpeedTestUiState.Done -> if (!state.revealed) render(
                SpeedTestUiState.Testing(SpeedTestProgress(SpeedTestPhase.Upload, 1f, state.result.metrics, state.result.server), state.network)
            ) else {
                binding.speedTesting.isVisible = false
                binding.speedFooter.isVisible = true
                metrics(state.result.metrics)
                details(state.network, state.result.server, state.result.testId, showTestId = true)
            }
            // 失败弹框覆盖页面；有上次结果时底层保持该结果，否则保持当前测速画面。
            is SpeedTestUiState.Failed -> state.previous?.let(::render)
        }
    }

    private fun metrics(metrics: SpeedTestMetrics) = with(binding.speedMetrics) {
        header(metricsDownload, metrics.downloadBytesPerSecond)
        header(metricsUpload, metrics.uploadBytesPerSecond)
        metric(metricsPing, metrics.pingMs)
        metric(metricsDownloadJitter, metrics.downloadJitterMs)
        metric(metricsUploadJitter, metrics.uploadJitterMs)
    }

    private fun details(network: SpeedTestNetwork?, server: SpeedTestServer?, testId: String?, showTestId: Boolean) {
        with(binding.speedDetails) {
            detail(detailsNetwork, network?.let(::networkName) ?: unknown, network?.model?.takeIf { it.isNotBlank() } ?: unknown)
            detail(
                detailsServer,
                server?.provider ?: unknown,
                server?.node?.let { context.getString(R.string.speed_test_node, it) } ?: unknown,
            )
            detailsTestId.isVisible = showTestId && testId != null
            detailsTestId.text = testId?.let { context.getString(R.string.speed_test_id, it) }
        }
    }

    private fun label(item: ItemSpeedHeaderValueBinding, @DrawableRes icon: Int, @StringRes text: Int) {
        item.headerIcon.setImageResource(icon)
        item.headerLabel.setText(text)
    }

    private fun label(item: ItemSpeedMetricBinding, @DrawableRes icon: Int, @StringRes text: Int) {
        item.metricIcon.setImageResource(icon)
        item.metricLabel.setText(text)
    }

    /** 吞吐量按稿件显示 KB/s（1KB = 1000 字节）整数，带本地化分组。 */
    private fun header(item: ItemSpeedHeaderValueBinding, bytesPerSecond: Double?) {
        item.headerValue.text = bytesPerSecond?.let { integers.format((it / 1000).roundToLong()) } ?: unknown
        // 未测得时保留单位行高度，使下载/上传两列标签始终同一水平线。
        item.headerUnit.isInvisible = bytesPerSecond == null
        item.root.contentDescription = listOf(item.headerLabel.text, item.headerValue.text, if (bytesPerSecond != null) item.headerUnit.text else null)
            .filterNotNull().joinToString(" ")
    }

    /** 10ms 以下保留一位小数，避免低延迟网络全部显示为 0/1。 */
    private fun metric(item: ItemSpeedMetricBinding, millis: Double?) {
        item.metricValue.text = millis?.let { if (it < 10) decimals.format(it) else integers.format(it.roundToLong()) } ?: unknown
        item.metricUnit.isVisible = millis != null
        item.root.contentDescription = listOf(item.metricLabel.text, item.metricValue.text, if (millis != null) item.metricUnit.text else null)
            .filterNotNull().joinToString(" ")
    }

    private fun detail(item: ItemSpeedDetailBinding, title: String, subtitle: String) {
        item.detailTitle.text = title
        item.detailSubtitle.text = subtitle
        item.root.contentDescription = "$title, $subtitle"
    }

    private fun networkName(network: SpeedTestNetwork): String = when (network.type) {
        SpeedTestNetworkType.Wifi -> context.getString(R.string.speed_test_network_wifi)
        SpeedTestNetworkType.Cellular -> network.carrier ?: context.getString(R.string.speed_test_network_cellular)
        SpeedTestNetworkType.Ethernet -> context.getString(R.string.speed_test_network_ethernet)
        SpeedTestNetworkType.Other -> context.getString(R.string.speed_test_network_other)
    }

    @StringRes
    private fun phaseText(phase: SpeedTestPhase) = when (phase) {
        SpeedTestPhase.Connecting -> R.string.speed_test_phase_connecting
        SpeedTestPhase.Latency -> R.string.speed_test_phase_latency
        SpeedTestPhase.Download -> R.string.speed_test_phase_download
        SpeedTestPhase.Upload -> R.string.speed_test_phase_upload
    }
}
