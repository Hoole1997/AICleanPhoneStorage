package com.example.aicleanphonestorage.core.data.risk

/** 功能间只共享扫描证据契约，不引用 SDK、Activity 或完整文件列表。 */
enum class ApkRiskLevel { MALWARE, PUA }

data class ApkRiskEvidence(
    val id: Long,
    val path: String,
    val bytes: Long,
    val modifiedMillis: Long,
    val md5: String,
    val level: ApkRiskLevel,
    val family: String,
)

internal interface ApkRiskSource {
    /** 证据来自已完成扫描索引；每批最多 40 项，来源会话过期时返回空集合。 */
    suspend fun apkRiskBatch(runId: String, afterId: Long): List<ApkRiskEvidence>

    object None : ApkRiskSource {
        override suspend fun apkRiskBatch(runId: String, afterId: Long) = emptyList<ApkRiskEvidence>()
    }
}

internal object ApkRiskNavigation {
    const val EXTRA_RUN = "cleanup.apk.risk.run"
    fun validRun(value: String?): String? = value?.takeIf {
        it.length == 36 && runCatching { java.util.UUID.fromString(it).toString() == it }.getOrDefault(false)
    }
}
