package io.docview.push.scene

import android.app.PendingIntent
import android.content.Context
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import io.docview.push.R

/** 新稿只影响场景通知；复用通知发布/点击基础设施，旧 RemoteViews XML 与字号完全保留。 */
internal object SceneNotificationViews {
    fun build(context: Context, scene: PushScene, text: CharSequence,
        button: String, pending: PendingIntent, animate: Boolean): Pair<RemoteViews, RemoteViews> {
        fun views(layout: Int, expanded: Boolean) = RemoteViews(context.packageName, layout).apply {
            setImageViewResource(R.id.iv, scene.icon)
            setTextViewText(R.id.sceneText, text)
            setTextViewText(R.id.tvAction, button)
            setOnClickPendingIntent(R.id.tvAction, pending)
            if (!expanded) {
                setInt(R.id.sceneText, "setMaxLines", compactLines(context, text))
                // Android 12+ 的系统折叠模板已在同一行提供 App 小图标，不能重复占用文案宽度。
                setViewVisibility(R.id.sceneAppLogo, if (android.os.Build.VERSION.SDK_INT >= 31) View.GONE else View.VISIBLE)
            }
            setViewVisibility(R.id.sceneButtonMotion, if (animate) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.sceneButtonStatic, if (animate) View.GONE else View.VISIBLE)
            if (expanded) {
                setViewVisibility(R.id.sceneArrowStart, if (animate) View.VISIBLE else View.GONE)
                setViewVisibility(R.id.sceneArrowEnd, if (animate) View.VISIBLE else View.GONE)
            }
            // 每次显式重置，SystemUI 的同 ID reapply 可在静态/动画模式间安全切换。
            removeAllViews(R.id.sceneIconSlot)
            setViewVisibility(R.id.iv, if (animate) View.GONE else View.VISIBLE)
            setViewVisibility(R.id.sceneIconSlot, if (animate) View.VISIBLE else View.GONE)
            if (animate) addView(R.id.sceneIconSlot, RemoteViews(context.packageName, animationLayout(scene)))
        }
        return views(R.layout.scene_notification_compact, false) to views(
            if (animate) R.layout.scene_notification_expanded else R.layout.scene_notification_expanded_static, true)
    }

    /** 系统折叠区上限 48dp。大字体/OEM 字体按真实两行高度退为一行，全文留在展开态。 */
    private fun compactLines(context: Context, text: CharSequence): Int {
        val paint = TextPaint().apply {
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14f, context.resources.displayMetrics)
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
            textLocale = context.resources.configuration.locales[0]
        }
        val sample = SpannableStringBuilder(text).append("\n").append(text)
        val layout = StaticLayout.Builder.obtain(sample, 0, sample.length, paint, 10000)
            .setIncludePad(false).setMaxLines(2)
            .apply { if (android.os.Build.VERSION.SDK_INT >= 28) setUseLineSpacingFromFallbacks(true) }.build()
        return if (layout.height <= 48 * context.resources.displayMetrics.density) 2 else 1
    }

    private fun animationLayout(scene: PushScene) = when (scene) {
        PushScene.CHARGE_START -> R.layout.scene_icon_charging_start
        PushScene.CHARGE_END -> R.layout.scene_icon_charging_end
        PushScene.LOW_BATTERY -> R.layout.scene_icon_low_battery
        PushScene.NETWORK -> R.layout.scene_icon_network
        PushScene.SCREENSHOT -> R.layout.scene_icon_screenshot
        PushScene.APP_INSTALL -> R.layout.scene_icon_app_installed
        PushScene.APP_UNINSTALL -> R.layout.scene_icon_app_uninstalled
        PushScene.DOWNLOAD -> R.layout.scene_icon_download
    }
}
