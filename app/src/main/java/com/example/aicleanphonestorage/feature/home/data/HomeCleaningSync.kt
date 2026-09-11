package com.example.aicleanphonestorage.feature.home.data

import net.corekit.core.controller.ChannelUserController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 应用级协调：共享归因口径、状态变更更新同 ID 常驻通知；不持有页面、不另建刷新定时器。 */
internal class HomeCleaningSync(
    private val cleaning: HomeCleaningState,
    private val refreshResident: () -> Unit,
) : ChannelUserController.ChannelChangeListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var channelRevision = 0L

    fun start() {
        ChannelUserController.addChannelChangeListener(this)
        scope.launch {
            // SDK 已在 Application 中安装默认渠道；本地存储读取离开主线程。
            try {
                val revision = channelRevision
                val paid = withContext(Dispatchers.IO) { ChannelUserController.getCurrentChannel() == ChannelUserController.UserChannelType.PAID }
                if (revision == channelRevision) cleaning.setPaidUser(paid)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                android.util.Log.w("HomeCleaning", "Use configured default audience", error)
            }
        }
        scope.launch { cleaning.state.collect { refreshResident() } }
    }

    override fun onChannelChanged(
        oldChannel: ChannelUserController.UserChannelType,
        newChannel: ChannelUserController.UserChannelType,
    ) {
        scope.launch {
            channelRevision++
            cleaning.setPaidUser(newChannel == ChannelUserController.UserChannelType.PAID)
        }
    }
}
