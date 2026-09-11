package com.example.aicleanphonestorage.core.analytics

/** 飞书 9 张工作表的事件名；未实现业务不通过虚构调用补齐。 */
internal enum class MetricEvent(val wireName: String) {
    APP_LAUNCH("app_launch"),
    PAGE_SHOW("page_show"),
    PAGE_LEAVE("page_leave"),
    HOME_STATE_SHOW("home_state_show"),
    CLEAN_NOW_CLICK("clean_now_click"),
    FEATURE_ENTRY_CLICK("feature_entry_click"),
    RATE_SHOW("rate_show"),
    RATE_CLICK("rate_click"),
    JUNK_SCAN_RESULT("junk_scan_result"),
    JUNK_GROUP_CLICK("junk_group_click"),
    JUNK_DETAIL_CHECK("junk_detail_check"),
    JUNK_CLEAN_CLICK("junk_clean_click"),
    JUNK_RESULT_SHOW("junk_result_show"),
    TRAFFIC_PAGE_SHOW("traffic_page_show"),
    TRAFFIC_MANAGER_CLICK("traffic_manager_click"),
    NOTIFY_PAGE_SHOW("notify_page_show"),
    NOTIFY_CLEAN_CLICK("notify_clean_click"),
    NOTIFY_CLEAN_RESULT("notify_clean_result"),
    SHOT_SCAN_RESULT("shot_scan_result"),
    SHOT_CHECK("shot_check"),
    SHOT_CLEAN_CLICK("shot_clean_click"),
    SHOT_RESULT_SHOW("shot_result_show"),
    PHOTO_SCAN_RESULT("photo_scan_result"),
    PHOTO_CHECK("photo_check"),
    PHOTO_COMPRESS_CLICK("photo_compress_click"),
    PHOTO_RESULT_SHOW("photo_result_show"),
    LARGE_SCAN_RESULT("large_scan_result"),
    LARGE_FILTER_CHANGE("large_filter_change"),
    LARGE_FILE_CHECK("large_file_check"),
    LARGE_CLEAN_CLICK("large_clean_click"),
    LARGE_RESULT_SHOW("large_result_show"),
    APPS_PAGE_SHOW("apps_page_show"),
    APPS_UNINSTALL_CLICK("apps_uninstall_click"),
    APPS_UNINSTALL_JUMP("apps_uninstall_jump"),
    UNUSED_SCAN_RESULT("unused_scan_result"),
    UNUSED_GROUP_CLICK("unused_group_click"),
    UNUSED_CHECK("unused_check"),
    UNUSED_CLEAN_CLICK("unused_clean_click"),
    UNUSED_RESULT_SHOW("unused_result_show"),
    NOTIFBAR_ENTRY_CLICK("notifbar_entry_click"),
}

internal fun interface EventSink {
    fun send(event: MetricEvent, parameters: Map<String, Any>)
}
