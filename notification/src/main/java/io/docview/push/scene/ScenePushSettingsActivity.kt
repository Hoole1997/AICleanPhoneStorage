package io.docview.push.scene

import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.*
import com.google.android.material.switchmaterial.SwitchMaterial
import io.docview.push.NotificationRuntimeOwner
import io.docview.push.databinding.ScreenScenePushSettingsBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class SceneSettingsViewModel(private val repository: SceneRepository) : ViewModel() {
    val state = repository.state
    private val failure = MutableStateFlow(false)
    val failed = failure.asStateFlow()
    init { viewModelScope.launch { try { repository.initialize() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { failure.value = true } } }
    fun choose(scene: PushScene?, enabled: Boolean) { viewModelScope.launch {
        try { repository.choose(scene, enabled); failure.value = false }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failure.value = true }
    } }
}

/** 新场景自己的本地开关页面，不改变旧日龄/FCM/Alarm/解锁/退后台策略和系统通知设置。 */
class ScenePushSettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT), SystemBarStyle.light(Color.TRANSPARENT, Color.BLACK))
        val binding = ScreenScenePushSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val repository = (application as NotificationRuntimeOwner).notificationRuntime.scenes
        val model = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = SceneSettingsViewModel(repository) as T
        })[SceneSettingsViewModel::class.java]
        binding.back.setOnClickListener { finish() }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
        var rendering = false
        val switches = PushScene.entries.associateWith { scene ->
            SwitchMaterial(this).apply {
                setText(scene.label)
                textSize = 16f
                minHeight = (56 * resources.displayMetrics.density).toInt()
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setPadding(0, (8 * resources.displayMetrics.density).toInt(), 0, (8 * resources.displayMetrics.density).toInt())
                setOnCheckedChangeListener { _, checked -> if (!rendering) model.choose(scene, checked) }
                binding.options.addView(this)
            }
        }
        binding.allScenes.setOnCheckedChangeListener { _, checked -> if (!rendering) model.choose(null, checked) }
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch { model.state.collect { value ->
                rendering = true
                binding.allScenes.isEnabled = value != null
                binding.allScenes.isChecked = value?.choices?.enabled == true
                switches.forEach { (scene, control) ->
                    control.isChecked = value != null && scene !in value.choices.disabled
                    control.isEnabled = value?.choices?.enabled == true
                }
                rendering = false
            } }
            launch { model.failed.collect { binding.error.isVisible = it } }
        } }
    }
}
