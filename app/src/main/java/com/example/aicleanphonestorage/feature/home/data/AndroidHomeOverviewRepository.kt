package com.example.aicleanphonestorage.feature.home.data

import com.example.aicleanphonestorage.feature.notifications.data.NotificationRulesStore
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** 首页只组合小型统计结果；平台/入口数据独立降级，基础存储读取错误仍交给首页错误状态。 */
internal class AndroidHomeOverviewRepository(
    private val base: HomeOverviewRepository,
    private val files: HomeFileMetricsSource,
    private val platform: HomePlatformMetricsSource,
    private val notifications: NotificationRulesStore,
) : HomeOverviewRepository {
    override fun observeOverview() =
        combine(
            base.observeOverview(),
            files.observe(),
            platform.observe(),
            notifications.selectedPackages
                .map<Set<String>, HomeToolMetric> {
                    HomeToolMetric.AppCount(it.size, selected = true)
                }
                .catch { if (it is IOException) emit(HomeToolMetric.Unavailable) else throw it },
        ) { overview, fileMetrics, system, notificationMetric ->
            overview.copy(
                tools =
                    fileMetrics.copy(
                        network = system.network,
                        apps = system.apps,
                        notifications = notificationMetric,
                    )
            )
        }
}
