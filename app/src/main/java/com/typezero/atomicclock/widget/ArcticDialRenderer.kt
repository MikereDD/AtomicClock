package com.typezero.atomicclock.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.typezero.atomicclock.R
import com.typezero.atomicclock.ui.formatOffset
import com.typezero.atomicclock.weather.formatDegrees
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Pixel-owned renderer for the Arctic widget.
 *
 * Android owns only the host rectangle. Arctic owns the visual composition inside
 * the square dial. Every complication is positioned in normalized dial coordinates,
 * so the approved geometry scales as one instrument at every widget size.
 *
 * The live AnalogClock hands are deliberately NOT painted here. They remain a final
 * native layer above this bitmap so hour/minute/second motion stays live without
 * regenerating the face every second.
 */
object ArcticDialRenderer {
    private const val MAX_RENDER_PX = 1024
    private const val MIN_RENDER_PX = 192

    // Canonical normalized geometry. (0.5, 0.5) is the true mechanical pivot.
    private const val WEATHER_X = 0.338f
    private const val HUMIDITY_X = 0.662f
    private const val AXIS_Y = 0.50f
    private const val ICON_CENTER_Y = 0.440f
    private const val VALUE_BASELINE_Y = 0.518f
    private const val LABEL_BASELINE_Y = 0.548f
    private const val DATE_BASELINE_Y = 0.612f
    private const val DRIFT_BASELINE_Y = 0.648f

    private const val ICON_SIZE = 0.055f
    private const val VALUE_TEXT_SIZE = 0.054f
    private const val LABEL_TEXT_SIZE = 0.019f
    private const val DATE_TEXT_SIZE = 0.027f
    private const val DRIFT_TEXT_SIZE = 0.020f
    private const val WEATHER_VALUE_MAX_WIDTH = 0.145f
    private const val WEATHER_LABEL_MAX_WIDTH = 0.165f
    private const val HUMIDITY_VALUE_MAX_WIDTH = 0.150f
    private const val HUMIDITY_LABEL_MAX_WIDTH = 0.165f
    private const val DATE_MAX_WIDTH = 0.265f
    private const val DRIFT_MAX_WIDTH = 0.200f

    fun render(context: Context, snapshot: WidgetSnapshot, requestedSizePx: Int): Bitmap {
        val size = requestedSizePx.coerceIn(MIN_RENDER_PX, MAX_RENDER_PX)
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.TRANSPARENT)

        drawFace(context, canvas, size)


        drawPrecisionSecondScale(canvas, size)
if (snapshot.hasWeather) {
            drawWeather(context, canvas, size, snapshot)
            drawHumidity(context, canvas, size, snapshot)
        }

        drawDateAndDrift(canvas, size, snapshot)
        return out
    }

    /** Retained for old preview/tests that still call the legacy signature. */
    fun render(sizePx: Int, use24: Boolean): Bitmap =
        Bitmap.createBitmap(sizePx.coerceAtLeast(64), sizePx.coerceAtLeast(64), Bitmap.Config.ARGB_8888)

    private fun drawFace(context: Context, canvas: Canvas, size: Int) {
        val face = BitmapFactory.decodeResource(context.resources, R.drawable.widget_arctic_face_master)
        try {
            canvas.drawBitmap(face, null, RectF(0f, 0f, size.toFloat(), size.toFloat()), facePaint)
        } finally {
            face.recycle()
        }
    }


    /**
     * The canonical Midnight face contains the exact timing scale.
     * Runtime timing drawing is intentionally disabled to prevent
     * doubled ticks, masking gaps, and visual drift.
     */
    private fun drawPrecisionSecondScale(canvas: Canvas, size: Int) {
        // Intentionally empty.
    }
    private fun drawWeather(context: Context, canvas: Canvas, size: Int, snapshot: WidgetSnapshot) {
        drawIcon(
            context,
            canvas,
            weatherIcon(snapshot.iconName),
            WEATHER_X * size,
            ICON_CENTER_Y * size,
            ICON_SIZE * size,
        )

        drawCenteredText(
            canvas,
            formatDegrees(snapshot.tempC, snapshot.fahrenheit),
            WEATHER_X * size,
            VALUE_BASELINE_Y * size,
            VALUE_TEXT_SIZE * size,
            Color.rgb(25, 30, 36),
            Typeface.DEFAULT_BOLD,
            WEATHER_VALUE_MAX_WIDTH * size,
        )

        val label = snapshot.label
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            .take(18)
        drawCenteredText(
            canvas,
            label,
            WEATHER_X * size,
            LABEL_BASELINE_Y * size,
            LABEL_TEXT_SIZE * size,
            CYAN,
            mediumTypeface,
            WEATHER_LABEL_MAX_WIDTH * size,
        )
    }

    private fun drawHumidity(context: Context, canvas: Canvas, size: Int, snapshot: WidgetSnapshot) {
        drawIcon(
            context,
            canvas,
            R.drawable.widget_midnight_humidity,
            HUMIDITY_X * size,
            ICON_CENTER_Y * size,
            ICON_SIZE * size,
        )

        drawCenteredText(
            canvas,
            "${snapshot.humidity.coerceIn(0, 100)}%",
            HUMIDITY_X * size,
            VALUE_BASELINE_Y * size,
            VALUE_TEXT_SIZE * size,
            Color.rgb(25, 30, 36),
            Typeface.DEFAULT_BOLD,
            HUMIDITY_VALUE_MAX_WIDTH * size,
        )
        drawCenteredText(
            canvas,
            "Humidity",
            HUMIDITY_X * size,
            LABEL_BASELINE_Y * size,
            LABEL_TEXT_SIZE * size,
            CYAN,
            mediumTypeface,
            HUMIDITY_LABEL_MAX_WIDTH * size,
        )
    }

    private fun drawDateAndDrift(canvas: Canvas, size: Int, snapshot: WidgetSnapshot) {
        val date = SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date())
        drawCenteredText(
            canvas,
            date,
            0.5f * size,
            DATE_BASELINE_Y * size,
            DATE_TEXT_SIZE * size,
            Color.rgb(25, 30, 36),
            mediumTypeface,
            DATE_MAX_WIDTH * size,
        )
        drawCenteredText(
            canvas,
            if (snapshot.hasSync) formatOffset(snapshot.driftMs) else "—",
            0.5f * size,
            DRIFT_BASELINE_Y * size,
            DRIFT_TEXT_SIZE * size,
            CYAN,
            mediumTypeface,
            DRIFT_MAX_WIDTH * size,
        )
    }

    private fun drawIcon(
        context: Context,
        canvas: Canvas,
        drawableRes: Int,
        centerX: Float,
        centerY: Float,
        side: Float,
    ) {
        val bitmap = BitmapFactory.decodeResource(context.resources, drawableRes)
        try {
            val half = side / 2f
            canvas.drawBitmap(
                bitmap,
                null,
                RectF(centerX - half, centerY - half, centerX + half, centerY + half),
                facePaint,
            )
        } finally {
            bitmap.recycle()
        }
    }

    private fun drawCenteredText(
        canvas: Canvas,
        text: String,
        x: Float,
        baselineY: Float,
        sizePx: Float,
        color: Int,
        typeface: Typeface,
        maxWidthPx: Float = Float.POSITIVE_INFINITY,
    ) {
        textPaint.textSize = sizePx
        textPaint.color = color
        textPaint.typeface = typeface
        textPaint.textAlign = Paint.Align.CENTER

        // Arctic owns its geometry. If localized/live text is wider than its
        // allotted complication region, scale the type down rather than allowing
        // Android-host-dependent collisions with the center field or dial rings.
        if (maxWidthPx.isFinite()) {
            val measured = textPaint.measureText(text)
            if (measured > maxWidthPx && measured > 0f) {
                textPaint.textSize = sizePx * (maxWidthPx / measured)
            }
        }

        canvas.drawText(text, x, baselineY, textPaint)
    }

    private fun weatherIcon(name: String): Int = when (name.uppercase(Locale.ROOT)) {
        "SUN" -> R.drawable.widget_midnight_weather_sun
        "MOON" -> R.drawable.widget_midnight_weather_moon
        "FOG" -> R.drawable.widget_midnight_weather_fog
        "RAIN" -> R.drawable.widget_midnight_weather_rain
        "SNOW" -> R.drawable.widget_midnight_weather_snow
        "STORM" -> R.drawable.widget_midnight_weather_storm
        else -> R.drawable.widget_midnight_weather_cloud
    }

    private val facePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val mediumTypeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val CYAN = Color.rgb(0, 102, 204)
}
