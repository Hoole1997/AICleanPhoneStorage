package com.example.aicleanphonestorage.feature.settings.addebug

import com.android.common.bill.ads.bidding.AdCacheStatusController
import com.android.common.bill.ads.bidding.AdSourceController
import com.android.common.bill.ads.bidding.AdSourceController.AdSource
import com.android.common.bill.ads.config.AdPlatform
import com.android.common.bill.ads.config.AdType
import com.android.common.bill.ads.frequency.AdFrequencyController
import com.android.common.bill.ads.frequency.AdFrequencyStatus
import com.android.common.bill.ads.interceptor.GlobalAdSwitchInterceptor
import com.android.common.risk.AdRisk
import com.example.aicleanphonestorage.BuildConfig
import com.example.aicleanphonestorage.app.ad.AdSdkInitializer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 只使用广告 SDK 发布包保留的公开 API。SDK 的源选择及全局开关内部读取 DataStore，
 * 因此连同步 getter 也必须离开主线程；所有调试实例共用有界 dispatcher 和写入锁。
 */
internal class SdkAdDebugSettings : AdDebugSettings {
    override suspend fun readControls(): AdDebugControls = withContext(ioDispatcher) {
        controlsLock.withLock {
            AdDebugControls(
                source = AdSourceController.getCurrentSource(),
                globalEnabled = GlobalAdSwitchInterceptor.isGlobalAdEnabled(),
            )
        }
    }

    override suspend fun setSource(source: AdSource) {
        withContext(ioDispatcher) {
            controlsLock.withLock { AdSourceController.setCurrentSource(source) }
        }
    }

    override suspend fun setGlobalEnabled(enabled: Boolean) {
        withContext(ioDispatcher) {
            controlsLock.withLock { setGlobalSwitch(enabled) }
        }
    }

    override suspend fun resetDefaults() {
        withContext(ioDispatcher) {
            controlsLock.withLock {
                AdSourceController.setCurrentSource(AdSource.BIDDING)
                setGlobalSwitch(true)
                // 平台 PROCESS/TODAY 与行为风控不属于本页；不能清除真实设备风险处置。
            }
        }
    }

    override suspend fun diagnostics(format: DebugAdFormat, source: AdSource): String =
        withContext(ioDispatcher) {
            controlsLock.withLock {
                val adType = format.toSdkType()
                buildString {
                    appendLine("SDK initialization=${AdSdkInitializer.state.value.name}")
                    appendLine("Format=${format.name}; source=${source.name}")
                    appendLine("Global ads enabled=${GlobalAdSwitchInterceptor.isGlobalAdEnabled()}")
                    appendLine("XM device SDK configured=${BuildConfig.XM_RISK_ENABLED}")
                    appendQuery("Total frequency") {
                        frequencySummary(AdFrequencyController.getTotalFrequencyStatus(adType))
                    }
                    source.platforms().forEach { platform ->
                        appendLine()
                        appendLine("[${platform.name}]")
                        appendQuery("Frequency") {
                            frequencySummary(AdFrequencyController.getPlatformFrequencyStatus(adType, platform))
                        }
                        appendQuery("Cache/platform") {
                            val status = AdCacheStatusController.getPlatformCacheStatus(adType, platform)
                            "available=${status.platformAvailable}; cached=${status.hasCache}; " +
                                "usableCache=${status.hasAvailableCache}; reason=${status.blockReason ?: "none"}"
                        }
                    }
                    appendLine()
                    appendLine("Platform availability includes configuration, initialization and platform risk restrictions.")
                    appendLine("The SDK does not expose separate PROCESS/TODAY or behavior risk status.")
                    appendQuery("Risk signal policy (configuration only)") {
                        val config = AdRisk.signalConfig.value
                        "vpn=${config.vpnAdConfig}; dns=${config.dnsAdConfig}; ip=${config.ipAdConfig}; " +
                            "emulator=${config.simulatorAdConfig}; adb=${config.adbAdConfig}; sim=${config.simAdConfig}; " +
                            "noPlay=${config.noGpAdConfig}; androidBelow9=${config.sys9LowAdConfig}; " +
                            "play=${config.playAdConfig}"
                    }
                    appendLine("Signal policy values: -1=not configured; 0=block AdMob; 1=block all when detected.")
                    append("A usable cache does not guarantee fill or an impression; SDK show gates are checked again.")
                }.take(MAX_DIAGNOSTIC_LENGTH)
            }
        }

    private fun setGlobalSwitch(enabled: Boolean) {
        if (enabled) GlobalAdSwitchInterceptor.enableGlobalAd()
        else GlobalAdSwitchInterceptor.disableGlobalAd()
    }

    /** 诊断失败显示未知，不把异常冒充已放行；不输出 SDK 异常里的广告位、URL 或凭据。 */
    private inline fun StringBuilder.appendQuery(label: String, query: () -> String) {
        append(label).append(": ")
        try {
            appendLine(query())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            appendLine("unavailable")
        } catch (_: LinkageError) {
            appendLine("unavailable (SDK API unavailable)")
        }
    }

    private fun frequencySummary(status: AdFrequencyStatus): String =
        "allowed=${status.isAllowed}; reason=${status.blockReasonKey}; " +
            "shows=${status.dailyShowCount}/${status.maxDailyShow} (left=${status.remainingShowCount}); " +
            "clicks=${status.dailyClickCount}/${status.maxDailyClick} (left=${status.remainingClickCount}); " +
            "interval=${status.lastShowIntervalSeconds}s/${status.minIntervalSeconds}s " +
            "(wait=${status.remainingIntervalSeconds}s)"

    private fun DebugAdFormat.toSdkType(): AdType = when (this) {
        DebugAdFormat.APP_OPEN -> AdType.APP_OPEN
        DebugAdFormat.INTERSTITIAL -> AdType.INTERSTITIAL
        DebugAdFormat.REWARDED -> AdType.REWARDED
        DebugAdFormat.FULL_SCREEN_NATIVE -> AdType.FULL_SCREEN_NATIVE
        DebugAdFormat.NATIVE -> AdType.NATIVE
        DebugAdFormat.BANNER -> AdType.BANNER
    }

    private fun AdSource.platforms(): List<AdPlatform> = when (this) {
        AdSource.BIDDING -> AdPlatform.entries
        AdSource.ADMOB -> listOf(AdPlatform.ADMOB)
        AdSource.GAM -> listOf(AdPlatform.GAM)
        AdSource.PANGLE -> listOf(AdPlatform.PANGLE)
        AdSource.TOPON -> listOf(AdPlatform.TOPON)
        AdSource.MAX -> listOf(AdPlatform.MAX)
    }

    private companion object {
        val ioDispatcher = Dispatchers.IO.limitedParallelism(2)
        val controlsLock = Mutex()
        const val MAX_DIAGNOSTIC_LENGTH = 6_000
    }
}
