package net.corekit.metrics.provider

import android.content.Context
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import net.corekit.core.controller.ChannelUserController
import net.corekit.core.report.ReporterData
import net.corekit.metrics.BuildConfig
import net.corekit.metrics.report.SharedParamsManager
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import kotlinx.coroutines.sync.withLock
import net.corekit.metrics.adjust.AdjustTracker
import net.corekit.metrics.data.FirebaseReporter
import net.corekit.metrics.log.MetricsLogger
import net.corekit.metrics.data.ThinkingReporter
import net.corekit.metrics.revenue.AdjustRevenueReporter
import net.corekit.metrics.revenue.FirebaseRevenueReporter
import net.corekit.core.ads.RevenueAdManager
import net.corekit.core.report.ReportDataManager

/** Provider 仅提供 Application Context；SDK 初始化由 Application 显式调用，避免阻塞 Provider 启动。 */
class MetricsModuleProvider : ContentProvider() {
    companion object {
        @Volatile private var applicationContext: Context? = null
        private val initialization = Mutex()
        private var initialized = false

        fun getApplicationContext(): Context? = applicationContext

        /** app 与 metrics 只安装一套上报器；多个入口并发初始化也不会重复注册 SDK。 */
        suspend fun initialize(context: Context) =
            withContext(Dispatchers.Main.immediate) {
                initialization.withLock {
                    if (initialized) return@withLock
                    val app = context.applicationContext
                    applicationContext = app
                    ChannelUserController.setDefaultChannel(BuildConfig.DEFAULT_USER_CHANNEL)
                    withContext(Dispatchers.IO) {
                        ThinkingReporter.init(app)
                        SharedParamsManager.initLoginData()
                    }
                    val firebaseAvailable = FirebaseApp.getApps(app).isNotEmpty()
                    val reporters = buildList<ReporterData> {
                        if (ThinkingReporter.checkInitialized()) add(ThinkingReporter())
                        if (firebaseAvailable) add(FirebaseReporter())
                    }
                    ReportDataManager.setReporters(reporters)
                    withContext(Dispatchers.IO) {
                        ReportDataManager.setCommonParams(SharedParamsManager.retrieveAllCommonParams())
                        ReportDataManager.setUserParams(SharedParamsManager.retrieveUserCommonParams())
                    }
                    AdjustTracker.init(app)
                    RevenueAdManager.setReporters(buildList {
                        if (AdjustTracker.checkInitialized()) add(AdjustRevenueReporter())
                        if (firebaseAvailable) add(FirebaseRevenueReporter())
                    })
                    initialized = true
                    MetricsLogger.i("Metrics initialized: flavor=${BuildConfig.FLAVOR}, firebase=$firebaseAvailable, thinking=${ThinkingReporter.checkInitialized()}, adjust=${AdjustTracker.checkInitialized()}")
                }
            }
    }

    override fun onCreate(): Boolean {
        applicationContext = context?.applicationContext
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? = null
    
    override fun getType(uri: Uri): String? = null
    
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
    
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int = 0
}
