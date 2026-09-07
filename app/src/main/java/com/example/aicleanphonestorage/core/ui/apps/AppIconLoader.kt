package com.example.aicleanphonestorage.core.ui.apps

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import androidx.core.graphics.createBitmap
import com.example.aicleanphonestorage.core.coroutines.TaskExecutor
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** 生命周期由页面所有；缓存按实际位图字节计费，资源查询/光栅化不发生在 onBindViewHolder。 */
internal class AppIconLoader(context: Context, private val executor: TaskExecutor) {
    private val manager = context.applicationContext.packageManager
    private val size = (42 * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
    private val cache = object : LruCache<String, Bitmap>(2 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    suspend fun load(packageName: String): Bitmap? = executor.io {
        cache.get(packageName)?.let { return@io it }
        try {
            val drawable = manager.getApplicationIcon(packageName)
            currentCoroutineContext().ensureActive()
            val bitmap = createBitmap(size, size)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(Canvas(bitmap))
            currentCoroutineContext().ensureActive()
            cache.put(packageName, bitmap)
            bitmap
        } catch (error: PackageManager.NameNotFoundException) {
            null
        } catch (error: SecurityException) {
            null
        }
    }

    // 不手动 recycle，ImageView 可能仍在绘制被驱逐的对象，由引用生命周期安全回收。
    fun clear() = cache.evictAll()
}
