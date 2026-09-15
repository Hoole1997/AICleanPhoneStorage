package com.example.aicleanphonestorage.feature.rating

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.databinding.DialogRatingPromptBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/** 星级为应用内反馈选择；用户点击 Rate 后由宿主请求原生 Play 卡片，不模拟原生卡片或评价结果。 */
class RatingPromptDialog : BottomSheetDialogFragment() {
    private var binding: DialogRatingPromptBinding? = null
    private var stars = 5
    private var resultSent = false

    override fun getTheme() = R.style.ThemeOverlay_Clean_Rating

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        stars = (savedInstanceState?.getInt("stars", 5) ?: 5).coerceIn(1, 5)
        resultSent = savedInstanceState?.getBoolean("result.sent") ?: false
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val view = DialogRatingPromptBinding.inflate(inflater, container, false)
        binding = view
        view.ratingTitle.text = getString(R.string.rating_title, getString(R.string.app_name))
        ViewCompat.setAccessibilityHeading(view.ratingTitle, true)
        ViewCompat.setAccessibilityPaneTitle(view.root, view.ratingTitle.text)
        val buttons = listOf(view.ratingStar1, view.ratingStar2, view.ratingStar3, view.ratingStar4, view.ratingStar5)
        buttons.forEachIndexed { index, button ->
            button.contentDescription = getString(R.string.rating_star_description, index + 1)
            button.setOnClickListener { stars = index + 1; render() }
            ViewCompat.setAccessibilityDelegate(button, object : AccessibilityDelegateCompat() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = "android.widget.RadioButton"
                    info.isCheckable = true
                    info.isChecked = stars == index + 1
                }
            })
        }
        view.ratingSubmit.setOnClickListener {
            result(submit = true)
            dismiss()
        }
        render()
        return view.root
    }

    private fun render() {
        val view = binding ?: return
        listOf(view.ratingStar1, view.ratingStar2, view.ratingStar3, view.ratingStar4, view.ratingStar5).forEachIndexed { index, button ->
            button.setImageResource(if (index < stars) R.drawable.rating_star_filled else R.drawable.rating_star_empty)
            button.isSelected = stars == index + 1
            button.isEnabled = !resultSent
        }
        view.ratingSubmit.isEnabled = !resultSent
    }

    override fun onStart() {
        super.onStart()
        if (resultSent) { binding?.root?.visibility = View.INVISIBLE; dismiss(); return }
        (dialog as? BottomSheetDialog)?.apply {
            setCanceledOnTouchOutside(true)
            behavior.apply {
                maxWidth = (600 * resources.displayMetrics.density).toInt()
                maxHeight = (resources.displayMetrics.heightPixels * 0.9f).toInt()
                setShouldRemoveExpandedCorners(false)
                skipCollapsed = true
                state = BottomSheetBehavior.STATE_EXPANDED
            }
            window?.let {
                it.setDimAmount(0.7f)
                androidx.core.view.WindowInsetsControllerCompat(it, it.decorView).isAppearanceLightNavigationBars = true
                if (android.os.Build.VERSION.SDK_INT < 27) it.navigationBarColor = android.graphics.Color.BLACK
            }
        }
        parentFragmentManager.setFragmentResult(SHOWN, Bundle())
    }

    override fun onCancel(dialog: DialogInterface) {
        result(submit = false)
        super.onCancel(dialog)
    }

    private fun result(submit: Boolean) {
        if (resultSent) return
        resultSent = true
        render()
        parentFragmentManager.setFragmentResult(RESULT, Bundle().apply {
            putInt(STARS, stars); putBoolean(SUBMIT, submit)
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("stars", stars)
        outState.putBoolean("result.sent", resultSent)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() { binding = null; super.onDestroyView() }

    companion object {
        const val TAG = "rating.prompt"
        const val SHOWN = "rating.prompt.shown"
        const val RESULT = "rating.prompt.result"
        const val STARS = "stars"
        const val SUBMIT = "submit"
    }
}
