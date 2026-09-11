# 仅保留本模块依赖 Gson 反射创建的配置模型；SDK 自身规则由其 AAR 提供。
# 库不能导出 -dontwarn ** / -ignorewarnings 或全局 keep，否则会影响宿主 R8 检查和压缩。
-keepattributes Signature,*Annotation*
-keep,allowobfuscation class net.corekit.metrics.revenue.RevenueConfigItem {
    *;
}
