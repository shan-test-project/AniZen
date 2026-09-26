package mihon.feature.announcements

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import eu.kanade.tachiyomi.R
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object AnnouncementNotifications {
    private const val CHANNEL_ID = "announcements_release_date"
    private const val NOTIFICATION_ID_BASE = 90_000
    private var channelEnsured = false

    @Synchronized
    private fun ensureChannel(context: Context) {
        if (channelEnsured || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Announcement release dates",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Notifies when a watchlisted announcement gets a confirmed release date"
        }
        context.getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)
        channelEnsured = true
    }

    fun notifyReleaseDateConfirmed(
        entry: AnnouncementEntry,
        context: Context = Injekt.get(),
    ) {
        ensureChannel(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("${entry.title} — release date confirmed")
            .setContentText(entry.description)
            .setSmallIcon(R.drawable.ic_info_24dp)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(
            NOTIFICATION_ID_BASE + entry.mediaId,
            notification,
        )
    }
}