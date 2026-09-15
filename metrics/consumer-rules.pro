# metrics 对宿主导出的 R8 契约；不关闭宿主的压缩、优化、混淆或缺失类检查。
# Gson 用反射读取字段注解；Signature 保留泛型类型信息。
-keepattributes Signature,RuntimeVisibleAnnotations,AnnotationDefault

# AdjustRevenueReporter / FirebaseRevenueReporter 均反序列化此数组元素。
# Kotlin 模型没有无参构造，Gson 通过 Unsafe 创建实例：保留类的可实例化性与字段，
# 防止 full mode 将其当作“从未实例化”的类优化；JSON 键由 SerializedName 固定，
# 所以类名和字段名允许混淆，copy/componentN/toString 等普通方法无需整体保留。
-keep,allowobfuscation class net.corekit.metrics.revenue.RevenueConfigItem {
    @com.google.gson.annotations.SerializedName <fields>;
}

# FirebaseReporter / ThinkingReporter 仅把 Map<String, Any> 转 JSON，不反射业务 DTO。
# MetricsModuleProvider 由 Manifest/AAPT 保留；Adjust、ThinkingData、Firebase
# 使用依赖自带规则。core 1.0.15 未附带规则；当前调用为显式接口/构造，
# 其 AdSlotSwitchController 使用 JsonParser 树解析，不反射业务 DTO。
