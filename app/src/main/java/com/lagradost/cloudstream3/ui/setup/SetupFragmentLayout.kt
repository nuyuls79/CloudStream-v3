package com.lagradost.cloudstream3.ui.setup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.utils.UIHelper.fixPaddingStatusbar
import org.acra.ACRA


class SetupFragmentLayout : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_setup_layout, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize views via findViewById
        val setupRoot = view.findViewById<View>(R.id.setup_root)
        val listview1 = view.findViewById<ListView>(R.id.listview1)
        val nextBtt = view.findViewById<Button>(R.id.next_btt)
        val prevBtt = view.findViewById<Button>(R.id.prev_btt)
        val acraSwitch = view.findViewById<SwitchCompat>(R.id.acra_switch)
        val crashReportingText = view.findViewById<android.widget.TextView>(R.id.crash_reporting_text)

        context?.fixPaddingStatusbar(setupRoot)

        with(context) {
            if (this == null) return
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)

            val prefNames = resources.getStringArray(R.array.app_layout)
            val prefValues = resources.getIntArray(R.array.app_layout_values)

            val currentLayout = settingsManager.getInt(getString(R.string.app_layout_key), -1)

            val arrayAdapter = ArrayAdapter<String>(this, R.layout.sort_bottom_single_choice)

            arrayAdapter.addAll(prefNames.toList())
            listview1?.adapter = arrayAdapter
            listview1?.choiceMode = AbsListView.CHOICE_MODE_SINGLE
            listview1?.setItemChecked(prefValues.indexOf(currentLayout), true)

            listview1?.setOnItemClickListener { _, _, position, _ ->
                settingsManager.edit()
                    .putInt(getString(R.string.app_layout_key), prefValues[position])
                    .apply()
                activity?.recreate()
            }

            acraSwitch?.setOnCheckedChangeListener { _, enableCrashReporting ->
                // Use same pref as in settings
                settingsManager.edit().putBoolean(ACRA.PREF_DISABLE_ACRA, !enableCrashReporting)
                    .apply()
                val text = if (enableCrashReporting) R.string.bug_report_settings_off else R.string.bug_report_settings_on
                crashReportingText?.text = getText(text)
            }

            val enableCrashReporting = !settingsManager.getBoolean(ACRA.PREF_DISABLE_ACRA, false)
            acraSwitch.isChecked = enableCrashReporting
            crashReportingText.text = getText(
                if (enableCrashReporting) R.string.bug_report_settings_off else R.string.bug_report_settings_on
            )

            nextBtt?.setOnClickListener {
                findNavController().navigate(R.id.navigation_home)
            }

            prevBtt?.setOnClickListener {
                findNavController().popBackStack()
            }
        }
    }
}