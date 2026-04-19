package com.lagradost.cloudstream3.ui.download

import android.app.Dialog
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.FragmentDownloadsBinding
import com.lagradost.cloudstream3.databinding.StreamInputBinding
import com.lagradost.cloudstream3.isMovieType
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.ui.download.DownloadButtonSetup.handleDownloadClick
import com.lagradost.cloudstream3.ui.player.GeneratorPlayer
import com.lagradost.cloudstream3.ui.player.LinkGenerator
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isTvSettings
import com.lagradost.cloudstream3.utils.AppUtils.loadResult
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.DOWNLOAD_EPISODE_CACHE
import com.lagradost.cloudstream3.utils.DataStore
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import com.lagradost.cloudstream3.utils.UIHelper.fixPaddingStatusbar
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import com.lagradost.cloudstream3.utils.VideoDownloadHelper
import com.lagradost.cloudstream3.utils.VideoDownloadManager

const val DOWNLOAD_NAVIGATE_TO = "downloadpage"

class DownloadFragment : Fragment() {
    private lateinit var downloadsViewModel: DownloadViewModel
    private var _binding: FragmentDownloadsBinding? = null
    private val binding get() = _binding!!

    private fun getBytesAsText(bytes: Long): String {
        return "%.1f".format(bytes / 1000000000f)
    }

    private fun View.setLayoutWidth(weight: Long) {
        val param = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.MATCH_PARENT,
            maxOf((weight / 1000000000f), 0.1f) // 100mb
        )
        this.layoutParams = param
    }

    private fun setList(list: List<VisualDownloadHeaderCached>) {
        main {
            (binding.downloadList.adapter as DownloadHeaderAdapter?)?.cardList = list
            binding.downloadList.adapter?.notifyDataSetChanged()
        }
    }

    override fun onDestroyView() {
        if (downloadDeleteEventListener != null) {
            VideoDownloadManager.downloadDeleteEvent -= downloadDeleteEventListener!!
            downloadDeleteEventListener = null
        }
        (binding.downloadList.adapter as DownloadHeaderAdapter?)?.killAdapter()
        super.onDestroyView()
        _binding = null
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        downloadsViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
        _binding = FragmentDownloadsBinding.inflate(inflater, container, false)
        return binding.root
    }

    private var downloadDeleteEventListener: ((Int) -> Unit)? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        hideKeyboard()

        observe(downloadsViewModel.noDownloadsText) {
            binding.textNoDownloads.text = it
        }
        observe(downloadsViewModel.headerCards) {
            setList(it)
            binding.downloadLoading.isVisible = false
        }
        observe(downloadsViewModel.availableBytes) {
            binding.downloadFreeTxt?.text = getString(R.string.storage_size_format).format(
                getString(R.string.free_storage),
                getBytesAsText(it)
            )
            binding.downloadFree?.setLayoutWidth(it)
        }
        observe(downloadsViewModel.usedBytes) {
            binding.downloadUsedTxt?.text = getString(R.string.storage_size_format).format(
                getString(R.string.used_storage),
                getBytesAsText(it)
            )
            binding.downloadUsed?.setLayoutWidth(it)
        }
        observe(downloadsViewModel.downloadBytes) {
            binding.downloadAppTxt?.text = getString(R.string.storage_size_format).format(
                getString(R.string.app_storage),
                getBytesAsText(it)
            )
            binding.downloadApp?.setLayoutWidth(it)
            binding.downloadStorageAppbar?.isVisible = it > 0
        }

        val adapter: RecyclerView.Adapter<RecyclerView.ViewHolder> = DownloadHeaderAdapter(
            ArrayList(),
            { click ->
                when (click.action) {
                    0 -> {
                        if (click.data.type.isMovieType()) {
                            // won't be called
                        } else {
                            val folder = DataStore.getFolderName(
                                DOWNLOAD_EPISODE_CACHE,
                                click.data.id.toString()
                            )
                            activity?.navigate(
                                R.id.action_navigation_downloads_to_navigation_download_child,
                                DownloadChildFragment.newInstance(click.data.name, folder)
                            )
                        }
                    }
                    1 -> {
                        (activity as AppCompatActivity?)?.loadResult(
                            click.data.url,
                            click.data.apiName
                        )
                    }
                }
            },
            { downloadClickEvent ->
                if (downloadClickEvent.data !is VideoDownloadHelper.DownloadEpisodeCached) return@DownloadHeaderAdapter
                handleDownloadClick(activity, downloadClickEvent.data.name, downloadClickEvent)
                if (downloadClickEvent.action == DOWNLOAD_ACTION_DELETE_FILE) {
                    context?.let { ctx ->
                        downloadsViewModel.updateList(ctx)
                    }
                }
            }
        )

        downloadDeleteEventListener = { id ->
            val list = (binding.downloadList.adapter as DownloadHeaderAdapter?)?.cardList
            if (list != null) {
                if (list.any { it.data.id == id }) {
                    context?.let { ctx ->
                        setList(ArrayList())
                        downloadsViewModel.updateList(ctx)
                    }
                }
            }
        }

        downloadDeleteEventListener?.let { VideoDownloadManager.downloadDeleteEvent += it }

        binding.downloadList.adapter = adapter
        binding.downloadList.layoutManager = GridLayoutManager(context, 1)
        binding.downloadStreamButton?.isGone = context?.isTvSettings() == true
        binding.downloadStreamButton?.setOnClickListener {
            val dialog = Dialog(it.context ?: return@setOnClickListener, R.style.AlertDialogCustom)
            dialog.setContentView(R.layout.stream_input)
            val streamBinding = StreamInputBinding.bind(dialog.findViewById(android.R.id.custom) ?: return@setOnClickListener)

            dialog.show()

            (activity?.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager?)?.primaryClip?.getItemAt(0)?.text?.toString()?.let { copy ->
                streamBinding.streamUrl?.setText(copy)
            }

            streamBinding.applyBtt?.setOnClickListener {
                val url = streamBinding.streamUrl.text?.toString()
                if (url.isNullOrEmpty()) {
                    showToast(activity, R.string.error_invalid_url, Toast.LENGTH_SHORT)
                } else {
                    val referer = streamBinding.streamReferer.text?.toString()
                    activity?.navigate(
                        R.id.global_to_navigation_player,
                        GeneratorPlayer.newInstance(
                            LinkGenerator(
                                listOf(url),
                                extract = true,
                                referer = referer
                            )
                        )
                    )
                    dialog.dismissSafe(activity)
                }
            }

            streamBinding.cancelBtt?.setOnClickListener {
                dialog.dismissSafe(activity)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            binding.downloadList.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                val dy = scrollY - oldScrollY
                if (dy > 0) {
                    binding.downloadStreamButton?.shrink()
                } else if (dy < -5) {
                    binding.downloadStreamButton?.extend()
                }
            }
        }

        downloadsViewModel.updateList(requireContext())
        context?.fixPaddingStatusbar(binding.downloadRoot)
    }
}