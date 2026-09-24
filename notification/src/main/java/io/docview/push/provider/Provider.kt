package io.docview.push.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import io.docview.push.config.ConfigCtrl
import io.docview.push.config.ContentController
import io.docview.push.controller.TriggerCtrl
import io.docview.push.timing.TimingCtrl
import io.docview.push.check.CheckCtrl
import io.docview.push.service.CoreService
import io.docview.push.utils.ResetCtrl
import io.docview.push.utils.Logger

/**
 * 通知模块内容提供者
 * 用于获取 Context 并初始化配置控制器
 */
class Provider : ContentProvider() {

    companion object {
        const val METHOD_START_KEEP_ALIVE_SERVICE = "startKeepAliveService"
        const val EXTRA_FROM = "from"
    }

    override fun onCreate(): Boolean {
        val app = context?.applicationContext as? io.docview.push.NotificationRuntimeOwner ?: return false
        app.notificationRuntime.initialize()
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        return null
    }

    override fun getType(uri: Uri): String? {
        return null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        return null
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        return 0
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        return 0
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method == METHOD_START_KEEP_ALIVE_SERVICE) {
            // 只有明确的内部命令才能启动服务，避免其他 Provider 调用产生副作用。
            val from = extras?.getString(EXTRA_FROM)?.trim()?.take(32)?.takeIf { it.isNotEmpty() }
                ?: "unknown"
            context?.let { CoreService.startService(it, providerFrom = from) }
            return Bundle()
        }
        return super.call(method, arg, extras)
    }
}
