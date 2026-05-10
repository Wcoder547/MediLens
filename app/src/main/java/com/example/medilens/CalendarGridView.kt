package com.example.medilens

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import java.util.Calendar

class CalendarGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // ── Public data ────────────────────────────────────────────────────────

    enum class DotColor { GREEN, ORANGE, RED }

    var onDayClick: ((day: Int) -> Unit)? = null

    // ── State ──────────────────────────────────────────────────────────────

    private var year        = Calendar.getInstance().get(Calendar.YEAR)
    private var month       = Calendar.getInstance().get(Calendar.MONTH)
    private var selectedDay = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
    private var daysInMonth = 30
    private var firstDayOfWeek = 0          // 0=Mon … 6=Sun offset
    private var dotData     = mapOf<Int, DotColor>()

    // ── Geometry (computed in onSizeChanged) ───────────────────────────────

    private var cellW = 0f
    private var cellH = 0f
    private val cols  = 7

    // ── Paints ─────────────────────────────────────────────────────────────

    private val paintBg     = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintSelect = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val paintToday  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val paintText   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize  = 14f * resources.displayMetrics.scaledDensity
    }
    private val paintDot    = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // ── Today reference ────────────────────────────────────────────────────

    private val todayCal = Calendar.getInstance()
    private val todayYear  = todayCal.get(Calendar.YEAR)
    private val todayMonth = todayCal.get(Calendar.MONTH)
    private val todayDay   = todayCal.get(Calendar.DAY_OF_MONTH)

    // ── Public API ─────────────────────────────────────────────────────────

    fun setMonthData(
        y: Int, m: Int,
        dots: Map<Int, DotColor>,
        selected: Int
    ) {
        year        = y
        month       = m
        dotData     = dots
        selectedDay = selected

        val cal = Calendar.getInstance().apply { set(y, m, 1) }
        daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        // Android: SUNDAY=1 … SATURDAY=7  →  convert to Mon-first offset
        val dow = cal.get(Calendar.DAY_OF_WEEK)      // 1=Sun,2=Mon…
        firstDayOfWeek = (dow + 5) % 7               // 0=Mon … 6=Sun

        requestLayout()
        invalidate()
    }

    fun setSelectedDay(day: Int) {
        selectedDay = day
        invalidate()
    }

    // ── Measure ────────────────────────────────────────────────────────────

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        cellW = w / cols.toFloat()
        cellH = cellW * 1.25f          // cells slightly taller than wide

        val rows = Math.ceil((firstDayOfWeek + daysInMonth) / cols.toDouble()).toInt()
        val h    = (rows * cellH).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        cellW = w / cols.toFloat()
    }

    // ── Draw ───────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (cellW == 0f) return

        val primaryColor  = ContextCompat.getColor(context, R.color.primary)
        val primaryText   = ContextCompat.getColor(context, R.color.primaryText)
        val subtextColor  = ContextCompat.getColor(context, R.color.subtext)
        val whiteColor    = Color.WHITE

        paintSelect.color = primaryColor
        paintToday.color  = primaryColor

        for (day in 1..daysInMonth) {
            val idx  = firstDayOfWeek + day - 1
            val col  = idx % cols
            val row  = idx / cols

            val cx = col * cellW + cellW / 2f
            val cy = row * cellH + cellH / 2f

            val isSelected = (day == selectedDay)
            val isToday    = (year == todayYear && month == todayMonth && day == todayDay)

            // ── Selection circle ──────────────────────────────────────────
            if (isSelected) {
                canvas.drawCircle(cx, cy - 4f, cellW * 0.38f, paintSelect)
            }

            // ── Today ring (when not selected) ────────────────────────────
            if (isToday && !isSelected) {
                canvas.drawCircle(cx, cy - 4f, cellW * 0.38f, paintToday)
            }

            // ── Day number ────────────────────────────────────────────────
            paintText.color = when {
                isSelected -> whiteColor
                isToday    -> primaryColor
                else       -> primaryText
            }
            // Vertical center of text inside circle
            val textY = cy - 4f - (paintText.descent() + paintText.ascent()) / 2f
            canvas.drawText(day.toString(), cx, textY, paintText)

            // ── Dot below date number ─────────────────────────────────────
            val dot = dotData[day]
            if (dot != null) {
                paintDot.color = when (dot) {
                    DotColor.GREEN  -> Color.parseColor("#4CAF50")
                    DotColor.RED    -> Color.parseColor("#F44336")
                    DotColor.ORANGE -> Color.parseColor("#FF9800")
                }
                val dotY = cy - 4f + cellW * 0.30f
                canvas.drawCircle(cx, dotY, 3.5f * resources.displayMetrics.density, paintDot)
            }
        }
    }

    // ── Touch ──────────────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) return true

        val col = (event.x / cellW).toInt()
        val row = (event.y / cellH).toInt()
        val idx = row * cols + col
        val day = idx - firstDayOfWeek + 1

        if (day in 1..daysInMonth) {
            selectedDay = day
            invalidate()
            onDayClick?.invoke(day)
        }
        return true
    }
}