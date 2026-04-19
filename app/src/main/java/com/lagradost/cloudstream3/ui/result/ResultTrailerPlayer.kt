package com.lagradost.cloudstream3.ui.result

import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.view.isVisible
import com.discord.panels.PanelsChildGestureRegionObserver
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.player.SubtitleData
import com.lagradost.cloudstream3.utils.IOnBackPressed

open class ResultTrailerPlayer : com.lagradost.cloudstream3.ui.player.FullScreenPlayer(),
    PanelsChildGestureRegionObserver.GestureRegionsListener, IOnBackPressed {

    override var lockRotation = false
    override var isFullScreenPlayer = false
    override var hasPipModeSupport = false

    companion object {
        const val TAG = "RESULT_TRAILER"
    }

    var playerWidthHeight: Pair<Int, Int>? = null

    override fun nextEpisode() {}

    override fun prevEpisode() {}

    override fun playerPositionChanged(posDur: Pair<Long, Long>) {}

    override fun nextMirror() {}

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        uiReset()
        fixPlayerSize()
    }

    private fun fixPlayerSize() {
        playerWidthHeight?.let { (w, h) ->
            val orientation = context?.resources?.configuration?.orientation ?: return

            val sw = if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                screenWidth
            } else {
                screenHeight
            }

            requireView().findViewById<View>(R.id.result_trailer_loading)?.isVisible = false
            requireView().findViewById<View>(R.id.result_smallscreen_holder)?.isVisible = !isFullScreenPlayer
            requireView().findViewById<View>(R.id.result_fullscreen_holder)?.isVisible = isFullScreenPlayer

            val playerBackground = requireView().findViewById<FrameLayout>(R.id.player_background)
            playerBackground?.apply {
                isVisible = true
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    if (isFullScreenPlayer) FrameLayout.LayoutParams.MATCH_PARENT else sw * h / w
                )
            }
        }
    }

    override fun playerDimensionsLoaded(widthHeight: Pair<Int, Int>) {
        playerWidthHeight = widthHeight
        fixPlayerSize()
    }

    override fun showMirrorsDialogue() {}
    override fun openOnlineSubPicker(context: Context, imdbId: Long?, dismissCallback: () -> Unit) {}

    override fun subtitlesChanged() {}

    override fun embeddedSubtitlesFetched(subtitles: List<SubtitleData>) {}

    override fun exitedPipMode() {}

    override fun onGestureRegionsUpdate(gestureRegions: List<Rect>) {}

    private fun updateFullscreen(fullscreen: Boolean) {
        isFullScreenPlayer = fullscreen
        lockRotation = fullscreen
        val fullscreenBtn = requireView().findViewById<ImageView>(R.id.player_fullscreen)
        fullscreenBtn?.setImageResource(if (fullscreen) R.drawable.baseline_fullscreen_exit_24 else R.drawable.baseline_fullscreen_24)
        if (fullscreen) {
            enterFullscreen()
            requireView().findViewById<View>(R.id.result_top_bar)?.isVisible = false
            requireView().findViewById<View>(R.id.result_fullscreen_holder)?.isVisible = true
            requireView().findViewById<View>(R.id.result_main_holder)?.isVisible = false
            val playerBackground = requireView().findViewById<FrameLayout>(R.id.player_background)
            playerBackground?.let { view ->
                (view.parent as? ViewGroup)?.removeView(view)
                requireView().findViewById<ViewGroup>(R.id.result_fullscreen_holder)?.addView(view)
            }
        } else {
            requireView().findViewById<View>(R.id.result_top_bar)?.isVisible = true
            requireView().findViewById<View>(R.id.result_fullscreen_holder)?.isVisible = false
            requireView().findViewById<View>(R.id.result_main_holder)?.isVisible = true
            val playerBackground = requireView().findViewById<FrameLayout>(R.id.player_background)
            playerBackground?.let { view ->
                (view.parent as? ViewGroup)?.removeView(view)
                requireView().findViewById<ViewGroup>(R.id.result_smallscreen_holder)?.addView(view)
            }
            exitFullscreen()
        }
        fixPlayerSize()
        uiReset()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val fullscreenBtn = requireView().findViewById<ImageView>(R.id.player_fullscreen)
        fullscreenBtn?.setOnClickListener {
            updateFullscreen(!isFullScreenPlayer)
        }
        updateFullscreen(isFullScreenPlayer)
        uiReset()
    }

    override fun onBackPressed(): Boolean {
        return if (isFullScreenPlayer) {
            updateFullscreen(false)
            false
        } else {
            true
        }
    }
}