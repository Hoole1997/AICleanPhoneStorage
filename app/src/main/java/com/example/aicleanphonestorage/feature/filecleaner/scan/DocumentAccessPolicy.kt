package com.example.aicleanphonestorage.feature.filecleaner.scan

import android.net.Uri
import android.provider.DocumentsContract

/** 垃圾入口只处理系统共享存储树，不把应用配置/云端提供者的目录当作设备垃圾。 */
internal object DocumentAccessPolicy : DirectoryScanPolicy {
    override fun visitDirectory(uri: Uri) = isLocalStorageTree(uri.toString())
    override fun includeNonemptyDirectory(uri: Uri) = false

    fun isLocalStorageTree(tree: String): Boolean = try {
        val uri = Uri.parse(tree)
        uri.scheme == "content" && uri.authority == "com.android.externalstorage.documents" &&
            DocumentsContract.isTreeUri(uri) && DocumentsContract.getTreeDocumentId(uri).isNotBlank()
    } catch (_: IllegalArgumentException) { false }

    fun supportsDelete(flags: Int) = flags and DocumentsContract.Document.FLAG_SUPPORTS_DELETE != 0
}
