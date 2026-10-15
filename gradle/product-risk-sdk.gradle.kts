// google 与 localRiskDebug 始终使用产品 SDK；普通 local 按自身开关选择是否合入。
// google 仍只在 Actions 上编译，local 可本机联调。工厂与 native 实现只维护一份。
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
    // 使用 flavor/buildType 的标准依赖域，避免 AGP 未创建组合 variant 的配置名。
    val localConfig = java.util.Properties().apply {
        file("src/local/config.properties").reader(Charsets.UTF_8).use { load(it) }
    }
    val configurations = buildList {
        add("googleImplementation")
        if (localConfig.getProperty("xmRiskEnabled", "false").toBooleanStrict()) {
            // local 已覆盖独立 riskDebug，不重复声明同一份 AAR。
            add("localImplementation")
        } else {
            // 普通 local 关闭时，仍保留用户已授权的独立 riskDebug 联调包。
            add("riskDebugImplementation")
        }
    }
    configurations.forEach { configuration ->
        add(configuration, files(
            riskSdkOutput.map { it.file("collect-1.3.2.aar") },
            riskSdkOutput.map { it.file("xm-0.0.5.aar") },
        ).builtBy(prepareProductRiskSdks))
        add(configuration, "com.google.android.play:integrity:1.6.0")
        add(configuration, "com.github.megatronking.stringfog:xor:5.0.0")
    }
}
