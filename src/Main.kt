package dev.sal.timekeeper

import android.app.Activity
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import contacts.core.Contacts
import contacts.core.entities.EventEntity
import contacts.core.equalTo
import contacts.core.invoke
import contacts.core.util.events
import contacts.core.util.names
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

@Composable
fun PermissionScreen(content: @Composable (List<Contact>) -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity

    // Permissions we need
    val requiredPermissions =
        arrayOf(
            android.Manifest.permission.READ_CONTACTS,
            android.Manifest.permission.READ_CALENDAR,
            android.Manifest.permission.WRITE_CALENDAR,
        )

    // State to hold whether permissions are granted
    var allPermissionsGranted by remember {
        mutableStateOf(
            requiredPermissions.all { permission ->
                ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            },
        )
    }

    // State to hold whether permissions are denied permanently
    var permissionsDeniedPermanently by remember { mutableStateOf(false) }

    // Launcher to request permissions
    val permissionsLauncher =
        rememberLauncherForActivityResult(
            RequestMultiplePermissions(),
        ) { permissions ->
            // Update the state based on whether permissions are granted
            allPermissionsGranted = permissions.values.all { it }

            if (!allPermissionsGranted) {
                // Check if any permission is denied permanently
                permissionsDeniedPermanently =
                    permissions.entries.any { (permission, isGranted) ->
                        !isGranted &&
                            activity?.let {
                                !ActivityCompat.shouldShowRequestPermissionRationale(it, permission)
                            } ?: false
                    }
            }
        }

    if (allPermissionsGranted) {
        // Fetch contacts and display main content
        val contactList by produceState(initialValue = emptyList()) {
            value =
                Contacts(context)
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
                        Contact(
                            name = name,
                            year = year,
                            month = month,
                            day = day,
                        )
                    }
        }

        content(contactList)
    } else if (permissionsDeniedPermanently) {
        // Show message and button to open app settings
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Permissions have been denied permanently. Please enable them in the app settings.")
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = {
                // Open app settings
                val intent =
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                context.startActivity(intent)
            }) {
                Text("Open App Settings")
            }
        }
    } else {
        // Show a button to request permissions
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("This app requires Contacts and Calendar permissions to function properly.")
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = {
                // Request permissions
                permissionsLauncher.launch(requiredPermissions)
            }) {
                Text("Grant Permissions")
            }
        }
    }
}

@Composable
fun Screen(contacts: List<Contact>) {
    val contentResolver = LocalContext.current.contentResolver
    val coroutineScope = rememberCoroutineScope()
    MaterialTheme {
        Scaffold(
            bottomBar = {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(all = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    Button(onClick = {
                        coroutineScope.launch(Dispatchers.IO) {
                            val calendarId = contentResolver.getOrCreateCalendar()
                            contacts.forEach {
                                contentResolver.addBirthdayEvent(
                                    name = it.name,
                                    year = it.year,
                                    month = it.month,
                                    day = it.day,
                                    calendarId = calendarId,
                                )
                            }
                        }
                    }) {
                        Text("Create Calendars")
                    }
                    Button(onClick = {
                        coroutineScope.launch(Dispatchers.IO) {
                            contentResolver.deletePreviousCalendars()
                        }
                    }) {
                        Text("Delete Calendars")
                    }
                }
            },
        ) { padding ->
            Column(
                modifier =
                    Modifier
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                contacts.forEachIndexed { index, it ->
                    ListItem(
                        headlineContent = {
                            Text("${it.name} ${it.month + 1}/${it.day}${if ((it.year ?: 0) > 1) "/" + it.year else ""}")
                        },
                        leadingContent = {
                            Icon(
                                if (index % 2 == 0) Icons.Filled.AccountCircle else Icons.Outlined.AccountCircle,
                                contentDescription = "Localized description",
                            )
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PermissionScreen { contactList ->
                Screen(contactList)
            }
        }
    }
}

private fun ContentResolver.getOrCreateCalendar(): Long {
    val calendarId = getCalendarIdByName("Birthdays")
    return calendarId ?: createCalendar()
}

private fun ContentResolver.getCalendarIdByName(name: String): Long? {
    val projection =
        arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.OWNER_ACCOUNT,
        )
    val selection =
        "${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} = ? AND ${CalendarContract.Calendars.OWNER_ACCOUNT} = ?"
    val selectionArgs = arrayOf(name, "dev.sal.timekeeper")

    val cursor =
        this.query(
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
    val values =
        ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, "TimeKeeper")
            put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(CalendarContract.Calendars.NAME, "Birthday Calendar")
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "Birthdays")
            put(CalendarContract.Calendars.CALENDAR_COLOR, Color.MAGENTA)
            put(
                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                CalendarContract.Calendars.CAL_ACCESS_OWNER,
            )
            put(CalendarContract.Calendars.OWNER_ACCOUNT, "dev.sal.timekeeper")
            put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, TimeZone.getDefault().id)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
        }

    val uri =
        CalendarContract.Calendars.CONTENT_URI
            .buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, "TimeKeeper")
            .appendQueryParameter(
                CalendarContract.Calendars.ACCOUNT_TYPE,
                CalendarContract.ACCOUNT_TYPE_LOCAL,
            ).build()

    val newUri = this.insert(uri, values)
    return newUri?.lastPathSegment?.toLong() ?: -1
}

private fun ContentResolver.addBirthdayEvent(
    name: String,
    year: Int?,
    month: Int,
    day: Int,
    calendarId: Long,
) {
    val calendar =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(Calendar.MONTH, month) // Months are 0-based in Calendar
            set(Calendar.DAY_OF_MONTH, day)
            if (year != null && year != 0 && year != 1) {
                set(Calendar.YEAR, year)
            } else {
                // If year is not provided, use the current year
                set(Calendar.YEAR, get(Calendar.YEAR))
            }
        }

    val values =
        ContentValues().apply {
            put(CalendarContract.Events.DTSTART, calendar.timeInMillis)
            put(CalendarContract.Events.DURATION, "P1D")
            put(CalendarContract.Events.TITLE, "$name's Birthday")
            put(CalendarContract.Events.DESCRIPTION, "Birthday Event")
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.EVENT_TIMEZONE, calendar.timeZone.id)
            put(CalendarContract.Events.ALL_DAY, 1) // Make it an all-day event
            put(CalendarContract.Events.RRULE, "FREQ=YEARLY") // Set the event to recur yearly
        }

    insert(CalendarContract.Events.CONTENT_URI, values)
}

private fun ContentResolver.deletePreviousCalendars() {
    val selection =
        "${CalendarContract.Calendars.ACCOUNT_NAME} = ? AND ${CalendarContract.Calendars.ACCOUNT_TYPE} = ?"
    val selectionArgs = arrayOf("TimeKeeper", CalendarContract.ACCOUNT_TYPE_LOCAL)

    val cursor =
        this.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID),
            selection,
            selectionArgs,
            null,
        )

    cursor?.use {
        while (it.moveToNext()) {
            val calendarId = it.getLong(it.getColumnIndexOrThrow(CalendarContract.Calendars._ID))
            val deleteUri =
                CalendarContract.Calendars.CONTENT_URI
                    .buildUpon()
                    .appendPath(calendarId.toString())
                    .build()
            this.delete(deleteUri, null, null)
        }
    }
}
