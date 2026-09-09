package io.docview.push.check

/**
 * 通知拦截原因枚举
 */
enum class BlockReason(val reason: String, val description: String) {
    // 基础条件检查失败
    NO_PERMISSION("no_permission", "无通知权限"),
    NOTIFICATION_DISABLED("notification_disabled", "通知开关已关闭"),
    DO_NOT_DISTURB("do_not_disturb", "免打扰时间段"),
    APP_IN_FOREGROUND("app_in_foreground", "应用在前台"),
    DAILY_LIMIT_REACHED("daily_limit_reached", "达到每日通知次数限制"),
    NEW_USER_COOLDOWN("new_user_cooldown", "新用户冷却时间"),

    // 触发间隔检查失败
    TRIGGER_INTERVAL_NOT_MET("trigger_interval_not_met", "触发间隔未满足"),

    // 其他原因
    UNKNOWN("unknown", "未知原因");

    companion object {
        /**
         * 根据原因字符串获取枚举
         */
        fun fromReason(reason: String): BlockReason {
            return values().find { it.reason == reason } ?: UNKNOWN
        }
    }
}
