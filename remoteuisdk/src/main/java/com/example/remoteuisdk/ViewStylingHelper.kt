package com.example.remoteuisdk

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

object ViewStylingHelper {

    fun applyTheme(view: View, colors: ThemeColors) {
        val tag = view.tag

        if (tag is String) {
            // Apply background color to layout backgrounds
            if (tag == "windowBackground" && colors.windowBackground != null) {
                try {
                    view.setBackgroundColor(Color.parseColor(colors.windowBackground))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Apply primary color to buttons or cards
            if (tag == "colorPrimary" && colors.colorPrimary != null) {
                try {
                    view.setBackgroundColor(Color.parseColor(colors.colorPrimary))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Apply secondary color to action buttons
            if (tag == "colorSecondary" && colors.colorSecondary != null) {
                try {
                    view.setBackgroundColor(Color.parseColor(colors.colorSecondary))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Apply primary text color to TextViews
            if (tag == "textColorPrimary" && colors.textColorPrimary != null) {
                if (view is TextView) {
                    try {
                        view.setTextColor(Color.parseColor(colors.textColorPrimary))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        // Recursive tree traversal if it is a ViewGroup
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                applyTheme(child, colors)
            }
        }
    }
}
