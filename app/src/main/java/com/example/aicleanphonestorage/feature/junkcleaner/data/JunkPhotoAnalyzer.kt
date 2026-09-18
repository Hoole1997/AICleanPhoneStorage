package com.example.aicleanphonestorage.feature.junkcleaner.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import com.example.aicleanphonestorage.feature.filecleaner.data.*
import com.example.aicleanphonestorage.feature.filecleaner.operations.FileContentAccess
import java.io.IOException
import java.util.ArrayDeque
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** 两阶段分析：同尺寸候选流式 SHA-256，然后小缩略图特征。一次只解码一张，不做全量两两比较。 */
internal class JunkPhotoAnalyzer(context: Context, private val index: JunkIndex) {
    private val content = FileContentAccess(context)

    private data class Reference(
        var file: ScannedFile,
        var signature: PhotoSignature,
        var grouped: Boolean = false,
        val groupKey: String = "similar:${file.id}",
        val anchorTime: Long = file.modifiedMillis,
    )

    suspend fun analyze(scan: Long, report: (Int) -> Unit): Int {
        var done = 0
        var skipped = 0
        var after = 0L
        while (true) {
            val batch = index.photos(scan, after, hashCandidates = true)
            if (batch.isEmpty()) break
            for (file in batch) {
                currentCoroutineContext().ensureActive()
                after = file.id
                try {
                    index.setHash(file.id, content.fingerprint(file))
                } catch (error: Exception) {
                    expected(error)
                    index.setHash(file.id, "!")
                    skipped++
                }
                report(++done)
            }
        }
        index.duplicateGroups(scan)
        // 以修改时间顺序维护最多 64 张参考图；仅比较七天内、相近比例和亮度的图，限制计算与误报。
        val references = ArrayDeque<Reference>(64)
        var modified = Long.MIN_VALUE
        after = 0
        while (true) {
            val batch = index.photosByTime(scan, modified, after)
            if (batch.isEmpty()) break
            for (file in batch) {
                currentCoroutineContext().ensureActive()
                modified = file.modifiedMillis
                after = file.id
                try {
                    val signature = signature(file)
                    if (signature == null) {
                        skipped++
                        continue
                    }
                    val match =
                        references.firstOrNull {
                            file.modifiedMillis > 0 &&
                                it.file.modifiedMillis > 0 &&
                                file.modifiedMillis - it.anchorTime in 0..7L * 86_400_000 &&
                                signature.similar(it.signature)
                        }
                    if (match != null) {
                        val group = match.groupKey
                        val keepNew =
                            signature.pixels > match.signature.pixels ||
                                (signature.pixels == match.signature.pixels &&
                                    file.size > match.file.size)
                        index.mark(match.file.id, JunkKind.SIMILAR, group, !keepNew)
                        index.mark(file.id, JunkKind.SIMILAR, group, keepNew)
                        match.grouped = true
                        if (keepNew) {
                            match.file = file
                            match.signature = signature
                        }
                    } else {
                        if (signature.needsReview) index.mark(file.id, JunkKind.REVIEW_QUALITY)
                        references.addLast(Reference(file, signature))
                        if (references.size > 64) references.removeFirst()
                    }
                } catch (error: Exception) {
                    expected(error)
                    skipped++
                } finally {
                    report(++done)
                }
            }
        }
        return skipped
    }

    private suspend fun signature(file: ScannedFile): PhotoSignature? =
        if (file.mime in setOf("image/jpeg", "image/png"))
            com.example.aicleanphonestorage.core.media.PhotoSignatureReader(content.app).read(file)
        else null

    private fun expected(error: Exception) {
        when (error) {
            is CancellationException -> throw error
            is IOException,
            is SecurityException,
            is IllegalArgumentException -> Unit
            else -> throw error
        }
    }
}
