package com.example.aicleanphonestorage.app

import com.example.aicleanphonestorage.core.coroutines.AppDispatchers
import com.example.aicleanphonestorage.core.coroutines.TaskExecutor
import com.example.aicleanphonestorage.feature.home.data.EmptyHomeOverviewRepository
import com.example.aicleanphonestorage.feature.home.data.HomeOverviewRepository

/** 手动依赖注入的唯一组装入口。应用级对象禁止保存 Activity/View 或启动隐式后台任务。 */
class AppContainer {
    val taskExecutor: TaskExecutor by lazy { TaskExecutor(AppDispatchers()) }
    val homeOverviewRepository: HomeOverviewRepository by lazy { EmptyHomeOverviewRepository() }
}
