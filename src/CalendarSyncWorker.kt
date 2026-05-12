package dev.sal.timekeeper

import android.content.Context
import android.provider.ContactsContract
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class CalendarSyncWorker(
    ctx: Context,
    params: WorkerParameters,
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val outcome = runCatching { applicationContext.syncBirthdayCalendar() }
        // Re-enqueue the one-time content-trigger work so we keep listening for
        // contact changes. Content-URI triggers fire once and must be renewed.
        schedule(applicationContext)
        return outcome.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() },
        )
    }

    companion object {
        private const val UNIQUE_PERIODIC = "birthday-sync-periodic"
        private const val UNIQUE_ON_CHANGE = "birthday-sync-on-change"

        fun schedule(context: Context) {
            val workManager = WorkManager.getInstance(context)

            val periodic = PeriodicWorkRequestBuilder<CalendarSyncWorker>(1, TimeUnit.DAYS)
                .build()
            workManager.enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                periodic,
            )

            val onChange = OneTimeWorkRequestBuilder<CalendarSyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .addContentUriTrigger(ContactsContract.Contacts.CONTENT_URI, true)
                        .build(),
                )
                .build()
            workManager.enqueueUniqueWork(
                UNIQUE_ON_CHANGE,
                ExistingWorkPolicy.REPLACE,
                onChange,
            )
        }
    }
}
