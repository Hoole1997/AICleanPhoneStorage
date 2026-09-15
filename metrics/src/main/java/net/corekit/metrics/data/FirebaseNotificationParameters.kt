package net.corekit.metrics.data

private val notificationEvents = setOf("Notific_Show", "Notific_Click", "Notific_Enter")

/** Firebase 普通字符串参数上限为 100 字符；仅在此适配通知文案，其他 reporter 仍收到原有界快照。 */
internal fun firebaseNotificationParameters(event: String, data: Map<String, Any>): Map<String, Any> {
    if (event !in notificationEvents) return data
    return data.mapValues { (key, value) ->
        if ((key == "title" || key == "text") && value is String && value.codePointCount(0, value.length) > 100)
            value.substring(0, value.offsetByCodePoints(0, 100))
        else value
    }
}
