package com.example.spam_decliner_9000.ui.settings

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.spam_decliner_9000.worker.ContactsSyncWorker

@Composable
fun SettingsScreen(vm: SettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val blockUnknown by vm.blockUnknownNumbers.collectAsState()
    val dbCount by vm.spamDatabaseCount.collectAsState()

    // Track whether this app holds the CALL_SCREENING role, re-checked on resume
    // so the UI updates immediately after the user returns from the system prompt.
    val lifecycleOwner = LocalLifecycleOwner.current
    var isDefaultScreeningApp by remember { mutableStateOf(false) }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            isDefaultScreeningApp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(RoleManager::class.java)
                roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
            } else false
        }
    }

    val roleRequestLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            isDefaultScreeningApp = roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        }
    }

    var contactsPermissionDenied by remember { mutableStateOf(false) }

    // Permission launcher — on grant: enable the toggle and kick off an immediate
    // contacts sync so the allowlist is populated right away rather than waiting
    // up to 24 hours for the first scheduled run.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            vm.setBlockUnknownNumbers(true)
            ContactsSyncWorker.runNow(context)
            contactsPermissionDenied = false
        } else {
            contactsPermissionDenied = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp)
    ) {
        // ---- Section: Call Blocking ----
        SectionHeader("Call Blocking")

        ListItem(
            headlineContent = { Text("Default screening app") },
            supportingContent = {
                Text(
                    if (isDefaultScreeningApp)
                        "This app is your active call screener"
                    else
                        "Tap to make this your call screening app"
                )
            },
            trailingContent = {
                if (isDefaultScreeningApp) {
                    Text(
                        "Active",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    FilledTonalButton(onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            val roleManager = context.getSystemService(RoleManager::class.java)
                            roleRequestLauncher.launch(
                                roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
                            )
                        }
                    }) { Text("Set up") }
                }
            }
        )

        ListItem(
            headlineContent = { Text("Block unknown numbers") },
            supportingContent = {
                Text(
                    if (blockUnknown)
                        "Numbers not in your contacts are sent to voicemail"
                    else
                        "Unknown numbers ring through normally"
                )
            },
            trailingContent = {
                Switch(
                    checked = blockUnknown,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                        } else {
                            vm.setBlockUnknownNumbers(false)
                        }
                    }
                )
            }
        )

        if (contactsPermissionDenied) {
            Card(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Contacts permission is required to tell known contacts " +
                                "apart from unknown numbers.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                        )
                    }) { Text("Open Settings") }
                }
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        // ---- Section: Contacts ----
        SectionHeader("Contacts")

        ListItem(
            headlineContent = { Text("Auto-sync contacts to allowlist") },
            supportingContent = {
                Text("Your contacts are automatically added to the allowlist every 24 hours. " +
                        "New contacts are picked up on the next daily run.")
            }
        )

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        // ---- Section: Spam Database ----
        SectionHeader("Spam Database")

        ListItem(
            headlineContent = { Text("FTC complaint entries") },
            supportingContent = { Text("Numbers reported for illegal robocalls") },
            trailingContent = {
                Text(
                    text = if (dbCount > 0) "%,d".format(dbCount) else "Empty",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (dbCount > 0)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )

        ListItem(
            headlineContent = { Text("Sync database now") },
            supportingContent = { Text("Downloads latest FTC data over Wi-Fi") },
            trailingContent = {
                FilledTonalButton(onClick = { vm.syncNow() }) {
                    Text("Sync")
                }
            }
        )

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        // ---- Section: How it works ----
        SectionHeader("How it works")

        val rules = listOf(
            "✅ Personal allowlist — always allowed through",
            "🚫 Android Spam Risk — blocked (no voicemail)",
            "🚫 Personal blocklist — blocked (no voicemail)",
            "🚫 FTC spam database — blocked (no voicemail)",
            "📬 Unknown number toggle — sent to voicemail",
            "✅ Everything else — allowed through"
        )
        rules.forEach { rule ->
            Text(
                text = rule,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}
