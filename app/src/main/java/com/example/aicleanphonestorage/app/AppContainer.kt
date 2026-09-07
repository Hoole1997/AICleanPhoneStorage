package com.example.aicleanphonestorage.app

import android.content.Context
import com.example.aicleanphonestorage.feature.networktraffic.data.AndroidNetworkTrafficRepository
import com.example.aicleanphonestorage.feature.networktraffic.data.NetworkTrafficRepository
import com.example.aicleanphonestorage.feature.networktraffic.data.TrafficSnapshotTransfer
import com.example.aicleanphonestorage.core.coroutines.AppDispatchers
import com.example.aicleanphonestorage.core.coroutines.TaskExecutor
import com.example.aicleanphonestorage.feature.home.data.EmptyHomeOverviewRepository
import com.example.aicleanphonestorage.feature.home.data.HomeOverviewRepository

/** 手动依赖注入的唯一组装入口。应用级对象禁止保存 Activity/View 或启动隐式后台任务。 */
class AppContainer(context: Context) {
    private val applicationContext = context.applicationContext
    val taskExecutor: TaskExecutor by lazy { TaskExecutor(AppDispatchers()) }
    val trafficSnapshotTransfer: TrafficSnapshotTransfer by lazy { TrafficSnapshotTransfer() }
    val homeOverviewRepository: HomeOverviewRepository by lazy { EmptyHomeOverviewRepository() }
    val networkTrafficRepository: NetworkTrafficRepository by lazy {
        AndroidNetworkTrafficRepository(applicationContext, taskExecutor)
    }
}
