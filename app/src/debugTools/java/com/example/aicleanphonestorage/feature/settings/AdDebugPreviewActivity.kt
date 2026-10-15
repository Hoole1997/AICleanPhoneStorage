package com.example.aicleanphonestorage.feature.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.databinding.ViewAdDebugPreviewBinding
import com.example.aicleanphonestorage.feature.settings.addebug.DebugAdFormat
import com.example.aicleanphonestorage.feature.settings.addebug.SdkDebugAdPresenter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 每次请求使用独立宿主；结束页面会触发 SDK 自身的 Activity 销毁清理，不依赖其混淆内部 API。 */
class AdDebugPreviewActivity : AppCompatActivity() {
    private val presenter = SdkDebugAdPresenter()
    private lateinit var binding: ViewAdDebugPreviewBinding
    private var request: Job? = null
    private var requestedFormat: DebugAdFormat? = null
    private var requestStarted = false

    private val processObserver = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) {
            // SDK 全屏 Activity 属于前台进程，不在本页 onStop 时误取消；真正退后台则关闭宿主。
            request?.cancel()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val format = runCatching {
            DebugAdFormat.valueOf(intent.getStringExtra(EXTRA_FORMAT).orEmpty())
        }.getOrNull()
        if (format == null) {
            finish()
            return
        }
        // 不在系统重建后自动再发一次真实广告请求。
        if (savedInstanceState != null) {
            finishWithResult(getString(R.string.ad_debug_interrupted))
            return
        }
        val page = SettingsPage.install(this, R.string.ad_debug_preview_title)
        binding = ViewAdDebugPreviewBinding.inflate(layoutInflater, page.settingsBody, true)
        binding.adDebugPreviewClose.setOnClickListener { finish() }
        binding.adDebugPreviewContainer.isVisible = format.isEmbedded()
        requestedFormat = format
        ProcessLifecycleOwner.get().lifecycle.addObserver(processObserver)
    }

    override fun onPostResume() {
        super.onPostResume()
        startRequestWhenReady()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) startRequestWhenReady()
    }

    private fun startRequestWhenReady() {
        val format = requestedFormat ?: return
        if (requestStarted || isFinishing || !hasWindowFocus() ||
            !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        // 新宿主 onCreate 时尚不可展示广告；只消费一次前台且有焦点的事件，不定时重试。
        requestStarted = true
        request = lifecycleScope.launch {
            try {
                val result = presenter.show(this@AdDebugPreviewActivity, binding.adDebugPreviewContainer, format)
                if (format.isEmbedded()) {
                    binding.adDebugPreviewProgress.isVisible = false
                    binding.adDebugPreviewResult.text = result
                    setResult(RESULT_OK, resultIntent(result))
                } else {
                    finishWithResult(result)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                finishWithResult(getString(R.string.ad_debug_error, error.message ?: error.javaClass.simpleName))
            }
        }
    }

    private fun finishWithResult(message: String) {
        setResult(RESULT_OK, resultIntent(message))
        finish()
    }

    override fun onDestroy() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(processObserver)
        request?.cancel()
        if (this::binding.isInitialized) presenter.clear(this, binding.adDebugPreviewContainer)
        super.onDestroy()
    }

    private fun resultIntent(message: String) = Intent().putExtra(EXTRA_RESULT, message.take(4_096))

    private fun DebugAdFormat.isEmbedded() = this == DebugAdFormat.NATIVE || this == DebugAdFormat.BANNER

    companion object {
        private const val EXTRA_FORMAT = "ad_debug.format"
        internal const val EXTRA_RESULT = "ad_debug.result"

        internal fun intent(context: Context, format: DebugAdFormat): Intent =
            Intent(context, AdDebugPreviewActivity::class.java).putExtra(EXTRA_FORMAT, format.name)
    }
}
