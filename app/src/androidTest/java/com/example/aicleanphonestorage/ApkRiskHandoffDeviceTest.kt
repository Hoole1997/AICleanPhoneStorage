package com.example.aicleanphonestorage

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.core.coroutines.AppDispatchers
import com.example.aicleanphonestorage.core.coroutines.TaskExecutor
import com.example.aicleanphonestorage.core.data.risk.*
import com.example.aicleanphonestorage.databinding.ItemMalwareStatusBinding
import com.example.aicleanphonestorage.feature.filecleaner.data.*
import com.example.aicleanphonestorage.feature.filecleaner.ui.*
import com.example.aicleanphonestorage.feature.junkcleaner.data.*
import com.example.aicleanphonestorage.feature.junkcleaner.ui.*
import com.example.aicleanphonestorage.feature.malware.data.*
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 仅操作独立缓存目录/数据库。假 APK 是普通测试字节，不安装、不清理用户文件。 */
@RunWith(AndroidJUnit4::class)
class ApkRiskHandoffDeviceTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext

    @Test fun onlyUnchangedApksInTheSourceRunGetMarkedAndSortedFirst()=runBlocking {
        val folder=File(context.cacheDir,"apk-risk-${UUID.randomUUID()}").apply{mkdirs()}.canonicalFile
        val isolated=object:ContextWrapper(context) {
            override fun getApplicationContext():Context=this
            override fun getDatabasePath(name:String)=File(folder,name)
        }
        try {
            withContext(Dispatchers.IO) {
                MalwareIndex(isolated).use { malware -> ScanIndex(isolated).use { cleanup ->
                    assertTrue(cleanup.writableDatabase.path.startsWith(folder.path))
                    val run=UUID.randomUUID().toString()
                    val good=File(folder,"risk.apk").apply{writeText("original-risk")}
                    val other=File(folder,"other/risk.apk").apply{parentFile!!.mkdirs();writeText("original-risk")}
                    val replaced=File(folder,"replaced.apk").apply{writeText("original")}
                    val stable=File(folder,"normal.apk").apply{writeText("a".repeat(4096))}
                    fun md5(file:File)=MessageDigest.getInstance("MD5").digest(file.readBytes()).joinToString(""){"%02x".format(it.toInt() and 255)}
                    fun record(file:File,installed:Boolean=false)=ThreatItem(0,file.name,"",file.path,installed,ThreatLevel.MALWARE,"Malware.Trojan",file.length(),file.lastModified(),md5(file))
                    malware.save(run,record(good))
                    malware.save(run,record(replaced))
                    malware.save(run,record(stable,true)) // 已安装应用永远不作为待清理 APK 的风险来源。
                    val previousTime=replaced.lastModified()
                    replaced.writeText("tampered")
                    assertTrue(replaced.setLastModified(previousTime)) // 相同大小/时间，指纹必须拦住替换。
                    fun row(file:File)=ScannedFile(uri=Uri.fromFile(file).toString(),name=file.name,mime="application/vnd.android.package-archive",
                        size=file.length(),modifiedMillis=file.lastModified(),category=FileCategory.APK,backend=FileBackend.DIRECT,
                        scope=folder.path,path=file.path,bucket=JunkKind.INSTALLERS.name)
                    val scan=cleanup.start(CleanupFeature.SMART_CLEAN)
                    cleanup.insert(scan,listOf(row(good),row(other),row(replaced),row(stable)))
                    val source=object:ApkRiskSource {
                        override suspend fun apkRiskBatch(runId:String,afterId:Long)=malware.apkRiskBatch(runId,afterId)
                    }
                    ApkRiskAnnotator(isolated,cleanup,source).apply(scan,run)
                    val handle=ScanHandle(scan,CleanupFeature.SMART_CLEAN,4,"Shared storage")
                    val rows=cleanup.page(handle,CleanupFilter(bucket=JunkKind.INSTALLERS.name),0,40)
                    assertEquals(1,rows.count{it.apkRisk!=null})
                    assertEquals(good.path,rows.first().path)
                    assertEquals(ApkRiskLevel.MALWARE,rows.first().apkRisk)
                    assertTrue(rows.all{!it.selected}) // 关联标记不修改选择规则。
                    assertEquals(1,JunkIndex(cleanup).categories(scan).single{it.kind==JunkKind.INSTALLERS}.flaggedApks)
                    malware.reset() // 原扫描消失不影响已经落入垃圾索引的快照。
                    assertEquals(ApkRiskLevel.MALWARE,cleanup.page(handle,CleanupFilter(bucket="INSTALLERS"),0,40).first().apkRisk)
                    val fresh=cleanup.start(CleanupFeature.SMART_CLEAN)
                    cleanup.insert(fresh,listOf(row(good)))
                    ApkRiskAnnotator(isolated,cleanup,source).apply(fresh,run)
                    assertNull(cleanup.page(ScanHandle(fresh,CleanupFeature.SMART_CLEAN,1,""),CleanupFilter(),0,40).single().apkRisk)
                    assertEquals("original-risk",good.readText())
                    assertEquals("tampered",replaced.readText())
                } }
            }
        } finally { folder.deleteRecursively() }
    }

    @Test fun markersSurviveDatabaseUpgradeWithoutSelectingOldFiles() {
        val folder=File(context.cacheDir,"apk-migration-${UUID.randomUUID()}").apply{mkdirs()}
        val isolated=object:ContextWrapper(context) {
            override fun getApplicationContext():Context=this
            override fun getDatabasePath(name:String)=File(folder,name)
        }
        try {
            android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(File(folder,"cleanup_index.db"),null).use { db ->
                db.execSQL("CREATE TABLE files(id INTEGER PRIMARY KEY,name TEXT,selected INTEGER)")
                db.execSQL("INSERT INTO files VALUES(1,'existing.apk',0)")
                db.version=7
            }
            ScanIndex(isolated).use { index ->
                index.readableDatabase.rawQuery("SELECT name,selected,risk_level FROM files WHERE id=1",null).use { c ->
                    assertTrue(c.moveToFirst());assertEquals("existing.apk",c.getString(0));assertEquals(0,c.getInt(1));assertEquals("",c.getString(2))
                }
            }
        } finally { folder.deleteRecursively() }
    }

    @Test fun progressFillHasRoundedEndsAndRecycledRowsClearRiskLabels() {
        val images=mutableListOf<Pair<String,Bitmap>>()
        instrumentation.runOnMainSync {
            val configuration=Configuration(context.resources.configuration).apply { densityDpi=320;fontScale=1f;screenWidthDp=375;setLocale(Locale.SIMPLIFIED_CHINESE) }
            val themed=ContextThemeWrapper(context.createConfigurationContext(configuration),R.style.Theme_AICleanPhoneStorage)
            val b=ItemMalwareStatusBinding.inflate(LayoutInflater.from(themed))
            val bar=b.scanProgress
            bar.progress=50
            measure(bar,570,12,View.MeasureSpec.EXACTLY)
            val image=Bitmap.createBitmap(570,12,Bitmap.Config.ARGB_8888).also { bar.draw(Canvas(it)) }
            assertTrue("Track left corner is rounded",Color.alpha(image.getPixel(0,0))<255)
            assertEquals("Fill top-right corner exposes track",Color.rgb(228,231,236),image.getPixel(284,0))
            assertNotEquals("Fill center-right remains filled",image.getPixel(284,0),image.getPixel(284,6))
            images+="rounded-progress" to image
            val executor=TaskExecutor(AppDispatchers())
            val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
            val adapter=CleanupFilesAdapter(CleanupFeature.SMART_CLEAN,CleanupThumbnailLoader(themed,executor),scope,{_,_->},{},{})
            val holder=adapter.onCreateViewHolder(FrameLayout(themed),0)
            val file=ScannedFile(id=1,uri="file:///test.apk",name="AMTSO-Antivirus-Test.apk",mime="application/vnd.android.package-archive",size=253380,modifiedMillis=1,
                category=FileCategory.APK,backend=FileBackend.DIRECT,scope="/",path="/test.apk",apkRisk=ApkRiskLevel.MALWARE)
            holder.bind(file,false)
            measure(holder.itemView,686,0,View.MeasureSpec.UNSPECIFIED)
            assertEquals(View.VISIBLE,holder.itemView.findViewById<View>(R.id.file_risk).visibility)
            images+="flagged-apk-row" to draw(holder.itemView)
            holder.bind(file.copy(id=2,apkRisk=null),false)
            assertEquals(View.GONE,holder.itemView.findViewById<View>(R.id.file_risk).visibility)
            holder.bind(null,false)
            assertEquals("",holder.itemView.findViewById<TextView>(R.id.file_risk).text.toString())
            adapter.onViewRecycled(holder);scope.cancel()
            val categories=JunkCategoriesAdapter({},{_,_->})
            categories.submitList(listOf(JunkRow.Category(JunkCategorySummary(JunkKind.INSTALLERS,3,500000,0,1),true)))
            val category=categories.onCreateViewHolder(FrameLayout(themed),1)
            categories.onBindViewHolder(category,0)
            measure(category.itemView,750,0,View.MeasureSpec.UNSPECIFIED)
            assertEquals(View.VISIBLE,category.itemView.findViewById<View>(R.id.junk_category_risks).visibility)
            images+="flagged-apk-category" to draw(category.itemView)
        }
        val out=File(context.getExternalFilesDir(null),"apk-risk-review").apply{mkdirs()}
        for((name,bitmap)in images){File(out,"$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}
    }
    @Test fun riskBadgesFitAllLocalesAtLargeFont() {
        for (language in listOf("en","zh-Hans","hi","es","ar","pt-BR","bn","ur","id","ru","fr","de","ja","ko","vi","tr")) {
            instrumentation.runOnMainSync {
                val configuration=Configuration(context.resources.configuration).apply {
                    densityDpi=320;fontScale=2f;screenWidthDp=320;setLocale(Locale.forLanguageTag(language))
                }
                val themed=ContextThemeWrapper(context.createConfigurationContext(configuration),R.style.Theme_AICleanPhoneStorage)
                val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
                val adapter=CleanupFilesAdapter(CleanupFeature.SMART_CLEAN,CleanupThumbnailLoader(themed,TaskExecutor(AppDispatchers())),scope,{_,_->},{},{})
                val holder=adapter.onCreateViewHolder(FrameLayout(themed),0)
                val file=ScannedFile(id=1,uri="file:///test.apk",name="AMTSO-Antivirus-Test.apk",mime="application/vnd.android.package-archive",size=253380,modifiedMillis=1,
                    category=FileCategory.APK,backend=FileBackend.DIRECT,scope="/",path="/test.apk",apkRisk=ApkRiskLevel.PUA)
                holder.bind(file,false)
                measure(holder.itemView,576,0,View.MeasureSpec.UNSPECIFIED)
                checkLabel(holder.itemView.findViewById(R.id.file_risk),language)
                adapter.onViewRecycled(holder);scope.cancel()
                val categories=JunkCategoriesAdapter({},{_,_->})
                categories.submitList(listOf(JunkRow.Category(JunkCategorySummary(JunkKind.INSTALLERS,3,500000,0,1),true)))
                val category=categories.onCreateViewHolder(FrameLayout(themed),1)
                categories.onBindViewHolder(category,0)
                measure(category.itemView,640,0,View.MeasureSpec.UNSPECIFIED)
                checkLabel(category.itemView.findViewById(R.id.junk_category_risks),language)
            }
        }
    }
    private fun checkLabel(label:TextView,language:String) {
        assertTrue("Clipped risk label in $language",label.height-label.compoundPaddingTop-label.compoundPaddingBottom>=label.layout.height)
        for (line in 0 until label.layout.lineCount)
            assertTrue("Risk label too wide in $language",label.layout.getLineMax(line)<=label.width-label.compoundPaddingLeft-label.compoundPaddingRight+2)
    }
    private fun measure(view:View,w:Int,h:Int,mode:Int) { view.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,mode));view.layout(0,0,view.measuredWidth,view.measuredHeight) }
    private fun draw(view:View)=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888).also{val c=Canvas(it);c.drawColor(Color.WHITE);view.draw(c)}
}
