package com.lagradost.cloudstream3

import android.app.Application
import org.acra.ACRA
import org.acra.annotation.AcraCore
import org.conscrypt.Conscrypt
import java.security.Security

@AcraCore(buildConfigClass = BuildConfig::class)
class AcraApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // 🔐 FIX SSL ANDROID 6 (TLS 1.2 / Cloudflare / HTTPS)
        try {
            Security.insertProviderAt(Conscrypt.newProvider(), 1)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Init ACRA
        ACRA.init(this)
    }
}