package de.sgh0hann.boost

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import kotlin.math.round

/** Design colors and constants. */
object Ui {
    const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    val bgTop: Int = Color.rgb(16, 33, 59)
    val bgBottom: Int = Color.rgb(20, 14, 44)
    val navBar: Int = Color.rgb(18, 20, 38)

    val panel: Int = Color.rgb(38, 38, 52)
    val panelLight: Int = Color.rgb(46, 48, 66)
    val panelDark: Int = Color.rgb(22, 26, 46)
    val stroke: Int = Color.argb(38, 255, 255, 255)

    val white: Int = Color.rgb(245, 247, 252)
    val muted: Int = Color.rgb(150, 160, 184)
    val dark: Int = Color.rgb(10, 22, 42)

    val accentLight: Int = Color.rgb(106, 208, 255)
    val accent: Int = Color.rgb(98, 204, 255)
    val accent2: Int = Color.rgb(63, 140, 255)

    val good: Int = Color.rgb(74, 222, 128)
    val warn: Int = Color.rgb(251, 191, 36)
    val bad: Int = Color.rgb(255, 107, 107)

    fun alpha(color: Int, a: Int): Int =
        Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
}

/** App logo (S with bolt). Same geometry as the launcher icon. */
class LogoView(context: Context, private val zoom: Float = 1.5f) : View(context) {

    private val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f, 0f, 108f, 108f,
            Color.rgb(20, 40, 74), Color.rgb(18, 15, 43),
            Shader.TileMode.CLAMP
        )
    }

    private val sPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 11f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        shader = LinearGradient(
            30f, 24f, 78f, 84f,
            Color.rgb(123, 216, 255), Color.rgb(63, 140, 255),
            Shader.TileMode.CLAMP
        )
    }

    private val boltFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private val boltEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.2f
        strokeJoin = Paint.Join.ROUND
        color = Color.rgb(20, 40, 74)
    }

    private val sPath = Path().apply {
        moveTo(71f, 37f)
        cubicTo(68f, 31f, 62f, 29f, 55f, 29f)
        cubicTo(45f, 29f, 39f, 34f, 39f, 41f)
        cubicTo(39f, 49f, 46f, 51f, 54f, 53.5f)
        cubicTo(62f, 56f, 69f, 58.5f, 69f, 67f)
        cubicTo(69f, 74f, 63f, 79f, 53f, 79f)
        cubicTo(46f, 79f, 40f, 76.5f, 37f, 70f)
    }

    private val boltPath = Path().apply {
        moveTo(66f, 26f)
        lineTo(41f, 59f)
        lineTo(54f, 59f)
        lineTo(44f, 84f)
        lineTo(71f, 47f)
        lineTo(58f, 47f)
        close()
    }

    override fun onDraw(canvas: Canvas) {
        val s = width / 108f
        canvas.save()
        canvas.scale(s, s)
        canvas.drawRoundRect(0f, 0f, 108f, 108f, 26f, 26f, tilePaint)
        canvas.translate(54f, 54f)
        canvas.scale(zoom, zoom)
        canvas.translate(-54f, -54f)
        canvas.drawPath(sPath, sPaint)
        canvas.drawPath(boltPath, boltEdge)
        canvas.drawPath(boltPath, boltFill)
        canvas.restore()
    }
}

enum class IconKind { HOME, SLIDERS, GAUGE, SHIELD, GEAR, ARROW, PLAY, STOP, CHECK, EYE, LIST }

/** Simple line icons drawn in code (no emojis, no resources). */
class IconView(context: Context, private val kind: IconKind) : View(context) {

    var tint: Int = Color.WHITE
        set(value) {
            field = value
            invalidate()
        }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 2f
    }

    private val path = Path()
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        val s = minOf(width, height) / 24f
        canvas.save()
        canvas.scale(s, s)
        p.color = tint
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2f

        when (kind) {
            IconKind.HOME -> {
                path.reset()
                path.moveTo(3.5f, 11f)
                path.lineTo(12f, 4f)
                path.lineTo(20.5f, 11f)
                path.moveTo(6f, 9.5f)
                path.lineTo(6f, 20f)
                path.lineTo(10f, 20f)
                path.lineTo(10f, 14.5f)
                path.lineTo(14f, 14.5f)
                path.lineTo(14f, 20f)
                path.lineTo(18f, 20f)
                path.lineTo(18f, 9.5f)
                canvas.drawPath(path, p)
            }

            IconKind.SLIDERS -> {
                val rows = listOf(6f to 8f, 12f to 16f, 18f to 11f)
                for ((y, x) in rows) {
                    p.style = Paint.Style.STROKE
                    canvas.drawLine(4f, y, 20f, y, p)
                    p.style = Paint.Style.FILL
                    canvas.drawCircle(x, y, 2.5f, p)
                }
            }

            IconKind.GAUGE -> {
                rect.set(3f, 5f, 21f, 23f)
                canvas.drawArc(rect, 180f, 180f, false, p)
                canvas.drawLine(12f, 14f, 16.5f, 9.5f, p)
                p.style = Paint.Style.FILL
                canvas.drawCircle(12f, 14f, 1.7f, p)
            }

            IconKind.SHIELD -> {
                path.reset()
                path.moveTo(12f, 3f)
                path.lineTo(19.5f, 6f)
                path.lineTo(19.5f, 12f)
                path.cubicTo(19.5f, 16.5f, 16f, 19.5f, 12f, 21f)
                path.cubicTo(8f, 19.5f, 4.5f, 16.5f, 4.5f, 12f)
                path.lineTo(4.5f, 6f)
                path.close()
                canvas.drawPath(path, p)
                path.reset()
                path.moveTo(8.5f, 12f)
                path.lineTo(11f, 14.5f)
                path.lineTo(15.5f, 9.5f)
                canvas.drawPath(path, p)
            }

            IconKind.GEAR -> {
                canvas.drawCircle(12f, 12f, 3f, p)
                canvas.drawCircle(12f, 12f, 6.8f, p)
                p.strokeWidth = 2.6f
                for (i in 0 until 8) {
                    canvas.save()
                    canvas.rotate(i * 45f, 12f, 12f)
                    canvas.drawLine(12f, 2.8f, 12f, 5.2f, p)
                    canvas.restore()
                }
            }

            IconKind.ARROW -> {
                path.reset()
                path.moveTo(5f, 12f)
                path.lineTo(19f, 12f)
                path.moveTo(13f, 6f)
                path.lineTo(19f, 12f)
                path.lineTo(13f, 18f)
                canvas.drawPath(path, p)
            }

            IconKind.PLAY -> {
                p.style = Paint.Style.FILL
                path.reset()
                path.moveTo(8f, 5f)
                path.lineTo(19f, 12f)
                path.lineTo(8f, 19f)
                path.close()
                canvas.drawPath(path, p)
            }

            IconKind.STOP -> {
                p.style = Paint.Style.FILL
                canvas.drawRoundRect(6f, 6f, 18f, 18f, 2.5f, 2.5f, p)
            }

            IconKind.EYE -> {
                path.reset()
                path.moveTo(2f, 12f)
                path.quadTo(12f, 3.5f, 22f, 12f)
                path.quadTo(12f, 20.5f, 2f, 12f)
                canvas.drawPath(path, p)
                canvas.drawCircle(12f, 12f, 3.2f, p)
            }

            IconKind.LIST -> {
                for (y in listOf(6f, 12f, 18f)) {
                    p.style = Paint.Style.STROKE
                    canvas.drawLine(9.5f, y, 20f, y, p)
                    p.style = Paint.Style.FILL
                    canvas.drawCircle(5f, y, 1.6f, p)
                }
            }

            IconKind.CHECK -> {
                path.reset()
                path.moveTo(5f, 12.5f)
                path.lineTo(10f, 17.5f)
                path.lineTo(19f, 7f)
                canvas.drawPath(path, p)
            }
        }

        canvas.restore()
    }
}

/** Switch in the style of the design. */
class ToggleView(context: Context) : View(context) {

    var checked: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        trackPaint.color = if (checked) Ui.accent2 else Color.rgb(72, 74, 94)
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, h / 2f, h / 2f, trackPaint)

        val r = h / 2f - h * 0.14f
        val cx = if (checked) w - h / 2f else h / 2f
        canvas.drawCircle(cx, h / 2f, r, knobPaint)
    }
}

/** Slider with gradient track. Calls [onDrag] while dragging and [onCommit] on release. */
class SliderView(context: Context) : View(context) {

    var min = 0f
    var max = 1f
    var step = 1f

    var value = 0f
        set(v) {
            field = v.coerceIn(min, max)
            invalidate()
        }

    var onDrag: ((Float) -> Unit)? = null
    var onCommit: ((Float) -> Unit)? = null

    private val d = context.resources.displayMetrics.density
    private val pad = 16f * d

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(60, 255, 255, 255)
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Ui.alpha(Ui.accent, 60)
    }
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val rect = RectF()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        fillPaint.shader = LinearGradient(
            pad, 0f, maxOf(w - pad, pad + 1f), 0f,
            Ui.accentLight, Ui.accent2,
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        val cy = height / 2f
        val th = 6f * d
        val left = pad
        val right = width - pad

        rect.set(left, cy - th / 2f, right, cy + th / 2f)
        canvas.drawRoundRect(rect, th / 2f, th / 2f, trackPaint)

        val frac = if (max > min) (value - min) / (max - min) else 0f
        val x = left + (right - left) * frac

        rect.set(left, cy - th / 2f, x, cy + th / 2f)
        canvas.drawRoundRect(rect, th / 2f, th / 2f, fillPaint)

        canvas.drawCircle(x, cy, 16f * d, haloPaint)
        canvas.drawCircle(x, cy, 11f * d, knobPaint)
    }

    private fun updateFrom(x: Float) {
        val left = pad
        val right = width - pad
        if (right <= left) return
        val frac = ((x - left) / (right - left)).coerceIn(0f, 1f)
        var v = min + frac * (max - min)
        if (step > 0f) v = min + round((v - min) / step) * step
        value = v
        onDrag?.invoke(value)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                updateFrom(e.x)
            }
            MotionEvent.ACTION_MOVE -> updateFrom(e.x)
            MotionEvent.ACTION_UP -> {
                updateFrom(e.x)
                onCommit?.invoke(value)
            }
            MotionEvent.ACTION_CANCEL -> Unit
        }
        return true
    }
}
