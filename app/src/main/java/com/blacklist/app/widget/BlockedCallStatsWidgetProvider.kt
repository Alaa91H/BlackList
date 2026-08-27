package com.blacklist.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.blacklist.app.MainActivity
import com.blacklist.app.R
import com.blacklist.app.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Compact local-only view of blocked-call activity. Database reads occur on an
 * I/O dispatcher and never participate in Telecom's call-response path.
 */
class BlockedCallStatsWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        refreshFromReceiver(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val component = ComponentName(appContext, BlockedCallStatsWidgetProvider::class.java)
            refreshFromReceiver(appContext, manager, manager.getAppWidgetIds(component))
        }
    }

    private fun refreshFromReceiver(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        if (widgetIds.isEmpty()) return
        val pendingResult = goAsync()
        launchRefresh(context.applicationContext, manager, widgetIds) { pendingResult.finish() }
    }

    companion object {
        private const val ACTION_REFRESH = "com.blacklist.app.widget.REFRESH_STATS"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** Safe to call after post-decision logging; it does not delay call screening. */
        fun refreshAll(context: Context) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val component = ComponentName(appContext, BlockedCallStatsWidgetProvider::class.java)
            launchRefresh(appContext, manager, manager.getAppWidgetIds(component))
        }

        private fun launchRefresh(
            context: Context,
            manager: AppWidgetManager,
            widgetIds: IntArray,
            onComplete: () -> Unit = {}
        ) {
            if (widgetIds.isEmpty()) {
                onComplete()
                return
            }
            scope.launch {
                try {
                    val logs = ServiceLocator.provideDatabase(context).blockedCallLogDao()
                    val today = logs.countSince(BlockedCallStats.startOfDayMillis(System.currentTimeMillis()))
                    val total = logs.count()
                    widgetIds.forEach { widgetId ->
                        manager.updateAppWidget(widgetId, remoteViews(context, today, total))
                    }
                } finally {
                    onComplete()
                }
            }
        }

        private fun remoteViews(context: Context, today: Int, total: Int): RemoteViews =
            RemoteViews(context.packageName, R.layout.widget_blocked_call_stats).apply {
                setTextViewText(R.id.widget_blocked_today_value, today.toString())
                setTextViewText(R.id.widget_total_blocked_value, total.toString())
                setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
                setOnClickPendingIntent(R.id.widget_refresh, refreshIntent(context))
            }

        private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_OPEN_APP,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        private fun refreshIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_REFRESH,
            Intent(context, BlockedCallStatsWidgetProvider::class.java).setAction(ACTION_REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        private const val REQUEST_OPEN_APP = 8101
        private const val REQUEST_REFRESH = 8102
    }
}
