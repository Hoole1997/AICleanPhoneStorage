// 产品 AAR 只进入 google 和用户指定的 localRiskDebug；普通 local 不合入 SDK。
// google 仍只在 Actions 上编译，localRiskDebug 可本机联调。工厂与 native 实现只维护一份。
// 原版 AAR 保留 SHA-256；只处理重复的 R8 注解与已审计过宽 consumer rules，不改 SDK native 内容。
val riskSdkOutput = layout.buildDirectory.dir("generated/product-risk-sdks")
val prepareProductRiskSdks = tasks.register<Exec>("prepareProductRiskSdks") {
    group = "build"
    val script = rootProject.file("scripts/prepare_product_risk_sdks.py")
    val originals = listOf(file("libs/collect-1.3.2.aar"), file("libs/xm-0.0.5.aar"))
    inputs.files(originals).withPropertyName("vendorRiskSdks")
    inputs.file(script).withPropertyName("preparationScript")
    outputs.dir(riskSdkOutput)
    commandLine("python3", script.absolutePath, "--input-dir", file("libs").absolutePath,
        "--output-dir", riskSdkOutput.get().asFile.absolutePath)
}
dependencies {
    // riskDebug 仅生成 local variant，因此用标准 buildType 依赖域，无组合配置名猜测。
    listOf("googleImplementation", "riskDebugImplementation").forEach { configuration ->
        add(configuration, files(
            riskSdkOutput.map { it.file("collect-1.3.2.aar") },
            riskSdkOutput.map { it.file("xm-0.0.5.aar") },
        ).builtBy(prepareProductRiskSdks))
        add(configuration, "com.google.android.play:integrity:1.6.0")
        add(configuration, "com.github.megatronking.stringfog:xor:5.0.0")
    }
}
