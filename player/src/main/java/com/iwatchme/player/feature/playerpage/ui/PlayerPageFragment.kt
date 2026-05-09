package com.iwatchme.player.feature.playerpage.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.iwatchme.player.PlayerSdk
import com.iwatchme.player.core.ui.UIComponentAdapterImpl
import com.iwatchme.player.databinding.FragmentPlayerPageBinding
import com.iwatchme.player.feature.playerpage.di.PlayerBizFacade
import com.iwatchme.player.feature.playerpage.di.PlayerPageComponent
import com.iwatchme.player.feature.playerpage.uicomponent.BizInfoUIComponent
import com.iwatchme.player.feature.playerpage.uicomponent.DetailTitleUIComponent
import com.iwatchme.player.feature.playerpage.uicomponent.EpisodeTitleUIComponent
import com.iwatchme.player.feature.playerpage.uicomponent.FullscreenButtonUIComponent
import com.iwatchme.player.feature.playerpage.uicomponent.PanelVisibilityUIComponent
import com.iwatchme.player.feature.playerpage.uicomponent.PlayerErrorUIComponent
import com.iwatchme.player.feature.playerpage.uicomponent.PlayerLoadingUIComponent
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class PlayerPageFragment : Fragment() {

    private var _binding: FragmentPlayerPageBinding? = null
    private val binding get() = _binding!!

    private lateinit var pageComponent: PlayerPageComponent
    private var pageScope: kotlinx.coroutines.CoroutineScope? = null

    private var listAdapter: UIComponentAdapterImpl? = null

    private var loadingBindJob: Job? = null
    private var errorBindJob: Job? = null
    private var titleBindJob: Job? = null
    private var fullscreenBtnBindJob: Job? = null
    private var episodeTitleBindJob: Job? = null
    private var bizInfoBindJob: Job? = null
    private var listVisibilityBindJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("Player", "[PlayerPageFragment] >>> PageScope CREATING")

        val appComponent = PlayerSdk.appComponent
        val scope = kotlinx.coroutines.MainScope()
        pageScope = scope
        pageComponent = appComponent.playerPageComponentFactory()
            .create(scope, requireActivity() as ComponentActivity)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentPlayerPageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("Player", "[PlayerPageFragment] onViewCreated — binding PlayerView and starting bootstrap")

        pageComponent.playerViewBinder().bind(binding.playerView)

        listAdapter = UIComponentAdapterImpl(viewLifecycleOwner.lifecycleScope)
        binding.videoList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = listAdapter
        }

        // 顶部合集标题
        val titleComponent = DetailTitleUIComponent(pageComponent.detailTitleService().viewModel)
        val titleEntry = titleComponent.wrapExistingView(binding.currentTitle)
        titleBindJob = viewLifecycleOwner.lifecycleScope.launch {
            titleComponent.bindToView(titleEntry)
        }

        // loading / error overlay（PageScope 级别）
        val loadingComponent = PlayerLoadingUIComponent(pageComponent.playerLoadingService().viewModel)
        val loadingEntry = loadingComponent.createViewEntry(requireContext())
        binding.playerOverlayContainer.removeAllViews()
        binding.playerOverlayContainer.addView(loadingEntry.root)
        loadingBindJob = viewLifecycleOwner.lifecycleScope.launch {
            loadingComponent.bindToView(loadingEntry)
        }

        val errorComponent = PlayerErrorUIComponent(pageComponent.playerErrorService().viewModel)
        val errorEntry = errorComponent.createViewEntry(requireContext())
        binding.playerOverlayContainer.addView(errorEntry.root)
        errorBindJob = viewLifecycleOwner.lifecycleScope.launch {
            errorComponent.bindToView(errorEntry)
        }

        // 全屏按钮 —— Fragment 完全不知道 ScreenStateRepository 存在，只 bind UIComponent
        val fullscreenBtn = FullscreenButtonUIComponent(
            pageComponent.screenStateService().fullscreenButtonViewModel,
        )
        val fullscreenEntry = fullscreenBtn.createViewEntry(requireContext())
        binding.playerOverlayContainer.addView(fullscreenEntry.root)
        fullscreenBtnBindJob = viewLifecycleOwner.lifecycleScope.launch {
            fullscreenBtn.bindToView(fullscreenEntry)
        }

        pageComponent.bootstrap().start()

        observeBizScope()
    }

    private fun observeBizScope() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                pageComponent.currentBizComponentRepository().componentFlow
                    .filterNotNull()
                    .collectLatest { facade ->
                        bindBizUI(facade)
                    }
            }
        }
    }

    private fun bindBizUI(facade: PlayerBizFacade) {
        Log.d("Player", "[PlayerPageFragment] BizComponent available — ${facade.javaClass.simpleName}")
        listAdapter?.submitList(facade.bizRecyclerViewService().components)

        episodeTitleBindJob?.cancel()
        val episodeTitleComponent = EpisodeTitleUIComponent(facade.episodeTitleService().viewModel)
        val episodeEntry = episodeTitleComponent.wrapExistingView(binding.episodeTitle)
        episodeTitleBindJob = viewLifecycleOwner.lifecycleScope.launch {
            episodeTitleComponent.bindToView(episodeEntry)
        }

        bizInfoBindJob?.cancel()
        val bizInfoComponent = BizInfoUIComponent(facade.bizInfoService().viewModel)
        val bizInfoEntry = bizInfoComponent.wrapExistingView(binding.bizInfo)
        bizInfoBindJob = viewLifecycleOwner.lifecycleScope.launch {
            bizInfoComponent.bindToView(bizInfoEntry)
        }

        // 列表面板可见性 —— 横屏时由 VideoListPanelService 驱动隐藏
        listVisibilityBindJob?.cancel()
        val panelVisComponent = PanelVisibilityUIComponent(facade.videoListPanelService().viewModel)
        val panelVisEntry = panelVisComponent.wrapExistingView(binding.videoList)
        listVisibilityBindJob = viewLifecycleOwner.lifecycleScope.launch {
            panelVisComponent.bindToView(panelVisEntry)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d("Player", "[PlayerPageFragment] onDestroyView — unbinding PlayerView")
        pageComponent.playerViewBinder().unbind(binding.playerView)
        loadingBindJob?.cancel()
        errorBindJob?.cancel()
        titleBindJob?.cancel()
        fullscreenBtnBindJob?.cancel()
        episodeTitleBindJob?.cancel()
        bizInfoBindJob?.cancel()
        listVisibilityBindJob?.cancel()
        listAdapter = null
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("Player", "[PlayerPageFragment] <<< PageScope DESTROYING — releasing ExoPlayer")
        pageComponent.exoPlayerHolder().release()
        pageScope?.cancel()
        pageScope = null
    }
}
