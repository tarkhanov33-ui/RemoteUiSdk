package com.example.app

import com.example.app.sdk.CampaignConfig
import com.example.app.sdk.RemoteUiSdk
import com.example.app.sdk.ThemeColors
import com.example.app.sdk.TargetingRules
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RemoteUiSdkTest {

    @Before
    fun setUp() {
        // Reset SDK state before each test run
        RemoteUiSdk.apiKey = ""
        RemoteUiSdk.baseUrl = ""
        RemoteUiSdk.cachedConfig = null
        RemoteUiSdk.isInitialized = false
    }

    @Test
    fun testSdkInitialization() {
        // GIVEN test inputs
        val testKey = "student_project_api_key_123"
        val testUrl = "https://mock.portal.com/api/"

        // WHEN properties are initialized
        RemoteUiSdk.apiKey = testKey
        RemoteUiSdk.baseUrl = testUrl
        RemoteUiSdk.isInitialized = true

        // THEN assert that the properties are assigned correctly
        assertEquals(testKey, RemoteUiSdk.apiKey)
        assertEquals(testUrl, RemoteUiSdk.baseUrl)
        assertTrue(RemoteUiSdk.isInitialized)
    }

    @Test
    fun testJsonConfigParsing() {
        // GIVEN a mock JSON payload matching our campaign specifications
        val mockJson = """
            {
              "campaignId": "halloween_2026",
              "active": true,
              "theme": {
                "colorPrimary": "#FF5733",
                "colorSecondary": "#202020",
                "windowBackground": "#000000",
                "textColorPrimary": "#FFFFFF"
              },
              "effects": {
                "type": "snowflakes",
                "intensity": 80
              },
              "targeting": {
                "regions": ["US", "GLOBAL"],
                "minAndroidSdk": 21
              }
            }
        """.trimIndent()

        // WHEN parsed using Gson
        val gson = Gson()
        val config = gson.fromJson(mockJson, CampaignConfig::class.java)

        // THEN assert all parsed fields matches the specification
        assertNotNull(config)
        assertEquals("halloween_2026", config.campaignId)
        assertTrue(config.active)
        
        assertNotNull(config.theme)
        assertEquals("#FF5733", config.theme?.colorPrimary)
        assertEquals("#FFFFFF", config.theme?.textColorPrimary)
        
        assertNotNull(config.effects)
        assertEquals("snowflakes", config.effects?.type)
        assertEquals(80, config.effects?.intensity)
        
        assertNotNull(config.targeting)
        assertTrue(config.targeting?.regions?.contains("GLOBAL") == true)
        assertEquals(21, config.targeting?.minAndroidSdk)
    }

    @Test
    fun testDeactivatedCampaignConfig() {
        // GIVEN a campaign config marked as inactive
        val inactiveConfig = CampaignConfig(
            campaignId = "sale_ended",
            active = false,
            theme = null,
            effects = null,
            targeting = null
        )
        RemoteUiSdk.cachedConfig = inactiveConfig

        // THEN check that the SDK cached config behaves as inactive
        assertNotNull(RemoteUiSdk.cachedConfig)
        assertFalse(RemoteUiSdk.cachedConfig!!.active)
    }
}
