package com.lagradost.cloudstream3.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.annotation.UiThread
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.preference.PreferenceFragmentCompat
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.AccountManagmentBinding
import com.lagradost.cloudstream3.databinding.AccountSwitchBinding
import com.lagradost.cloudstream3.databinding.AddAccountInputBinding
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.syncproviders.AccountManager
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.aniListApi
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.malApi
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.nginxApi
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.openSubtitlesApi
import com.lagradost.cloudstream3.syncproviders.AuthAPI
import com.lagradost.cloudstream3.syncproviders.InAppAuthAPI
import com.lagradost.cloudstream3.syncproviders.OAuth2API
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.getPref
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isTvSettings
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setUpToolbar
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard
import com.lagradost.cloudstream3.utils.UIHelper.setImage

class SettingsAccount : PreferenceFragmentCompat() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setUpToolbar(R.string.category_account)
    }

    private fun showLoginInfo(api: AccountManager, info: AuthAPI.LoginInfo) {
        val builder = AlertDialog.Builder(context ?: return, R.style.AlertDialogCustom)
            .setView(R.layout.account_managment)
        val dialog = builder.show()

        // Inflate binding untuk dialog account_managment
        val binding = AccountManagmentBinding.bind(dialog.findViewById(android.R.id.custom) ?: return)

        binding.accountMainProfilePictureHolder?.isVisible =
            binding.accountMainProfilePicture?.setImage(info.profilePicture) == true

        binding.accountLogout?.setOnClickListener {
            api.logOut()
            dialog.dismissSafe(activity)
        }

        (info.name ?: context?.getString(R.string.no_data))?.let {
            binding.accountName?.text = it
        }

        binding.accountSite?.text = api.name
        binding.accountSwitchAccount?.setOnClickListener {
            dialog.dismissSafe(activity)
            showAccountSwitch(it.context, api)
        }
    }

    @UiThread
    private fun addAccount(api: AccountManager) {
        try {
            when (api) {
                is OAuth2API -> {
                    api.authenticate()
                }
                is InAppAuthAPI -> {
                    val builder = AlertDialog.Builder(context ?: return, R.style.AlertDialogCustom)
                        .setView(R.layout.add_account_input)
                    val dialog = builder.show()

                    // Inflate binding untuk dialog add_account_input
                    val binding = AddAccountInputBinding.bind(dialog.findViewById(android.R.id.custom) ?: return)

                    val visibilityMap = mapOf(
                        binding.loginEmailInput to api.requiresEmail,
                        binding.loginPasswordInput to api.requiresPassword,
                        binding.loginServerInput to api.requiresServer,
                        binding.loginUsernameInput to api.requiresUsername
                    )

                    if (context?.isTvSettings() == true) {
                        visibilityMap.forEach { (input, isVisible) ->
                            input.isVisible = isVisible

                            // Band-aid for weird FireTV behavior causing crashes because keyboard covers the screen
                            input.setOnEditorActionListener { textView, actionId, _ ->
                                if (actionId == EditorInfo.IME_ACTION_NEXT) {
                                    val view = textView.focusSearch(View.FOCUS_DOWN)
                                    return@setOnEditorActionListener view?.requestFocus(View.FOCUS_DOWN) == true
                                }
                                return@setOnEditorActionListener true
                            }
                        }
                    } else {
                        visibilityMap.forEach { (input, isVisible) ->
                            input.isVisible = isVisible
                        }
                    }

                    binding.createAccount?.isGone = api.createAccountUrl.isNullOrBlank()
                    binding.createAccount?.setOnClickListener {
                        val i = Intent(Intent.ACTION_VIEW)
                        i.data = Uri.parse(api.createAccountUrl)
                        try {
                            startActivity(i)
                        } catch (e: Exception) {
                            logError(e)
                        }
                    }
                    binding.text1?.text = api.name

                    if (api.storesPasswordInPlainText) {
                        api.getLatestLoginData()?.let { data ->
                            binding.loginEmailInput?.setText(data.email ?: "")
                            binding.loginServerInput?.setText(data.server ?: "")
                            binding.loginUsernameInput?.setText(data.username ?: "")
                            binding.loginPasswordInput?.setText(data.password ?: "")
                        }
                    }

                    binding.applyBtt?.setOnClickListener {
                        val loginData = InAppAuthAPI.LoginData(
                            username = if (api.requiresUsername) binding.loginUsernameInput?.text?.toString() else null,
                            password = if (api.requiresPassword) binding.loginPasswordInput?.text?.toString() else null,
                            email = if (api.requiresEmail) binding.loginEmailInput?.text?.toString() else null,
                            server = if (api.requiresServer) binding.loginServerInput?.text?.toString() else null,
                        )
                        ioSafe {
                            val isSuccessful = try {
                                api.login(loginData)
                            } catch (e: Exception) {
                                logError(e)
                                false
                            }
                            activity?.runOnUiThread {
                                try {
                                    showToast(
                                        activity,
                                        getString(if (isSuccessful) R.string.authenticated_user else R.string.authenticated_user_fail).format(
                                            api.name
                                        )
                                    )
                                } catch (e: Exception) {
                                    logError(e) // format might fail
                                }
                            }
                        }
                        dialog.dismissSafe(activity)
                    }
                    binding.cancelBtt?.setOnClickListener {
                        dialog.dismissSafe(activity)
                    }
                }
                else -> {
                    throw NotImplementedError("You are trying to add an account that has an unknown login method")
                }
            }
        } catch (e: Exception) {
            logError(e)
        }
    }

    private fun showAccountSwitch(context: Context, api: AccountManager) {
        val accounts = api.getAccounts() ?: return

        val builder = AlertDialog.Builder(context, R.style.AlertDialogCustom)
            .setView(R.layout.account_switch)
        val dialog = builder.show()

        // Inflate binding untuk dialog account_switch
        val binding = AccountSwitchBinding.bind(dialog.findViewById(android.R.id.custom) ?: return)

        binding.accountAdd?.setOnClickListener {
            addAccount(api)
            dialog?.dismissSafe(activity)
        }

        val ogIndex = api.accountIndex

        val items = ArrayList<AuthAPI.LoginInfo>()

        for (index in accounts) {
            api.accountIndex = index
            val accountInfo = api.loginInfo()
            if (accountInfo != null) {
                items.add(accountInfo)
            }
        }
        api.accountIndex = ogIndex
        val adapter = AccountAdapter(items, R.layout.account_single) {
            dialog?.dismissSafe(activity)
            api.changeAccount(it.card.accountIndex)
        }
        val list = binding.accountList
        list?.adapter = adapter
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        hideKeyboard()
        setPreferencesFromResource(R.xml.settings_account, rootKey)

        val syncApis =
            listOf(
                R.string.mal_key to malApi,
                R.string.anilist_key to aniListApi,
                R.string.opensubtitles_key to openSubtitlesApi,
                R.string.nginx_key to nginxApi,
            )

        for ((key, api) in syncApis) {
            getPref(key)?.apply {
                title =
                    getString(R.string.login_format).format(api.name, getString(R.string.account))
                setOnPreferenceClickListener {
                    val info = api.loginInfo()
                    if (info != null) {
                        showLoginInfo(api, info)
                    } else {
                        addAccount(api)
                    }
                    return@setOnPreferenceClickListener true
                }
            }
        }
    }
}