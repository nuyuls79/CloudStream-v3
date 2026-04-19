package com.lagradost.cloudstream3.ui.home

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.apis
import com.lagradost.cloudstream3.APIHolder.filterProviderByPreferredMedia
import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.APIHolder.getApiProviderLangSettings
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.OAuth2Apis
import com.lagradost.cloudstream3.ui.APIRepository.Companion.noneApi
import com.lagradost.cloudstream3.ui.APIRepository.Companion.randomApi
import com.lagradost.cloudstream3.ui.AutofitRecyclerView
import com.lagradost.cloudstream3.ui.WatchType
import com.lagradost.cloudstream3.ui.quicksearch.QuickSearchFragment
import com.lagradost.cloudstream3.ui.result.START_ACTION_RESUME_LATEST
import com.lagradost.cloudstream3.ui.search.*
import com.lagradost.cloudstream3.ui.search.SearchHelper.handleSearchClickCallback
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isTrueTvSettings
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isTvSettings
import com.lagradost.cloudstream3.utils.AppUtils.loadSearchResult
import com.lagradost.cloudstream3.utils.AppUtils.setMaxViewPoolSize
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.DataStoreHelper.deleteAllBookmarkedData
import com.lagradost.cloudstream3.utils.DataStoreHelper.deleteAllResumeStateIds
import com.lagradost.cloudstream3.utils.DataStoreHelper.removeLastWatched
import com.lagradost.cloudstream3.utils.DataStoreHelper.setResultWatchState
import com.lagradost.cloudstream3.utils.Event
import com.lagradost.cloudstream3.utils.HOMEPAGE_API
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showOptionSelectStringRes
import com.lagradost.cloudstream3.utils.SubtitleHelper.getFlagFromIso
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import com.lagradost.cloudstream3.utils.UIHelper.fixPaddingStatusbar
import com.lagradost.cloudstream3.utils.UIHelper.fixPaddingStatusbarView
import com.lagradost.cloudstream3.utils.UIHelper.getSpanCount
import com.lagradost.cloudstream3.utils.UIHelper.popupMenuNoIconsAndNoStringRes
import com.lagradost.cloudstream3.utils.UIHelper.setImage
import com.lagradost.cloudstream3.utils.UIHelper.setImageBlur
import com.lagradost.cloudstream3.widget.CenterZoomLayoutManager
import java.util.*

const val HOME_BOOKMARK_VALUE_LIST = "home_bookmarked_last_list"
const val HOME_PREF_HOMEPAGE = "home_pref_homepage"

class HomeFragment : Fragment() {
    companion object {
        val configEvent = Event<Int>()
        var currentSpan = 1
        val listHomepageItems = mutableListOf<SearchResponse>()

        private val errorProfilePics = listOf(
            R.drawable.monke_benene,
            R.drawable.monke_burrito,
            R.drawable.monke_coco,
            R.drawable.monke_cookie,
            R.drawable.monke_flusdered,
            R.drawable.monke_funny,
            R.drawable.monke_like,
            R.drawable.monke_party,
            R.drawable.monke_sob,
            R.drawable.monke_drink,
        )

        val errorProfilePic = errorProfilePics.random()

        fun Activity.loadHomepageList(
            item: HomePageList,
            deleteCallback: (() -> Unit)? = null,
        ) {
            loadHomepageList(
                expand = HomeViewModel.ExpandableHomepageList(item, 1, false),
                deleteCallback = deleteCallback,
                expandCallback = null
            )
        }

        fun Activity.loadHomepageList(
            expand: HomeViewModel.ExpandableHomepageList,
            deleteCallback: (() -> Unit)? = null,
            expandCallback: (suspend (String) -> HomeViewModel.ExpandableHomepageList?)? = null
        ) {
            val context = this
            val bottomSheetDialogBuilder = BottomSheetDialog(context)
            bottomSheetDialogBuilder.setContentView(R.layout.home_episodes_expanded)
            val title = bottomSheetDialogBuilder.findViewById<TextView>(R.id.home_expanded_text)!!
            val item = expand.list
            title.text = item.name
            val recycle = bottomSheetDialogBuilder.findViewById<AutofitRecyclerView>(R.id.home_expanded_recycler)!!
            val titleHolder = bottomSheetDialogBuilder.findViewById<FrameLayout>(R.id.home_expanded_drag_down)!!

            val delete = bottomSheetDialogBuilder.findViewById<TextView>(R.id.home_expanded_delete)
            delete.isGone = deleteCallback == null
            if (deleteCallback != null) {
                delete.setOnClickListener {
                    try {
                        val builder: AlertDialog.Builder = AlertDialog.Builder(context)
                        val dialogClickListener = DialogInterface.OnClickListener { _, which ->
                            when (which) {
                                DialogInterface.BUTTON_POSITIVE -> {
                                    deleteCallback.invoke()
                                    bottomSheetDialogBuilder.dismissSafe(this)
                                }
                                DialogInterface.BUTTON_NEGATIVE -> {}
                            }
                        }
                        builder.setTitle(R.string.delete_file)
                            .setMessage(context.getString(R.string.delete_message).format(item.name))
                            .setPositiveButton(R.string.delete, dialogClickListener)
                            .setNegativeButton(R.string.cancel, dialogClickListener)
                            .show()
                    } catch (e: Exception) {
                        logError(e)
                    }
                }
            }

            titleHolder.setOnClickListener {
                bottomSheetDialogBuilder.dismissSafe(this)
            }

            recycle.spanCount = currentSpan
            recycle.adapter = SearchAdapter(item.list.toMutableList(), recycle) { callback ->
                handleSearchClickCallback(this, callback)
                if (callback.action == SEARCH_ACTION_LOAD || callback.action == SEARCH_ACTION_PLAY_FILE) {
                    bottomSheetDialogBuilder.dismissSafe(this)
                }
            }.apply {
                hasNext = expand.hasNext
            }

            recycle.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                var expandCount = 0
                val name = expand.list.name

                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                    val adapter = recyclerView.adapter
                    if (adapter !is SearchAdapter) return
                    val count = adapter.itemCount
                    val currentHasNext = adapter.hasNext
                    if (!recyclerView.canScrollVertically(1) && currentHasNext && expandCount != count) {
                        expandCount = count
                        ioSafe {
                            expandCallback?.invoke(name)?.let { newExpand ->
                                (recyclerView.adapter as? SearchAdapter?)?.apply {
                                    hasNext = newExpand.hasNext
                                    updateList(newExpand.list.list)
                                }
                            }
                        }
                    }
                }
            })

            val spanListener = { span: Int ->
                recycle.spanCount = span
            }
            configEvent += spanListener
            bottomSheetDialogBuilder.setOnDismissListener {
                configEvent -= spanListener
            }
            bottomSheetDialogBuilder.show()
        }

        fun getPairList(
            anime: MaterialButton?,
            cartoons: MaterialButton?,
            tvs: MaterialButton?,
            docs: MaterialButton?,
            movies: MaterialButton?,
            asian: MaterialButton?,
            livestream: MaterialButton?,
            nsfw: MaterialButton?
        ): List<Pair<MaterialButton?, List<TvType>>> {
            return listOf(
                Pair(anime, listOf(TvType.Anime, TvType.OVA, TvType.AnimeMovie)),
                Pair(cartoons, listOf(TvType.Cartoon)),
                Pair(tvs, listOf(TvType.TvSeries)),
                Pair(docs, listOf(TvType.Documentary)),
                Pair(movies, listOf(TvType.Movie, TvType.Torrent)),
                Pair(asian, listOf(TvType.AsianDrama)),
                Pair(livestream, listOf(TvType.Live)),
                Pair(nsfw, listOf(TvType.JAV, TvType.Hentai, TvType.XXX))
            )
        }

        fun Context.selectHomepage(selectedApiName: String?, callback: (String) -> Unit) {
            val validAPIs = filterProviderByPreferredMedia().toMutableList()
            validAPIs.add(0, randomApi)
            validAPIs.add(0, noneApi)
            val builder = BottomSheetDialog(this)
            builder.setContentView(R.layout.home_select_mainpage)
            builder.show()
            builder.let { dialog ->
                val isMultiLang = getApiProviderLangSettings().size > 1
                var currentApiName = selectedApiName
                var currentValidApis: MutableList<MainAPI> = mutableListOf()
                val preSelectedTypes = this.getKey<List<String>>(HOME_PREF_HOMEPAGE)
                    ?.mapNotNull { listName -> TvType.values().firstOrNull { it.name == listName } }
                    ?.toMutableList() ?: mutableListOf(TvType.Movie, TvType.TvSeries)

                val anime = dialog.findViewById<MaterialButton>(R.id.home_select_anime)
                val cartoons = dialog.findViewById<MaterialButton>(R.id.home_select_cartoons)
                val tvs = dialog.findViewById<MaterialButton>(R.id.home_select_tv_series)
                val docs = dialog.findViewById<MaterialButton>(R.id.home_select_documentaries)
                val movies = dialog.findViewById<MaterialButton>(R.id.home_select_movies)
                val asian = dialog.findViewById<MaterialButton>(R.id.home_select_asian)
                val livestream = dialog.findViewById<MaterialButton>(R.id.home_select_livestreams)
                val cancelBtt = dialog.findViewById<MaterialButton>(R.id.cancel_btt)
                val applyBtt = dialog.findViewById<MaterialButton>(R.id.apply_btt)
                val nsfw = dialog.findViewById<MaterialButton>(R.id.home_select_nsfw)

                val pairList = getPairList(anime, cartoons, tvs, docs, movies, asian, livestream, nsfw)

                cancelBtt?.setOnClickListener { dialog.dismissSafe() }
                applyBtt?.setOnClickListener {
                    if (currentApiName != selectedApiName) {
                        currentApiName?.let(callback)
                    }
                    dialog.dismissSafe()
                }

                val listView = dialog.findViewById<ListView>(R.id.listview1)
                val arrayAdapter = ArrayAdapter<String>(this, R.layout.sort_bottom_single_choice)
                listView?.adapter = arrayAdapter
                listView?.choiceMode = AbsListView.CHOICE_MODE_SINGLE
                listView?.setOnItemClickListener { _, _, i, _ ->
                    if (currentValidApis.isNotEmpty()) {
                        currentApiName = currentValidApis[i].name
                        currentApiName?.let(callback)
                        dialog.dismissSafe()
                    }
                }

                fun updateList() {
                    this.setKey(HOME_PREF_HOMEPAGE, preSelectedTypes)
                    arrayAdapter.clear()
                    currentValidApis = validAPIs.filter { api ->
                        api.hasMainPage && api.supportedTypes.any { preSelectedTypes.contains(it) }
                    }.sortedBy { it.name.lowercase() }.toMutableList()
                    currentValidApis.addAll(0, validAPIs.subList(0, 2))
                    val names = currentValidApis.map { if (isMultiLang) "${getFlagFromIso(it.lang)?.plus(" ") ?: ""}${it.name}" else it.name }
                    val index = currentValidApis.map { it.name }.indexOf(currentApiName)
                    listView?.setItemChecked(index, true)
                    arrayAdapter.addAll(names)
                    arrayAdapter.notifyDataSetChanged()
                }

                for ((button, validTypes) in pairList) {
                    val isValid = validAPIs.any { api -> validTypes.any { api.supportedTypes.contains(it) } }
                    button?.isVisible = isValid
                    if (isValid) {
                        fun buttonContains(): Boolean = preSelectedTypes.any { validTypes.contains(it) }
                        button?.isSelected = buttonContains()
                        button?.setOnClickListener {
                            preSelectedTypes.clear()
                            preSelectedTypes.addAll(validTypes)
                            for ((otherButton, _) in pairList) otherButton?.isSelected = false
                            button.isSelected = true
                            updateList()
                        }
                        button?.setOnLongClickListener {
                            if (!buttonContains()) {
                                button.isSelected = true
                                preSelectedTypes.addAll(validTypes)
                            } else {
                                button.isSelected = false
                                preSelectedTypes.removeAll(validTypes)
                            }
                            updateList()
                            true
                        }
                    }
                }
                updateList()
            }
        }
    }

    private val homeViewModel: HomeViewModel by activityViewModels()

    // View references
    private lateinit var homeMainHolder: View
    private lateinit var homeMainPosterRecyclerview: RecyclerView
    private lateinit var homeChangeApi: View
    private lateinit var homeChangeApiLoading: View
    private lateinit var homeApiFab: com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
    private lateinit var homeRandom: com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
    private lateinit var homeProviderName: TextView
    private lateinit var homeSearch: SearchView
    private lateinit var homeProviderMetaInfo: TextView
    private lateinit var homeLoadingShimmer: View
    private lateinit var homeLoading: View
    private lateinit var homeLoadingError: View
    private lateinit var homeLoaded: NestedScrollView
    private lateinit var resultErrorText: TextView
    private lateinit var homeReloadConnectionerror: TextView
    private lateinit var homeReloadConnectionOpenInBrowser: TextView
    private lateinit var homeMasterRecycler: RecyclerView
    private lateinit var homeBookmarkedHolder: View
    private lateinit var homeBookmarkedChildRecyclerview: RecyclerView
    private lateinit var homeBookmarkedChildMoreInfo: View
    private lateinit var homeWatchHolder: View
    private lateinit var homeWatchChildRecyclerview: RecyclerView
    private lateinit var homeWatchChildMoreInfo: View
    private lateinit var homeWatchParentItemTitle: TextView
    private lateinit var homeStatusbar: View
    private lateinit var homeLoadingStatusbar: View
    private lateinit var homeProfilePictureHolder: View
    private lateinit var homeProfilePicture: ImageView
    private lateinit var homeMainPlay: View
    private lateinit var homeMainInfo: View
    private lateinit var homeMainText: TextView
    private lateinit var homeBlurPoster: ImageView
    private lateinit var homeFocusText: TextView
    private lateinit var homeTypeWatchingBtt: MaterialButton
    private lateinit var homeTypeCompletedBtt: MaterialButton
    private lateinit var homeTypeDroppedBtt: MaterialButton
    private lateinit var homeTypeOnHoldBtt: MaterialButton
    private lateinit var homePlanToWatchBtt: MaterialButton

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val layout = if (context?.isTvSettings() == true) R.layout.fragment_home_tv else R.layout.fragment_home
        return inflater.inflate(layout, container, false)
    }

    private fun initViews(view: View) {
        homeMainHolder = view.findViewById(R.id.home_main_holder)
        homeMainPosterRecyclerview = view.findViewById(R.id.home_main_poster_recyclerview)
        homeChangeApi = view.findViewById(R.id.home_change_api)
        homeChangeApiLoading = view.findViewById(R.id.home_change_api_loading)
        homeApiFab = view.findViewById(R.id.home_api_fab)
        homeRandom = view.findViewById(R.id.home_random)
        homeProviderName = view.findViewById(R.id.home_provider_name)
        homeSearch = view.findViewById(R.id.home_search)
        homeProviderMetaInfo = view.findViewById(R.id.home_provider_meta_info)
        homeLoadingShimmer = view.findViewById(R.id.home_loading_shimmer)
        homeLoading = view.findViewById(R.id.home_loading)
        homeLoadingError = view.findViewById(R.id.home_loading_error)
        homeLoaded = view.findViewById(R.id.home_loaded)
        resultErrorText = view.findViewById(R.id.result_error_text)
        homeReloadConnectionerror = view.findViewById(R.id.home_reload_connectionerror)
        homeReloadConnectionOpenInBrowser = view.findViewById(R.id.home_reload_connection_open_in_browser)
        homeMasterRecycler = view.findViewById(R.id.home_master_recycler)
        homeBookmarkedHolder = view.findViewById(R.id.home_bookmarked_holder)
        homeBookmarkedChildRecyclerview = view.findViewById(R.id.home_bookmarked_child_recyclerview)
        homeBookmarkedChildMoreInfo = view.findViewById(R.id.home_bookmarked_child_more_info)
        homeWatchHolder = view.findViewById(R.id.home_watch_holder)
        homeWatchChildRecyclerview = view.findViewById(R.id.home_watch_child_recyclerview)
        homeWatchChildMoreInfo = view.findViewById(R.id.home_watch_child_more_info)
        homeWatchParentItemTitle = view.findViewById(R.id.home_watch_parent_item_title)
        homeStatusbar = view.findViewById(R.id.home_statusbar)
        homeLoadingStatusbar = view.findViewById(R.id.home_loading_statusbar)
        homeProfilePictureHolder = view.findViewById(R.id.home_profile_picture_holder)
        homeProfilePicture = view.findViewById(R.id.home_profile_picture)
        homeMainPlay = view.findViewById(R.id.home_main_play)
        homeMainInfo = view.findViewById(R.id.home_main_info)
        homeMainText = view.findViewById(R.id.home_main_text)
        homeBlurPoster = view.findViewById(R.id.home_blur_poster)
        homeFocusText = view.findViewById(R.id.home_focus_text)
        homeTypeWatchingBtt = view.findViewById(R.id.home_type_watching_btt)
        homeTypeCompletedBtt = view.findViewById(R.id.home_type_completed_btt)
        homeTypeDroppedBtt = view.findViewById(R.id.home_type_dropped_btt)
        homeTypeOnHoldBtt = view.findViewById(R.id.home_type_on_hold_btt)
        homePlanToWatchBtt = view.findViewById(R.id.home_plan_to_watch_btt)
    }

    private fun toggleMainVisibility(visible: Boolean) {
        homeMainHolder.isVisible = visible
        homeMainPosterRecyclerview.isVisible = visible
    }

    private fun fixGrid() {
        activity?.getSpanCount()?.let { currentSpan = it }
        configEvent.invoke(currentSpan)
    }

    private val apiChangeClickListener = View.OnClickListener { view ->
        view.context.selectHomepage(currentApiName) { api ->
            homeViewModel.loadAndCancel(api)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        fixGrid()
    }

    override fun onResume() {
        super.onResume()
        reloadStored()
    }

    private fun reloadStored() {
        homeViewModel.loadResumeWatching()
        val list = EnumSet.noneOf(WatchType::class.java)
        getKey<IntArray>(HOME_BOOKMARK_VALUE_LIST)?.map { WatchType.fromInternalId(it) }?.let {
            list.addAll(it)
        }
        homeViewModel.loadStoredData(list)
    }

    private fun focusCallback(card: SearchResponse) {
        homeFocusText.text = card.name
        homeBlurPoster.setImageBlur(card.posterUrl, 50)
    }

    private fun homeHandleSearch(callback: SearchClickCallback) {
        if (callback.action == SEARCH_ACTION_FOCUSED) {
            focusCallback(callback.card)
        } else {
            handleSearchClickCallback(activity, callback)
        }
    }

    private var currentApiName: String? = null
    private var toggleRandomButton = false

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews(view)
        fixGrid()

        homeChangeApi.setOnClickListener(apiChangeClickListener)
        homeChangeApiLoading.setOnClickListener(apiChangeClickListener)
        homeApiFab.setOnClickListener(apiChangeClickListener)
        homeRandom.setOnClickListener {
            if (listHomepageItems.isNotEmpty()) {
                activity.loadSearchResult(listHomepageItems.random())
            }
        }

        context?.let {
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(it)
            toggleRandomButton = settingsManager.getBoolean(getString(R.string.random_button_key), false)
            homeRandom.isVisible = toggleRandomButton
            if (!toggleRandomButton) homeRandom.visibility = View.GONE
        }

        observe(homeViewModel.apiName) { apiName ->
            currentApiName = apiName
            setKey(HOMEPAGE_API, apiName)
            homeApiFab.text = apiName
            homeProviderName.text = apiName
            try {
                homeSearch.queryHint = getString(R.string.search_hint_site).format(apiName)
            } catch (e: Exception) { logError(e) }
            homeProviderMetaInfo.isVisible = false
            getApiFromNameNull(apiName)?.let { currentApi ->
                val typeChoices = listOf(
                    Pair(R.string.movies, listOf(TvType.Movie)),
                    Pair(R.string.tv_series, listOf(TvType.TvSeries)),
                    Pair(R.string.documentaries, listOf(TvType.Documentary)),
                    Pair(R.string.cartoons, listOf(TvType.Cartoon)),
                    Pair(R.string.anime, listOf(TvType.Anime, TvType.OVA, TvType.AnimeMovie)),
                    Pair(R.string.asian_drama, listOf(TvType.AsianDrama)),
                    Pair(R.string.torrent, listOf(TvType.Torrent)),
                    Pair(R.string.jav, listOf(TvType.JAV)),
                    Pair(R.string.hentai, listOf(TvType.Hentai)),
                    Pair(R.string.xxx, listOf(TvType.XXX))
                ).filter { item -> currentApi.supportedTypes.any { type -> item.second.contains(type) } }
                homeProviderMetaInfo.text = typeChoices.joinToString(separator = ", ") { getString(it.first) }
                homeProviderMetaInfo.isVisible = true
            }
        }

        observe(homeViewModel.randomItems) { items ->
            if (items.isNullOrEmpty()) {
                toggleMainVisibility(false)
            } else {
                val tempAdapter = homeMainPosterRecyclerview.adapter as HomeChildItemAdapter?
                if (tempAdapter != null && tempAdapter.cardList == items) {
                    toggleMainVisibility(true)
                    return@observe
                }
                val randomSize = items.size
                homeMainPosterRecyclerview.adapter = HomeChildItemAdapter(
                    items.toMutableList(),
                    R.layout.home_result_big_grid,
                    nextFocusUp = homeMainPosterRecyclerview.nextFocusUpId,
                    nextFocusDown = homeMainPosterRecyclerview.nextFocusDownId
                ) { callback -> homeHandleSearch(callback) }
                if (context?.isTvSettings() == false) {
                    homeMainPosterRecyclerview.post {
                        (homeMainPosterRecyclerview.layoutManager as? CenterZoomLayoutManager)?.let { manager ->
                            manager.updateSize(forceUpdate = true)
                            if (randomSize > 2) {
                                manager.scrollToPosition(randomSize / 2)
                                manager.snap { dx ->
                                    homeMainPosterRecyclerview.post {
                                        homeMainPosterRecyclerview.smoothScrollBy(dx, 0)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    items.firstOrNull()?.let { focusCallback(it) }
                }
                toggleMainVisibility(true)
            }
        }

        homeSearch.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                QuickSearchFragment.pushSearch(activity, query, currentApiName?.let { arrayOf(it) })
                return true
            }
            override fun onQueryTextChange(newText: String): Boolean = true
        })

        observe(homeViewModel.page) { data ->
            when (data) {
                is Resource.Success -> {
                    homeLoadingShimmer.stopShimmer()
                    val d = data.value
                    listHomepageItems.clear()
                    (homeMasterRecycler.adapter as? ParentItemAdapter?)?.updateList(d.values.toMutableList(), homeMasterRecycler)
                    homeLoading.isVisible = false
                    homeLoadingError.isVisible = false
                    homeLoaded.isVisible = true
                    if (toggleRandomButton) homeRandom.isVisible = listHomepageItems.isNotEmpty()
                    else homeRandom.isGone = true
                }
                is Resource.Failure -> {
                    homeLoadingShimmer.stopShimmer()
                    resultErrorText.text = data.errorString
                    homeReloadConnectionerror.setOnClickListener(apiChangeClickListener)
                    homeReloadConnectionOpenInBrowser.setOnClickListener { view ->
                        val validAPIs = apis
                        view.popupMenuNoIconsAndNoStringRes(validAPIs.mapIndexed { index, api -> Pair(index, api.name) }) {
                            try {
                                val i = Intent(Intent.ACTION_VIEW).apply { data = Uri.parse(validAPIs[itemId].mainUrl) }
                                startActivity(i)
                            } catch (e: Exception) { logError(e) }
                        }
                    }
                    homeLoading.isVisible = false
                    homeLoadingError.isVisible = true
                    homeLoaded.isVisible = false
                }
                is Resource.Loading -> {
                    (homeMasterRecycler.adapter as? ParentItemAdapter?)?.updateList(listOf())
                    homeLoadingShimmer.startShimmer()
                    homeLoading.isVisible = true
                    homeLoadingError.isVisible = false
                    homeLoaded.isVisible = false
                }
            }
        }

        val toggleList = listOf(
            Pair(homeTypeWatchingBtt, WatchType.WATCHING),
            Pair(homeTypeCompletedBtt, WatchType.COMPLETED),
            Pair(homeTypeDroppedBtt, WatchType.DROPPED),
            Pair(homeTypeOnHoldBtt, WatchType.ONHOLD),
            Pair(homePlanToWatchBtt, WatchType.PLANTOWATCH),
        )
        for (item in toggleList) {
            val watch = item.second
            item.first.setOnClickListener { homeViewModel.loadStoredData(EnumSet.of(watch)) }
            item.first.setOnLongClickListener { itemView ->
                val list = EnumSet.noneOf(WatchType::class.java)
                itemView.context.getKey<IntArray>(HOME_BOOKMARK_VALUE_LIST)
                    ?.map { WatchType.fromInternalId(it) }?.let { list.addAll(it) }
                if (list.contains(watch)) list.remove(watch) else list.add(watch)
                homeViewModel.loadStoredData(list)
                true
            }
        }

        observe(homeViewModel.availableWatchStatusTypes) { availableWatchStatusTypes ->
            context?.setKey(HOME_BOOKMARK_VALUE_LIST, availableWatchStatusTypes.first.map { it.internalId }.toIntArray())
            for (item in toggleList) {
                val watch = item.second
                item.first.apply {
                    isVisible = availableWatchStatusTypes.second.contains(watch)
                    isSelected = availableWatchStatusTypes.first.contains(watch)
                }
            }
        }

        observe(homeViewModel.bookmarks) { (isVis, bookmarks) ->
            homeBookmarkedHolder.isVisible = isVis
            (homeBookmarkedChildRecyclerview.adapter as? HomeChildItemAdapter?)?.updateList(bookmarks)
            homeBookmarkedChildMoreInfo.setOnClickListener {
                activity?.loadHomepageList(
                    HomePageList(getString(R.string.error_bookmarks_text), bookmarks)
                ) { deleteAllBookmarkedData(); homeViewModel.loadStoredData(null) }
            }
        }

        observe(homeViewModel.resumeWatching) { resumeWatching ->
            homeWatchHolder.isVisible = resumeWatching.isNotEmpty()
            (homeWatchChildRecyclerview.adapter as? HomeChildItemAdapter?)?.updateList(resumeWatching)
            homeWatchChildMoreInfo.setOnClickListener {
                activity?.loadHomepageList(
                    HomePageList(homeWatchParentItemTitle.text?.toString() ?: getString(R.string.continue_watching), resumeWatching)
                ) { deleteAllResumeStateIds(); homeViewModel.loadResumeWatching() }
            }
        }

        homeBookmarkedChildRecyclerview.adapter = HomeChildItemAdapter(
            ArrayList(),
            nextFocusUp = homeBookmarkedChildRecyclerview.nextFocusUpId,
            nextFocusDown = homeBookmarkedChildRecyclerview.nextFocusDownId
        ) { callback ->
            if (callback.action == SEARCH_ACTION_SHOW_METADATA) {
                activity?.showOptionSelectStringRes(
                    callback.view, callback.card.posterUrl,
                    listOf(R.string.action_open_watching, R.string.action_remove_from_bookmarks),
                    listOf(R.string.action_open_play, R.string.action_open_watching, R.string.action_remove_from_bookmarks)
                ) { (isTv, actionId) ->
                    fun play() { activity.loadSearchResult(callback.card, START_ACTION_RESUME_LATEST); reloadStored() }
                    fun remove() { setResultWatchState(callback.card.id, WatchType.NONE.internalId); reloadStored() }
                    fun info() { handleSearchClickCallback(activity, SearchClickCallback(SEARCH_ACTION_LOAD, callback.view, -1, callback.card)); reloadStored() }
                    if (isTv) {
                        when (actionId) { 0 -> play(); 1 -> info(); 2 -> remove() }
                    } else {
                        when (actionId) { 0 -> info(); 1 -> remove() }
                    }
                }
            } else {
                homeHandleSearch(callback)
            }
        }

        homeWatchChildRecyclerview.adapter = HomeChildItemAdapter(
            ArrayList(),
            nextFocusUp = homeWatchChildRecyclerview.nextFocusUpId,
            nextFocusDown = homeWatchChildRecyclerview.nextFocusDownId
        ) { callback ->
            if (callback.action == SEARCH_ACTION_SHOW_METADATA) {
                activity?.showOptionSelectStringRes(
                    callback.view, callback.card.posterUrl,
                    listOf(R.string.action_open_watching, R.string.action_remove_watching),
                    listOf(R.string.action_open_play, R.string.action_open_watching, R.string.action_remove_watching)
                ) { (isTv, actionId) ->
                    fun play() { activity.loadSearchResult(callback.card, START_ACTION_RESUME_LATEST); reloadStored() }
                    fun remove() { (callback.card as? DataStoreHelper.ResumeWatchingResult)?.let { removeLastWatched(it.parentId); reloadStored() } }
                    fun info() { handleSearchClickCallback(activity, SearchClickCallback(SEARCH_ACTION_LOAD, callback.view, -1, callback.card)); reloadStored() }
                    if (isTv) {
                        when (actionId) { 0 -> play(); 1 -> info(); 2 -> remove() }
                    } else {
                        when (actionId) { 0 -> info(); 1 -> remove() }
                    }
                }
            } else {
                homeHandleSearch(callback)
            }
        }

        context?.fixPaddingStatusbarView(homeStatusbar)
        context?.fixPaddingStatusbar(homeLoadingStatusbar)

        homeMasterRecycler.adapter = ParentItemAdapter(mutableListOf(), { callback -> homeHandleSearch(callback) },
            { item -> activity?.loadHomepageList(item, expandCallback = { homeViewModel.expandAndReturn(it) }) },
            { name -> homeViewModel.expand(name) })
        homeMasterRecycler.setMaxViewPoolSize(0, Int.MAX_VALUE)
        homeMasterRecycler.layoutManager = object : LinearLayoutManager(context) {
            override fun supportsPredictiveItemAnimations(): Boolean = false
        }

        if (context?.isTvSettings() == false) {
            LinearSnapHelper().attachToRecyclerView(homeMainPosterRecyclerview)
            val centerLayoutManager = CenterZoomLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            centerLayoutManager.setOnSizeListener { index ->
                (homeMainPosterRecyclerview.adapter as? HomeChildItemAdapter?)?.cardList?.get(index)?.let { random ->
                    homeMainPlay.setOnClickListener { activity.loadSearchResult(random, START_ACTION_RESUME_LATEST) }
                    homeMainInfo.setOnClickListener { activity.loadSearchResult(random) }
                    homeMainText.text = random.name + if (random is AnimeSearchResponse && !random.dubStatus.isNullOrEmpty()) {
                        random.dubStatus?.joinToString(prefix = " • ", separator = " | ") { it.name } ?: ""
                    } else ""
                }
            }
            homeMainPosterRecyclerview.layoutManager = centerLayoutManager
        }

        reloadStored()
        val apiName = context?.getKey<String>(HOMEPAGE_API)
        if (homeViewModel.apiName.value != apiName || apiName == null) {
            homeViewModel.loadAndCancel(apiName)
        }

        homeLoaded.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            val dy = scrollY - oldScrollY
            if (dy > 0) {
                homeApiFab.shrink()
                homeRandom.shrink()
            } else if (dy < -5 && context?.isTvSettings() == false) {
                homeApiFab.extend()
                homeRandom.extend()
            }
        })

        homeProfilePictureHolder.isVisible = false
        context?.let { ctx ->
            if (ctx.isTvSettings()) {
                homeApiFab.isVisible = false
                homeChangeApi.isVisible = true
                if (ctx.isTrueTvSettings()) {
                    homeChangeApiLoading.isVisible = true
                    homeChangeApiLoading.isFocusable = true
                    homeChangeApiLoading.isFocusableInTouchMode = true
                    homeChangeApi.isFocusable = true
                    homeChangeApi.isFocusableInTouchMode = true
                }
            } else {
                homeApiFab.isVisible = true
                homeChangeApi.isVisible = false
                homeChangeApiLoading.isVisible = false
            }
            for (syncApi in OAuth2Apis) {
                val login = syncApi.loginInfo()
                val pic = login?.profilePicture
                if (homeProfilePicture.setImage(pic, errorImageDrawable = errorProfilePic)) {
                    homeProfilePictureHolder.isVisible = true
                    break
                }
            }
        }
    }
}