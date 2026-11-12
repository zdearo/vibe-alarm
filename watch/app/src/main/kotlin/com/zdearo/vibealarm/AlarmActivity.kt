package com.zdearo.vibealarm

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.animation.doOnEnd
import kotlin.math.abs

/** Tela "Despertando": acende no disparo, mostra o progresso da caminhada e de propósito não tem botão de parar. */
class AlarmActivity : Activity() {
    private lateinit var ring: Ring
    private lateinit var time: TextView
    private lateinit var hint: TextView
    private lateinit var count: TextView
    private var animator: ValueAnimator? = null
    private var shown = 0f
    private var k = 1f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        k = Ui.scale(this)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        ring = Ring(this)
        time = text(40f, Ui.TEXT, Ui.display(this), -0.04f)
        hint = text(13f, Ui.TEXT, Ui.body(this, 600))
        count = text(12f, Ui.MUTED, Ui.body(this, 400))
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(time)
            addView(hint, LinearLayout.LayoutParams(-2, -2).apply { topMargin = (8 * k).toInt() })
            addView(count, LinearLayout.LayoutParams(-2, -2).apply { topMargin = (4 * k).toInt() })
        }
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(Ui.INK)
            addView(ring, FrameLayout.LayoutParams(-1, -1))
            addView(column, FrameLayout.LayoutParams(-1, -1))
        })
    }

    override fun onResume() {
        super.onResume()
        AlarmState.listener = ::render
        render()
    }

    override fun onPause() {
        AlarmState.listener = null
        animator?.cancel()
        super.onPause()
    }

    // Sem saída: só a caminhada encerra o alarme (o gesto de voltar do sistema pode fechar a tela, mas não a vibração).
    @Deprecated("Sem saída")
    override fun onBackPressed() = Unit

    private fun render() {
        if (!AlarmState.active && !AlarmState.done) {
            finish()
            return
        }
        time.text = AlarmState.timeLabel
        if (AlarmState.goal <= 0) {
            hint.text = "Teste de vibração"
            count.text = "Termina sozinho em instantes"
            ring.fraction = 0f
            return
        }
        hint.text = when {
            AlarmState.done -> "Bom dia"
            AlarmState.waiting -> "Continue andando"
            else -> "Levante-se e caminhe"
        }
        animateTo(AlarmState.progress.toFloat() / AlarmState.goal)
    }

    /**
     * O progresso medido chega em degraus (uma vez por pausa da vibração, a cada ~2 s). Em vez de saltar,
     * a barra e o número sobem e descem de forma contínua, ~1 s de progresso por segundo; ao fechar, deixa terminar.
     */
    private fun animateTo(target: Float) {
        animator?.cancel()
        val goal = AlarmState.goal
        if (abs(target - shown) < 0.001f) {
            show(target, goal)
            if (AlarmState.done) closeSoon()
            return
        }
        animator = ValueAnimator.ofFloat(shown, target).apply {
            duration = (abs(target - shown) * goal * 1000).toLong().coerceIn(500, 2500)
            interpolator = LinearInterpolator()
            addUpdateListener { show(it.animatedValue as Float, goal) }
            if (AlarmState.done) doOnEnd { closeSoon() }
            start()
        }
    }

    private fun show(fraction: Float, goal: Int) {
        shown = fraction
        ring.fraction = fraction
        count.text = "${(fraction * goal).toInt()} de $goal s"
    }

    private fun closeSoon() {
        ring.postDelayed({ finish() }, 700)
    }

    private fun text(sizeDp: Float, color: Int, face: Typeface, spacing: Float = 0f) = TextView(this).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_PX, sizeDp * k)
        setTextColor(color)
        typeface = face
        letterSpacing = spacing
        includeFontPadding = false
        gravity = Gravity.CENTER
    }

    /** Anel de progresso: trilho em surface, avanço em text. */
    private class Ring(context: Context) : View(context) {
        var fraction = 0f
            set(v) {
                field = v.coerceIn(0f, 1f)
                invalidate()
            }
        private val stroke = 5 * Ui.scale(context)
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.ROUND
        }

        override fun onDraw(canvas: Canvas) {
            val r = RectF(stroke, stroke, width - stroke, height - stroke)
            paint.color = Ui.SURFACE
            canvas.drawArc(r, 0f, 360f, false, paint)
            paint.color = Ui.TEXT
            canvas.drawArc(r, -90f, 360f * fraction, false, paint)
        }
    }
}
