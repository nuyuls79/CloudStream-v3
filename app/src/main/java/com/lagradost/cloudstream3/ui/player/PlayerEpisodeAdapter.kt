package com.lagradost.cloudstream3.ui.player

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.ContentLoadingProgressBar
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.PlayerEpisodesBinding
import com.lagradost.cloudstream3.databinding.PlayerEpisodesLargeBinding
import com.lagradost.cloudstream3.databinding.PlayerEpisodesSmallBinding
import com.lagradost.cloudstream3.databinding.ResultEpisodeLargeBinding
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.ui.result.getDisplayPosition
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isTrueTvSettings
import com.lagradost.cloudstream3.utils.AppUtils.html
import com.lagradost.cloudstream3.utils.UIHelper.setImage

data class PlayerEpisodeClickEvent(val action: Int, val data: Any)

class PlayerEpisodeAdapter(
    private val items: MutableList<Any> = mutableListOf(),
    private val clickCallback: (PlayerEpisodeClickEvent) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val binding = PlayerEpisodesBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PlayerEpisodeCardViewHolder(binding, clickCallback)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        println("HOLDER $holder $position")

        when (holder) {
            is PlayerEpisodeCardViewHolder -> {
                holder.bind(items[position])
            }
        }
    }

    override fun getItemCount(): Int {
        return items.size
    }

    fun updateList(newList: List<Any>) {
        println("Updated list $newList")
        val diffResult = DiffUtil.calculateDiff(EpisodeDiffCallback(this.items, newList))
        items.clear()
        items.addAll(newList)

        diffResult.dispatchUpdatesTo(this)
    }

    class PlayerEpisodeCardViewHolder
    constructor(
        private val binding: PlayerEpisodesBinding,
        private val clickCallback: (PlayerEpisodeClickEvent) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("SetTextI18n")
        fun bind(card: Any) {
            if (card is ResultEpisode) {
                // Determine which layout to use based on whether poster exists
                val isLargeLayout = card.poster != null
                
                // Get the appropriate binding for the selected layout
                val largeBinding = if (isLargeLayout) {
                    PlayerEpisodesLargeBinding.bind(binding.root.findViewById(R.id.episode_holder_large))
                } else null
                
                val smallBinding = if (!isLargeLayout) {
                    PlayerEpisodesSmallBinding.bind(binding.root.findViewById(R.id.episode_holder))
                } else null

                // Show/hide appropriate layouts
                if (isLargeLayout) {
                    binding.episodeHolderLarge?.isVisible = true
                    binding.episodeHolder?.isVisible = false
                } else {
                    binding.episodeHolderLarge?.isVisible = false
                    binding.episodeHolder?.isVisible = true
                }

                val activeBinding = if (isLargeLayout) largeBinding else smallBinding
                val rootView = if (isLargeLayout) binding.episodeHolderLarge else binding.episodeHolder

                if (activeBinding == null || rootView == null) return

                // Episode text
                activeBinding.episodeText?.apply {
                    val name = if (card.name == null) {
                        "${context.getString(R.string.episode)} ${card.episode}"
                    } else {
                        "${card.episode}. ${card.name}"
                    }
                    text = name
                    isSelected = true
                }

                // Filler indicator
                activeBinding.episodeFiller?.isVisible = card.isFiller == true

                // Progress bar
                val displayPos = card.getDisplayPosition()
                activeBinding.episodeProgress?.max = (card.duration / 1000).toInt()
                activeBinding.episodeProgress?.progress = (displayPos / 1000).toInt()
                activeBinding.episodeProgress?.isVisible = displayPos > 0L

                // Poster image (only for large layout)
                if (isLargeLayout && largeBinding != null) {
                    largeBinding.episodePoster?.isVisible = largeBinding.episodePoster?.setImage(card.poster) == true
                }

                // Rating
                if (card.rating != null) {
                    activeBinding.episodeRating?.text = activeBinding.episodeRating?.context
                        ?.getString(R.string.rated_format)
                        ?.format(card.rating.toFloat() / 10f)
                } else {
                    activeBinding.episodeRating?.text = ""
                }
                activeBinding.episodeRating?.isGone = activeBinding.episodeRating?.text.isNullOrBlank()

                // Description
                activeBinding.episodeDescript?.apply {
                    text = card.description.html()
                    isGone = text.isNullOrBlank()
                }

                // Click listener
                rootView.setOnClickListener {
                    clickCallback.invoke(PlayerEpisodeClickEvent(0, card))
                }

                // TV mode focus handling
                if (rootView.context.isTrueTvSettings()) {
                    rootView.isFocusable = true
                    rootView.isFocusableInTouchMode = true
                    rootView.touchscreenBlocksFocus = false
                }
            }
        }
    }
}

class EpisodeDiffCallback(
    private val oldList: List<Any>,
    private val newList: List<Any>
) : DiffUtil.Callback() {
    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val a = oldList[oldItemPosition]
        val b = newList[newItemPosition]
        return if (a is ResultEpisode && b is ResultEpisode) {
            a.id == b.id
        } else {
            a == b
        }
    }

    override fun getOldListSize() = oldList.size
    override fun getNewListSize() = newList.size
    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
        oldList[oldItemPosition] == newList[newItemPosition]
}