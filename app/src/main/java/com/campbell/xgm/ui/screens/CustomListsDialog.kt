package com.campbell.xgm.ui.screens

import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.campbell.xgm.domain.services.SafetyInterceptor
import com.campbell.xgm.domain.services.SpeedBoostManager
import com.campbell.xgm.ui.components.AlienButton

@Composable
fun CustomListsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val boostManager = remember { SpeedBoostManager(context) }
    var customLists by remember { mutableStateOf(boostManager.getCustomLists()) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingList by remember { mutableStateOf<Pair<String, List<String>>?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = "Custom Boost Lists",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxHeight(0.6f)) {
                if (customLists.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No custom lists yet.\nCreate one to boost specific app groups.",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(customLists.entries.toList()) { (name, packages) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${packages.size} apps",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }
                                AlienButton(
                                    text = "Boost",
                                    onClick = {
                                        val appsToKill = boostManager.getAppsToKillForList(name)
                                        if (appsToKill.isNotEmpty()) {
                                            SafetyInterceptor.startForceStop(appsToKill)
                                            boostManager.markAsClosed(appsToKill)
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                AlienButton(
                                    text = "Edit",
                                    onClick = { editingList = name to packages }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                AlienButton(
                                    text = "Del",
                                    isDanger = true,
                                    onClick = {
                                        boostManager.deleteCustomList(name)
                                        customLists = boostManager.getCustomLists()
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                AlienButton(
                    text = "Create New List",
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            AlienButton(text = "Done", onClick = onDismiss)
        }
    )

    if (showCreateDialog) {
        CreateEditListDialog(
            initialName = null,
            initialPackages = emptyList(),
            onDismiss = { showCreateDialog = false },
            onSave = { name, packages ->
                boostManager.saveCustomList(name, packages)
                customLists = boostManager.getCustomLists()
                showCreateDialog = false
            }
        )
    }

    editingList?.let { (name, packages) ->
        CreateEditListDialog(
            initialName = name,
            initialPackages = packages,
            onDismiss = { editingList = null },
            onSave = { newName, newPackages ->
                if (newName != name) {
                    boostManager.deleteCustomList(name)
                }
                boostManager.saveCustomList(newName, newPackages)
                customLists = boostManager.getCustomLists()
                editingList = null
            }
        )
    }
}

@Composable
private fun CreateEditListDialog(
    initialName: String?,
    initialPackages: List<String>,
    onDismiss: () -> Unit,
    onSave: (String, List<String>) -> Unit
) {
    val context = LocalContext.current
    var listName by remember { mutableStateOf(initialName ?: "") }
    var selectedPackages by remember { mutableStateOf(initialPackages.toSet()) }
    var searchQuery by remember { mutableStateOf("") }
    var installedApps by remember { mutableStateOf<List<Triple<String, String, android.graphics.drawable.Drawable>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val pm = context.packageManager
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                .filter { it.packageName != context.packageName }
                .map { app ->
                    Triple(
                        app.packageName,
                        pm.getApplicationLabel(app).toString(),
                        pm.getApplicationIcon(app)
                    )
                }
                .sortedBy { it.second }
            installedApps = apps
            isLoading = false
        }
    }

    val filteredApps = remember(searchQuery, installedApps) {
        if (searchQuery.isBlank()) installedApps
        else installedApps.filter {
            it.second.contains(searchQuery, ignoreCase = true) ||
                    it.first.contains(searchQuery, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = if (initialName != null) "Edit List" else "Create List",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxHeight(0.7f)) {
                OutlinedTextField(
                    value = listName,
                    onValueChange = { listName = it },
                    label = { Text("List name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.secondary,
                        cursorColor = MaterialTheme.colorScheme.secondary
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search apps...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.secondary,
                        cursorColor = MaterialTheme.colorScheme.secondary
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${selectedPackages.size} apps selected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (isLoading) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(filteredApps) { (pkg, name, _) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedPackages = if (pkg in selectedPackages) {
                                            selectedPackages - pkg
                                        } else {
                                            selectedPackages + pkg
                                        }
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = pkg in selectedPackages,
                                    onCheckedChange = { checked ->
                                        selectedPackages = if (checked) {
                                            selectedPackages + pkg
                                        } else {
                                            selectedPackages - pkg
                                        }
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = MaterialTheme.colorScheme.secondary
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = name,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = pkg,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            AlienButton(
                text = "Save",
                onClick = { onSave(listName, selectedPackages.toList()) },
                isEnabled = listName.isNotBlank()
            )
        },
        dismissButton = {
            AlienButton(text = "Cancel", onClick = onDismiss)
        }
    )
}
