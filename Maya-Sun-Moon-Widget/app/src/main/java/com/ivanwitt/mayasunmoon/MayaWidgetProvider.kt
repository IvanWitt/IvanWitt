package com.ivanwitt.mayasunmoon

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import java.time.Instant
import java.time.ZoneId
import kotlin.math.min

class MayaWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
        scheduleNext(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateWidgets(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        AstroSyncJobService.scheduleIfNeeded(context)
        scheduleNext(context)
    }

    override fun onDisabled(context: Context) {
        cancelScheduled(context)
        super.onDisabled(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_REFRESH,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                updateAll(context)
                scheduleNext(context)
                return
            }
        }
        super.onReceive(context, intent)
    }

    companion object {
        const val ACTION_REFRESH = "com.ivanwitt.mayasunmoon.REFRESH_WIDGET"
        private const val REQUEST_REFRESH = 4107
        private const val REFRESH_INTERVAL_MS = 15 * 60 * 1000L
        private const val TAG = "MayaWidgetProvider"

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, MayaWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            updateWidgets(context, manager, ids)
        }

        private fun updateWidgets(
            context: Context,
            manager: AppWidgetManager,
            ids: IntArray
        ) {
            if (ids.isEmpty()) return

            val settings = WidgetPrefs.load(context)
            val now = System.currentTimeMillis()
            val zone = ZoneId.systemDefault()

            AstroSyncJobService.scheduleIfNeeded(context)
            val networkCache = SkyScheduleStore.loadUsable(context, settings, zone, now)
            val snapshot = AstroEngine.snapshot(settings, now, zone, networkCache)
            val localDate = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            val mayaDate = MayaCalendar.fromGregorian(localDate, settings.correlation)

            ids.forEach { id ->
                // Important for launchers that enter placement mode before the first expensive render:
                // publish a visible RemoteViews immediately, so the widget is never an invisible drag target.
                showPlaceholder(context, manager, id)

                val options = manager.getAppWidgetOptions(id)
                val density = context.resources.displayMetrics.density
                val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 300)
                val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 190)

                // The launcher scales the bitmap to the widget bounds. Capping render resolution makes the
                // first placement fast and avoids oversized RemoteViews bitmap payloads on high-density phones.
                val requestedWidthPx = (widthDp * density).toInt().coerceAtLeast(480)
                val requestedHeightPx = (heightDp * density).toInt().coerceAtLeast(300)
                val widthPx = min(requestedWidthPx, 760)
                val heightPx = min(requestedHeightPx, 520)

                val bitmapResult = runCatching {
                    val foreground = WidgetRenderer.render(
                        width = widthPx,
                        height = heightPx,
                        settings = settings,
                        snapshot = snapshot,
                        mayaDate = mayaDate,
                        nowMillis = now,
                        zone = zone
                    )
                    DynamicScenery.compose(
                        width = widthPx,
                        height = heightPx,
                        snapshot = snapshot,
                        foreground = foreground
                    )
                }

                bitmapResult.onSuccess { bitmap ->
                    val views = RemoteViews(context.packageName, R.layout.maya_widget)
                    views.setImageViewBitmap(R.id.widget_image, bitmap)
                    views.setViewVisibility(R.id.widget_loading, View.GONE)
                    attachSettingsClick(context, views, id)
                    manager.updateAppWidget(id, views)
                }.onFailure { error ->
                    Log.e(TAG, "Unable to render widget $id", error)
                    // Leave the visible placeholder on the desktop instead of making the widget disappear.
                    val views = RemoteViews(context.packageName, R.layout.maya_widget)
                    views.setViewVisibility(R.id.widget_loading, View.VISIBLE)
                    views.setTextViewText(R.id.widget_loading, "Maya Sun/Moon\nНажмите для настроек")
                    attachSettingsClick(context, views, id)
                    manager.updateAppWidget(id, views)
                }
            }
        }

        private fun showPlaceholder(context: Context, manager: AppWidgetManager, id: Int) {
            val views = RemoteViews(context.packageName, R.layout.maya_widget)
            views.setViewVisibility(R.id.widget_loading, View.VISIBLE)
            views.setTextViewText(R.id.widget_loading, "Maya Sun/Moon")
            attachSettingsClick(context, views, id)
            manager.updateAppWidget(id, views)
        }

        private fun attachSettingsClick(context: Context, views: RemoteViews, id: Int) {
            val openSettings = Intent(context, MainActivity::class.java)
            val openPending = PendingIntent.getActivity(
                context,
                id,
                openSettings,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, openPending)
        }

        private fun scheduleNext(context: Context) {
            val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarm.set(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + REFRESH_INTERVAL_MS,
                refreshPendingIntent(context)
            )
        }

        private fun cancelScheduled(context: Context) {
            val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarm.cancel(refreshPendingIntent(context))
        }

        private fun refreshPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, MayaWidgetProvider::class.java).setAction(ACTION_REFRESH)
            return PendingIntent.getBroadcast(
                context,
                REQUEST_REFRESH,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
