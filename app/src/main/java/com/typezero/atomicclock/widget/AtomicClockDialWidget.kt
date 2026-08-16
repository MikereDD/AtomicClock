package com.typezero.atomicclock.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.widget.RemoteViews
import com.typezero.atomicclock.MainActivity
import com.typezero.atomicclock.R
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Midnight precision Dial widget.
 *
 * Android owns the host rectangle. Atomic Clock owns the pixels inside it.
 * The face and every complication are rendered by [MidnightDialRenderer] from one
 * normalized coordinate system; the native AnalogClock is retained only as the
 * final live-hand layer.
 */
open class AtomicClockDialWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        WidgetWork.ensureScheduled(context)
        WidgetWork.refreshNow(context, WidgetRefreshWorker.REASON_WIDGET)
        ids.forEach { render(context, manager, it) }
    }

    override fun onEnabled(context: Context) {
        WidgetWork.ensureScheduled(context)
        WidgetWork.refreshNow(context, WidgetRefreshWorker.REASON_WIDGET)
    }

    override fun onDisabled(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val anyLeft = listOf(
            AtomicClockWidgetSmall::class.java,
            AtomicClockWidgetLarge::class.java,
            AtomicClockDialWidgetCompact::class.java,
            AtomicClockDialWidget::class.java,
            AtomicClockDialWidgetLarge::class.java,
        ).any { manager.getAppWidgetIds(ComponentName(context, it)).isNotEmpty() }
        if (!anyLeft) WidgetWork.cancel(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        newOptions: Bundle,
    ) {
        render(context, manager, id)
    }

    companion object {
        private val dialProviders = listOf(
            AtomicClockDialWidgetCompact::class.java,
            AtomicClockDialWidget::class.java,
            AtomicClockDialWidgetLarge::class.java,
        )

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            dialProviders.forEach { provider ->
                manager.getAppWidgetIds(ComponentName(context, provider))
                    .forEach { render(context, manager, it) }
            }
        }

        private fun render(context: Context, manager: AppWidgetManager, id: Int) {
            val snapshot = WidgetStore.load(context)
            val options = manager.getAppWidgetOptions(id)

            val views = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                responsiveViews(context, snapshot, options)
            } else {
                val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 220).toFloat()
                val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 220).toFloat()
                makeViews(context, snapshot, widthDp, heightDp)
            }

            manager.updateAppWidget(id, views)
        }

        private fun responsiveViews(
            context: Context,
            snapshot: WidgetSnapshot,
            options: Bundle,
        ): RemoteViews {
            val sizes = options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
                .orEmpty()
                .filter { it.width > 0f && it.height > 0f }

            if (sizes.isEmpty()) {
                val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 220).toFloat()
                val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 220).toFloat()
                return makeViews(context, snapshot, widthDp, heightDp)
            }

            // RemoteViews' responsive map lets the launcher choose the matching bitmap for
            // each real host size/orientation instead of forcing provider-specific tiers.
            val mapping = LinkedHashMap<SizeF, RemoteViews>()
            sizes.distinctBy { "${it.width.roundToInt()}x${it.height.roundToInt()}" }
                .forEach { size ->
                    mapping[size] = makeViews(context, snapshot, size.width, size.height)
                }
            return RemoteViews(mapping)
        }

        private fun makeViews(
            context: Context,
            snapshot: WidgetSnapshot,
            hostWidthDp: Float,
            hostHeightDp: Float,
        ): RemoteViews {
            val squareDp = min(hostWidthDp, hostHeightDp).coerceAtLeast(48f)
            val density = context.resources.displayMetrics.density
            val renderPx = (squareDp * density).roundToInt()
            val (layoutRes, composite) = when (snapshot.dialTheme) {
                DialTheme.ARCTIC -> R.layout.widget_dial_arctic to
                    ArcticDialRenderer.render(context, snapshot, renderPx)
                DialTheme.RETRO_BRASS -> R.layout.widget_dial_retrobrass to
                    RetroBrassDialRenderer.render(context, snapshot, renderPx)
                else -> R.layout.widget_dial_midnight to
                    MidnightDialRenderer.render(context, snapshot, renderPx)
            }

            return RemoteViews(context.packageName, layoutRes).apply {
                setInt(R.id.dial_root, "setBackgroundResource", backgroundRes(snapshot.bgLevel))
                setImageViewBitmap(R.id.dial_composite, composite)
                setOnClickPendingIntent(R.id.dial_root, launchIntent(context))
            }
        }

        private fun backgroundRes(level: Int): Int = when (level) {
            WidgetBackground.SOLID.ordinal -> R.drawable.widget_bg_solid
            WidgetBackground.CLEAR.ordinal -> R.drawable.widget_bg_clear
            else -> R.drawable.widget_bg_translucent
        }

        private fun launchIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return PendingIntent.getActivity(
                context,
                40,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
