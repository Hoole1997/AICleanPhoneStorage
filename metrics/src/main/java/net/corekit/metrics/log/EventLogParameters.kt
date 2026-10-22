package net.corekit.metrics.log

/** 自由输入仅交给已授权的上报通道，诊断日志不复制原文；返回新映射，不修改实际发送参数。 */
object EventLogParameters {
    fun redact(parameters: Map<String, Any>): Map<String, Any> =
        if ("has_other_content" !in parameters) parameters
        else parameters.mapValues { (key, value) -> if (key == "has_other_content") "[redacted]" else value }
}
