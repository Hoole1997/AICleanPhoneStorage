package com.example.aicleanphonestorage.core.ui.completion

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.aicleanphonestorage.core.ui.motion.MotionPreferences
import com.example.aicleanphonestorage.databinding.ScreenCompletionBinding

/** 通用结果页仅呈现不可变摘要；业务确认、写入和返回目的地属于调用方。 */
class CompletionActivity : AppCompatActivity() {
    private lateinit var binding: ScreenCompletionBinding
    private lateinit var motion: CompletionMotion
    private lateinit var preferences: MotionPreferences
    private var resumed = false
    private var allowed = false
    private var report: CompletionReport? = null
    private var leaving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val value = CompletionContract.read(intent)
        if (value == null) {
            finish()
            return
        }
        report = value
        onBackPressedDispatcher.addCallback(this) { exitToHome() }
        enableEdgeToEdge(
            SystemBarStyle.dark(Color.TRANSPARENT),
            SystemBarStyle.light(Color.TRANSPARENT, Color.BLACK),
        )
        binding = ScreenCompletionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars =
                insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
                )
            binding.completionContent.setPadding(bars.left, bars.top, bars.right, 0)
            // 广告白底延伸到导航栏，深色导航图标不会落到蓝底；广告内容保留安全距离。
            val padding = (16 * resources.displayMetrics.density).toInt()
            binding.completionAd.setPadding(padding, padding, padding, padding + bars.bottom)
            binding.completionAd.minimumHeight =
                (137 * resources.displayMetrics.density).toInt() + bars.bottom
            androidx.core.view.WindowCompat.getInsetsController(window, binding.root)
                .isAppearanceLightNavigationBars = bars.bottom > 0
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
        ViewCompat.setAccessibilityHeading(binding.completionTitle, true)
        binding.completionBack.setOnClickListener { exitToHome() }
        binding.completionContinue.setOnClickListener { exitToHome() }
        binding.completionOriginals.setOnClickListener {
            complete(CompletionContract.REMOVE_ORIGINALS)
        }
        CompletionRenderer(binding).render(value)
        motion = CompletionMotion(binding, savedInstanceState != null)
        preferences =
            MotionPreferences(this) { enabled ->
                allowed = enabled
                updateMotion()
            }
    }

    private fun exitToHome() {
        if (leaving) return
        // 返回结果交给原功能页，它附上业务来源并导航首页；本页不请求退出广告。
        complete(CompletionContract.CONTINUE)
    }

    private fun complete(action: String) {
        if (leaving) return
        leaving = true
        setResult(
            RESULT_OK,
            Intent()
                .putExtra(CompletionContract.ACTION, action)
                .putExtra(CompletionContract.SOURCE, intent.getStringExtra(CompletionContract.SOURCE))
                .putExtra(CompletionContract.OPERATION, report?.operationId ?: 0),
        )
        finish()
    }

    private fun updateMotion() {
        if (::motion.isInitialized)
            motion.update(resumed, hasWindowFocus(), allowed && report?.celebrate == true)
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        if (::preferences.isInitialized) preferences.start()
        updateMotion()
    }

    override fun onPause() {
        resumed = false
        if (::preferences.isInitialized) preferences.stop()
        updateMotion()
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        updateMotion()
    }

    override fun onDestroy() {
        if (::motion.isInitialized) motion.stop()
        if (::preferences.isInitialized) preferences.stop()
        super.onDestroy()
    }
}
