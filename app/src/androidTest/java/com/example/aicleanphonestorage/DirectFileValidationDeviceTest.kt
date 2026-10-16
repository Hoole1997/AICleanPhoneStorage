package com.example.aicleanphonestorage

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.core.coroutines.*
import com.example.aicleanphonestorage.feature.filecleaner.data.ScannedFile
import com.example.aicleanphonestorage.feature.filecleaner.operations.FileContentAccess
import com.example.aicleanphonestorage.feature.filecleaner.operations.FileSnapshotChangedException
import com.example.aicleanphonestorage.feature.filecleaner.scan.DirectStorageScanner
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 真实文件系统、扫描器与删除前校验；仅操作随机测试目录，不删除用户文件。 */
@RunWith(AndroidJUnit4::class)
class DirectFileValidationDeviceTest {
    @Test fun fractionalTimestampSurvivesScanAndValidationButSameSecondChangeIsRejected() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "direct-validation-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val file = File(root, "fixture.mp4").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val base = 1_700_000_000_000L
            Files.setLastModifiedTime(file.toPath(), FileTime.fromMillis(base + 123))
            val rows = mutableListOf<ScannedFile>()
            val access = FileContentAccess(context)
            TaskExecutor(AppDispatchers()).io {
                DirectStorageScanner { "video/mp4" }.scan(listOf(root.path), false,
                    { row, _ -> rows += row }, { _, _ -> })
                val row = rows.single()
                assertEquals(base + 123, row.modifiedMillis)
                Log.i("DirectMetadataTest", "scan=${row.modifiedMillis} legacy_file=${file.lastModified()} nio=${Files.getLastModifiedTime(file.toPath()).toMillis()}")
                access.validate(row)
                // 同一秒内的真实变化仍必须拒绝，不能用四舍五入或一秒误差掩盖问题。
                Files.setLastModifiedTime(file.toPath(), FileTime.fromMillis(base + 456))
                val changed = assertThrows(FileSnapshotChangedException::class.java) { access.validate(row) }
                assertEquals(base + 123, changed.scannedModifiedMillis)
                assertEquals(base + 456, changed.currentModifiedMillis)
                assertEquals(changed.scannedBytes, changed.currentBytes)
                // 时间恢复但内容大小变化也不能删除。
                file.appendBytes(byteArrayOf(4))
                Files.setLastModifiedTime(file.toPath(), FileTime.fromMillis(base + 123))
                val resized = assertThrows(FileSnapshotChangedException::class.java) { access.validate(row) }
                assertEquals(3L, resized.scannedBytes); assertEquals(4L, resized.currentBytes)
            }
        } finally { root.deleteRecursively() }
    }
}
