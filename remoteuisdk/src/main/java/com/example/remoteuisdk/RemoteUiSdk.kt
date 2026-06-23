package com.example.remoteuisdk

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.google.gson.Gson
import java.net.HttpURLConnection
import java.net.URL

object RemoteUiSdk {
    private const val TAG = "RemoteUiSdk"

    var apiKey: String = ""
    var baseUrl: String = ""
    var cachedConfig: CampaignConfig? = null
    var isInitialized = false

    /**
     * Initializes the SDK and triggers a network thread to fetch the theme config.
     */
    fun init(context: Context, apiKey: String, baseUrl: String) {
        this.apiKey = apiKey
        this.baseUrl = baseUrl
        this.isInitialized = true

        Log.d(TAG, "SDK init with API Key: $apiKey")

        // Fetch config from server in a background thread
        Thread {
            try {
                // Smart url construction
                val configUrl = if (baseUrl.endsWith("/") || baseUrl.contains("/api")) {
                    val base = if (baseUrl.endsWith("/")) baseUrl.substring(0, baseUrl.length - 1) else baseUrl
                    "$base/config?apiKey=$apiKey"
                } else {
                    baseUrl
                }
                
                Log.d(TAG, "Requesting config from: $configUrl")
                val url = URL(configUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                
                if (apiKey.isNotEmpty()) {
                    connection.setRequestProperty("X-Master-Key", apiKey)
                    connection.setRequestProperty("X-Access-Key", apiKey)
                    connection.setRequestProperty("Authorization", "Bearer $apiKey")
                }
                
                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val jsonText = connection.inputStream.bufferedReader().use { it.readText() }
                    val gson = Gson()
                    var config: CampaignConfig? = null
                    try {
                        config = gson.fromJson(jsonText, CampaignConfig::class.java)
                        if (config?.campaignId == null) {
                            val jsonObject = gson.fromJson(jsonText, com.google.gson.JsonObject::class.java)
                            if (jsonObject.has("record")) {
                                config = gson.fromJson(jsonObject.get("record"), CampaignConfig::class.java)
                            }
                        }
                    } catch (pe: Exception) {
                        Log.e(TAG, "Parsing error, trying jsonbin.io wrapper: ${pe.message}")
                    }
                    cachedConfig = config
                    Log.d(TAG, "Config loaded successfully. Campaign ID: " + cachedConfig?.campaignId)
                } else {
                    Log.e(TAG, "Server returned response code: $responseCode")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching configuration: " + e.message)
                e.printStackTrace()
            }
        }.start()
    }

    /**
     * Applies theme configurations to the rootView.
     */
    fun applyTheme(activity: Activity, rootView: View) {
        val config = cachedConfig
        if (config == null) {
            Log.d(TAG, "ApplyTheme: No configuration downloaded yet.")
            return
        }

        if (!config.active) {
            Log.d(TAG, "ApplyTheme: Campaign is deactivated.")
            removeActiveEffects(activity)
            return
        }

        // Evaluate targeting constraints (simple check)
        if (config.targeting != null) {
            val regions = config.targeting.regions
            if (regions != null && regions.isNotEmpty() && !regions.contains("GLOBAL")) {
                if (!regions.contains("US")) {
                    Log.d(TAG, "Targeting: Region is not supported for this device.")
                    removeActiveEffects(activity)
                    return
                }
            }
            
            // SDK version check
            val minSdk = config.targeting.minAndroidSdk
            if (minSdk != null && android.os.Build.VERSION.SDK_INT < minSdk) {
                Log.d(TAG, "Targeting: Android SDK version is too old.")
                removeActiveEffects(activity)
                return
            }
        }

        // Apply background and button color configurations
        if (config.theme != null) {
            ViewStylingHelper.applyTheme(rootView, config.theme)
        }

        // Add visual holiday animation overlay
        if (config.effects != null && config.effects.type != null && config.effects.type != "none") {
            val effectType = config.effects.type
            val intensity = config.effects.intensity ?: 50

            activity.runOnUiThread {
                val contentFrame = activity.findViewById<ViewGroup>(android.R.id.content)
                if (contentFrame != null) {
                    
                    // Remove old effect overlay if present
                    for (i in 0 until contentFrame.childCount) {
                        val child = contentFrame.getChildAt(i)
                        if (child is HolidayEffectView) {
                            contentFrame.removeView(child)
                            break
                        }
                    }

                    // Create and append the new custom drawing layer
                    val effectView = HolidayEffectView(activity)
                    effectView.setEffect(effectType, intensity)
                    
                    val params = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    contentFrame.addView(effectView, params)
                    Log.d(TAG, "Injected effect: $effectType")
                }
            }
        } else {
            removeActiveEffects(activity)
        }
    }

    private fun removeActiveEffects(activity: Activity) {
        activity.runOnUiThread {
            val contentFrame = activity.findViewById<ViewGroup>(android.R.id.content) ?: return@runOnUiThread
            for (i in 0 until contentFrame.childCount) {
                val child = contentFrame.getChildAt(i)
                if (child is HolidayEffectView) {
                    contentFrame.removeView(child)
                    Log.d(TAG, "Cleared active effect overlay.")
                    break
                }
            }
        }
    }
}
