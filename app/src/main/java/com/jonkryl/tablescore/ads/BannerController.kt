package com.jonkryl.tablescore.ads

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.view.Gravity
import android.widget.FrameLayout
import com.jonkryl.tablescore.BuildConfig
import com.yandex.mobile.ads.banner.BannerAdEventListener
import com.yandex.mobile.ads.banner.BannerAdSize
import com.yandex.mobile.ads.banner.BannerAdView
import com.yandex.mobile.ads.common.AdRequest
import com.yandex.mobile.ads.common.AdRequestError
import com.yandex.mobile.ads.common.ImpressionData
import com.yandex.mobile.ads.common.YandexAds
import kotlin.math.roundToInt

/** One banner per activity, outside the controls. A failed ad never changes game state. */
class BannerController(private val activity: Activity) {
    private val preferences = activity.getSharedPreferences("ad_privacy", Activity.MODE_PRIVATE)
    private var host: FrameLayout? = null
    private var banner: BannerAdView? = null
    private var destroyed = false
    private var attached = false

    fun attach(container: FrameLayout) {
        if (destroyed || attached) return
        attached = true
        host = container
        configurePrivacy()
        YandexAds.initialize(activity.applicationContext) {
            activity.runOnUiThread {
                if (!destroyed) container.post { loadBanner() }
            }
        }
    }

    private fun configurePrivacy() {
        // Consent is passed on every launch, before SDK initialization or any request.
        YandexAds.setUserConsent(preferences.getBoolean("personalized", false))
        YandexAds.setLocationTracking(false)
        YandexAds.setAppAdAnalyticsReporting(false)
    }

    private fun loadBanner() {
        val container = host ?: return
        if (destroyed || activity.isDestroyed || activity.isFinishing || banner != null) return
        val density = activity.resources.displayMetrics.density
        val width = ((container.width.takeIf { it > 0 }
            ?: activity.resources.displayMetrics.widthPixels) / density).roundToInt().coerceAtLeast(1)
        val size = BannerAdSize.sticky(activity, width)
        // Reserve the full slot before loading, including no-fill/offline outcomes.
        container.minimumHeight = maxOf(container.minimumHeight, size.getHeightInPixels(activity))
        val view = BannerAdView(activity)
        banner = view
        view.setAdSize(size)
        view.setBannerAdEventListener(object : BannerAdEventListener {
            override fun onAdLoaded() {
                if (destroyed || activity.isDestroyed) view.destroy()
            }
            override fun onAdFailedToLoad(error: AdRequestError) {
                // Deliberately no retry loop and no interaction with score controls.
            }
            override fun onAdClicked() = Unit
            override fun onImpression(data: ImpressionData?) = Unit
        })
        container.addView(view, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        view.loadAd(AdRequest.Builder(BuildConfig.YANDEX_BANNER_ID).build())
    }

    fun showPrivacyChoice() {
        if (destroyed || activity.isFinishing) return
        val russian = activity.resources.configuration.locales[0].language == "ru"
        AlertDialog.Builder(activity)
            .setTitle(if (russian) "Приватность рекламы" else "Ad privacy")
            .setMessage(if (russian)
                "По умолчанию показывается контекстная реклама Яндекса. Имена игроков и счёт остаются на устройстве. SDK может передавать технические данные для показа рекламы. Можно разрешить персонализацию или сохранить контекстный режим; баннер остаётся в обоих режимах. Геолокация и рекламный ID не запрашиваются."
                else "Yandex contextual ads are the default. Player names and scores stay on your device. The SDK may send technical data to serve ads. You can allow personalization or keep contextual ads; the banner remains in both modes. Location and advertising ID are not requested.")
            .setPositiveButton(if (russian) "Разрешить персонализацию" else "Allow personalization") { _, _ -> choose(true) }
            .setNegativeButton(if (russian) "Контекстная реклама" else "Contextual ads") { _, _ -> choose(false) }
            .setNeutralButton(if (russian) "Политика" else "Privacy policy") { _, _ ->
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.PRIVACY_POLICY_URL)))
            }.show()
    }

    private fun choose(personalized: Boolean) {
        // This is a data-consent choice, never an ad-free switch.
        if (!preferences.edit().putBoolean("personalized", personalized).commit()) return
        configurePrivacy()
        banner?.destroy()
        banner = null
        host?.removeAllViews()
        loadBanner()
    }

    fun destroy() {
        destroyed = true
        banner?.destroy()
        banner = null
        host = null
    }
}
