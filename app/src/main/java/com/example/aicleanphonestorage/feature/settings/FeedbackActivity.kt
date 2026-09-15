package com.example.aicleanphonestorage.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.widget.doAfterTextChanged
import com.example.aicleanphonestorage.BuildConfig
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.databinding.ViewSettingsFeedbackBinding

/** 草稿最多 2000 字，由原生 View 状态恢复；不落库、上传、记录日志或收集手机标识。 */
class FeedbackActivity : AppCompatActivity() {
    private lateinit var binding: ViewSettingsFeedbackBinding
    private var openingEmail = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val page = SettingsPage.install(this, R.string.settings_feedback)
        binding = ViewSettingsFeedbackBinding.inflate(layoutInflater, page.settingsBody, true)
        ViewCompat.setAccessibilityHeading(binding.feedbackHeading, true)
        binding.feedbackMessage.doAfterTextChanged {
            binding.feedbackInput.error = null
            updateActions()
        }
        binding.feedbackSend.setOnClickListener {
            val text = binding.feedbackMessage.text?.toString()?.trim().orEmpty()
            if (text.isBlank()) {
                binding.feedbackInput.error = getString(R.string.settings_feedback_required)
                return@setOnClickListener
            }
            if (openingEmail) return@setOnClickListener
            val intent =
                SettingsDestinations.feedback(
                    BuildConfig.FEEDBACK_EMAIL,
                    getString(R.string.settings_feedback_subject, getString(R.string.app_name)),
                    text,
                )
            openingEmail =
                SettingsDestinations.open(this, intent, R.string.settings_email_unavailable)
            updateActions()
        }
        binding.feedbackCopy.setOnClickListener {
            val text = binding.feedbackMessage.text?.toString()?.trim().orEmpty()
            if (text.isNotEmpty()) {
                getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(
                        ClipData.newPlainText(getString(R.string.settings_feedback), text)
                    )
                if (android.os.Build.VERSION.SDK_INT < 33)
                    Toast.makeText(this, R.string.settings_feedback_copied, Toast.LENGTH_SHORT)
                        .show()
            }
        }
        updateActions()
    }

    override fun onResume() {
        super.onResume()
        openingEmail = false
        updateActions()
    }

    private fun updateActions() {
        val hasText = !binding.feedbackMessage.text.isNullOrBlank()
        binding.feedbackSend.isEnabled = hasText && !openingEmail
        binding.feedbackCopy.isEnabled = hasText
        binding.feedbackSend.alpha = if (binding.feedbackSend.isEnabled) 1f else 0.45f
        binding.feedbackCopy.alpha = if (hasText) 1f else 0.45f
    }
}
