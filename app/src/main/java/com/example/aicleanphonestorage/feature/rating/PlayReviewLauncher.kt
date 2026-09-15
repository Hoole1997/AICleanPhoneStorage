package com.example.aicleanphonestorage.feature.rating

import android.content.Context
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import com.google.android.gms.tasks.Task
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal fun interface PlayReviewLauncher {
    suspend fun launch(activity: FragmentActivity)
}

/** ReviewInfo 只在本次请求栈中使用；不缓存/序列化 token，不读取或推断 Google 的评分结果。 */
internal class GooglePlayReviewLauncher(
    private val managerFactory: (Context) -> ReviewManager = ReviewManagerFactory::create,
) : PlayReviewLauncher {
    override suspend fun launch(activity: FragmentActivity) {
        val manager: ReviewManager = managerFactory(activity.applicationContext)
        val info = manager.requestReviewFlow().awaitTask()
        if (activity.isFinishing || activity.isDestroyed || !activity.hasWindowFocus() ||
            !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        manager.launchReviewFlow(activity, info).awaitTask()
        // completion 不保证卡片真的展示或评论已提交，不能补报“评价成功”。
    }
}

private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (!continuation.isActive) return@addOnCompleteListener
        when {
            task.isCanceled -> continuation.cancel()
            task.isSuccessful -> continuation.resume(task.result)
            else -> continuation.resumeWithException(task.exception ?: IllegalStateException("Play review task failed"))
        }
    }
}
