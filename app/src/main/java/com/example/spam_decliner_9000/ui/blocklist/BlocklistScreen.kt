package com.example.spam_decliner_9000.ui.blocklist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.spam_decliner_9000.data.model.UserListEntry
import com.example.spam_decliner_9000.ui.utils.formatPhoneNumber

@Composable
fun BlocklistScreen(vm: BlocklistViewModel = hiltViewModel()) {
    val blocklist by vm.blocklist.collectAsState()
    val allowlist by vm.allowlist.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddDialog by remember { mutableStateOf(false) }

    val tabs = listOf("Blocklist", "Allowlist")

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add number")
            }
        }
    ) { padding ->
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(padding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            when (selectedTab) {
                0 -> NumberList(
                    entries = blocklist,
                    emptyMessage = "No blocked numbers. Add a number with the + button.",
                    onDelete = { vm.removeFromBlocklist(it) }
                )
                1 -> NumberList(
                    entries = allowlist,
                    emptyMessage = "No allowlisted numbers. Add a number with the + button.",
                    onDelete = { vm.removeFromAllowlist(it) }
                )
            }
        }
    }

    if (showAddDialog) {
        AddNumberDialog(
            tabLabel = tabs[selectedTab],
            onConfirm = { number ->
                if (selectedTab == 0) vm.addToBlocklist(number)
                else vm.addToAllowlist(number)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }
}

@Composable
private fun NumberList(
    entries: List<UserListEntry>,
    emptyMessage: String,
    onDelete: (UserListEntry) -> Unit
) {
    if (entries.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = emptyMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(32.dp)
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(entries, key = { it.id }) { entry ->
            ListItem(
                headlineContent = {
                    // Show contact name as the primary label if available,
                    // otherwise fall back to the raw phone number
                    Text(entry.contactName ?: formatPhoneNumber(entry.phoneNumber))
                },
                supportingContent = {
                    // If a name is shown above, show the number below it
                    if (entry.contactName != null) {
                        Text(formatPhoneNumber(entry.phoneNumber))
                    } else {
                        entry.note?.let { Text(it) }
                    }
                },
                trailingContent = {
                    IconButton(onClick = { onDelete(entry) }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Remove",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        }
    }
}

@Composable
private fun AddNumberDialog(
    tabLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var number by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to $tabLabel") },
        text = {
            Column {
                Text("Enter the phone number to add.")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = number,
                    onValueChange = { number = it; error = false },
                    label = { Text("Phone number") },
                    placeholder = { Text("+14155551234") },
                    isError = error,
                    supportingText = if (error) ({ Text("Please enter a valid number") }) else null,
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (number.isBlank()) { error = true; return@TextButton }
                onConfirm(number)
            }) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
