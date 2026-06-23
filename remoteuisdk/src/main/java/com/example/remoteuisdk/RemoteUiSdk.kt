package com.example.remoteuisdk

import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.google.gson.Gson
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object RemoteUiSdk {
    private const val TAG = "RemoteUiSdk"

    var apiKey: String = ""
    var baseUrl: String = ""
    var cachedConfig: CampaignConfig? = null
    var isInitialized = false

    private var activeActivity: WeakReference<Activity>? = null
    private var serverSocket: ServerSocket? = null
    private var isServerRunning = false

    /**
     * Initializes the SDK and triggers a network thread to fetch the theme config.
     */
    fun init(context: Context, apiKey: String, baseUrl: String) {
        this.apiKey = apiKey
        this.baseUrl = baseUrl
        this.isInitialized = true

        Log.d(TAG, "SDK init with API Key: $apiKey")

        // Track active Activity automatically
        val app = context.applicationContext as? Application
        app?.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {
                activeActivity = WeakReference(activity)
            }
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {
                if (activeActivity?.get() == activity) {
                    activeActivity = null
                }
            }
        })

        // Start local debug server for live previews
        startDebugServer()

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
                    
                    // Apply theme color updates if there is an active activity
                    val activity = activeActivity?.get()
                    if (activity != null) {
                        activity.runOnUiThread {
                            val view = activity.findViewById<View>(android.R.id.content)
                            if (view != null) {
                                applyTheme(activity, view)
                            }
                        }
                    }
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

    private fun startDebugServer() {
        if (isServerRunning) return
        isServerRunning = true
        Thread {
            try {
                serverSocket = ServerSocket(8080)
                Log.d(TAG, "Debug server started on port 8080")
                while (isServerRunning) {
                    val socket = serverSocket?.accept() ?: break
                    Thread { handleClient(socket) }.start()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in debug server: ${e.message}")
            }
        }.start()
    }

    fun stopDebugServer() {
        isServerRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {}
        serverSocket = null
    }

    private fun handleClient(socket: Socket) {
        try {
            val input = socket.getInputStream()
            val output = socket.getOutputStream()
            
            // Read HTTP headers
            val headerBuilder = StringBuilder()
            var c: Int
            while (true) {
                c = input.read()
                if (c == -1) break
                headerBuilder.append(c.toChar())
                if (headerBuilder.endsWith("\r\n\r\n")) {
                    break
                }
            }
            
            val header = headerBuilder.toString()
            if (header.isEmpty()) {
                socket.close()
                return
            }
            
            val firstLine = header.split("\r\n")[0]
            val parts = firstLine.split(" ")
            if (parts.size < 2) {
                socket.close()
                return
            }
            
            val method = parts[0]
            val path = parts[1]
            
            if (method == "OPTIONS") {
                sendResponse(output, "HTTP/1.1 204 No Content\r\n" +
                        "Access-Control-Allow-Origin: *\r\n" +
                        "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                        "Access-Control-Allow-Headers: *\r\n" +
                        "\r\n", ByteArray(0))
                socket.close()
                return
            }
            
            if (path.startsWith("/screenshot") && method == "GET") {
                val activity = activeActivity?.get()
                if (activity != null) {
                    val doneSignal = CountDownLatch(1)
                    var jpegBytes: ByteArray? = null
                    activity.runOnUiThread {
                        try {
                            val view = activity.window.decorView
                            if (view.width > 0 && view.height > 0) {
                                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                                val canvas = Canvas(bitmap)
                                view.draw(canvas)
                                val outStream = ByteArrayOutputStream()
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outStream)
                                jpegBytes = outStream.toByteArray()
                                bitmap.recycle()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Screenshot error: ${e.message}")
                        } finally {
                            doneSignal.countDown()
                        }
                    }
                    doneSignal.await(2, TimeUnit.SECONDS)
                    
                    if (jpegBytes != null) {
                        sendResponse(output, "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: image/jpeg\r\n" +
                                "Access-Control-Allow-Origin: *\r\n" +
                                "Content-Length: ${jpegBytes!!.size}\r\n" +
                                "\r\n", jpegBytes!!)
                    } else {
                        sendError(output, 500, "Could not capture screenshot")
                    }
                } else {
                    sendError(output, 404, "No active activity")
                }
            } else if (path.startsWith("/config") && method == "POST") {
                // Read Content-Length
                var contentLength = 0
                val lines = header.split("\r\n")
                for (line in lines) {
                    if (line.lowercase().startsWith("content-length:")) {
                        contentLength = line.substring(15).trim().toIntOrNull() ?: 0
                    }
                }
                
                val bodyBytes = ByteArray(contentLength)
                var read = 0
                while (read < contentLength) {
                    val r = input.read(bodyBytes, read, contentLength - read)
                    if (r == -1) break
                    read += r
                }
                val body = String(bodyBytes, Charsets.UTF_8)
                
                try {
                    val gson = Gson()
                    val config = gson.fromJson(body, CampaignConfig::class.java)
                    cachedConfig = config
                    
                    val activity = activeActivity?.get()
                    if (activity != null) {
                        activity.runOnUiThread {
                            val view = activity.findViewById<View>(android.R.id.content)
                            if (view != null) {
                                applyTheme(activity, view)
                            }
                        }
                    }
                    
                    val responseMsg = "{\"status\":\"success\"}"
                    val responseBytes = responseMsg.toByteArray(Charsets.UTF_8)
                    sendResponse(output, "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: application/json\r\n" +
                            "Access-Control-Allow-Origin: *\r\n" +
                            "Content-Length: ${responseBytes.size}\r\n" +
                            "\r\n", responseBytes)
                } catch (e: Exception) {
                    sendError(output, 400, "Invalid JSON: ${e.message}")
                }
            } else {
                sendError(output, 404, "Not Found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling client: ${e.message}")
        } finally {
            try {
                socket.close()
            } catch (ex: Exception) {}
        }
    }

    private fun sendResponse(output: OutputStream, headers: String, body: ByteArray) {
        output.write(headers.toByteArray(Charsets.UTF_8))
        if (body.isNotEmpty()) {
            output.write(body)
        }
        output.flush()
    }

    private fun sendError(output: OutputStream, code: Int, message: String) {
        val json = "{\"error\":\"$message\"}"
        val body = json.toByteArray(Charsets.UTF_8)
        val statusText = when (code) {
            400 -> "Bad Request"
            404 -> "Not Found"
            else -> "Internal Server Error"
        }
        sendResponse(output, "HTTP/1.1 $code $statusText\r\n" +
                "Content-Type: application/json\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Content-Length: ${body.size}\r\n" +
                "\r\n", body)
    }
}
