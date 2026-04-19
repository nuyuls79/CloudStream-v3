package com.lagradost.cloudstream3.ui.setup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import androidx.core.util.forEach
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.APIHolder.getApiProviderLangSettings
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.utils.SubtitleHelper
import com.lagradost.cloudstream3.utils.UIHelper.fixPaddingStatusbar

class SetupFragmentProviderLanguage : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_setup_provider_languages, container, false)
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

            val current = this.getApiProviderLangSettings()
            val langs = APIHolder.apis.map { it.lang }.toSet()
                .sortedBy { SubtitleHelper.fromTwoLettersToLanguage(it) }

            val currentList = current.map { langs.indexOf(it) }
            val languageNames = langs.map {
                val emoji = SubtitleHelper.getFlagFromIso(it)
                val name = SubtitleHelper.fromTwoLettersToLanguage(it)
                "$emoji $name"
            }
            arrayAdapter.addAll(languageNames)

            listview1?.adapter = arrayAdapter
            listview1?.choiceMode = AbsListView.CHOICE_MODE_MULTIPLE
            currentList.forEach {
                listview1.setItemChecked(it, true)
            }

            listview1?.setOnItemClickListener { _, _, _, _ ->
                val currentLanguages = mutableListOf<String>()
                listview1?.checkedItemPositions?.forEach { key, value ->
                    if (value) currentLanguages.add(langs[key])
                }
                settingsManager.edit().putStringSet(
                    this.getString(R.string.provider_lang_key),
                    currentLanguages.toSet()
                ).apply()
            }

            nextBtt?.setOnClickListener {
                findNavController().navigate(R.id.navigation_setup_provider_languages_to_navigation_setup_media)
            }

            prevBtt?.setOnClickListener {
                findNavController().popBackStack()
            }
        }
    }
}