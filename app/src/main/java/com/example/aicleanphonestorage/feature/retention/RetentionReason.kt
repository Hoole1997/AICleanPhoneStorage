package com.example.aicleanphonestorage.feature.retention

/** 顺序对应现有清理产品原因选项；协议值与界面翻译独立，不能直接上传索引或显示文案。 */
internal enum class RetentionReason(val wire: String) {
    DIFFICULT("difficult_to_use"),
    ADS("too_many_ads"),
    // 清理效果不符合预期与运行卡顿均属于体验问题；不能冒充“找到了类似应用”。
    RESULTS("poor_user_experience"),
    FEATURES("unmet_needs"),
    SLOW("poor_user_experience"),
    OTHER("other");

    companion object {
        fun fromSelection(index: Int?): RetentionReason? = index?.let { entries.getOrNull(it) }
    }
}
