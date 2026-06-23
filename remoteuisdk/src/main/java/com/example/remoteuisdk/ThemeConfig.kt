package com.example.remoteuisdk

import com.google.gson.annotations.SerializedName

data class CampaignConfig(
    @SerializedName("campaignId") val campaignId: String,
    @SerializedName("active") val active: Boolean,
    @SerializedName("theme") val theme: ThemeColors?,
    @SerializedName("effects") val effects: HolidayEffects?,
    @SerializedName("targeting") val targeting: TargetingRules?
)

data class ThemeColors(
    @SerializedName("colorPrimary") val colorPrimary: String?,
    @SerializedName("colorSecondary") val colorSecondary: String?,
    @SerializedName("windowBackground") val windowBackground: String?,
    @SerializedName("textColorPrimary") val textColorPrimary: String?
)

data class HolidayEffects(
    @SerializedName("type") val type: String?, // "snowflakes", "hearts", "confetti"
    @SerializedName("intensity") val intensity: Int?
)

data class TargetingRules(
    @SerializedName("regions") val regions: List<String>?,
    @SerializedName("minAndroidSdk") val minAndroidSdk: Int?,
    @SerializedName("appVersionFilter") val appVersionFilter: String?
)
