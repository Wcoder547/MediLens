package com.example.medilens

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class SkyBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var hour: Int = 12
    private var isCompleted: Boolean = false

    private val stars = List(18) {
        Pair(Random.nextFloat(), Random.nextFloat())
    }

    fun setHour(h: Int, completed: Boolean = false) {
        hour = h
        isCompleted = completed
        invalidate()
    }

    private fun resetPaint() {
        paint.shader = null
        paint.colorFilter = null
        paint.maskFilter = null
        paint.pathEffect = null
        paint.xfermode = null
        paint.alpha = 255
        paint.style = Paint.Style.FILL
        paint.strokeWidth = 0f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        resetPaint()

        if (isCompleted) {
            drawCompleted(canvas)
            return
        }

        when {
            hour in 6..11 -> drawMorning(canvas)
            hour in 12..16 -> drawAfternoon(canvas)
            hour in 17..20 -> drawEvening(canvas)
            else -> drawNight(canvas)
        }
    }

    private fun drawMorning(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        paint.shader = LinearGradient(
            0f, 0f, w, h,
            intArrayOf(
                Color.parseColor("#FFF9C4"),
                Color.parseColor("#FFECB3"),
                Color.parseColor("#B3E5FC")
            ),
            floatArrayOf(0f, 0.4f, 1f),
            Shader.TileMode.CLAMP
        )

        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        val sunX = w * 0.88f
        val sunY = h * 0.75f
        val sunR = h * 0.55f

        paint.color = Color.parseColor("#40FFE082")
        canvas.drawCircle(sunX, sunY, sunR * 1.5f, paint)

        paint.color = Color.parseColor("#FFCA28")
        canvas.drawCircle(sunX, sunY, sunR, paint)

        paint.color = Color.parseColor("#80FFB300")
        paint.strokeWidth = 2f
        paint.style = Paint.Style.STROKE

        for (i in 0..7) {
            val angle = Math.toRadians(i * 45.0)
            val startR = sunR * 1.15f
            val endR = sunR * 1.45f

            canvas.drawLine(
                sunX + (startR * cos(angle)).toFloat(),
                sunY + (startR * sin(angle)).toFloat(),
                sunX + (endR * cos(angle)).toFloat(),
                sunY + (endR * sin(angle)).toFloat(),
                paint
            )
        }

        paint.style = Paint.Style.FILL
    }

    private fun drawAfternoon(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        paint.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(
                Color.parseColor("#E3F2FD"),
                Color.parseColor("#BBDEFB")
            ),
            null,
            Shader.TileMode.CLAMP
        )

        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        val sunX = w * 0.85f
        val sunY = h * 0.25f
        val sunR = h * 0.28f

        paint.color = Color.parseColor("#30FFF9C4")
        canvas.drawCircle(sunX, sunY, sunR * 2f, paint)

        paint.color = Color.parseColor("#FDD835")
        canvas.drawCircle(sunX, sunY, sunR, paint)

        paint.color = Color.parseColor("#60F9A825")
        paint.strokeWidth = 2.5f
        paint.style = Paint.Style.STROKE

        for (i in 0..11) {
            val angle = Math.toRadians(i * 30.0)
            val startR = sunR * 1.2f
            val endR = sunR * 1.6f

            canvas.drawLine(
                sunX + (startR * cos(angle)).toFloat(),
                sunY + (startR * sin(angle)).toFloat(),
                sunX + (endR * cos(angle)).toFloat(),
                sunY + (endR * sin(angle)).toFloat(),
                paint
            )
        }

        paint.style = Paint.Style.FILL

        paint.color = Color.parseColor("#80FFFFFF")
        canvas.drawRoundRect(w * 0.05f, h * 0.15f, w * 0.35f, h * 0.35f, 30f, 30f, paint)
        canvas.drawRoundRect(w * 0.02f, h * 0.22f, w * 0.32f, h * 0.38f, 30f, 30f, paint)
    }

    private fun drawEvening(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        paint.shader = LinearGradient(
            0f, 0f, w, h,
            intArrayOf(
                Color.parseColor("#4A148C"),
                Color.parseColor("#E64A19"),
                Color.parseColor("#FF8F00")
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )

        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        val sunX = w * 0.15f
        val sunY = h * 0.85f
        val sunR = h * 0.45f

        paint.color = Color.parseColor("#40FF6D00")
        canvas.drawCircle(sunX, sunY, sunR * 1.8f, paint)

        paint.color = Color.parseColor("#FF6D00")
        canvas.drawCircle(sunX, sunY, sunR, paint)

        paint.color = Color.parseColor("#80FFFFFF")
        stars.take(6).forEach { (fx, fy) ->
            val sx = fx * w * 0.6f + w * 0.35f
            val sy = fy * h * 0.5f
            canvas.drawCircle(sx, sy, 1.5f, paint)
        }
    }

    private fun drawNight(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        paint.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(
                Color.parseColor("#0D0D2B"),
                Color.parseColor("#1A1A4E"),
                Color.parseColor("#0D1B4B")
            ),
            null,
            Shader.TileMode.CLAMP
        )

        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        paint.color = Color.parseColor("#CCFFFFFF")
        stars.forEach { (fx, fy) ->
            val sx = fx * w
            val sy = fy * h * 0.75f
            val sr = if (fx > 0.7f) 2f else 1.2f
            canvas.drawCircle(sx, sy, sr, paint)
        }

        val moonX = w * 0.78f
        val moonY = h * 0.28f
        val moonR = h * 0.22f

        paint.color = Color.parseColor("#15FFF9E7")
        canvas.drawCircle(moonX, moonY, moonR * 1.8f, paint)

        paint.color = Color.parseColor("#FFF9E7")
        canvas.drawCircle(moonX, moonY, moonR, paint)

        // Crescent bite: same night background color, not random old paint state
        paint.color = Color.parseColor("#1A1A4E")
        canvas.drawCircle(
            moonX + moonR * 0.55f,
            moonY - moonR * 0.1f,
            moonR * 0.82f,
            paint
        )
    }

    private fun drawCompleted(canvas: Canvas) {
        paint.color = Color.parseColor("#F5F5F5")
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        val minHeight = (80 * resources.displayMetrics.density).toInt()
        val finalHeight = if (measuredHeight < minHeight) minHeight else measuredHeight

        setMeasuredDimension(measuredWidth, finalHeight)
    }
}