package com.example.aicleanphonestorage.feature.appmanager.data

import com.example.aicleanphonestorage.core.data.apps.InstalledAppSummary
import com.example.aicleanphonestorage.core.data.apps.InstalledAppsReader

internal data class AppManagerCatalog(val apps: List<InstalledAppSummary>)

internal interface AppManagerRepository {
    suspend fun load(progress: (Int, Int) -> Unit = { _, _ -> }): AppManagerCatalog
}

internal class AndroidAppManagerRepository(private val reader: InstalledAppsReader) :
    AppManagerRepository {
    override suspend fun load(progress: (Int, Int) -> Unit) =
        AppManagerCatalog(reader.read(progress = progress))
}
