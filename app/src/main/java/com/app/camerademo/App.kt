package com.app.camerademo

import android.app.Application
import com.google.android.gms.ads.MobileAds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class App : Application() {

    override fun onCreate() {
        super.onCreate()

        // ✅ Initialize AdMob
        MobileAds.initialize(this)

        CoroutineScope(Dispatchers.IO).launch {
            Util.getFileNumber()
        }
    }
}
