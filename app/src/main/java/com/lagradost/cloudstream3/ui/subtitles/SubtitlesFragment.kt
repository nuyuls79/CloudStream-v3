package com.lagradost.cloudstream3.ui.subtitles

import android.app.Activity
import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.FontRes
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.fasterxml.jackson.annotation.JsonProperty
import com.google.android.exoplayer2.text.Cue
import com.google.android.exoplayer2.ui.CaptionStyleCompat
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.CommonActivity.onColorSelectedEvent
import com.lagradost.cloudstream3.CommonActivity.onDialogDismissedEvent
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isTrueTvSettings
import com.lagradost.cloudstream3.utils.Event
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showDialog
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showMultiDialog
import com.lagradost.cloudstream3.utils.SubtitleHelper
import com.lagradost.cloudstream3.utils.UIHelper.fixPaddingStatusbar
import com.lagradost.cloudstream3.utils.UIHelper.hideSystemUI
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import com.lagradost.cloudstream3.utils.UIHelper.popCurrentPage
import java.io.File

const val SUBTITLE_KEY = "subtitle_settings"
const val SUBTITLE_AUTO_SELECT_KEY = "subs_auto_select"
const val SUBTITLE_DOWNLOAD_KEY = "subs_auto_download"

data class SaveCaptionStyle(
    @JsonProperty("foregroundColor") var foregroundColor: Int,
    @JsonProperty("backgroundColor") var backgroundColor: Int,
    @JsonProperty("windowColor") var windowColor: Int,
    @CaptionStyleCompat.EdgeType
    @JsonProperty("edgeType") var edgeType: Int,
    @JsonProperty("edgeColor") var edgeColor: Int,
    @FontRes
    @JsonProperty("typeface") var typeface: Int?,
    @JsonProperty("typefaceFilePath") var typefaceFilePath: String?,
    /**in dp**/
    @JsonProperty("elevation") var elevation: Int,
    /**in sp**/
    @JsonProperty("fixedTextSize") var fixedTextSize: Float?,
    @JsonProperty("removeCaptions") var removeCaptions: Boolean = false,
    @JsonProperty("removeBloat") var removeBloat: Boolean = true,
)

const val DEF_SUBS_ELEVATION = 20

class SubtitlesFragment : Fragment() {
    companion object {
        val applyStyleEvent = Event<SaveCaptionStyle>()

        fun Context.fromSaveToStyle(data: SaveCaptionStyle): CaptionStyleCompat {
            return CaptionStyleCompat(
                data.foregroundColor,
                data.backgroundColor,
                data.windowColor,
                data.edgeType,
                data.edgeColor,
                data.typefaceFilePath?.let {
                    try {
                        Typeface.createFromFile(File(it))
                    } catch (e: Exception) {
                        null
                    }
                } ?: data.typeface?.let {
                    ResourcesCompat.getFont(this, it)
                } ?: Typeface.SANS_SERIF
            )
        }

        fun push(activity: Activity?, hide: Boolean = true) {
            activity.navigate(R.id.global_to_navigation_subtitles, Bundle().apply {
                putBoolean("hide", hide)
            })
        }

        private fun getDefColor(id: Int): Int {
            return when (id) {
                0 -> Color.WHITE
                1 -> Color.BLACK
                2 -> Color.TRANSPARENT
                3 -> Color.TRANSPARENT
                else -> Color.TRANSPARENT
            }
        }

        fun Context.saveStyle(style: SaveCaptionStyle) {
            this.setKey(SUBTITLE_KEY, style)
        }

        fun getCurrentSavedStyle(): SaveCaptionStyle {
            return getKey(SUBTITLE_KEY) ?: SaveCaptionStyle(
                getDefColor(0),
                getDefColor(2),
                getDefColor(3),
                CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                getDefColor(1),
                null,
                null,
                DEF_SUBS_ELEVATION,
                null,
            )
        }

        private fun Context.getSavedFonts(): List<File> {
            val externalFiles = getExternalFilesDir(null) ?: return emptyList()
            val fontDir = File(externalFiles.absolutePath + "/Fonts").also {
                it.mkdir()
            }
            return fontDir.list()?.mapNotNull {
                if (it.endsWith(".ttf")) {
                    File(fontDir.absolutePath + "/" + it)
                } else null
            } ?: listOf()
        }

        private fun Context.getCurrentStyle(): CaptionStyleCompat {
            return fromSaveToStyle(getCurrentSavedStyle())
        }

        private fun getPixels(unit: Int, size: Float): Int {
            val metrics: DisplayMetrics = Resources.getSystem().displayMetrics
            return TypedValue.applyDimension(unit, size, metrics).toInt()
        }

        fun getDownloadSubsLanguageISO639_1(): List<String> {
            return getKey(SUBTITLE_DOWNLOAD_KEY) ?: listOf("en")
        }

        fun getAutoSelectLanguageISO639_1(): String {
            return getKey(SUBTITLE_AUTO_SELECT_KEY) ?: "en"
        }
    }

    private lateinit var state: SaveCaptionStyle
    private var hide: Boolean = true

    // View references
    private lateinit var subsRoot: View
    private lateinit var subsTextColor: View
    private lateinit var subsOutlineColor: View
    private lateinit var subsBackgroundColor: View
    private lateinit var subsWindowColor: View
    private lateinit var subsSubtitleElevation: TextView
    private lateinit var subsEdgeType: TextView
    private lateinit var subsFontSize: TextView
    private lateinit var subsFont: TextView
    private lateinit var subsAutoSelectLanguage: TextView
    private lateinit var subsDownloadLanguages: TextView
    private lateinit var cancelBtt: View
    private lateinit var applyBtt: View
    private lateinit var subtitleText: TextView
    private lateinit var subsImportText: TextView
    private lateinit var subtitlesRemoveBloat: androidx.appcompat.widget.SwitchCompat
    private lateinit var subtitlesRemoveCaptions: androidx.appcompat.widget.SwitchCompat
    private lateinit var subtitlesFilterSubLang: androidx.appcompat.widget.SwitchCompat

    private fun onColorSelected(stuff: Pair<Int, Int>) {
        context?.setColor(stuff.first, stuff.second)
        if (hide) activity?.hideSystemUI()
    }

    private fun onDialogDismissed(id: Int) {
        if (hide) activity?.hideSystemUI()
    }

    private fun Context.setColor(id: Int, color: Int?) {
        val realColor = color ?: getDefColor(id)
        when (id) {
            0 -> state.foregroundColor = realColor
            1 -> state.edgeColor = realColor
            2 -> state.backgroundColor = realColor
            3 -> state.windowColor = realColor
            else -> Unit
        }
        updateState()
    }

    private fun Context.updateState() {
        subtitleText.setStyle(fromSaveToStyle(state))
    }

    private fun getColor(id: Int): Int {
        val color = when (id) {
            0 -> state.foregroundColor
            1 -> state.edgeColor
            2 -> state.backgroundColor
            3 -> state.windowColor
            else -> Color.TRANSPARENT
        }
        return if (color == Color.TRANSPARENT) Color.BLACK else color
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.subtitle_settings, container, false)
    }

    override fun onDestroy() {
        super.onDestroy()
        onColorSelectedEvent -= ::onColorSelected
        onDialogDismissedEvent -= ::onDialogDismissed
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize views via findViewById
        subsRoot = view.findViewById(R.id.subs_root)
        subsTextColor = view.findViewById(R.id.subs_text_color)
        subsOutlineColor = view.findViewById(R.id.subs_outline_color)
        subsBackgroundColor = view.findViewById(R.id.subs_background_color)
        subsWindowColor = view.findViewById(R.id.subs_window_color)
        subsSubtitleElevation = view.findViewById(R.id.subs_subtitle_elevation)
        subsEdgeType = view.findViewById(R.id.subs_edge_type)
        subsFontSize = view.findViewById(R.id.subs_font_size)
        subsFont = view.findViewById(R.id.subs_font)
        subsAutoSelectLanguage = view.findViewById(R.id.subs_auto_select_language)
        subsDownloadLanguages = view.findViewById(R.id.subs_download_languages)
        cancelBtt = view.findViewById(R.id.cancel_btt)
        applyBtt = view.findViewById(R.id.apply_btt)
        subtitleText = view.findViewById(R.id.subtitle_text)
        subsImportText = view.findViewById(R.id.subs_import_text)
        subtitlesRemoveBloat = view.findViewById(R.id.subtitles_remove_bloat)
        subtitlesRemoveCaptions = view.findViewById(R.id.subtitles_remove_captions)
        subtitlesFilterSubLang = view.findViewById(R.id.subtitles_filter_sub_lang)

        hide = arguments?.getBoolean("hide") ?: true
        onColorSelectedEvent += ::onColorSelected
        onDialogDismissedEvent += ::onDialogDismissed

        subsImportText.text = getString(R.string.subs_import_text).format(
            context?.getExternalFilesDir(null)?.absolutePath.toString() + "/Fonts"
        )

        context?.fixPaddingStatusbar(subsRoot)

        state = getCurrentSavedStyle()
        context?.updateState()

        val isTvTrueSettings = context?.isTrueTvSettings() == true

        fun View.setFocusableInTv() {
            this.isFocusableInTouchMode = isTvTrueSettings
        }

        fun View.setup(id: Int) {
            setFocusableInTv()
            this.setOnClickListener {
                activity?.let {
                    ColorPickerDialog.newBuilder()
                        .setDialogId(id)
                        .setShowAlphaSlider(true)
                        .setColor(getColor(id))
                        .show(it)
                }
            }
            this.setOnLongClickListener {
                it.context.setColor(id, null)
                showToast(activity, R.string.subs_default_reset_toast, Toast.LENGTH_SHORT)
                return@setOnLongClickListener true
            }
        }

        subsTextColor.setup(0)
        subsOutlineColor.setup(1)
        subsBackgroundColor.setup(2)
        subsWindowColor.setup(3)

        val dismissCallback = {
            if (hide) activity?.hideSystemUI()
        }

        subsSubtitleElevation.setFocusableInTv()
        subsSubtitleElevation.setOnClickListener { textView ->
            val suffix = "dp"
            val elevationTypes = listOf(
                Pair(0, textView.context.getString(R.string.none)),
                Pair(10, "10$suffix"), Pair(20, "20$suffix"), Pair(30, "30$suffix"),
                Pair(40, "40$suffix"), Pair(50, "50$suffix"), Pair(60, "60$suffix"),
                Pair(70, "70$suffix"), Pair(80, "80$suffix"), Pair(90, "90$suffix"),
                Pair(100, "100$suffix")
            )
            activity?.showDialog(
                elevationTypes.map { it.second },
                elevationTypes.map { it.first }.indexOf(state.elevation),
                (textView as TextView).text.toString(),
                false,
                dismissCallback
            ) { index ->
                state.elevation = elevationTypes.map { it.first }[index]
                textView.context.updateState()
                if (hide) activity?.hideSystemUI()
            }
        }

        subsSubtitleElevation.setOnLongClickListener {
            state.elevation = DEF_SUBS_ELEVATION
            it.context.updateState()
            showToast(activity, R.string.subs_default_reset_toast, Toast.LENGTH_SHORT)
            return@setOnLongClickListener true
        }

        subsEdgeType.setFocusableInTv()
        subsEdgeType.setOnClickListener { textView ->
            val edgeTypes = listOf(
                Pair(CaptionStyleCompat.EDGE_TYPE_NONE, textView.context.getString(R.string.subtitles_none)),
                Pair(CaptionStyleCompat.EDGE_TYPE_OUTLINE, textView.context.getString(R.string.subtitles_outline)),
                Pair(CaptionStyleCompat.EDGE_TYPE_DEPRESSED, textView.context.getString(R.string.subtitles_depressed)),
                Pair(CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW, textView.context.getString(R.string.subtitles_shadow)),
                Pair(CaptionStyleCompat.EDGE_TYPE_RAISED, textView.context.getString(R.string.subtitles_raised))
            )
            activity?.showDialog(
                edgeTypes.map { it.second },
                edgeTypes.map { it.first }.indexOf(state.edgeType),
                (textView as TextView).text.toString(),
                false,
                dismissCallback
            ) { index ->
                state.edgeType = edgeTypes.map { it.first }[index]
                textView.context.updateState()
            }
        }

        subsEdgeType.setOnLongClickListener {
            state.edgeType = CaptionStyleCompat.EDGE_TYPE_OUTLINE
            it.context.updateState()
            showToast(activity, R.string.subs_default_reset_toast, Toast.LENGTH_SHORT)
            return@setOnLongClickListener true
        }

        subsFontSize.setFocusableInTv()
        subsFontSize.setOnClickListener { textView ->
            val suffix = "sp"
            val fontSizes = listOf(
                Pair(null, textView.context.getString(R.string.normal)),
                Pair(6f, "6$suffix"), Pair(7f, "7$suffix"), Pair(8f, "8$suffix"),
                Pair(9f, "9$suffix"), Pair(10f, "10$suffix"), Pair(11f, "11$suffix"),
                Pair(12f, "12$suffix"), Pair(13f, "13$suffix"), Pair(14f, "14$suffix"),
                Pair(15f, "15$suffix"), Pair(16f, "16$suffix"), Pair(17f, "17$suffix"),
                Pair(18f, "18$suffix"), Pair(19f, "19$suffix"), Pair(20f, "20$suffix"),
                Pair(21f, "21$suffix"), Pair(22f, "22$suffix"), Pair(23f, "23$suffix"),
                Pair(24f, "24$suffix"), Pair(25f, "25$suffix"), Pair(26f, "26$suffix"),
                Pair(28f, "28$suffix"), Pair(30f, "30$suffix"), Pair(32f, "32$suffix"),
                Pair(34f, "34$suffix"), Pair(36f, "36$suffix"), Pair(38f, "38$suffix"),
                Pair(40f, "40$suffix"), Pair(42f, "42$suffix"), Pair(44f, "44$suffix"),
                Pair(48f, "48$suffix"), Pair(60f, "60$suffix")
            )
            activity?.showDialog(
                fontSizes.map { it.second },
                fontSizes.map { it.first }.indexOf(state.fixedTextSize),
                (textView as TextView).text.toString(),
                false,
                dismissCallback
            ) { index ->
                state.fixedTextSize = fontSizes.map { it.first }[index]
            }
        }

        subtitlesRemoveBloat.isChecked = state.removeBloat
        subtitlesRemoveBloat.setOnCheckedChangeListener { _, b -> state.removeBloat = b }

        subtitlesRemoveCaptions.isChecked = state.removeCaptions
        subtitlesRemoveCaptions.setOnCheckedChangeListener { _, b -> state.removeCaptions = b }

        context?.let { ctx ->
            subtitlesFilterSubLang.isChecked = PreferenceManager.getDefaultSharedPreferences(ctx)
                .getBoolean(getString(R.string.filter_sub_lang_key), false)
        }
        subtitlesFilterSubLang.setOnCheckedChangeListener { _, b ->
            context?.let { ctx ->
                PreferenceManager.getDefaultSharedPreferences(ctx)
                    .edit()
                    .putBoolean(getString(R.string.filter_sub_lang_key), b)
                    .apply()
            }
        }

        subsFontSize.setOnLongClickListener { _ ->
            state.fixedTextSize = null
            showToast(activity, R.string.subs_default_reset_toast, Toast.LENGTH_SHORT)
            return@setOnLongClickListener true
        }

        subsFont.setFocusableInTv()
        subsFont.setOnClickListener { textView ->
            val fontTypes = listOf(
                Pair(null, textView.context.getString(R.string.normal)),
                Pair(R.font.trebuchet_ms, "Trebuchet MS"),
                Pair(R.font.netflix_sans, "Netflix Sans"),
                Pair(R.font.google_sans, "Google Sans"),
                Pair(R.font.open_sans, "Open Sans"),
                Pair(R.font.futura, "Futura"),
                Pair(R.font.consola, "Consola"),
                Pair(R.font.gotham, "Gotham"),
                Pair(R.font.lucida_grande, "Lucida Grande"),
                Pair(R.font.stix_general, "STIX General"),
                Pair(R.font.times_new_roman, "Times New Roman"),
                Pair(R.font.verdana, "Verdana"),
                Pair(R.font.ubuntu_regular, "Ubuntu"),
                Pair(R.font.comic_sans, "Comic Sans"),
                Pair(R.font.poppins_regular, "Poppins"),
            )
            val savedFontTypes = textView.context.getSavedFonts()
            val currentIndex = savedFontTypes.indexOfFirst { it.absolutePath == state.typefaceFilePath }
                .let { index ->
                    if (index == -1) fontTypes.indexOfFirst { it.first == state.typeface }
                    else index + fontTypes.size
                }
            activity?.showDialog(
                fontTypes.map { it.second } + savedFontTypes.map { it.name },
                currentIndex,
                (textView as TextView).text.toString(),
                false,
                dismissCallback
            ) { index ->
                if (index < fontTypes.size) {
                    state.typeface = fontTypes[index].first
                    state.typefaceFilePath = null
                } else {
                    state.typefaceFilePath = savedFontTypes[index - fontTypes.size].absolutePath
                    state.typeface = null
                }
                textView.context.updateState()
            }
        }

        subsFont.setOnLongClickListener { textView ->
            state.typeface = null
            state.typefaceFilePath = null
            textView.context.updateState()
            showToast(activity, R.string.subs_default_reset_toast, Toast.LENGTH_SHORT)
            return@setOnLongClickListener true
        }

        subsAutoSelectLanguage.setFocusableInTv()
        subsAutoSelectLanguage.setOnClickListener { textView ->
            val langMap = arrayListOf(
                SubtitleHelper.Language639(
                    textView.context.getString(R.string.none),
                    textView.context.getString(R.string.none),
                    "", "", "", "", ""
                )
            )
            langMap.addAll(SubtitleHelper.languages)
            val lang639_1 = langMap.map { it.ISO_639_1 }
            activity?.showDialog(
                langMap.map { it.languageName },
                lang639_1.indexOf(getAutoSelectLanguageISO639_1()),
                (textView as TextView).text.toString(),
                true,
                dismissCallback
            ) { index ->
                setKey(SUBTITLE_AUTO_SELECT_KEY, lang639_1[index])
            }
        }

        subsAutoSelectLanguage.setOnLongClickListener {
            setKey(SUBTITLE_AUTO_SELECT_KEY, "en")
            showToast(activity, R.string.subs_default_reset_toast, Toast.LENGTH_SHORT)
            return@setOnLongClickListener true
        }

        subsDownloadLanguages.setFocusableInTv()
        subsDownloadLanguages.setOnClickListener { textView ->
            val langMap = SubtitleHelper.languages
            val lang639_1 = langMap.map { it.ISO_639_1 }
            val keys = getDownloadSubsLanguageISO639_1()
            val keyMap = keys.map { lang639_1.indexOf(it) }.filter { it >= 0 }
            activity?.showMultiDialog(
                langMap.map { it.languageName },
                keyMap,
                (textView as TextView).text.toString(),
                dismissCallback
            ) { indexList ->
                setKey(SUBTITLE_DOWNLOAD_KEY, indexList.map { lang639_1[it] }.toList())
            }
        }

        subsDownloadLanguages.setOnLongClickListener {
            setKey(SUBTITLE_DOWNLOAD_KEY, listOf("en"))
            showToast(activity, R.string.subs_default_reset_toast, Toast.LENGTH_SHORT)
            return@setOnLongClickListener true
        }

        cancelBtt.setOnClickListener {
            activity?.popCurrentPage()
        }

        applyBtt.setOnClickListener {
            it.context.saveStyle(state)
            applyStyleEvent.invoke(state)
            it.context.fromSaveToStyle(state)
            activity?.popCurrentPage()
        }

        subtitleText.setCues(
            listOf(
                Cue.Builder()
                    .setTextSize(
                        getPixels(TypedValue.COMPLEX_UNIT_SP, 25.0f).toFloat(),
                        Cue.TEXT_SIZE_TYPE_ABSOLUTE
                    )
                    .setText(subtitleText.context.getString(R.string.subtitles_example_text))
                    .build()
            )
        )
    }
}