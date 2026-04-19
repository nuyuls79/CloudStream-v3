package com.lagradost.cloudstream3.ui.player

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Color
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.util.DisplayMetrics
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.CommonActivity.keyEventListener
import com.lagradost.cloudstream3.CommonActivity.playerEventListener
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.PlayerCustomLayoutBinding
import com.lagradost.cloudstream3.databinding.TrailerCustomLayoutBinding
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.ui.player.GeneratorPlayer.Companion.subsProvidersIsActive
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showDialog
import com.lagradost.cloudstream3.utils.UIHelper.colorFromAttribute
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import com.lagradost.cloudstream3.utils.UIHelper.getNavigationBarHeight
import com.lagradost.cloudstream3.utils.UIHelper.getStatusBarHeight
import com.lagradost.cloudstream3.utils.UIHelper.hideSystemUI
import com.lagradost.cloudstream3.utils.UIHelper.popCurrentPage
import com.lagradost.cloudstream3.utils.UIHelper.showSystemUI
import com.lagradost.cloudstream3.utils.UIHelper.toPx
import com.lagradost.cloudstream3.utils.Vector2
import kotlin.math.*

const val MINIMUM_SEEK_TIME = 7000L
const val MINIMUM_VERTICAL_SWIPE = 2.0f
const val MINIMUM_HORIZONTAL_SWIPE = 2.0f
const val VERTICAL_MULTIPLIER = 2.0f
const val HORIZONTAL_MULTIPLIER = 2.0f
const val DOUBLE_TAB_MAXIMUM_HOLD_TIME = 200L
const val DOUBLE_TAB_MINIMUM_TIME_BETWEEN = 200L
const val DOUBLE_TAB_PAUSE_PERCENTAGE = 0.15

open class FullScreenPlayer : AbstractPlayerFragment() {
    protected open var lockRotation = true
    protected open var isFullScreenPlayer = true
    protected open var isTv = false

    // View Binding
    protected var customLayoutBinding: PlayerCustomLayoutBinding? = null
    protected var trailerBinding: TrailerCustomLayoutBinding? = null

    protected var isShowing = false
    protected var isLocked = false

    protected fun setEpisodes(ep: List<Any>) {
        // hasEpisodes = ep.size > 1
        // (player_episode_list?.adapter as? PlayerEpisodeAdapter?)?.updateList(ep)
    }

    protected var hasEpisodes = false
        private set

    protected var currentPrefQuality = Qualities.P2160.value
    protected var fastForwardTime = 10000L
    protected var swipeHorizontalEnabled = false
    protected var swipeVerticalEnabled = false
    protected var playBackSpeedEnabled = false
    protected var playerResizeEnabled = false
    protected var doubleTapEnabled = false
    protected var doubleTapPauseEnabled = true

    protected var subtitleDelay
        set(value) = try {
            player.setSubtitleOffset(-value)
        } catch (e: Exception) {
            logError(e)
        }
        get() = try {
            -player.getSubtitleOffset()
        } catch (e: Exception) {
            logError(e)
            0L
        }

    protected var useTrueSystemBrightness = true
    private val fullscreenNotch = true

    protected val displayMetrics: DisplayMetrics = Resources.getSystem().displayMetrics

    protected val screenWidth: Int
        get() = max(displayMetrics.widthPixels, displayMetrics.heightPixels)
    protected val screenHeight: Int
        get() = min(displayMetrics.widthPixels, displayMetrics.heightPixels)

    private var statusBarHeight: Int? = null
    private var navigationBarHeight: Int? = null

    private val brightnessIcons = listOf(
        R.drawable.sun_1, R.drawable.sun_2, R.drawable.sun_3,
        R.drawable.sun_4, R.drawable.sun_5, R.drawable.sun_6
    )

    private val volumeIcons = listOf(
        R.drawable.ic_baseline_volume_mute_24,
        R.drawable.ic_baseline_volume_down_24,
        R.drawable.ic_baseline_volume_up_24,
    )

    open fun showMirrorsDialogue() {
        throw NotImplementedError()
    }

    open fun openOnlineSubPicker(
        context: Context,
        imdbId: Long?,
        dismissCallback: (() -> Unit)
    ) {
        throw NotImplementedError()
    }

    private fun isValidTouch(rawX: Float, rawY: Float): Boolean {
        val statusHeight = statusBarHeight ?: 0
        return rawY > statusHeight && rawX < screenWidth
    }

    override fun exitedPipMode() {
        animateLayoutChanges()
    }

    protected fun animateLayoutChanges() {
        val binding = customLayoutBinding ?: return

        if (isShowing) {
            updateUIVisibility()
        } else {
            binding.playerHolder?.postDelayed({ updateUIVisibility() }, 200)
        }

        val titleMove = if (isShowing) 0f else -50.toPx.toFloat()
        fragmentBinding?.playerVideoTitle?.let {
            ObjectAnimator.ofFloat(it, "translationY", titleMove).apply {
                duration = 200
                start()
            }
        }
        fragmentBinding?.playerVideoTitleRez?.let {
            ObjectAnimator.ofFloat(it, "translationY", titleMove).apply {
                duration = 200
                start()
            }
        }
        val playerBarMove = if (isShowing) 0f else 50.toPx.toFloat()
        binding.bottomPlayerBar?.let {
            ObjectAnimator.ofFloat(it, "translationY", playerBarMove).apply {
                duration = 200
                start()
            }
        }

        val fadeTo = if (isShowing) 1f else 0f
        val fadeAnimation = AlphaAnimation(1f - fadeTo, fadeTo).apply {
            duration = 100
            fillAfter = true
        }

        val sView = subView
        val sStyle = subStyle
        if (sView != null && sStyle != null) {
            val move = if (isShowing) -((binding.bottomPlayerBar?.height?.toFloat() ?: 0f) + 40.toPx) else -sStyle.elevation.toPx.toFloat()
            ObjectAnimator.ofFloat(sView, "translationY", move).apply {
                duration = 200
                start()
            }
        }

        val playerSourceMove = if (isShowing) 0f else -50.toPx.toFloat()
        fragmentBinding?.playerOpenSource?.let {
            ObjectAnimator.ofFloat(it, "translationY", playerSourceMove).apply {
                duration = 200
                start()
            }
        }

        if (!isLocked) {
            binding.playerFfwdHolder?.alpha = 1f
            binding.playerRewHolder?.alpha = 1f
            binding.shadowOverlay?.isVisible = true
            binding.shadowOverlay?.startAnimation(fadeAnimation)
            binding.playerFfwdHolder?.startAnimation(fadeAnimation)
            binding.playerRewHolder?.startAnimation(fadeAnimation)
            binding.playerPausePlay?.startAnimation(fadeAnimation)
        }

        binding.bottomPlayerBar?.startAnimation(fadeAnimation)
        fragmentBinding?.playerOpenSource?.startAnimation(fadeAnimation)
        fragmentBinding?.playerTopHolder?.startAnimation(fadeAnimation)
    }

    override fun subtitlesChanged() {
        customLayoutBinding?.playerSubtitleOffsetBtt?.isGone = player.getCurrentPreferredSubtitle() == null
    }

    protected fun enterFullscreen() {
        if (isFullScreenPlayer) {
            activity?.hideSystemUI()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && fullscreenNotch) {
                val params = activity?.window?.attributes
                params?.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                activity?.window?.attributes = params
            }
        }
        if (lockRotation)
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    protected fun exitFullscreen() {
        activity?.showSystemUI()
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER
        val lp = activity?.window?.attributes
        lp?.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lp?.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
        }
        activity?.window?.attributes = lp
    }

    override fun onResume() {
        enterFullscreen()
        super.onResume()
    }

    override fun onDestroy() {
        exitFullscreen()
        player.release()
        player.releaseCallbacks()
        super.onDestroy()
    }

    private fun setPlayBackSpeed(speed: Float) {
        try {
            setKey(PLAYBACK_SPEED_KEY, speed)
            customLayoutBinding?.playerSpeedBtt?.text =
                getString(R.string.player_speed_text_format).format(speed).replace(".0x", "x")
        } catch (e: Exception) {
            logError(e)
        }
        player.setPlaybackSpeed(speed)
    }

    private fun skipOp() {
        player.seekTime(85000)
    }

    private fun showSubtitleOffsetDialog() {
        context?.let { ctx ->
            val builder = AlertDialog.Builder(ctx, R.style.AlertDialogCustom)
                .setView(R.layout.subtitle_offset)
            val dialog = builder.create()
            dialog.show()

            val beforeOffset = subtitleDelay

            val applyButton = dialog.findViewById<TextView>(R.id.apply_btt)!!
            val cancelButton = dialog.findViewById<TextView>(R.id.cancel_btt)!!
            val input = dialog.findViewById<EditText>(R.id.subtitle_offset_input)!!
            val sub = dialog.findViewById<ImageView>(R.id.subtitle_offset_subtract)!!
            val subMore = dialog.findViewById<ImageView>(R.id.subtitle_offset_subtract_more)!!
            val add = dialog.findViewById<ImageView>(R.id.subtitle_offset_add)!!
            val addMore = dialog.findViewById<ImageView>(R.id.subtitle_offset_add_more)!!
            val subTitle = dialog.findViewById<TextView>(R.id.subtitle_offset_sub_title)!!

            input.doOnTextChanged { text, _, _, _ ->
                text?.toString()?.toLongOrNull()?.let {
                    subtitleDelay = it
                    when {
                        it > 0L -> context?.getString(R.string.subtitle_offset_extra_hint_later_format)?.format(it)
                        it < 0L -> context?.getString(R.string.subtitle_offset_extra_hint_before_format)?.format(-it)
                        it == 0L -> context?.getString(R.string.subtitle_offset_extra_hint_none_format)
                        else -> null
                    }?.let { str -> subTitle.text = str }
                }
            }
            input.text = Editable.Factory.getInstance()?.newEditable(beforeOffset.toString())

            val buttonChange = 100L
            val buttonChangeMore = 1000L

            fun changeBy(by: Long) {
                val current = (input.text?.toString()?.toLongOrNull() ?: 0) + by
                input.text = Editable.Factory.getInstance()?.newEditable(current.toString())
            }

            add.setOnClickListener { changeBy(buttonChange) }
            addMore.setOnClickListener { changeBy(buttonChangeMore) }
            sub.setOnClickListener { changeBy(-buttonChange) }
            subMore.setOnClickListener { changeBy(-buttonChangeMore) }

            dialog.setOnDismissListener {
                if (isFullScreenPlayer) activity?.hideSystemUI()
            }
            applyButton.setOnClickListener {
                dialog.dismissSafe(activity)
                player.seekTime(1L)
            }
            cancelButton.setOnClickListener {
                subtitleDelay = beforeOffset
                dialog.dismissSafe(activity)
            }
        }
    }

    private fun showSpeedDialog() {
        val speedsText = listOf("0.5x", "0.75x", "0.85x", "1x", "1.15x", "1.25x", "1.4x", "1.5x", "1.75x", "2x")
        val speedsNumbers = listOf(0.5f, 0.75f, 0.85f, 1f, 1.15f, 1.25f, 1.4f, 1.5f, 1.75f, 2f)
        val speedIndex = speedsNumbers.indexOf(player.getPlaybackSpeed())

        activity?.let { act ->
            act.showDialog(
                speedsText, speedIndex, act.getString(R.string.player_speed), false,
                { if (isFullScreenPlayer) activity?.hideSystemUI() }
            ) { index ->
                if (isFullScreenPlayer) activity?.hideSystemUI()
                setPlayBackSpeed(speedsNumbers[index])
            }
        }
    }

    fun resetRewindText() {
        customLayoutBinding?.exoRewText?.text = getString(R.string.rew_text_regular_format).format(fastForwardTime / 1000)
    }

    fun resetFastForwardText() {
        customLayoutBinding?.exoFfwdText?.text = getString(R.string.ffw_text_regular_format).format(fastForwardTime / 1000)
    }

    private fun rewind() {
        val binding = customLayoutBinding ?: return
        try {
            fragmentBinding?.playerCenterMenu?.isGone = false
            binding.playerRewHolder?.alpha = 1f

            val rotateLeft = AnimationUtils.loadAnimation(context, R.anim.rotate_left)
            binding.exoRew?.startAnimation(rotateLeft)

            val goLeft = AnimationUtils.loadAnimation(context, R.anim.go_left)
            goLeft.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {}
                override fun onAnimationRepeat(animation: Animation?) {}
                override fun onAnimationEnd(animation: Animation?) {
                    binding.exoRewText?.post {
                        resetRewindText()
                        fragmentBinding?.playerCenterMenu?.isGone = !isShowing
                        binding.playerRewHolder?.alpha = if (isShowing) 1f else 0f
                    }
                }
            })
            binding.exoRewText?.startAnimation(goLeft)
            binding.exoRewText?.text = getString(R.string.rew_text_format).format(fastForwardTime / 1000)
            player.seekTime(-fastForwardTime)
        } catch (e: Exception) {
            logError(e)
        }
    }

    private fun fastForward() {
        val binding = customLayoutBinding ?: return
        try {
            fragmentBinding?.playerCenterMenu?.isGone = false
            binding.playerFfwdHolder?.alpha = 1f

            val rotateRight = AnimationUtils.loadAnimation(context, R.anim.rotate_right)
            binding.exoFfwd?.startAnimation(rotateRight)

            val goRight = AnimationUtils.loadAnimation(context, R.anim.go_right)
            goRight.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {}
                override fun onAnimationRepeat(animation: Animation?) {}
                override fun onAnimationEnd(animation: Animation?) {
                    binding.exoFfwdText?.post {
                        resetFastForwardText()
                        fragmentBinding?.playerCenterMenu?.isGone = !isShowing
                        binding.playerFfwdHolder?.alpha = if (isShowing) 1f else 0f
                    }
                }
            })
            binding.exoFfwdText?.startAnimation(goRight)
            binding.exoFfwdText?.text = getString(R.string.ffw_text_format).format(fastForwardTime / 1000)
            player.seekTime(fastForwardTime)
        } catch (e: Exception) {
            logError(e)
        }
    }

    private fun onClickChange() {
        isShowing = !isShowing
        if (isShowing) {
            customLayoutBinding?.playerIntroPlay?.isGone = true
            autoHide()
        }
        if (isFullScreenPlayer) activity?.hideSystemUI()
        animateLayoutChanges()
        customLayoutBinding?.playerPausePlay?.requestFocus()
    }

    private fun toggleLock() {
        val binding = customLayoutBinding ?: return

        if (!isShowing) {
            onClickChange()
        }

        isLocked = !isLocked
        if (isLocked && isShowing) {
            binding.playerHolder?.postDelayed({
                if (isLocked && isShowing) {
                    onClickChange()
                }
            }, 200)
        }

        val fadeTo = if (isLocked) 0f else 1f
        val fadeAnimation = AlphaAnimation(fragmentBinding?.playerVideoTitle?.alpha ?: 1f, fadeTo).apply {
            duration = 100
            fillAfter = true
        }

        updateUIVisibility()
        binding.playerPausePlay?.startAnimation(fadeAnimation)
        binding.playerFfwdHolder?.startAnimation(fadeAnimation)
        binding.playerRewHolder?.startAnimation(fadeAnimation)

        fragmentBinding?.playerVideoTitleRez?.startAnimation(fadeAnimation)
        fragmentBinding?.playerEpisodeFiller?.startAnimation(fadeAnimation)
        fragmentBinding?.playerVideoTitle?.startAnimation(fadeAnimation)
        fragmentBinding?.playerTopHolder?.startAnimation(fadeAnimation)

        binding.playerLockHolder?.startAnimation(fadeAnimation)
        binding.shadowOverlay?.isVisible = true
        binding.shadowOverlay?.startAnimation(fadeAnimation)

        updateLockUI()
    }

    private fun updateUIVisibility() {
        val binding = customLayoutBinding ?: return
        val isGone = isLocked || !isShowing
        var togglePlayerTitleGone = isGone
        context?.let {
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(it)
            val limitTitle = settingsManager.getInt(getString(R.string.prefer_limit_title_key), 0)
            if (limitTitle < 0) {
                togglePlayerTitleGone = true
            }
        }
        binding.playerLockHolder?.isGone = isGone
        fragmentBinding?.playerVideoBar?.isGone = isGone
        binding.playerPausePlayHolder?.isGone = isGone
        binding.playerPausePlay?.isGone = isGone
        fragmentBinding?.playerTopHolder?.isGone = isGone
        fragmentBinding?.playerVideoTitle?.isGone = togglePlayerTitleGone
        fragmentBinding?.playerVideoTitleRez?.isGone = isGone
        fragmentBinding?.playerEpisodeFiller?.isGone = isGone
        fragmentBinding?.playerCenterMenu?.isGone = isGone
        binding.playerLock?.isGone = !isShowing
        fragmentBinding?.playerGoBackHolder?.isGone = isGone
    }

    private fun updateLockUI() {
        val binding = customLayoutBinding ?: return
        binding.playerLock?.setIconResource(if (isLocked) R.drawable.video_locked else R.drawable.video_unlocked)
        if (layout == R.layout.fragment_player) {
            val color = if (isLocked) context?.colorFromAttribute(R.attr.colorPrimary) else Color.WHITE
            if (color != null) {
                binding.playerLock?.setTextColor(color)
                binding.playerLock?.iconTint = ColorStateList.valueOf(color)
                binding.playerLock?.rippleColor = ColorStateList.valueOf(Color.argb(50, color.red, color.green, color.blue))
            }
        }
    }

    private var currentTapIndex = 0
    protected fun autoHide() {
        currentTapIndex++
        val index = currentTapIndex
        customLayoutBinding?.playerHolder?.postDelayed({
            if (!isCurrentTouchValid && isShowing && index == currentTapIndex && player.getIsPlaying()) {
                onClickChange()
            }
        }, 2000)
    }

    private var currentDoubleTapIndex = 0
    private fun toggleShowDelayed() {
        if (doubleTapEnabled || doubleTapPauseEnabled) {
            val index = currentDoubleTapIndex
            customLayoutBinding?.playerHolder?.postDelayed({
                if (index == currentDoubleTapIndex) {
                    onClickChange()
                }
            }, DOUBLE_TAB_MINIMUM_TIME_BETWEEN)
        } else {
            onClickChange()
        }
    }

    private var isCurrentTouchValid = false
    private var currentTouchStart: Vector2? = null
    private var currentTouchLast: Vector2? = null
    private var currentTouchAction: TouchAction? = null
    private var currentLastTouchAction: TouchAction? = null
    private var currentTouchStartPlayerTime: Long? = null
    private var currentTouchStartTime: Long? = null
    private var currentLastTouchEndTime: Long = 0
    private var currentClickCount: Int = 0

    private var currentRequestedVolume: Float = 0.0f
    private var currentRequestedBrightness: Float = 1.0f

    enum class TouchAction {
        Brightness, Volume, Time
    }

    companion object {
        private fun forceLetters(inp: Long, letters: Int = 2): String {
            val added = letters - inp.toString().length
            return if (added > 0) "0".repeat(added) + inp.toString() else inp.toString()
        }

        private fun convertTimeToString(sec: Long): String {
            val rsec = sec % 60L
            val min = ceil((sec - rsec) / 60.0).toInt()
            val rmin = min % 60L
            val h = ceil((min - rmin) / 60.0).toLong()
            return (if (h > 0) forceLetters(h) + ":" else "") + (if (rmin >= 0 || h >= 0) forceLetters(rmin) + ":" else "") + forceLetters(rsec)
        }
    }

    private fun calculateNewTime(startTime: Long?, touchStart: Vector2?, touchEnd: Vector2?): Long? {
        if (touchStart == null || touchEnd == null || startTime == null) return null
        val diffX = (touchEnd.x - touchStart.x) * HORIZONTAL_MULTIPLIER / screenWidth.toFloat()
        val duration = player.getDuration() ?: return null
        return max(min(startTime + ((duration * (diffX * diffX)) * (if (diffX < 0) -1 else 1)).toLong(), duration), 0)
    }

    private fun getBrightness(): Float? {
        return if (useTrueSystemBrightness) {
            try {
                Settings.System.getInt(context?.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
            } catch (e: Exception) {
                useTrueSystemBrightness = false
                getBrightness()
            }
        } else {
            try {
                activity?.window?.attributes?.screenBrightness
            } catch (e: Exception) {
                logError(e)
                null
            }
        }
    }

    private fun setBrightness(brightness: Float) {
        if (useTrueSystemBrightness) {
            try {
                Settings.System.putInt(context?.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                Settings.System.putInt(context?.contentResolver, Settings.System.SCREEN_BRIGHTNESS, (brightness * 255).toInt())
            } catch (e: Exception) {
                useTrueSystemBrightness = false
                setBrightness(brightness)
            }
        } else {
            try {
                val lp = activity?.window?.attributes
                lp?.screenBrightness = brightness
                activity?.window?.attributes = lp
            } catch (e: Exception) {
                logError(e)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun handleMotionEvent(view: View?, event: MotionEvent?): Boolean {
        val binding = customLayoutBinding ?: return false
        if (event == null || view == null) return false
        val currentTouch = Vector2(event.x, event.y)
        val startTouch = currentTouchStart
        binding.playerIntroPlay?.isGone = true
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isCurrentTouchValid = isValidTouch(currentTouch.x, currentTouch.y)
                if (isCurrentTouchValid) {
                    currentTouchStartTime = System.currentTimeMillis()
                    currentTouchStart = currentTouch
                    currentTouchLast = currentTouch
                    currentTouchStartPlayerTime = player.getPosition()

                    getBrightness()?.let { currentRequestedBrightness = it }
                    (activity?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.let { audioManager ->
                        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        currentRequestedVolume = currentVolume.toFloat() / maxVolume.toFloat()
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (isCurrentTouchValid && !isLocked && isFullScreenPlayer) {
                    if (swipeHorizontalEnabled && currentTouchAction == TouchAction.Time) {
                        val startTime = currentTouchStartPlayerTime
                        if (startTime != null) {
                            calculateNewTime(startTime, startTouch, currentTouch)?.let { seekTo ->
                                if (abs(seekTo - startTime) > MINIMUM_SEEK_TIME) {
                                    player.seekTo(seekTo)
                                }
                            }
                        }
                    }
                }

                val holdTime = currentTouchStartTime?.minus(System.currentTimeMillis())
                if (isCurrentTouchValid && currentTouchAction == null && currentLastTouchAction == null
                    && holdTime != null && holdTime < DOUBLE_TAB_MAXIMUM_HOLD_TIME) {
                    if (!isLocked && (System.currentTimeMillis() - currentLastTouchEndTime) < DOUBLE_TAB_MINIMUM_TIME_BETWEEN) {
                        currentClickCount++
                        if (currentClickCount >= 1) {
                            currentDoubleTapIndex++
                            if (doubleTapPauseEnabled && isFullScreenPlayer) {
                                when {
                                    currentTouch.x < screenWidth / 2 - (DOUBLE_TAB_PAUSE_PERCENTAGE * screenWidth) -> {
                                        if (doubleTapEnabled) rewind()
                                    }
                                    currentTouch.x > screenWidth / 2 + (DOUBLE_TAB_PAUSE_PERCENTAGE * screenWidth) -> {
                                        if (doubleTapEnabled) fastForward()
                                    }
                                    else -> player.handleEvent(CSPlayerEvent.PlayPauseToggle)
                                }
                            } else if (doubleTapEnabled && isFullScreenPlayer) {
                                if (currentTouch.x < screenWidth / 2) rewind() else fastForward()
                            }
                        }
                    } else {
                        currentClickCount = 0
                        toggleShowDelayed()
                    }
                } else {
                    currentClickCount = 0
                }

                autoHide()
                isCurrentTouchValid = false
                currentTouchStart = null
                currentLastTouchAction = currentTouchAction
                currentTouchAction = null
                currentTouchStartPlayerTime = null
                currentTouchLast = null
                currentTouchStartTime = null

                binding.playerTimeText?.isVisible = false
                binding.playerProgressbarLeftHolder?.isVisible = false
                binding.playerProgressbarRightHolder?.isVisible = false
                currentLastTouchEndTime = System.currentTimeMillis()
            }
            MotionEvent.ACTION_MOVE -> {
                if (startTouch != null && isCurrentTouchValid && !isLocked && isFullScreenPlayer) {
                    if (currentTouchAction == null) {
                        val diffFromStart = startTouch - currentTouch
                        if (swipeVerticalEnabled) {
                            if (abs(diffFromStart.y * 100 / screenHeight) > MINIMUM_VERTICAL_SWIPE) {
                                currentTouchAction = if (startTouch.x < screenWidth / 2) {
                                    if (isShowing) {
                                        isShowing = false
                                        animateLayoutChanges()
                                    }
                                    TouchAction.Brightness
                                } else {
                                    TouchAction.Volume
                                }
                            }
                        }
                        if (swipeHorizontalEnabled) {
                            if (abs(diffFromStart.x * 100 / screenHeight) > MINIMUM_HORIZONTAL_SWIPE) {
                                currentTouchAction = TouchAction.Time
                            }
                        }
                    }

                    val lastTouch = currentTouchLast
                    if (lastTouch != null) {
                        val diffFromLast = lastTouch - currentTouch
                        val verticalAddition = diffFromLast.y * VERTICAL_MULTIPLIER / screenHeight.toFloat()

                        binding.playerTimeText?.isVisible = false
                        binding.playerProgressbarLeftHolder?.isVisible = false
                        binding.playerProgressbarRightHolder?.isVisible = false

                        when (currentTouchAction) {
                            TouchAction.Time -> {
                                val startTime = currentTouchStartPlayerTime?.div(1000L)?.times(1000L)
                                if (startTime != null) {
                                    calculateNewTime(startTime, startTouch, currentTouch)?.let { newMs ->
                                        val skipMs = newMs - startTime
                                        binding.playerTimeText?.text = "${convertTimeToString(newMs / 1000)} [${if (abs(skipMs) < 1000) "" else (if (skipMs > 0) "+" else "-")}${convertTimeToString(abs(skipMs / 1000))}]"
                                        binding.playerTimeText?.isVisible = true
                                    }
                                }
                            }
                            TouchAction.Brightness -> {
                                binding.playerProgressbarRightHolder?.isVisible = true
                                val lastRequested = currentRequestedBrightness
                                currentRequestedBrightness = min(1.0f, max(currentRequestedBrightness + verticalAddition, 0.0f))
                                if (lastRequested != currentRequestedBrightness) setBrightness(currentRequestedBrightness)
                                binding.playerProgressbarRight?.max = 100_000
                                binding.playerProgressbarRight?.progress = max(2_000, (currentRequestedBrightness * 100_000f).toInt())
                                binding.playerProgressbarRightIcon?.setImageResource(brightnessIcons[min(brightnessIcons.size - 1, max(0, round(currentRequestedBrightness * (brightnessIcons.size - 1)).toInt()))])
                            }
                            TouchAction.Volume -> {
                                (activity?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.let { audioManager ->
                                    binding.playerProgressbarLeftHolder?.isVisible = true
                                    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                    val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                    currentRequestedVolume = min(1.0f, max(currentRequestedVolume + verticalAddition, 0.0f))
                                    binding.playerProgressbarLeft?.max = 100_000
                                    binding.playerProgressbarLeft?.progress = max(2_000, (currentRequestedVolume * 100_000f).toInt())
                                    binding.playerProgressbarLeftIcon?.setImageResource(volumeIcons[min(volumeIcons.size - 1, max(0, round(currentRequestedVolume * (volumeIcons.size - 1)).toInt()))])
                                    val desiredVolume = round(currentRequestedVolume * maxVolume).toInt()
                                    if (desiredVolume != currentVolume) {
                                        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, if (desiredVolume < currentVolume) AudioManager.ADJUST_LOWER else AudioManager.ADJUST_RAISE, 0)
                                    }
                                }
                            }
                            else -> Unit
                        }
                    }
                }
            }
        }
        currentTouchLast = currentTouch
        return true
    }

    private fun handleKeyEvent(event: KeyEvent, hasNavigated: Boolean): Boolean {
        val binding = customLayoutBinding ?: return false
        if (hasNavigated) {
            autoHide()
        } else {
            event.keyCode.let { keyCode ->
                when (event.action) {
                    KeyEvent.ACTION_DOWN -> {
                        when (keyCode) {
                            KeyEvent.KEYCODE_DPAD_CENTER -> {
                                if (!isShowing) {
                                    if (!isLocked) player.handleEvent(CSPlayerEvent.PlayPauseToggle)
                                    onClickChange()
                                    return true
                                }
                            }
                            KeyEvent.KEYCODE_DPAD_UP -> {
                                if (!isShowing) {
                                    onClickChange()
                                    return true
                                }
                            }
                            KeyEvent.KEYCODE_DPAD_LEFT -> {
                                if (!isShowing && !isLocked) {
                                    player.seekTime(-10000L)
                                    return true
                                } else if (binding.playerPausePlay?.isFocused == true) {
                                    player.seekTime(-30000L)
                                    return true
                                }
                            }
                            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                if (!isShowing && !isLocked) {
                                    player.seekTime(10000L)
                                    return true
                                } else if (binding.playerPausePlay?.isFocused == true) {
                                    player.seekTime(30000L)
                                    return true
                                }
                            }
                        }
                    }
                }

                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_UP,
                    KeyEvent.KEYCODE_DPAD_DOWN_LEFT, KeyEvent.KEYCODE_DPAD_DOWN_RIGHT,
                    KeyEvent.KEYCODE_DPAD_UP_LEFT, KeyEvent.KEYCODE_DPAD_UP_RIGHT -> {
                        if (!isShowing) return true else autoHide()
                    }
                    KeyEvent.KEYCODE_BACK -> {
                        if (isShowing && isTv) {
                            onClickChange()
                            return true
                        }
                    }
                }
            }
        }
        return false
    }

    protected fun uiReset() {
        val binding = customLayoutBinding ?: return
        isLocked = false
        isShowing = false

        binding.playerSkipEpisode?.isVisible = false
        binding.playerSkipOp?.isVisible = false
        binding.shadowOverlay?.isVisible = false

        updateLockUI()
        updateUIVisibility()
        animateLayoutChanges()
        resetFastForwardText()
        resetRewindText()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Inisialisasi binding untuk custom layout
        customLayoutBinding = PlayerCustomLayoutBinding.bind(view.findViewById(R.id.player_custom_layout) ?: return)

        setPlayBackSpeed(getKey(PLAYBACK_SPEED_KEY) ?: 1.0f)

        playerEventListener = { eventType ->
            when (eventType) {
                PlayerEventType.Lock -> toggleLock()
                PlayerEventType.NextEpisode -> player.handleEvent(CSPlayerEvent.NextEpisode)
                PlayerEventType.Pause -> player.handleEvent(CSPlayerEvent.Pause)
                PlayerEventType.PlayPauseToggle -> player.handleEvent(CSPlayerEvent.PlayPauseToggle)
                PlayerEventType.Play -> player.handleEvent(CSPlayerEvent.Play)
                PlayerEventType.Resize -> nextResize()
                PlayerEventType.PrevEpisode -> player.handleEvent(CSPlayerEvent.PrevEpisode)
                PlayerEventType.SeekForward -> player.handleEvent(CSPlayerEvent.SeekForward)
                PlayerEventType.ShowSpeed -> showSpeedDialog()
                PlayerEventType.SeekBack -> player.handleEvent(CSPlayerEvent.SeekBack)
                PlayerEventType.ToggleMute -> player.handleEvent(CSPlayerEvent.ToggleMute)
                PlayerEventType.ToggleHide -> onClickChange()
                PlayerEventType.ShowMirrors -> showMirrorsDialogue()
                PlayerEventType.SearchSubtitlesOnline -> {
                    if (subsProvidersIsActive) {
                        openOnlineSubPicker(view.context, null) {}
                    }
                }
            }
        }

        keyEventListener = { eventNav ->
            if (player.isActive()) {
                val (event, hasNavigated) = eventNav
                event?.let { handleKeyEvent(it, hasNavigated) } ?: false
            } else false
        }

        try {
            context?.let { ctx ->
                val settingsManager = PreferenceManager.getDefaultSharedPreferences(ctx)
                fastForwardTime = settingsManager.getInt(ctx.getString(R.string.double_tap_seek_time_key), 10).toLong() * 1000L
                navigationBarHeight = ctx.getNavigationBarHeight()
                statusBarHeight = ctx.getStatusBarHeight()
                swipeHorizontalEnabled = settingsManager.getBoolean(ctx.getString(R.string.swipe_enabled_key), true)
                swipeVerticalEnabled = settingsManager.getBoolean(ctx.getString(R.string.swipe_vertical_enabled_key), true)
                playBackSpeedEnabled = settingsManager.getBoolean(ctx.getString(R.string.playback_speed_enabled_key), false)
                playerResizeEnabled = settingsManager.getBoolean(ctx.getString(R.string.player_resize_enabled_key), true)
                doubleTapEnabled = settingsManager.getBoolean(ctx.getString(R.string.double_tap_enabled_key), false)
                doubleTapPauseEnabled = settingsManager.getBoolean(ctx.getString(R.string.double_tap_pause_enabled_key), false)
                currentPrefQuality = settingsManager.getInt(ctx.getString(R.string.quality_pref_key), currentPrefQuality)
            }

            customLayoutBinding?.playerSpeedBtt?.isVisible = playBackSpeedEnabled
            customLayoutBinding?.playerResizeBtt?.isVisible = playerResizeEnabled
        } catch (e: Exception) {
            logError(e)
        }

        customLayoutBinding?.playerPausePlay?.setOnClickListener {
            autoHide()
            player.handleEvent(CSPlayerEvent.PlayPauseToggle)
        }

        customLayoutBinding?.playerResizeBtt?.setOnClickListener {
            autoHide()
            nextResize()
        }

        customLayoutBinding?.playerSpeedBtt?.setOnClickListener {
            autoHide()
            showSpeedDialog()
        }

        customLayoutBinding?.playerSkipOp?.setOnClickListener {
            autoHide()
            skipOp()
        }

        customLayoutBinding?.playerSkipEpisode?.setOnClickListener {
            autoHide()
            player.handleEvent(CSPlayerEvent.NextEpisode)
        }

        customLayoutBinding?.playerLock?.setOnClickListener {
            autoHide()
            toggleLock()
        }

        customLayoutBinding?.playerSubtitleOffsetBtt?.setOnClickListener {
            showSubtitleOffsetDialog()
        }

        customLayoutBinding?.exoRew?.setOnClickListener {
            autoHide()
            rewind()
        }

        customLayoutBinding?.exoFfwd?.setOnClickListener {
            autoHide()
            fastForward()
        }

        fragmentBinding?.playerGoBack?.setOnClickListener {
            activity?.popCurrentPage()
        }

        fragmentBinding?.playerSourcesBtt?.setOnClickListener {
            showMirrorsDialogue()
        }

        customLayoutBinding?.playerIntroPlay?.setOnClickListener {
            customLayoutBinding?.playerIntroPlay?.isGone = true
            player.handleEvent(CSPlayerEvent.Play)
            updateUIVisibility()
        }

        customLayoutBinding?.playerHolder?.setOnTouchListener { callView, event ->
            handleMotionEvent(callView, event)
        }

        customLayoutBinding?.exoProgress?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> currentTapIndex++
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_BUTTON_RELEASE -> autoHide()
            }
            false
        }

        try {
            uiReset()
        } catch (e: Exception) {
            logError(e)
        }
    }
}