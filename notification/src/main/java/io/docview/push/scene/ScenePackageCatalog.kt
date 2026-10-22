package io.docview.push.scene

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.google.gson.Gson
import kotlinx.coroutines.*

/** 有界的用户应用显示名快照，供卸载后使用；不加载图标、权限列表或 APK 内容，不上传名称/包名。 */
internal class ScenePackageCatalog(context: Context, private val storage: SceneStorage) {
    private val app = context.applicationContext
    private val names = LinkedHashMap<String, String>()

    suspend fun initialize() = withContext(Dispatchers.IO) {
        storage.cached("package_catalog")?.let { raw ->
            runCatching { SceneConfig.objectValue(raw) }.getOrNull()?.entrySet()?.take(512)?.forEach { (key, value) ->
                if (validPackage(key) && value.isJsonPrimitive && value.asJsonPrimitive.isString) names[key] = value.asString.take(80)
            }
        }
        @Suppress("DEPRECATION")
        val apps = app.packageManager.getInstalledApplications(0)
        for (info in apps) {
            currentCoroutineContext().ensureActive()
            if (userApp(info)) {
                names[info.packageName] = runCatching { info.loadLabel(app.packageManager).toString().take(80) }.getOrDefault("")
                trim()
            }
        }
        persist()
    }

    suspend fun installed(packageName: String): String? = withContext(Dispatchers.IO) {
        val info = info(packageName) ?: return@withContext null
        if (!userApp(info)) return@withContext null
        val label = runCatching { info.loadLabel(app.packageManager).toString().take(80) }.getOrDefault("")
        names[packageName] = label; trim(); persist()
        label
    }
    fun removedName(packageName: String): String? = names[packageName]
    suspend fun validate(event: SceneEvent): Boolean = withContext(Dispatchers.IO) {
        if (!validPackage(event.reference) || event.reference == app.packageName) return@withContext false
        try {
            @Suppress("DEPRECATION")
            val info = app.packageManager.getApplicationInfo(event.reference, 0)
            event.scene == PushScene.APP_INSTALL && userApp(info)
        } catch (_: PackageManager.NameNotFoundException) { event.scene == PushScene.APP_UNINSTALL }
        catch (_: RuntimeException) { false }
    }
    @Suppress("DEPRECATION")
    private fun info(packageName: String) = if (!validPackage(packageName)) null else try { app.packageManager.getApplicationInfo(packageName, 0) }
        catch (_: PackageManager.NameNotFoundException) { null }
        catch (_: RuntimeException) { null }
    private fun userApp(info: ApplicationInfo) = info.packageName != app.packageName && info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
    private fun trim() { while (names.size > 512) names.remove(names.keys.first()) }
    private suspend fun persist() = storage.cache("package_catalog", Gson().toJson(names))
    companion object { fun validPackage(value: String) = value.length <= 255 && value.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")) }
}
