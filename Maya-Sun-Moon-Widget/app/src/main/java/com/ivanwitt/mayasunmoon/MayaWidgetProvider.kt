package com.ivanwitt.mayasunmoon

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews
import java.time.Instant
import java.time.ZoneId

class MayaWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
        scheduleNext(context)
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

            // This only schedules a system job when the 72-hour cache is stale/missing.
            // The job itself is network-constrained, so it waits silently if the phone is offline.
            AstroSyncJobService.scheduleIfNeeded(context)
            val networkCache = SkyScheduleStore.loadUsable(context, settings, zone, now)

            val snapshot = AstroEngine.snapshot(settings, now, zone, networkCache)
            val localDate = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            val mayaDate = MayaCalendar.fromGregorian(localDate, settings.correlation)

            ids.forEach { id ->
                val options = manager.getAppWidgetOptions(id)
                val density = context.resources.displayMetrics.density
                val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 300)
                val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 190)
                val widthPx = (widthDp * density).toInt().coerceAtLeast(480)
                val heightPx = (heightDp * density).toInt().coerceAtLeast(300)

                // Keep the existing clock/calendar renderer untouched as the foreground.  The new
                // scenery is composed underneath it, so v0.2.11 remains easy to restore and the
                // day/night experiment is isolated from the proven layout/astronomy code.
                val foreground = WidgetRenderer.render(
                    width = widthPx,
                    height = heightPx,
                    settings = settings,
                    snapshot = snapshot,
                    mayaDate = mayaDate,
                    nowMillis = now,
                    zone = zone
                )
                val bitmap = DynamicScenery.compose(
                    width = widthPx,
                    height = heightPx,
                    snapshot = snapshot,
                    foreground = foreground
                )

                val views = RemoteViews(context.packageName, R.layout.maya_widget)
                views.setImageViewBitmap(R.id.widget_image, bitmap)

                val openSettings = Intent(context, MainActivity::class.java)
                val openPending = PendingIntent.getActivity(
                    context,
                    id,
                    openSettings,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_root, openPending)

                manager.updateAppWidget(id, views)
            }
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
