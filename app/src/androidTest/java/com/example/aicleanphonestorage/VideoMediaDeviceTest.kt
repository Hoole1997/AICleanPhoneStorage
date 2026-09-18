package com.example.aicleanphonestorage

import android.content.ContentValues
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.core.coroutines.*
import com.example.aicleanphonestorage.feature.filecleaner.data.*
import com.example.aicleanphonestorage.feature.filecleaner.operations.FileOperationEngine
import com.example.aicleanphonestorage.feature.videos.data.VideoMediaScanner
import com.example.aicleanphonestorage.feature.videos.ui.VideoThumbnailLoader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 只对测试自行创建的视频执行扫描/删除结果核验；finally 仅清理这两个确定的测试 URI。 */
@RunWith(AndroidJUnit4::class)
class VideoMediaDeviceTest {
    @Test fun allFilesGateAndDirectDeletionReuseSharedPermission() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val resolver = context.contentResolver
        val executor = TaskExecutor(AppDispatchers())
        // 系统改变 all-files AppOp 会杀进程，因此授权状态由测试启动前的 adb 配置，不在用例内切换。
        org.junit.Assume.assumeTrue(android.os.Environment.isExternalStorageManager())
        val index = ScanIndex(context)
        val scan = index.start(CleanupFeature.VIDEOS)
        var uri: android.net.Uri? = null
        var deleted = false
        try {
            val access = com.example.aicleanphonestorage.feature.filecleaner.scan.CleanupAccess(context)
            val granted = access.resolve(CleanupFeature.VIDEOS)
            assertEquals(com.example.aicleanphonestorage.feature.filecleaner.scan.AccessRequest.NONE, granted.request)
            assertTrue(granted.videos)
            assertFalse(granted.limited)
            val name = "video-manager-test-${System.nanoTime()}.mp4"
            uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/VideoCleanerTests")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            })!!
            resolver.openOutputStream(uri)!!.use { output ->
                instrumentation.context.assets.open("video-fixture.mp4").use { it.copyTo(output) }
            }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            val rows = mutableListOf<ScannedFile>()
            executor.io { VideoMediaScanner(context).scan({ file, _ -> if (file.name == name) rows += file }) { _, _ -> } }
            assertEquals(1, rows.size)
            index.insert(scan, rows)
            val handle = ScanHandle(scan, CleanupFeature.VIDEOS, 1, "Test fixture")
            index.finishScan(handle)
            val operation = index.prepareOperation(handle, CleanupFilter())
            val result = FileOperationEngine(context, index, executor).delete(operation) { _, _ -> }
            assertTrue("All-files access should not create a system confirmation", result is com.example.aicleanphonestorage.feature.filecleaner.operations.OperationStep.Finished)
            val summary = (result as com.example.aicleanphonestorage.feature.filecleaner.operations.OperationStep.Finished).summary
            deleted = summary.deleted == 1
            assertEquals(1, summary.deleted)
            assertEquals(0, summary.failed)
            resolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)!!.use { assertFalse(it.moveToFirst()) }
        } finally {
            if (!deleted) uri?.let { resolver.delete(it, null, null) }
            index.discard(scan); index.close()
        }
    }

    @Test fun allFilesDeniedKeepsVideoAtEntry() = runBlocking {
        org.junit.Assume.assumeFalse(android.os.Environment.isExternalStorageManager())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val access = com.example.aicleanphonestorage.feature.filecleaner.scan.CleanupAccess(context)
        assertEquals(com.example.aicleanphonestorage.feature.filecleaner.scan.AccessRequest.ALL_FILES,
            access.resolve(CleanupFeature.VIDEOS).request)
    }

    @Test fun realMediaScanRejectsInvalidContainerAndConsentDoesNotInventSuccess() = runBlocking {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val resolver=context.contentResolver
        val executor=TaskExecutor(AppDispatchers())
        val prefix="video-cleaner-test-${System.nanoTime()}"
        fun create(name: String, valid: Boolean): android.net.Uri {
            val uri=resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "$prefix-$name.mp4")
                put(MediaStore.MediaColumns.MIME_TYPE,"video/mp4")
                put(MediaStore.MediaColumns.RELATIVE_PATH,"Movies/VideoCleanerTests")
                put(MediaStore.MediaColumns.IS_PENDING,1)
            })!!
            resolver.openOutputStream(uri)!!.use { output ->
                if (valid) instrumentation.context.assets.open("video-fixture.mp4").use { it.copyTo(output) }
                else output.write(ByteArray(4096){ 1 })
            }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING,0) },null,null)
            return uri
        }
        val valid=create("valid",true)
        val invalid=create("invalid",false)
        var validDeleted=false
        val index=ScanIndex(context)
        val scan=index.start(CleanupFeature.VIDEOS)
        try {
            instrumentation.uiAutomation.adoptShellPermissionIdentity(android.Manifest.permission.READ_MEDIA_VIDEO)
            val rows=mutableListOf<ScannedFile>()
            executor.io { VideoMediaScanner(context).scan({file,_->if(file.name.startsWith(prefix)) rows+=file}){_,_->} }
            assertEquals(listOf(valid.lastPathSegment),rows.map { android.net.Uri.parse(it.uri).lastPathSegment })
            val loader=VideoThumbnailLoader(context,executor)
            val bitmap=loader.load(rows.single())
            assertNotNull(bitmap)
            assertTrue(bitmap!!.width <= 640 && bitmap.height <= 640)
            loader.clear()
            index.insert(scan,rows)
            val handle=ScanHandle(scan,CleanupFeature.VIDEOS,1,"Test fixture")
            index.finishScan(handle)
            val file=index.page(handle,CleanupFilter(),0,1).single()
            val operation=index.prepareOperation(handle,CleanupFilter())
            val engine=FileOperationEngine(context,index,executor)
            assertTrue(engine.delete(operation) { _, _ -> } is com.example.aicleanphonestorage.feature.filecleaner.operations.OperationStep.Consent)
            assertNotNull(index.get(file.id))
            // 模拟系统返回 OK 但文件仍在：不能把回调本身当作真实删除成功。
            engine.consentResult(operation,true)
            assertEquals(0,engine.summary(operation).deleted)
            assertEquals(1,engine.summary(operation).failed)
            assertNotNull(index.get(file.id))
            val cancelled=index.prepareOperation(handle,CleanupFilter())
            assertTrue(engine.delete(cancelled) { _, _ -> } is com.example.aicleanphonestorage.feature.filecleaner.operations.OperationStep.Consent)
            engine.consentResult(cancelled,false)
            assertEquals(0,engine.summary(cancelled).deleted)
            assertTrue(index.get(file.id)!!.selected)
            val deleted=index.prepareOperation(handle,CleanupFilter())
            assertTrue(engine.delete(deleted) { _, _ -> } is com.example.aicleanphonestorage.feature.filecleaner.operations.OperationStep.Consent)
            // 仅删除本测试创建的内容，以真实 Provider 缺失验证成功统计。
            resolver.delete(valid,null,null)
            validDeleted=true
            engine.consentResult(deleted,true)
            assertEquals(1,engine.summary(deleted).deleted)
            assertNull(index.get(file.id))
        } finally {
            instrumentation.uiAutomation.dropShellPermissionIdentity()
            if (!validDeleted) resolver.delete(valid,null,null)
            resolver.delete(invalid,null,null)
            index.discard(scan);index.close()
        }
    }
}
