package com.lagradost.cloudstream3.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.pm.PackageInfoCompat.getLongVersionCode
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceFragmentCompat
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.LogcatBinding
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.getPref
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setUpToolbar
import com.lagradost.cloudstream3.utils.BackupUtils.backup
import com.lagradost.cloudstream3.utils.BackupUtils.restorePrompt
import com.lagradost.cloudstream3.utils.InAppUpdater.Companion.runAutoUpdate
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard
import com.lagradost.cloudstream3.utils.VideoDownloadManager
import okhttp3.internal.closeQuietly
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import kotlin.concurrent.thread

class SettingsUpdates : PreferenceFragmentCompat() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setUpToolbar(R.string.category_updates)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        hideKeyboard()
        setPreferencesFromResource(R.xml.settings_updates, rootKey)

        getPref(R.string.backup_key)?.setOnPreferenceClickListener {
            activity?.backup()
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.redo_setup_key)?.setOnPreferenceClickListener {
            findNavController().navigate(R.id.navigation_setup_language)
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.restore_key)?.setOnPreferenceClickListener {
            activity?.restorePrompt()
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.show_logcat_key)?.setOnPreferenceClickListener { pref ->
            val builder = AlertDialog.Builder(pref.context, R.style.AlertDialogCustom)
                .setView(R.layout.logcat)
            val dialog = builder.create()
            dialog.show()

            // Inflate binding untuk dialog logcat
            val binding = LogcatBinding.bind(dialog.findViewById(android.R.id.custom) ?: return@setOnPreferenceClickListener true)

            val log = StringBuilder()
            try {
                val process = Runtime.getRuntime().exec("logcat -d")
                val bufferedReader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?
                while (bufferedReader.readLine().also { line = it } != null) {
                    log.append(line)
                }
                bufferedReader.close()
                process.waitFor()
            } catch (e: Exception) {
                logError(e)
            }

            val text = log.toString()
            binding.text1?.text = text

            binding.copyBtt?.setOnClickListener {
                val serviceClipboard = (activity?.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?)
                    ?: return@setOnClickListener
                val clip = ClipData.newPlainText("logcat", text)
                serviceClipboard.setPrimaryClip(clip)
                dialog.dismissSafe(activity)
            }

            binding.clearBtt?.setOnClickListener {
                Runtime.getRuntime().exec("logcat -c")
                dialog.dismissSafe(activity)
            }

            binding.saveBtt?.setOnClickListener {
                var fileStream: OutputStream? = null
                try {
                    fileStream = VideoDownloadManager.setupStream(
                        it.context,
                        "logcat",
                        null,
                        "txt",
                        false
                    ).fileStream
                    fileStream?.writer()?.write(text)
                    activity?.runOnUiThread {
                        CommonActivity.showToast(activity, R.string.saved, Toast.LENGTH_SHORT)
                    }
                } catch (e: Exception) {
                    logError(e)
                } finally {
                    fileStream?.closeQuietly()
                    dialog.dismissSafe(activity)
                }
            }

            binding.closeBtt?.setOnClickListener {
                dialog.dismissSafe(activity)
            }

            return@setOnPreferenceClickListener true
        }

        // Append versionCode to app_version on Manual update pref
        getPref(R.string.manual_check_update_key)?.let {
            var currentVersion = 0L
            context?.let { ctx ->
                ctx.packageName?.let { pkg ->
                    ctx.packageManager?.getPackageInfo(pkg, 0)?.let { pinfo ->
                        currentVersion = getLongVersionCode(pinfo)
                    }
                }
            }
            it.summary = "${getString(R.string.app_version)} r$currentVersion"
        }

        getPref(R.string.manual_check_update_key)?.setOnPreferenceClickListener {
            thread {
                if (!requireActivity().runAutoUpdate(false)) {
                    activity?.runOnUiThread {
                        CommonActivity.showToast(
                            activity,
                            R.string.no_update_found,
                            Toast.LENGTH_SHORT
                        )
                    }
                }
            }
            return@setOnPreferenceClickListener true
        }
    }
}