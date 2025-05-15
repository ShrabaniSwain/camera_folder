package com.app.camerademo

import android.annotation.SuppressLint
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.app.camerademo.Util.Interstitial_Ad_ID
import com.app.camerademo.databinding.ActivitySplashBinding
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private val binding by lazy { ActivitySplashBinding.inflate(layoutInflater) }
    private var interstitialAd: InterstitialAd? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        val prefs = getSharedPreferences("ad_prefs", MODE_PRIVATE)
        val launchCount = prefs.getInt("launch_count", 0)

        if (launchCount < 2) {
            loadInterstitial {
                prefs.edit().putInt("launch_count", launchCount + 1).apply()
                goToMainApp()
            }
        } else {
            binding.logo.postDelayed({
                goToMainApp()
            }, 500)
        }

    }

    private fun loadInterstitial(onComplete: () -> Unit) {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            this,
            Interstitial_Ad_ID,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    interstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            interstitialAd = null
                            onComplete()
                        }

                        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                            onComplete()
                        }
                    }
                    ad.show(this@SplashActivity)
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    onComplete()
                }
            }
        )
    }
    private fun goToMainApp() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

}