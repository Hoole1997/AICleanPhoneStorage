package io.docview.push.config

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.util.Properties

/** 仅在推送 IO 队列访问；noBackup 保证卸载/重装或设备恢复不继承首启日与轮播游标。 */
internal class DayContentStorage(context: Context, firstObservedEpochDay: Long) {
    private val directory = File(context.noBackupFilesDir, "push_day_content").apply { mkdirs() }
    private val launchFile = AtomicFile(File(directory, "first_launch.txt"))
    private val cursorFile = AtomicFile(File(directory, "cursors.properties"))
    val firstLaunchEpochDay: Long = read(launchFile, 64)?.trim()?.toLongOrNull()
        ?.takeIf { it in 0..365_000 } ?: firstObservedEpochDay.also { write(launchFile, it.toString()) }
    private val cursors = Properties().apply {
        read(cursorFile, 4096)?.let { text -> runCatching { load(text.reader()) } }
    }

    fun readIndex(day: Int): Int = cursors.getProperty("D$day")?.toIntOrNull()?.coerceAtLeast(0) ?: 0

    fun writeIndex(day: Int, index: Int) {
        cursors.setProperty("D$day", index.toString())
        val text = java.io.StringWriter().also { cursors.store(it, null) }.toString()
        write(cursorFile, text)
    }

    fun cached(day: Int): String? = read(AtomicFile(File(directory, "${DayContentPool.key(day)}.json")), MAX_DAY_POOL_BYTES)
    fun cache(day: Int, json: String) = write(AtomicFile(File(directory, "${DayContentPool.key(day)}.json")), json)

    private fun read(file: AtomicFile, maxBytes: Int): String? = runCatching {
        file.openRead().use { stream ->
            require(stream.channel.size() <= maxBytes)
            stream.bufferedReader(Charsets.UTF_8).readText()
        }
    }.getOrNull()

    private fun write(file: AtomicFile, text: String) {
        val stream = file.startWrite()
        try {
            stream.write(text.toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }
}
