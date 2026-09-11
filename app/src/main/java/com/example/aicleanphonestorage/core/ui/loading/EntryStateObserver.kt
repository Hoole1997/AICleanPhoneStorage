package com.example.aicleanphonestorage.core.ui.loading

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** 入口渲染会开关弹框/跳页，必须与 RESUMED 对齐；恢复时重放暂停期间产生的最终状态。 */
internal fun <T> AppCompatActivity.observeEntryState(state: StateFlow<T>, render: (T) -> Unit) =
    lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.RESUMED) { state.collect { render(it) } }
    }
