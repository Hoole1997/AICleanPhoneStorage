package com.example.aicleanphonestorage.feature.home.ui

import android.view.View
import com.example.aicleanphonestorage.core.ui.motion.MotionPreferences
import androidx.recyclerview.widget.RecyclerView
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.databinding.ScreenHomeBinding
import kotlin.math.ceil

/** 页面渲染与 Activity 生命周期解耦；只处理布局和轻量状态，没有后台任务或平台数据读取。 */
internal class HomeRenderer(private val binding: ScreenHomeBinding, actions: HomeUiActions,
    private val nativeContainer: android.view.ViewGroup? = null) {
    private val configuration = binding.root.resources.configuration
    private val contentWidthDp = minOf(configuration.screenWidthDp, 600) - 32
    private val expanded = configuration.fontScale > 1.2f || contentWidthDp < 320
    private val adapter = HomeListAdapter(expanded, actions, nativeContainer)
    private var lastContent: HomeContent? = null
    private var resumed=false
    private var focused=false
    private var motionAllowed=false
    private val motionPreferences=MotionPreferences(binding.root.context){allowed->motionAllowed=allowed;updateMotion()}
    private val scrollListener=object:RecyclerView.OnScrollListener(){
        override fun onScrollStateChanged(recyclerView:RecyclerView,newState:Int)=updateMotion()
        override fun onScrolled(recyclerView:RecyclerView,dx:Int,dy:Int){if(recyclerView.scrollState==RecyclerView.SCROLL_STATE_IDLE)adapter.refreshMotionVisibility()}
    }
    private val layoutListener=View.OnLayoutChangeListener { _,_,_,_,_,_,_,_,_->adapter.refreshMotionVisibility() }
    fun setResumed(value:Boolean){
        resumed=value
        if(value)motionPreferences.start()else motionPreferences.stop()
        updateMotion()
    }
    fun setWindowFocused(value:Boolean){focused=value;updateMotion()}
    private fun updateMotion(){
        adapter.setMotionActive(resumed && focused && motionAllowed && binding.homeList.isVisible && binding.homeList.scrollState==RecyclerView.SCROLL_STATE_IDLE)
        adapter.refreshMotionVisibility()
    }

    init {
        val grid = GridLayoutManager(binding.root.context, toolColumnCount())
        grid.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int) = if (adapter.getItemViewType(position) == HomeListAdapter.TOOL) 1 else grid.spanCount
        }
        binding.homeList.layoutManager = grid
        binding.homeList.adapter = adapter
        binding.homeList.addItemDecoration(HomeGridSpacing())
        // 摘要更新不做整卡闪烁/交叉淡入；默认滚动惯性由 RecyclerView 管理。
        binding.homeList.itemAnimator = null
        binding.homeList.addOnScrollListener(scrollListener)
        binding.homeList.addOnLayoutChangeListener(layoutListener)
        binding.settingsButton.setOnClickListener { actions.onSettings() }
    }

    private fun toolColumnCount(): Int {
        if (expanded) return 1
        val resources = binding.root.resources
        // 按实际字体/系统字号测量全部标题；空间不足时整组改单列，字号和完整文案始终一致。
        // 每次页面创建只测量七个短字符串，无逐帧测量、缩字循环或后台任务。
        val paint = TextPaint().apply {
            typeface = ResourcesCompat.getFont(binding.root.context, R.font.home_roboto_medium)
            textSize = resources.getDimension(R.dimen.home_tool_title_size)
        }
        val titleWidth = HomeTool.entries.maxOf { ceil(paint.measureText(resources.getString(it.titleRes))) }
        val requiredCardWidth = titleWidth + 2 * resources.getDimension(R.dimen.home_tool_card_padding) + 1
        val availableWidth = contentWidthDp * resources.displayMetrics.density
        val cardWidth = (availableWidth - resources.getDimension(R.dimen.home_grid_gap)) / 2
        return if (cardWidth >= requiredCardWidth) 2 else 1
    }

    fun render(state: HomeUiState, preview: HomeContent? = null) {
        val content = preview ?: (state as? HomeUiState.Ready)?.overview?.toHomeContent(configuration.locales[0])
        binding.homeList.isVisible = content != null
        binding.statusPanel.isVisible = content == null
        binding.loadingIndicator.isVisible = content == null && state is HomeUiState.Loading
        binding.errorMessage.isVisible = content == null && state is HomeUiState.Failure
        binding.retryButton.isVisible = content == null && state is HomeUiState.Failure
        if (state is HomeUiState.Failure) {
            binding.errorMessage.setText(when (state.reason) {
                HomeUiState.Reason.PermissionRequired -> R.string.home_permission_required
                HomeUiState.Reason.StorageUnavailable -> R.string.home_storage_unavailable
            })
        }
        if (content != null && content != lastContent) {
            lastContent = content
            adapter.submitList(content.rows(includeNativeAd = nativeContainer != null))
        }
        updateMotion()
    }

    fun dispose() {
        // 主动断开 RecyclerView 和 adapter，释放回调及可回收视图；没有应用级图片缓存。
        setResumed(false)
        binding.homeList.removeOnScrollListener(scrollListener)
        binding.homeList.removeOnLayoutChangeListener(layoutListener)
        binding.homeList.adapter = null
    }
}
