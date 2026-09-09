package io.docview.push.earthquake

import com.google.gson.annotations.SerializedName

/**
 * 地震数据模型
 */
data class EarthquakeResponse(
    @SerializedName("type")
    val type: String,
    @SerializedName("metadata")
    val metadata: Metadata,
    @SerializedName("features")
    val features: List<EarthquakeFeature>
)

data class Metadata(
    @SerializedName("generated")
    val generated: Long,
    @SerializedName("url")
    val url: String,
    @SerializedName("title")
    val title: String,
    @SerializedName("status")
    val status: Int,
    @SerializedName("api")
    val api: String,
    @SerializedName("count")
    val count: Int
)

data class EarthquakeFeature(
    @SerializedName("type")
    val type: String,
    @SerializedName("properties")
    val properties: EarthquakeProperties,
    @SerializedName("geometry")
    val geometry: EarthquakeGeometry,
    @SerializedName("id")
    val id: String
)

data class EarthquakeProperties(
    @SerializedName("mag")
    val mag: Double?,
    @SerializedName("place")
    val place: String?,
    @SerializedName("time")
    val time: Long?,
    @SerializedName("updated")
    val updated: Long?,
    @SerializedName("url")
    val url: String?,
    @SerializedName("title")
    val title: String?,
    @SerializedName("type")
    val type: String?,
    @SerializedName("alert")
    val alert: String?,
    @SerializedName("status")
    val status: String?,
    @SerializedName("magType")
    val magType: String?,
    @SerializedName("tsunami")
    val tsunami: Int?
)

data class EarthquakeGeometry(
    @SerializedName("type")
    val type: String,
    @SerializedName("coordinates")
    val coordinates: List<Double> // [longitude, latitude, depth]
)

/**
 * 自定义地震信息模型
 */
data class EarthquakeInfo(
    /**
     * 震级
     */
    @SerializedName("magnitude")
    val magnitude: Double,

    /**
     * 发震时间（格式化后的时间字符串，格式：2025/03/03 03:03:33）
     */
    @SerializedName("time")
    val time: String,

    /**
     * 震源深度（单位：公里）
     */
    @SerializedName("depth")
    val depth: Double,

    /**
     * 是否有海啸威胁
     */
    @SerializedName("hasTsunami")
    val hasTsunami: Boolean,

    /**
     * USGS警报级别
     * 可能的值：
     * - "green": 低风险，地震影响较小
     * - "yellow": 中等风险，地震可能造成轻微到中等影响
     * - "orange": 高风险，地震可能造成重大影响
     * - "red": 严重风险，地震可能造成严重破坏
     * - null: 没有警报级别（通常是小地震或数据不足时使用默认值"green"）
     */
    @SerializedName("alert")
    val alert: String?,

    /**
     * 震级类型（如：md, ml, mb等）
     */
    @SerializedName("magType")
    val magType: String?,

    /**
     * 数据状态（如：automatic, reviewed等）
     */
    @SerializedName("status")
    val status: String?,

    /**
     * 地点信息
     */
    @SerializedName("place")
    val place: String?,

    /**
     * 短时间格式（JST时区，格式：JST 7:30 PM）
     */
    @SerializedName("shortTime")
    val shortTime: String,

    /**
     * 短震级类型（取magType第一个字母转大写，如果不存在就为"M"）
     */
    @SerializedName("shortMagType")
    val shortMagType: String,

)


/** 地震扩展保留来源协议编号，与清理业务的 Content 类型分开。 */
const val EARTHQUAKE_NOTIFICATION_ACTION = 12
