package com.example.aicleanphonestorage.feature.filecleaner.data

import android.content.ContentValues
import android.content.Context
import com.example.aicleanphonestorage.core.data.risk.ApkRiskEvidence
import com.example.aicleanphonestorage.core.data.risk.ApkRiskSource
import com.example.aicleanphonestorage.feature.filecleaner.operations.FileContentAccess
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** 新垃圾索引发布前关联历史检测证据；只标记本次真实扫描到且内容未变化的 APK，不插入/选择/删除文件。 */
internal class ApkRiskAnnotator(
    context: Context,
    private val index: ScanIndex,
    private val source: ApkRiskSource,
) {
    private val access = FileContentAccess(context)

    suspend fun apply(scan: Long, run: String) {
        var after = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val previous = after
            val evidence = source.apkRiskBatch(run, after)
            if (evidence.isEmpty()) return
            for (item in evidence) {
                currentCoroutineContext().ensureActive()
                if (item.id <= after) continue
                after = item.id
                try { mark(scan, item) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: java.io.IOException) { /* 文件消失/变更：跳过标记，交由原清理流程重新验证。 */ }
                catch (_: SecurityException) { /* 访问权变化不能当作风险证据。 */ }
                catch (_: IllegalArgumentException) { /* 不合法路径或证据不能覆盖清理候选。 */ }
            }
            // 来源必须按 id 单调分页，防止异常实现造成无限循环。
            if (after <= previous || evidence.last().id != after) return
        }
    }

    private suspend fun mark(scan: Long, evidence: ApkRiskEvidence) {
        if (!evidence.md5.matches(Regex("[0-9a-f]{32}"))) return
        val canonical = File(evidence.path).canonicalPath
        val name = File(canonical).name
        var after = 0L
        while (true) {
            val candidates = index.readableDatabase.rawQuery(
                "SELECT * FROM files WHERE scan=? AND id>? AND category='APK' AND (path=? OR (path='' AND name=? AND size=?)) ORDER BY id LIMIT 40",
                arrayOf(scan.toString(), after.toString(), canonical, name, evidence.bytes.toString()),
            ).use { c -> buildList { while (c.moveToNext()) add(index.row(c)) } }
            if (candidates.isEmpty()) return
            for (file in candidates) {
                currentCoroutineContext().ensureActive()
                after = file.id
                if (file.size != evidence.bytes) continue
                val sameTime = if (file.backend == FileBackend.MEDIA)
                    file.modifiedMillis / 1000 == evidence.modifiedMillis / 1000 else file.modifiedMillis == evidence.modifiedMillis
                if (!sameTime) continue
                val identity = access.identityPath(file) ?: continue
                if (File(identity).canonicalPath != canonical) continue
                access.validate(file)
                val digest = MessageDigest.getInstance("MD5")
                access.input(file).use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        digest.update(buffer, 0, read)
                    }
                }
                access.validate(file)
                val hash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
                if (hash != evidence.md5) continue
                index.writableDatabase.update("files", ContentValues().apply {
                    put("risk_level", evidence.level.name)
                    put("risk_family", evidence.family.take(200))
                }, "id=? AND scan=?", arrayOf(file.id.toString(), scan.toString()))
            }
        }
    }
}
