package com.example.remoteuisdk

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import java.util.Random

class HolidayEffectView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 50f // Size for emojis
    }
    private val random = Random()
    
    // Primitive coordinates and speeds arrays for simplicity
    private val xCoords = FloatArray(120)
    private val yCoords = FloatArray(120)
    private val speeds = FloatArray(120)
    private val colors = IntArray(120)
    
    private var effectType: String = ""
    private var particleCount = 0

    init {
        isClickable = false
        isFocusable = false
    }

    fun setEffect(type: String, intensity: Int) {
        this.effectType = type
        this.particleCount = intensity.coerceIn(10, 100)
        
        // Initialize position arrays
        for (i in 0 until particleCount) {
            xCoords[i] = random.nextInt(1200).toFloat()
            yCoords[i] = -random.nextInt(1000).toFloat()
            speeds[i] = random.nextFloat() * 6f + 3f
            
            // Random colors for confetti circles
            val colorList = intArrayOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.MAGENTA, Color.CYAN)
            colors[i] = colorList[random.nextInt(colorList.size)]
        }
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        // Touch events fall through to elements below
        return false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (effectType == "" || effectType == "none" || particleCount == 0) return

        val w = width.toFloat()
        val h = height.toFloat()

        for (i in 0 until particleCount) {
            // Draw particles depending on selection
            if (effectType == "snowflakes") {
                canvas.drawText("❄️", xCoords[i], yCoords[i], paint)
            } else if (effectType == "hearts") {
                canvas.drawText("❤️", xCoords[i], yCoords[i], paint)
            } else if (effectType == "confetti") {
                paint.color = colors[i]
                canvas.drawCircle(xCoords[i], yCoords[i], 12f, paint)
            }

            // Apply movement physics
            if (effectType == "hearts") {
                yCoords[i] -= speeds[i] // float up
            } else {
                yCoords[i] += speeds[i] // fall down
            }

            // Recalculate if gone off boundaries
            if (effectType == "hearts" && yCoords[i] < -60f) {
                yCoords[i] = h + random.nextInt(200)
                xCoords[i] = random.nextFloat() * w
            } else if (effectType != "hearts" && yCoords[i] > h + 60f) {
                yCoords[i] = -random.nextInt(200).toFloat()
                xCoords[i] = random.nextFloat() * w
            }
        }

        // Loop draw animation
        postInvalidateOnAnimation()
    }
}
