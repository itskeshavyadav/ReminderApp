package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import androidx.glance.appwidget.updateAll
import com.example.MainActivity
import com.example.R
import com.example.data.local.AppDatabase
import com.example.widget.glance.RemindersGlanceWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object WidgetUpdateHelper {

    fun updateAllWidgets(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Update Glance Widget
                RemindersGlanceWidget().updateAll(context)
            } catch (e: Exception) {
                // Ignore if glance widgets are not currently placed
            }

            // Update Standard RemoteViews Widget
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, ReminderAppWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

            if (appWidgetIds.isEmpty()) return@launch

            val db = AppDatabase.getDatabase(context)
            val activeReminders = db.reminderDao().getTopUpcomingRemindersSync()
            val totalActive = db.reminderDao().getAllActiveRemindersSync().size

            val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

            for (widgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_reminder_layout)

                // Open App Intent
                val openIntent = Intent(context, MainActivity::class.java).apply {
                    putExtra("open_add_reminder", true)
                }
                val openPending = PendingIntent.getActivity(
                    context, 0, openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_root, openPending)
                views.setOnClickPendingIntent(R.id.widget_btn_add, openPending)

                views.setTextViewText(R.id.widget_pending_count, "$totalActive Due")

                if (activeReminders.isNotEmpty()) {
                    val r1 = activeReminders[0]
                    val r1Nag = if (r1.isNagging) "⚡ " else ""
                    views.setTextViewText(R.id.widget_item_title_1, "$r1Nag${r1.title}")
                    views.setTextViewText(R.id.widget_item_time_1, timeFormat.format(Date(r1.dueTimestamp)))
                    views.setViewVisibility(R.id.widget_item_1, View.VISIBLE)
                } else {
                    views.setTextViewText(R.id.widget_item_title_1, "No pending reminders")
                    views.setTextViewText(R.id.widget_item_time_1, "All clear")
                    views.setViewVisibility(R.id.widget_item_1, View.VISIBLE)
                }

                if (activeReminders.size > 1) {
                    val r2 = activeReminders[1]
                    val r2Nag = if (r2.isNagging) "⚡ " else ""
                    views.setTextViewText(R.id.widget_item_title_2, "$r2Nag${r2.title}")
                    views.setTextViewText(R.id.widget_item_time_2, timeFormat.format(Date(r2.dueTimestamp)))
                    views.setViewVisibility(R.id.widget_item_2, View.VISIBLE)
                } else {
                    views.setViewVisibility(R.id.widget_item_2, View.GONE)
                }

                appWidgetManager.updateAppWidget(widgetId, views)
            }
        }
    }
}
