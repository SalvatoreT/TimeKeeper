package dev.sal.timekeeper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import contacts.core.Contacts
import contacts.core.entities.EventEntity
import contacts.core.entities.toDisplayString
import contacts.core.equalTo
import contacts.core.invoke
import contacts.core.util.events
import contacts.core.util.names


@Composable
fun Screen(contacts: List<Contact>) {
    MaterialTheme {
        Scaffold { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                contacts.forEachIndexed { index, it ->
                    ListItem(
                        headlineContent = { Text("${it.firstName} ${it.lastName} ${it.birthdate}") },
                        leadingContent = {
                            Icon(
                                if (index % 2 == 0) Icons.Filled.AccountCircle else Icons.Outlined.AccountCircle,
                                contentDescription = "Localized description",
                            )
                        }
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
        val contactList = Contacts(this)
            .query()
            .where {
                Event { (Event.Type equalTo EventEntity.Type.BIRTHDAY) }
            }
            .find()
            .map { contact ->
                Contact(
                    firstName = contact.names().first().givenName ?: "",
                    lastName = contact.names().first().familyName ?: "",
                    birthdate = contact.events()
                        .find { event -> event.type == EventEntity.Type.BIRTHDAY }
                        ?.date
                        ?.toDisplayString()
                        ?: ""
                )
            }
        setContent {
            Screen(contactList)
        }
    }
}