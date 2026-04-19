package com.lagradost.cloudstream3.ui.settings

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.MainSettingsBinding
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.accountManagers
import com.lagradost.cloudstream3.ui.home.HomeFragment
import com.lagradost.cloudstream3.utils.UIHelper.fixPaddingStatusbar
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import com.lagradost.cloudstream3.utils.UIHelper.setImage
import java.io.File

class SettingsFragment : Fragment() {
    private var _binding: MainSettingsBinding? = null
    private val binding get() = _binding!!

    companion object {
        var beneneCount = 0

        fun PreferenceFragmentCompat?.getPref(id: Int): Preference? {
            if (this == null) return null
            return try {
                findPreference(getString(id))
            } catch (e: Exception) {
                logError(e)
                null
            }
        }

        // Catatan: Fungsi setUpToolbar ini masih menggunakan synthetic, akan diperbaiki di file masing-masing fragment.
        // Untuk sementara, fungsi ini tidak digunakan di SettingsFragment.
        // Nanti akan diganti dengan versi yang menerima Toolbar dari binding.
        @Deprecated("Use setupToolbar(toolbar) instead")
        fun PreferenceFragmentCompat?.setUpToolbar(@StringRes title: Int) {
            // Implementasi lama yang mengakses settings_toolbar secara synthetic.
            // Karena synthetic dihapus, fungsi ini tidak akan berfungsi.
            // Setiap fragment yang memanggil ini harus diubah untuk menggunakan binding.
        }

        fun getFolderSize(dir: File): Long {
            var size: Long = 0
            dir.listFiles()?.let {
                for (file in it) {
                    size += if (file.isFile) {
                        file.length()
                    } else getFolderSize(file)
                }
            }
            return size
        }

        private fun Context.getLayoutInt(): Int {
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
            return settingsManager.getInt(this.getString(R.string.app_layout_key), -1)
        }

        fun Context.isTvSettings(): Boolean {
            var value = getLayoutInt()
            if (value == -1) {
                value = if (isAutoTv()) 1 else 0
            }
            return value == 1 || value == 2
        }

        fun Context.isTrueTvSettings(): Boolean {
            var value = getLayoutInt()
            if (value == -1) {
                value = if (isAutoTv()) 1 else 0
            }
            return value == 1
        }

        fun Context.isEmulatorSettings(): Boolean {
            return getLayoutInt() == 2
        }

        private fun Context.isAutoTv(): Boolean {
            val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager?
            val model = Build.MODEL.lowercase()
            return uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
                    Build.MODEL.contains("AFT") ||
                    model.contains("firestick") ||
                    model.contains("fire tv") ||
                    model.contains("chromecast")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        _binding = MainSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fun navigate(id: Int) {
            activity?.navigate(id, Bundle())
        }

        val isTrueTv = context?.isTrueTvSettings() == true

        // Set profile picture if any account is logged in
        for (syncApi in accountManagers) {
            val login = syncApi.loginInfo()
            val pic = login?.profilePicture ?: continue
            if (binding.settingsProfilePic?.setImage(
                    pic,
                    errorImageDrawable = HomeFragment.errorProfilePic
                ) == true
            ) {
                binding.settingsProfileText?.text = login.name
                binding.settingsProfile?.isVisible = true
                break
            }
        }

        // Setup click listeners for each settings category
        listOf(
            Pair(binding.settingsGeneral, R.id.action_navigation_settings_to_navigation_settings_general),
            Pair(binding.settingsPlayer, R.id.action_navigation_settings_to_navigation_settings_player),
            Pair(binding.settingsCredits, R.id.action_navigation_settings_to_navigation_settings_account),
            Pair(binding.settingsUi, R.id.action_navigation_settings_to_navigation_settings_ui),
            Pair(binding.settingsLang, R.id.action_navigation_settings_to_navigation_settings_lang),
            Pair(binding.settingsUpdates, R.id.action_navigation_settings_to_navigation_settings_updates),
            Pair(binding.settingsNsfwId, R.id.action_navigation_settings_to_navigation_settings_nsfw),
        ).forEach { (view, navigationId) ->
            view?.apply {
                setOnClickListener {
                    navigate(navigationId)
                }
                if (isTrueTv) {
                    isFocusable = true
                    isFocusableInTouchMode = true
                }
            }
        }

        // Set version text
        binding.settings?.let { textView ->
            var currentVersion = 0L
            context?.let { ctx ->
                ctx.packageName?.let { pkg ->
                    ctx.packageManager?.getPackageInfo(pkg, 0)?.let { pinfo ->
                        currentVersion = PackageInfoCompat.getLongVersionCode(pinfo)
                    }
                }
            }
            textView.text = "${getString(R.string.app_version)} r$currentVersion"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}