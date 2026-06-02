package com.example.spam_decliner_9000.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.spam_decliner_9000.data.model.BlockedCallEntry
import java.text.SimpleDateFormat
import java.util.*

private enum class Filter { ALL, BLOCKED, ALLOWED }

@Composable
fun HistoryScreen(vm: HistoryViewModel = hiltViewModel()) {
    val calls by vm.calls.collectAsState()
    var filter by remember { mutableStateOf(Filter.ALL) }

    val visible = when (filter) {
        Filter.ALL     -> calls
        Filter.BLOCKED -> calls.filter { it.outcome != "allowed" }
        Filter.ALLOWED -> calls.filter { it.outcome == "allowed" }
    }

    Column(Modifier.fillMaxSize()) {
        FilterChipRow(filter, onFilterChange = { filter = it })

        if (visible.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (calls.isEmpty()) "No calls logged yet"
                    else "No calls match this filter",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(visible, key = { it.id }) { entry ->
                    CallRow(
                        entry = entry,
                        onBlock = { vm.addToBlocklist(entry) },
                        onAllow = { vm.addToAllowlist(entry) }
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun FilterChipRow(current: Filter, onFilterChange: (Filter) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Filter.entries.forEach { f ->
            FilterChip(
                selected = current == f,
                onClick = { onFilterChange(f) },
                label = { Text(f.name.lowercase().replaceFirstChar { it.uppercase() }) }
            )
        }
    }
}

@Composable
private fun CallRow(
    entry: BlockedCallEntry,
    onBlock: () -> Unit,
    onAllow: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val isAllowed = entry.outcome == "allowed"

    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.phoneNumber,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutcomeChip(entry.outcome)
                    Text(
                        text = sourceLabel(entry.source),
                        style = MaterialTheme.typography.bodySmall,
                        color = sourceColor(entry.source)
                    )
                }
                Text(
                    text = formatTime(entry.blockedAtMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!isAllowed) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Less" else "Actions")
                }
            }
        }

        if (expanded && !isAllowed) {
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = { onBlock(); expanded = false }) {
                    Text("Add to Blocklist")
                }
                OutlinedButton(onClick = { onAllow(); expanded = false }) {
                    Text("Add to Allowlist")
                }
            }
        }
    }
}

@Composable
private fun OutcomeChip(outcome: String) {
    val (label, color) = when (outcome) {
        "blocked"  -> "Blocked"  to MaterialTheme.colorScheme.error
        "voicemail"-> "Voicemail" to MaterialTheme.colorScheme.secondary
        "allowed"  -> "Allowed"  to MaterialTheme.colorScheme.primary
        else       -> outcome    to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.12f),
        contentColor = color,
        tonalElevation = 0.dp
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun sourceColor(source: String) = when (source) {
    "android_spam_risk"  -> MaterialTheme.colorScheme.error
    "personal_blocklist" -> MaterialTheme.colorScheme.error
    "spam_database"      -> MaterialTheme.colorScheme.tertiary
    "unknown_number"     -> MaterialTheme.colorScheme.secondary
    "allowlist"          -> MaterialTheme.colorScheme.primary
    "contact"            -> MaterialTheme.colorScheme.primary
    "default_allow"      -> MaterialTheme.colorScheme.onSurfaceVariant
    else                 -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun sourceLabel(source: String) = when (source) {
    "android_spam_risk"  -> "Android Spam Risk"
    "personal_blocklist" -> "Personal Blocklist"
    "spam_database"      -> "FTC Spam Database"
    "unknown_number"     -> "Unknown Number"
    "allowlist"          -> "Your Allowlist"
    "contact"            -> "In Contacts"
    "default_allow"      -> "Default Allow"
    else                 -> source
}

private fun formatTime(ms: Long): String {
    val fmt = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    return fmt.format(Date(ms))
}
