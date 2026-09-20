package com.example.aicleanphonestorage

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.feature.malware.data.ScanTarget
import com.example.aicleanphonestorage.feature.malware.data.TrustlookScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 默认只扫描自身 APK；显式启用时可扫描固定无害 AMTSO 测试样本，不枚举用户的其他应用。 */
@RunWith(AndroidJUnit4::class)
class TrustlookIntegrationDeviceTest {
    @Test fun scanOnlyOwnApkWithLocalLicense()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.leafmotivation.quizguessoncolor",context.packageName)
        val start=android.os.SystemClock.elapsedRealtime()
        withContext(Dispatchers.IO) {
            val scanner=TrustlookScanner(context)
            try {
                scanner.initialize()
                val item=scanner.scan(ScanTarget(context.applicationInfo.publicSourceDir,context.packageName,true))
                assertEquals(context.packageName,item.packageName)
                assertTrue(item.installed)
            } finally { scanner.cancel() }
        }
        println("Own-APK SDK scan elapsedMs=${android.os.SystemClock.elapsedRealtime()-start}")
    }
    /** 仅在显式传入 amtso_fixture=true 时运行；固定官方样本及 SHA-256，不扫描任意设备文件。 */
    @Test fun scanOfficialAmtsoFixtureWhenRequested()=runBlocking {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("amtso_fixture")=="true")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        withContext(Dispatchers.IO) {
            val file=java.io.File("/sdcard/Download/AntivirusTest/AMTSO-Antivirus-Test.apk")
            assertTrue("Official fixture must be readable through the app's existing file access",file.canRead())
            val digest=java.security.MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer=ByteArray(16*1024)
                while(true) { val read=input.read(buffer);if(read<0)break;digest.update(buffer,0,read) }
            }
            val hash=digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
            assertEquals("2b4f33ba1a1e392ab3c79bb6dc848d5440239323220498ad84dd5f124d510484",hash)
            val scanner=TrustlookScanner(context)
            try {
                scanner.initialize()
                val item=scanner.scan(ScanTarget(file.path,"",false))
                val folder=java.io.File(context.getExternalFilesDir(null),"malware-fixture").apply { mkdirs() }
                java.io.File(folder,"result.txt").writeText("source=AMTSO official harmless Android test APK\nsha256=$hash\nlevel=${item.level}\nfamily=${item.family.take(200)}\n")
                assertFalse(item.installed)
                assertEquals(file.length(),item.bytes)
                assertEquals(file.lastModified(),item.modifiedMillis)
                val actualMd5=java.security.MessageDigest.getInstance("MD5").digest(file.readBytes()).joinToString("") { "%02x".format(it.toInt() and 255) }
                assertEquals("SDK fingerprint must identify the exact APK bytes",actualMd5,item.md5)
                assertEquals("Trustlook should identify this official antivirus test sample",com.example.aicleanphonestorage.feature.malware.data.ThreatLevel.MALWARE,item.level)
            } finally { scanner.cancel() }
        }
    }
}
