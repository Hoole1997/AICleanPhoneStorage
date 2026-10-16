package com.example.aicleanphonestorage.app.ad

/**
 * 主线程上的一次广告请求。主动关闭先失效请求再取消工作，避免同步取消回调/迟到结果重复续接。
 * 关闭加载框必须立即放行业务，不能等待广告网络或 SDK 的取消清理结束。
 */
internal class LoadingAdRequest(
    private val cancelWork: () -> Unit,
    private val hideLoading: () -> Unit,
    private val release: () -> Unit,
    private val onResult: (Boolean) -> Unit,
) {
    private var finished = false

    fun complete(success: Boolean) {
        if (finished) return
        finished = true
        release()
        onResult(success)
    }

    fun cancelLoading() {
        if (finished) return
        finished = true
        try {
            cancelWork()
        } finally {
            try {
                hideLoading()
            } finally {
                release()
                onResult(false)
            }
        }
    }
}
