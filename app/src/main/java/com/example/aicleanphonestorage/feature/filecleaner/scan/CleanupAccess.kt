package com.example.aicleanphonestorage.feature.filecleaner.scan

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.aicleanphonestorage.feature.filecleaner.data.CleanupFeature
import kotlinx.coroutines.flow.first

private val Context.cleanupAccessData by preferencesDataStore("cleanup_access")

internal enum class AccessRequest {
    NONE,
    PHOTOS,
    ALL_FILES,
    DIRECTORY,
}

internal enum class ScanSourceKind {
    MEDIA,
    DIRECT,
    DOCUMENT,
}

internal data class ScanAccess(
    val request: AccessRequest,
    val source: ScanSourceKind? = null,
    val roots: List<String> = emptyList(),
    val limited: Boolean = false,
)

/** 授权策略集中在入口；所有文件访问只用于用户触发的清理，仍不访问其他应用私有目录。 */
internal class CleanupAccess(context: Context) {
    private val app = context.applicationContext

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(app, permission) == PackageManager.PERMISSION_GRANTED

    fun photoPermissions(): Array<String> =
        when {
            Build.VERSION.SDK_INT >= 34 ->
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
                )
            Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
            Build.VERSION.SDK_INT <= 28 ->
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                )
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    @Suppress("DEPRECATION")
    suspend fun resolve(feature: CleanupFeature): ScanAccess {
        val allFiles = Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()
        val images =
            feature == CleanupFeature.PHOTO_COMPRESS || feature == CleanupFeature.SCREENSHOTS
        if (images) {
            val full =
                if (Build.VERSION.SDK_INT >= 33) granted(Manifest.permission.READ_MEDIA_IMAGES)
                else granted(Manifest.permission.READ_EXTERNAL_STORAGE)
            val partial =
                Build.VERSION.SDK_INT >= 34 &&
                    granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            if (
                Build.VERSION.SDK_INT <= 28 &&
                    full &&
                    !granted(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            )
                return ScanAccess(AccessRequest.PHOTOS)
            if (full || partial || allFiles)
                return ScanAccess(
                    AccessRequest.NONE,
                    ScanSourceKind.MEDIA,
                    limited = !full && !allFiles,
                )
            if (!allFiles) return ScanAccess(AccessRequest.PHOTOS)
        }
        val tree = app.cleanupAccessData.data.first()[TREE]
        if (
            !images &&
                tree != null &&
                app.contentResolver.persistedUriPermissions.any {
                    it.uri.toString() == tree && it.isReadPermission && it.isWritePermission
                }
        )
            return ScanAccess(AccessRequest.NONE, ScanSourceKind.DOCUMENT, listOf(tree))
        if (allFiles) {
            val roots =
                app.getSystemService(StorageManager::class.java)
                    .storageVolumes
                    .mapNotNull { it.directory?.absolutePath }
                    .distinct()
            return ScanAccess(
                AccessRequest.NONE,
                ScanSourceKind.DIRECT,
                roots.ifEmpty { listOf(Environment.getExternalStorageDirectory().absolutePath) },
            )
        }
        return ScanAccess(
            if (Build.VERSION.SDK_INT >= 30) AccessRequest.ALL_FILES else AccessRequest.DIRECTORY
        )
    }

    suspend fun rememberTree(uri: String) {
        app.cleanupAccessData.edit { it[TREE] = uri }
    }

    companion object {
        private val TREE = stringPreferencesKey("tree_uri")
    }
}
