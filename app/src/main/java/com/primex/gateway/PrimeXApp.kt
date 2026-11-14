package com.primex.gateway

import android.app.Application
import com.primex.gateway.utils.PrefsManager

class PrimeXApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PrefsManager.init(this)
    }
}
