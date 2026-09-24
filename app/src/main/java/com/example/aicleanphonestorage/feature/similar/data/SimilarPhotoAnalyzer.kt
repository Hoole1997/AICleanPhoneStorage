package com.example.aicleanphonestorage.feature.similar.data

import android.content.Context
import com.example.aicleanphonestorage.core.media.PhotoSignatureReader
import com.example.aicleanphonestorage.feature.filecleaner.operations.FileContentAccess
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** 顺序有界解码、流式哈希、磁盘候选索引；扫描从不删除图片。固定参考图防止相似关系无限传递。 */
internal class SimilarPhotoAnalyzer(context: Context, private val index: SimilarPhotoIndex) {
    private val reader = PhotoSignatureReader(context)
    private val content = FileContentAccess(context)

    suspend fun analyze(scan: Long, report: (Int) -> Unit): Int {
        var after = 0L
        var processed = 0
        var readable = 0
        var skipped = 0
        while (true) {
            val batch = index.candidates(scan, after)
            if (batch.isEmpty()) break
            for (file in batch) {
                currentCoroutineContext().ensureActive()
                after = file.id
                try {
                    val signature =
                        reader.read(file, stableSampling = true) ?: throw IOException("Unsupported or damaged image")
                    index.signature(file, signature)
                    if (index.hasSameMetadata(scan, file))
                        index.hash(file.id, content.fingerprint(file))
                    readable++
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (
                        e !is IOException &&
                            e !is SecurityException &&
                            e !is IllegalArgumentException
                    )
                        throw e
                    index.unavailable(file.id, notify = false)
                    skipped++
                }
                report(++processed)
            }
        }
        if (processed > 0 && readable == 0) throw IOException("No readable photo candidates")
        var hash = ""
        while (true) {
            val hashes = index.exactHashes(scan, hash)
            if (hashes.isEmpty()) break
            for (value in hashes) {
                currentCoroutineContext().ensureActive()
                index.exactGroup(scan, value)
                hash = value
            }
        }
        after = 0
        while (true) {
            val batch = index.candidates(scan, after, ungrouped = true)
            if (batch.isEmpty()) break
            for (file in batch) {
                currentCoroutineContext().ensureActive()
                after = file.id
                val signature = index.signature(file.id) ?: continue
                var reference = file.id
                var comparisons = 0
                var matched = false
                while (comparisons < SimilarPolicy.MAX_COMPARISONS) {
                    val candidates = index.references(scan, file, signature, reference)
                    if (candidates.isEmpty()) break
                    for ((candidate, metrics) in candidates.take(SimilarPolicy.MAX_COMPARISONS - comparisons)) {
                        currentCoroutineContext().ensureActive()
                        reference = candidate.id
                        comparisons++
                        if (SimilarPolicy.matches(signature, metrics)) {
                            index.join(candidate, file, signature)
                            matched = true
                            break
                        }
                    }
                    if (matched) break
                }
                // 极端碰撞桶不无界两两比较；在页面说明中披露跳过数量，不伪装为完整识别。
                if (!matched && comparisons >= SimilarPolicy.MAX_COMPARISONS) skipped++
                report(++processed)
            }
        }
        index.rebuild(scan)
        return skipped
    }
}
