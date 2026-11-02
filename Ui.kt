package com.smartguard.app

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Rang-palette: phone ke light/dark mode ke hisaab se apne aap badalta hai. */
class Pal(c: Context) {
    val dark = (c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    val bg = if (dark) 0xFF0E1320.toInt() else 0xFFF3F6FC.toInt()
    val card = if (dark) 0xFF1A2133.toInt() else 0xFFFFFFFF.toInt()
    val track = if (dark) 0xFF2A3350.toInt() else 0xFFE6ECF7.toInt()
    val soft = if (dark) 0xFF222B42.toInt() else 0xFFE8EFFF.toInt()
    val text = if (dark) 0xFFEAF0FF.toInt() else 0xFF14213D.toInt()
    val sub = if (dark) 0xFF93A0BD.toInt() else 0xFF6B7690.toInt()
    val accent = 0xFF2F6BFF.toInt()
    val good = 0xFF22C55E.toInt()
    val warn = 0xFFF59E0B.toInt()
    val bad = 0xFFEF4444.toInt()
    fun scoreColor(s: Int): Int = if (s >= 80) good else if (s >= 55) warn else bad
}

object Ui {
    fun dp(c: Context, v: Int): Int = (v * c.resources.displayMetrics.density).toInt()

    fun round(color: Int, radius: Float): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
    }

    fun text(c: Context, s: String, size: Float, color: Int, bold: Boolean = false): TextView = TextView(c).apply {
        text = s
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    fun card(c: Context, p: Pal): LinearLayout = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        background = round(p.card, dp(c, 18).toFloat())
        setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14))
        elevation = dp(c, 2).toFloat()
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(c, 12) }
    }

    fun primary(c: Context, p: Pal, label: String, onClick: () -> Unit): Button = Button(c).apply {
        text = label
        isAllCaps = false
        textSize = 16f
        setTextColor(Color.WHITE)
        background = round(p.accent, dp(c, 14).toFloat())
        stateListAnimator = null
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(-1, dp(c, 52)).apply { topMargin = dp(c, 12) }
    }

    fun ghost(c: Context, p: Pal, label: String, onClick: () -> Unit): Button = Button(c).apply {
        text = label
        isAllCaps = false
        textSize = 14f
        setTextColor(p.accent)
        background = round(p.soft, dp(c, 12).toFloat())
        stateListAnimator = null
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(-1, dp(c, 46)).apply { topMargin = dp(c, 10) }
    }

    fun tile(c: Context, p: Pal, emoji: String, title: String, sub: String, onClick: () -> Unit): LinearLayout =
        LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            background = round(p.card, dp(c, 16).toFloat())
            setPadding(dp(c, 14), dp(c, 14), dp(c, 14), dp(c, 14))
            elevation = dp(c, 2).toFloat()
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(c, 4), dp(c, 6), dp(c, 4), dp(c, 2)) }
            addView(text(c, emoji, 26f, p.text))
            addView(text(c, title, 15f, p.text, true))
            addView(text(c, sub, 12f, p.sub))
            isClickable = true
            setOnClickListener { onClick() }
        }
}

/** Label + percent + progress bar wali ek line. */
class BarRow(private val c: Context, private val p: Pal, label: String) {
    val view = LinearLayout(c)
    private val value = TextView(c)
    private val fill = View(c)
    private val track = LinearLayout(c)

    init {
        view.orientation = LinearLayout.VERTICAL
        view.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = Ui.dp(c, 10) }
        val top = LinearLayout(c)
        val name = Ui.text(c, label, 14f, p.text, true)
        name.layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        value.textSize = 13f
        value.setTextColor(p.sub)
        top.addView(name)
        top.addView(value)
        track.weightSum = 100f
        track.background = Ui.round(p.track, Ui.dp(c, 6).toFloat())
        track.layoutParams = LinearLayout.LayoutParams(-1, Ui.dp(c, 8)).apply { topMargin = Ui.dp(c, 6) }
        fill.layoutParams = LinearLayout.LayoutParams(0, -1, 0f)
        track.addView(fill)
        view.addView(top)
        view.addView(track)
    }

    fun set(pct: Int, color: Int, label: String) {
        value.text = label
        fill.background = Ui.round(color, Ui.dp(c, 6).toFloat())
        (fill.layoutParams as LinearLayout.LayoutParams).weight = pct.coerceIn(0, 100).toFloat()
        fill.requestLayout()
    }
}

/** Gol (arc) health-score meter, animation ke saath. */
class GaugeView(c: Context, private val pal: Pal) : View(c) {
    private var shown = 0
    private var color = pal.good
    private var label = ""
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    fun setScore(target: Int, col: Int, lbl: String) {
        color = col
        label = lbl
        val anim = android.animation.ValueAnimator.ofInt(shown, target)
        anim.duration = 800L
        anim.addUpdateListener { a ->
            shown = a.animatedValue as Int
            invalidate()
        }
        anim.start()
    }

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val s = minOf(w, h)
        val stroke = s * 0.075f
        arc.strokeWidth = stroke
        val r = RectF(stroke, stroke, s - stroke, s - stroke)
        arc.color = pal.track
        cv.drawArc(r, 135f, 270f, false, arc)
        arc.color = color
        cv.drawArc(r, 135f, 270f * shown / 100f, false, arc)
        txt.color = pal.text
        txt.textSize = s * 0.30f
        txt.isFakeBoldText = true
        cv.drawText("$shown", s / 2f, s / 2f + s * 0.07f, txt)
        txt.color = pal.sub
        txt.textSize = s * 0.09f
        txt.isFakeBoldText = false
        cv.drawText(label, s / 2f, s / 2f + s * 0.24f, txt)
    }
}
