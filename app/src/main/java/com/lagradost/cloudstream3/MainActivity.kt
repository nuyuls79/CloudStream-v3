package com.lagradost.cloudstream3

import android.content.ComponentName
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import androidx.annotation.IdRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import androidx.preference.PreferenceManager
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.android.gms.cast.framework.*
import com.google.android.material.navigationrail.NavigationRailView
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import com.lagradost.cloudstream3.APIHolder.allProviders
import com.lagradost.cloudstream3.APIHolder.apis
import com.lagradost.cloudstream3.APIHolder.getApiDubstatusSettings
import com.lagradost.cloudstream3.APIHolder.initAll
import com.lagradost.cloudstream3.APIHolder.updateHasTrailers
import com.lagradost.cloudstream3.CommonActivity.loadThemes
import com.lagradost.cloudstream3.CommonActivity.onColorSelectedEvent
import com.lagradost.cloudstream3.CommonActivity.onDialogDismissedEvent
import com.lagradost.cloudstream3.CommonActivity.onUserLeaveHint
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.CommonActivity.updateLocale
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.normalSafeApiCall
import com.lagradost.cloudstream3.network.initClient
import com.lagradost.cloudstream3.providersnsfw.*
import com.lagradost.cloudstream3.receivers.VideoDownloadRestartReceiver
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.OAuth2Apis
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.accountManagers
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.appString
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.inAppAuths
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.ui.download.DOWNLOAD_NAVIGATE_TO
import com.lagradost.cloudstream3.ui.result.ResultFragment
import com.lagradost.cloudstream3.ui.search.SearchResultBuilder
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isEmulatorSettings
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isTvSettings
import com.lagradost.cloudstream3.ui.settings.SettingsGeneral
import com.lagradost.cloudstream3.ui.setup.HAS_DONE_SETUP_KEY
import com.lagradost.cloudstream3.utils.AppUtils.isCastApiAvailable
import com.lagradost.cloudstream3.utils.AppUtils.loadCache
import com.lagradost.cloudstream3.utils.AppUtils.loadResult
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.BackupUtils.setUpBackup
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.DataStore
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.removeKey
import com.lagradost.cloudstream3.utils.DataStore.setKey
import com.lagradost.cloudstream3.utils.DataStoreHelper.migrateResumeWatching
import com.lagradost.cloudstream3.utils.DataStoreHelper.setViewPos
import com.lagradost.cloudstream3.utils.IOnBackPressed
import com.lagradost.cloudstream3.utils.InAppUpdater.Companion.runAutoUpdate
import com.lagradost.cloudstream3.utils.UIHelper.changeStatusBarState
import com.lagradost.cloudstream3.utils.UIHelper.checkWrite
import com.lagradost.cloudstream3.utils.UIHelper.colorFromAttribute
import com.lagradost.cloudstream3.utils.UIHelper.getResourceColor
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import com.lagradost.cloudstream3.utils.UIHelper.requestRW
import com.lagradost.cloudstream3.utils.USER_PROVIDER_API
import com.lagradost.nicehttp.Requests
import com.lagradost.nicehttp.ResponseParser
import com.lagradost.cloudstream3.databinding.ActivityMainBinding
import com.lagradost.cloudstream3.databinding.ActivityMainTvBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import java.io.File
import kotlin.concurrent.thread
import kotlin.reflect.KClass

// ... (konstanta tetap sama) ...

class MainActivity : AppCompatActivity(), ColorPickerDialogListener {
    companion object {
        const val TAG = "MAINACT"
    }

    // Binding untuk layout biasa dan TV (hanya satu yang aktif)
    private var binding: ActivityMainBinding? = null
    private var bindingTv: ActivityMainTvBinding? = null

    override fun onColorSelected(dialogId: Int, color: Int) {
        onColorSelectedEvent.invoke(Pair(dialogId, color))
    }

    override fun onDialogDismissed(dialogId: Int) {
        onDialogDismissedEvent.invoke(dialogId)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateLocale()

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navHostFragment.navController.currentDestination?.let { updateNavBar(it) }
    }

    private fun updateNavBar(destination: NavDestination) {
        this.hideKeyboard()

        // Gunakan binding yang sesuai (non-TV)
        binding?.let { b ->
            b.castMiniControllerHolder?.isVisible =
                !listOf(R.id.navigation_results, R.id.navigation_player).contains(destination.id)

            val isNavVisible = listOf(
                R.id.navigation_home,
                R.id.navigation_search,
                R.id.navigation_downloads,
                R.id.navigation_settings,
                R.id.navigation_download_child,
                R.id.navigation_subtitles,
                R.id.navigation_chrome_subtitles,
                R.id.navigation_settings_player,
                R.id.navigation_settings_updates,
                R.id.navigation_settings_ui,
                R.id.navigation_settings_account,
                R.id.navigation_settings_lang,
                R.id.navigation_settings_general,
            ).contains(destination.id)

            val landscape = when (resources.configuration.orientation) {
                Configuration.ORIENTATION_LANDSCAPE -> true
                Configuration.ORIENTATION_PORTRAIT -> false
                else -> false
            }

            b.navView?.isVisible = isNavVisible && !landscape
            b.navRailView?.isVisible = isNavVisible && landscape
        }
        // Untuk layout TV, biasanya tidak ada navigasi bawah, jadi tidak perlu diatur
    }

    // ... (SessionManagerListenerImpl, onResume, onPause, dispatchKeyEvent, onKeyDown, onUserLeaveHint tetap sama) ...

    private fun backPressed() {
        this.window?.navigationBarColor =
            this.colorFromAttribute(R.attr.primaryGrayBackground)
        this.updateLocale()
        super.onBackPressed()
        this.updateLocale()
    }

    override fun onBackPressed() {
        ((supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment?)?.childFragmentManager?.primaryNavigationFragment as? IOnBackPressed)?.onBackPressed()
            ?.let { runNormal ->
                if (runNormal) backPressed()
            } ?: run {
            backPressed()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        // ... (sama seperti kode asli) ...
        if (VLC_REQUEST_CODE == requestCode) {
            if (resultCode == RESULT_OK && data != null) {
                val pos: Long = data.getLongExtra(VLC_EXTRA_POSITION_OUT, -1)
                val dur: Long = data.getLongExtra(VLC_EXTRA_DURATION_OUT, -1)
                val id = getKey<Int>(VLC_LAST_ID_KEY)
                println("SET KEY $id at $pos / $dur")
                if (dur > 0 && pos > 0) {
                    setViewPos(id, pos, dur)
                }
                removeKey(VLC_LAST_ID_KEY)
                ResultFragment.updateUI()
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onDestroy() {
        val broadcastIntent = Intent().apply {
            action = "restart_service"
            setClass(this@MainActivity, VideoDownloadRestartReceiver::class.java)
        }
        this.sendBroadcast(broadcastIntent)
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent?) {
        handleAppIntent(intent)
        super.onNewIntent(intent)
    }

    // ... (handleAppIntent, matchDestination, onNavDestinationSelected, test tetap sama) ...

    override fun onCreate(savedInstanceState: Bundle?) {
        // ... (semua kode inisialisasi sebelum setContentView tetap sama) ...
        // (Potongan panjang di atas tidak diubah, hanya bagian setContentView dan binding yang diubah)

        // ===== BAGIAN YANG DIUBAH =====
        loadThemes(this)
        updateLocale()
        app.initClient(this)
        super.onCreate(savedInstanceState)
        try {
            if (isCastApiAvailable()) {
                mSessionManager = CastContext.getSharedInstance(this).sessionManager
            }
        } catch (e: Exception) {
            logError(e)
        }

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)

        // Inflate binding berdasarkan tipe device (TV atau non-TV)
        if (isTvSettings()) {
            bindingTv = ActivityMainTvBinding.inflate(layoutInflater)
            setContentView(bindingTv!!.root)
        } else {
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding!!.root)
        }

        changeStatusBarState(isEmulatorSettings())

        setUpBackup()
        CommonActivity.init(this)

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        // Setup navigasi dengan binding yang sesuai
        binding?.let { b ->
            b.navView?.setupWithNavController(navController)
            b.navRailView?.setupWithNavController(navController)

            b.navRailView?.setOnItemSelectedListener { item ->
                onNavDestinationSelected(item, navController)
            }
            b.navView?.setOnItemSelectedListener { item ->
                onNavDestinationSelected(item, navController)
            }

            val rippleColor = ColorStateList.valueOf(getResourceColor(R.attr.colorPrimary, 0.1f))
            b.navView?.itemRippleColor = rippleColor
            b.navRailView?.itemRippleColor = rippleColor
            b.navRailView?.itemActiveIndicatorColor = rippleColor
            b.navView?.itemActiveIndicatorColor = rippleColor

            CastButtonFactory.setUpMediaRouteButton(this, b.mediaRouteButton)
        }
        // Untuk layout TV, mungkin tidak ada komponen navigasi, jadi tidak perlu setup

        navController.addOnDestinationChangedListener { _, destination, _ ->
            updateNavBar(destination)
        }

        // ... (sisa kode setelah setContentView tetap sama, termasuk handleAppIntent dll) ...
        loadCache()
        test()
        updateHasTrailers()
        // ... (semua kode hingga akhir onCreate) ...

        // Pastikan untuk mengakses view hanya melalui binding yang tidak null
        // Contoh: sebelumnya ada `nav_view?.itemRippleColor` sudah diganti dengan binding.
        // Jika ada akses lain seperti `findViewById(R.id.xxx)` yang tidak diganti, periksa.
    }
}