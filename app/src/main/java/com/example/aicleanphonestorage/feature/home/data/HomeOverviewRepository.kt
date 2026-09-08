package com.example.aicleanphonestorage.feature.home.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** 读取摘要，不触发扫描。真实实现必须 main-safe，且在取消订阅时释放监听器、Cursor 和流。 后续在 data 包接入 MediaStore/数据库，页面不需要依赖平台数据源。 */
interface HomeOverviewRepository {
    fun observeOverview(): Flow<HomeOverview>
}

/** 基础架构阶段的显式空实现；没有模拟扫描结果，也不会读取设备文件或申请权限。 */
class EmptyHomeOverviewRepository : HomeOverviewRepository {
    override fun observeOverview(): Flow<HomeOverview> = flowOf(HomeOverview())
}
