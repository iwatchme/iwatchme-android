package com.iwatchme.player.feature.playerpage.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.iwatchme.player.PlayerSdk
import com.iwatchme.player.core.ui.UIComponentAdapterImpl
import com.iwatchme.player.databinding.FragmentPlayerPageBinding
import com.iwatchme.player.feature.playerpage.di.PlayerBizComponent
import com.iwatchme.player.feature.playerpage.di.PlayerPageComponent
import com.iwatchme.player.feature.playerpage.uicomponent.DetailTitleUIComponent
import com.iwatchme.player.feature.playerpage.uicomponent.EpisodeTitleUIComponent
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
    private var episodeTitleBindJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("Player", "[PlayerPageFragment] >>> PageScope CREATING")

        val appComponent = PlayerSdk.appComponent
        val scope = kotlinx.coroutines.MainScope()
        pageScope = scope
        pageComponent = appComponent.playerPageComponentFactory().create(scope)
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
        Log.d("Player", "[PlayerPageFragment] onViewCreated — binding PlayerView and starting anchor")

        pageComponent.playerViewBinder().bind(binding.playerView)

        listAdapter = UIComponentAdapterImpl(viewLifecycleOwner.lifecycleScope)
        binding.videoList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = listAdapter
        }

        // 顶部合集标题（BizScope 数据），点击切合集
        val titleComponent = DetailTitleUIComponent(pageComponent.pageDetailRepository().detailFlow)
        val titleEntry = titleComponent.wrapExistingView(binding.currentTitle)
        titleBindJob = viewLifecycleOwner.lifecycleScope.launch {
            titleComponent.bindToView(titleEntry)
        }
        binding.currentTitle.setOnClickListener {
            Log.d("Player", "[PlayerPageFragment] currentTitle clicked — cycling to next detail (BizScope rebuild)")
            pageComponent.pageDetailRepository().cycleNextDetail()
        }

        // loading / error overlay（PageScope 级别）
        val uiStateRepo = pageComponent.playerUiStateRepository()
        val loadingComponent = PlayerLoadingUIComponent(uiStateRepo.playbackStateFlow)
        val loadingEntry = loadingComponent.createViewEntry(requireContext())
        binding.playerOverlayContainer.removeAllViews()
        binding.playerOverlayContainer.addView(loadingEntry.root)
        loadingBindJob = viewLifecycleOwner.lifecycleScope.launch {
            loadingComponent.bindToView(loadingEntry)
        }

        val errorComponent = PlayerErrorUIComponent(uiStateRepo.playbackStateFlow, uiStateRepo.errorMessageFlow)
        val errorEntry = errorComponent.createViewEntry(requireContext())
        binding.playerOverlayContainer.addView(errorEntry.root)
        errorBindJob = viewLifecycleOwner.lifecycleScope.launch {
            errorComponent.bindToView(errorEntry)
        }

        pageComponent.anchor().start()

        observeBizScope()
    }

    private fun observeBizScope() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                pageComponent.bizComponentBridge().bizComponentFlow
                    .filterNotNull()
                    .collectLatest { bizComponent ->
                        bindBizUI(bizComponent)
                    }
            }
        }
    }

    private fun bindBizUI(bizComponent: PlayerBizComponent) {
        val service = bizComponent.bizRecyclerViewService()
        Log.d("Player", "[PlayerPageFragment] BizComponent available — ${service.components.size} components")
        listAdapter?.submitList(service.components)

        // 当前播放集标题（EpisodeScope 等价数据，从 BizScope 的 SelectionRepository 读 selectedItemFlow）
        episodeTitleBindJob?.cancel()
        val episodeTitleComponent = EpisodeTitleUIComponent(bizComponent.selectionRepository().selectedItemFlow)
        val episodeEntry = episodeTitleComponent.wrapExistingView(binding.episodeTitle)
        episodeTitleBindJob = viewLifecycleOwner.lifecycleScope.launch {
            episodeTitleComponent.bindToView(episodeEntry)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d("Player", "[PlayerPageFragment] onDestroyView — unbinding PlayerView")
        pageComponent.playerViewBinder().unbind(binding.playerView)
        loadingBindJob?.cancel()
        errorBindJob?.cancel()
        titleBindJob?.cancel()
        episodeTitleBindJob?.cancel()
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
