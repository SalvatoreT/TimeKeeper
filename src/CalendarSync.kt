package dev.sal.timekeeper

import android.Manifest
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import contacts.core.Contacts
import contacts.core.entities.EventEntity
import contacts.core.equalTo
import contacts.core.invoke
import contacts.core.util.events
import contacts.core.util.names
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.TimeZone

internal const val PREFS_NAME = "timekeeper"
internal const val KEY_LAST_SYNC_AT = "last_sync_at"

private const val CALENDAR_DISPLAY_NAME = "Birthdays"
private const val ACCOUNT_NAME = "TimeKeeper"
private const val OWNER_ACCOUNT = "dev.sal.timekeeper"

private val REQUIRED_PERMISSIONS = arrayOf(
    Manifest.permission.READ_CONTACTS,
    Manifest.permission.READ_CALENDAR,
    Manifest.permission.WRITE_CALENDAR,
)

fun Context.hasSyncPermissions(): Boolean =
    REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

fun Context.loadBirthdayContacts(): List<Contact> =
    Contacts(this)
        .query()
        .where {
            Event { (Event.Type equalTo EventEntity.Type.BIRTHDAY) }
        }.find()
        .mapNotNull { contact ->
            val (year, month, day) =
                contact
                    .events()
                    .find { event -> event.type == EventEntity.Type.BIRTHDAY }
                    ?.date
                    ?.let {
                        arrayOf(it.year ?: 0, it.month, it.dayOfMonth)
                    } ?: return@mapNotNull null
            val name = contact.names().firstOrNull()?.displayName ?: return@mapNotNull null
            Contact(name = name, year = year, month = month, day = day)
        }

suspend fun Context.syncBirthdayCalendar(): Boolean = withContext(Dispatchers.IO) {
    if (!hasSyncPermissions()) return@withContext false

    val calendarId = contentResolver.getOrCreateBirthdayCalendar()
    contentResolver.deleteEventsInCalendar(calendarId)
    loadBirthdayContacts().forEach {
        contentResolver.addBirthdayEvent(
            name = it.name,
            year = it.year,
            month = it.month,
            day = it.day,
            calendarId = calendarId,
        )
    }
    getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putLong(KEY_LAST_SYNC_AT, System.currentTimeMillis())
        .apply()
    true
}

private fun ContentResolver.getOrCreateBirthdayCalendar(): Long =
    getCalendarIdByName(CALENDAR_DISPLAY_NAME) ?: createCalendar()

private fun ContentResolver.getCalendarIdByName(name: String): Long? {
    val projection = arrayOf(
        CalendarContract.Calendars._ID,
        CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
        CalendarContract.Calendars.OWNER_ACCOUNT,
    )
    val selection =
        "${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} = ? AND ${CalendarContract.Calendars.OWNER_ACCOUNT} = ?"
    val selectionArgs = arrayOf(name, OWNER_ACCOUNT)

    val cursor = query(
        CalendarContract.Calendars.CONTENT_URI,
        projection,
        selection,
        selectionArgs,
        null,
    )

    return cursor?.use {
        if (it.moveToFirst()) {
            it.getLong(it.getColumnIndexOrThrow(CalendarContract.Calendars._ID))
        } else {
            null
        }
    }
}

private fun ContentResolver.createCalendar(): Long {
    val values = ContentValues().apply {
        put(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
        put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
        put(CalendarContract.Calendars.NAME, "Birthday Calendar")
        put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CALENDAR_DISPLAY_NAME)
        put(CalendarContract.Calendars.CALENDAR_COLOR, Color.MAGENTA)
        put(
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.CAL_ACCESS_OWNER,
        )
        put(CalendarContract.Calendars.OWNER_ACCOUNT, OWNER_ACCOUNT)
        put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, TimeZone.getDefault().id)
        put(CalendarContract.Calendars.SYNC_EVENTS, 1)
    }

    val uri = CalendarContract.Calendars.CONTENT_URI
        .buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
        .appendQueryParameter(
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.ACCOUNT_TYPE_LOCAL,
        ).build()

    return insert(uri, values)?.lastPathSegment?.toLong() ?: -1
}

private fun ContentResolver.deleteEventsInCalendar(calendarId: Long) {
    delete(
        CalendarContract.Events.CONTENT_URI,
        "${CalendarContract.Events.CALENDAR_ID} = ?",
        arrayOf(calendarId.toString()),
    )
}

private fun ContentResolver.addBirthdayEvent(
    name: String,
    year: Int?,
    month: Int,
    day: Int,
    calendarId: Long,
) {
    val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        set(Calendar.MONTH, month)
        set(Calendar.DAY_OF_MONTH, day)
        if (year != null && year != 0 && year != 1) {
            set(Calendar.YEAR, year)
        } else {
            set(Calendar.YEAR, get(Calendar.YEAR))
        }
    }

    val values = ContentValues().apply {
        put(CalendarContract.Events.DTSTART, calendar.timeInMillis)
        put(CalendarContract.Events.DURATION, "P1D")
        put(CalendarContract.Events.TITLE, "$name's Birthday")
        put(CalendarContract.Events.DESCRIPTION, "Birthday Event")
        put(CalendarContract.Events.CALENDAR_ID, calendarId)
        put(CalendarContract.Events.EVENT_TIMEZONE, calendar.timeZone.id)
        put(CalendarContract.Events.ALL_DAY, 1)
        put(CalendarContract.Events.RRULE, "FREQ=YEARLY")
    }

    insert(CalendarContract.Events.CONTENT_URI, values)
}
