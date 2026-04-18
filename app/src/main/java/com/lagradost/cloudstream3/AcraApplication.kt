package com.lagradost.cloudstream3

import android.app.Application
import org.acra.ACRA
import org.acra.config.CoreConfigurationBuilder
import org.conscrypt.Conscrypt
import java.security.Security

class AcraApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // 🔐 FIX SSL ANDROID 6 (TLS 1.2 / Cloudflare / HTTPS)
        try {
            Security.insertProviderAt(Conscrypt.newProvider(), 1)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Init ACRA tanpa annotation
        val config = CoreConfigurationBuilder(this).build()
        ACRA.init(this, config)
    }
}
