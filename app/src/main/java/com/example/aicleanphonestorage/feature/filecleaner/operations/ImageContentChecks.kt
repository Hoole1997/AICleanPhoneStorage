package com.example.aicleanphonestorage.feature.filecleaner.operations

import com.example.aicleanphonestorage.feature.filecleaner.data.ScannedFile
import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** 共享静态图片约束，避免压缩或相似分析忽略 PNG 动画帧。 */
internal class ImageContentChecks(private val content: FileContentAccess) {
    suspend fun requireStaticPng(item: ScannedFile) {
        content.input(item).use { stream ->
            val input = java.io.DataInputStream(stream)
            val signature = ByteArray(8)
            input.readFully(signature)
            var inspected = 8L
            while (inspected < 1_048_576) {
                currentCoroutineContext().ensureActive()
                val length = input.readInt().toLong() and 0xffffffffL
                val type = ByteArray(4)
                input.readFully(type)
                val name = String(type, Charsets.US_ASCII)
                if (name == "acTL") throw IOException("Animated PNG is not supported")
                if (name == "IDAT" || name == "IEND") return
                if (length > 1_048_576 - inspected) throw IOException("Unsupported PNG metadata")
                var remaining = length + 4
                while (remaining > 0) {
                    val skipped = input.skip(remaining)
                    if (skipped > 0) remaining -= skipped
                    else {
                        if (input.read() < 0) throw IOException("Truncated PNG")
                        remaining--
                    }
                }
                inspected += length + 12
            }
            throw IOException("Unsupported PNG")
        }
    }
}
