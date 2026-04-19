package com.lagradost.cloudstream3.ui.setup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.utils.DataStore.removeKey
import com.lagradost.cloudstream3.utils.HOMEPAGE_API
import com.lagradost.cloudstream3.utils.UIHelper.fixPaddingStatusbar


class SetupFragmentMedia : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_setup_media, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize views via findViewById
        val setupRoot = view.findViewById<View>(R.id.setup_root)
        val listview1 = view.findViewById<ListView>(R.id.listview1)
        val nextBtt = view.findViewById<Button>(R.id.next_btt)
        val prevBtt = view.findViewById<Button>(R.id.prev_btt)

        context?.fixPaddingStatusbar(setupRoot)

        with(context) {
            if (this == null) return
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)

            val arrayAdapter = ArrayAdapter<String>(this, R.layout.sort_bottom_single_choice)

            val currentPrefMedia = settingsManager.getInt(getString(R.string.prefer_media_type_key), 0)

            val prefNames = resources.getStringArray(R.array.media_type_pref)
            val prefValues = resources.getIntArray(R.array.media_type_pref_values)

            arrayAdapter.addAll(prefNames.toList())
            listview1?.adapter = arrayAdapter
            listview1?.choiceMode = AbsListView.CHOICE_MODE_SINGLE
            listview1?.setItemChecked(currentPrefMedia, true)

            listview1?.setOnItemClickListener { _, _, position, _ ->
                settingsManager.edit()
                    .putInt(getString(R.string.prefer_media_type_key), prefValues[position])
                    .apply()

                // Regenerate set homepage
                removeKey(HOMEPAGE_API)
            }

            nextBtt?.setOnClickListener {
                findNavController().navigate(R.id.navigation_setup_media_to_navigation_setup_layout)
            }

            prevBtt?.setOnClickListener {
                findNavController().popBackStack()
            }
        }
    }
}