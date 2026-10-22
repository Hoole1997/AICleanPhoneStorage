package io.docview.push.scene

import android.content.Intent

/** 通知只传小型白名单快照；在启动页/首页恢复时使用，不传列表、完整 extras 或文件内容。 */
data class SceneLandingData(val code: Int, val reference: String = "", val occurredAt: Long = 0,
    val batteryPercent: Int? = null, val chargeDurationMs: Long? = null) {
    fun write(intent: Intent) {
        intent.putExtra(CODE, code).putExtra(REFERENCE, reference.take(512)).putExtra(TIME, occurredAt)
        batteryPercent?.takeIf { it in 0..100 }?.let { intent.putExtra(PERCENT, it) }
        chargeDurationMs?.takeIf { it in 0..604_800_000L }?.let { intent.putExtra(DURATION, it) }
    }
    companion object {
        const val CODE = "scene.landing.code"
        const val REFERENCE = "scene.landing.reference"
        const val TIME = "scene.landing.time"
        const val PERCENT = "scene.landing.percent"
        const val DURATION = "scene.landing.charge_duration"
        internal fun from(event: SceneEvent) = SceneLandingData(event.scene.code, event.reference, event.occurredAt, event.batteryPercent, event.chargeDurationMs)
        fun read(intent: Intent): SceneLandingData? {
            val scene = PushScene.fromCode(intent.getIntExtra(CODE, -1)) ?: return null
            val raw = intent.getStringExtra(REFERENCE).orEmpty().take(512)
            val reference = when (scene) {
                PushScene.APP_INSTALL, PushScene.APP_UNINSTALL -> raw.takeIf { it.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")) }.orEmpty()
                PushScene.SCREENSHOT, PushScene.DOWNLOAD -> raw.takeIf {
                    val uri = android.net.Uri.parse(it)
                    uri.scheme == "content" && uri.authority == "media" && uri.lastPathSegment?.toLongOrNull() != null
                }.orEmpty()
                else -> ""
            }
            return SceneLandingData(scene.code, reference, intent.getLongExtra(TIME, 0).coerceAtLeast(0),
                intent.getIntExtra(PERCENT, -1).takeIf { it in 0..100 }, intent.getLongExtra(DURATION, -1).takeIf { it in 0..604_800_000L })
        }
        fun clear(intent: Intent) { listOf(CODE, REFERENCE, TIME, PERCENT, DURATION).forEach(intent::removeExtra) }
    }
}
