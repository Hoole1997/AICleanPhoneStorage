package io.docview.push.controller

object BgNotiInterceptController {

    // 是否标记下一次拦截
    private var shouldInterceptNext = false

    /**
     * @return true-允许拉起，false-拦截不拉起
     */
    fun shouldLaunch(): Boolean {
        // 如果标记了下一次拦截，则拦截
        if (shouldInterceptNext) {
            shouldInterceptNext = false  // 自动清除标记
            return false
        }
        // 默认允许拉起
        return true
    }

    /**
     * 标记下一次拦截拉起
     */
    fun markNextIntercept() {
        shouldInterceptNext = true
    }

    /**
     * 清除拦截标记
     */
    fun clearIntercept() {
        shouldInterceptNext = false
    }

    /**
     * 判断是否标记了拦截
     */
    fun isInterceptMarked(): Boolean {
        return shouldInterceptNext
    }
}
