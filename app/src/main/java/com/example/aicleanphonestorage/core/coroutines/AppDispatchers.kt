package com.example.aicleanphonestorage.core.coroutines

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** 调度器只在组装依赖时创建，测试可注入 TestDispatcher，不把线程策略散落到业务中。 */
data class AppDispatchers(
    val io: CoroutineDispatcher = Dispatchers.IO,
    val computation: CoroutineDispatcher = Dispatchers.Default,
)
