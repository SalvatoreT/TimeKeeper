package dev.sal.timekeeper

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.Manifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PermissionScreen(content: @Composable (List<Contact>) -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity

    val requiredPermissions =
        arrayOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR,
        )

    var allPermissionsGranted by remember {
        mutableStateOf(
            requiredPermissions.all { permission ->
                ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            },
        )
    }

    var permissionsDeniedPermanently by remember { mutableStateOf(false) }

    val permissionsLauncher =
        rememberLauncherForActivityResult(
            RequestMultiplePermissions(),
        ) { permissions ->
            allPermissionsGranted = permissions.values.all { it }

            if (!allPermissionsGranted) {
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
        LaunchedEffect(Unit) {
            CalendarSyncWorker.schedule(context.applicationContext)
        }
        val contactList by produceState(initialValue = emptyList()) {
            value = withContext(Dispatchers.IO) { context.loadBirthdayContacts() }
        }
        content(contactList)
    } else {
        MaterialTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (permissionsDeniedPermanently) {
                        Text("Permissions have been denied permanently. Please enable them in the app settings.")
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = {
                            val intent =
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                            context.startActivity(intent)
                        }) {
                            Text("Open App Settings")
                        }
                    } else {
                        Text("This app requires Contacts and Calendar permissions to function properly.")
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = {
                            permissionsLauncher.launch(requiredPermissions)
                        }) {
                            Text("Grant Permissions")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberLastSyncedAt(): Long {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    var lastSyncedAt by remember { mutableLongStateOf(prefs.getLong(KEY_LAST_SYNC_AT, 0L)) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
            if (key == KEY_LAST_SYNC_AT) {
                lastSyncedAt = sp.getLong(KEY_LAST_SYNC_AT, 0L)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return lastSyncedAt
}

private fun formatLastSynced(timestamp: Long): String {
    if (timestamp <= 0L) return "Last synced: never"
    val relative = DateUtils.getRelativeTimeSpanString(
        timestamp,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
    )
    return "Last synced: $relative"
}

@Composable
fun Screen(contacts: List<Contact>) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val lastSyncedAt = rememberLastSyncedAt()
    MaterialTheme {
        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(all = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Button(onClick = {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Syncing calendar...")
                        }
                        coroutineScope.launch {
                            val ok = context.syncBirthdayCalendar()
                            snackbarHostState.showSnackbar(
                                if (ok) "Calendar synced" else "Sync failed — check permissions",
                            )
                        }
                    }) {
                        Text("Sync now")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = formatLastSynced(lastSyncedAt),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
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
                if (contacts.isEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Warning, contentDescription = "warning icon")
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("No contacts found. Please update your address book.")
                    }
                } else {
                    contacts.forEachIndexed { index, it ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (index % 2 == 0) Icons.Filled.AccountCircle else Icons.Outlined.AccountCircle,
                                contentDescription = "account icon",
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("${it.name} ${it.month + 1}/${it.day}${if ((it.year ?: 0) > 1) "/" + it.year else ""}")
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PermissionScreen { contactList ->
                Screen(contactList)
            }
        }
    }
}
