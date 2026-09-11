package com.example.aicleanphonestorage.feature.home.ui

/** 单个 RecyclerView 的异构行，首页摘要与工具网格一起回收，避免嵌套滚动容器。 */
internal sealed interface HomeRow {
    val key: String
    data class Hero(val content: HeroContent) : HomeRow { override val key = "hero" }
    data class Statistics(val content: HomeStatistics) : HomeRow { override val key = "statistics" }
    data object NativeAd : HomeRow { override val key = "native_home" }
    data object Section : HomeRow { override val key = "section" }
    data class Tool(val content: HomeToolItem) : HomeRow { override val key = content.tool.name }
}

internal fun HomeContent.rows(includeNativeAd: Boolean = false): List<HomeRow> = buildList {
    add(HomeRow.Hero(hero))
    add(HomeRow.Statistics(statistics))
    if (includeNativeAd) add(HomeRow.NativeAd)
    add(HomeRow.Section)
    tools.forEach { add(HomeRow.Tool(it)) }
}
