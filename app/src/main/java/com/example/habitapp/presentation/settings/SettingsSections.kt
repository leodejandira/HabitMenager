package com.example.habitapp.presentation.settings

import android.Manifest
import android.app.AlarmManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.habitapp.R
import com.example.habitapp.domain.model.CalendarView
import com.example.habitapp.presentation.components.ConfirmDialog
import com.example.habitapp.presentation.theme.HabitTheme
import java.time.LocalDate

@Composable
internal fun InfoText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = HabitTheme.spacing.screenHorizontal, vertical = HabitTheme.spacing.xs),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CalendarViewSelector(selected: CalendarView, onSelected: (CalendarView) -> Unit) {
    val spacing = HabitTheme.spacing
    Text(
        stringResource(R.string.settings_default_calendar_view),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(horizontal = spacing.screenHorizontal, vertical = spacing.xs),
    )
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = spacing.screenHorizontal, vertical = spacing.sm)) {
        CalendarView.entries.forEachIndexed { index, view ->
            SegmentedButton(
                selected = view == selected,
                onClick = { onSelected(view) },
                shape = SegmentedButtonDefaults.itemShape(index, CalendarView.entries.size),
            ) {
                Text(
                    stringResource(
                        when (view) {
                            CalendarView.MONTH -> R.string.calendar_month
                            CalendarView.WEEK -> R.string.calendar_week
                            CalendarView.DAY -> R.string.calendar_day
                        },
                    ),
                )
            }
        }
    }
}

/** Re-reads permission state whenever the screen resumes (the user may change it in system settings). */
@Composable
private fun rememberResumeCounter(): Int {
    val owner = LocalLifecycleOwner.current
    var counter by remember { mutableIntStateOf(0) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) counter++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return counter
}

private fun Context.openSafely(intent: Intent) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Log.w("Settings", "No activity for ${intent.action}", e)
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NotificationSettings() {
    val context = LocalContext.current
    val resumes = rememberResumeCounter()
    var requested by remember { mutableIntStateOf(0) }
    val notificationsOn = remember(resumes, requested) { NotificationManagerCompat.from(context).areNotificationsEnabled() }
    val exactOn = remember(resumes) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { requested++ }
    val spacing = HabitTheme.spacing

    Column(Modifier.padding(horizontal = spacing.screenHorizontal), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Text(
            stringResource(if (notificationsOn) R.string.settings_notifications_on else R.string.settings_notifications_off),
            style = MaterialTheme.typography.bodyLarge,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            if (!notificationsOn && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                OutlinedButton(onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                    Text(stringResource(R.string.settings_allow_notifications))
                }
            }
            OutlinedButton(onClick = {
                context.openSafely(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                )
            }) { Text(stringResource(R.string.settings_open_notification_settings)) }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Text(
                stringResource(if (exactOn) R.string.settings_exact_on else R.string.settings_exact_off),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!exactOn) {
                OutlinedButton(onClick = {
                    context.openSafely(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                }) { Text(stringResource(R.string.settings_allow_exact)) }
            }
        }
        Text(
            stringResource(R.string.settings_reminder_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun LanguageSettings() {
    val context = LocalContext.current
    val spacing = HabitTheme.spacing
    Column(Modifier.padding(horizontal = spacing.screenHorizontal), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Text(stringResource(R.string.settings_language_summary), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = {
                context.openSafely(Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.parse("package:${context.packageName}")))
            }) { Text(stringResource(R.string.settings_choose_language)) }
        } else {
            Text(stringResource(R.string.settings_language_device), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DataSettings(busy: Boolean, actions: SettingsActions) {
    val spacing = HabitTheme.spacing
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { actions.onExport(it.toString()) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pendingImport = uri
    }
    Column(Modifier.padding(horizontal = spacing.screenHorizontal), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Text(stringResource(R.string.settings_backup_summary), style = MaterialTheme.typography.bodyMedium)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            OutlinedButton(enabled = !busy, onClick = { exportLauncher.launch("steady-backup-${LocalDate.now()}.json") }) {
                Text(stringResource(R.string.settings_export))
            }
            OutlinedButton(enabled = !busy, onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) {
                Text(stringResource(R.string.settings_import))
            }
            OutlinedButton(enabled = !busy, onClick = { confirmReset = true }) {
                Text(stringResource(R.string.settings_reset), color = MaterialTheme.colorScheme.error)
            }
        }
    }
    pendingImport?.let { uri ->
        ConfirmDialog(
            title = stringResource(R.string.import_confirm_title),
            message = stringResource(R.string.import_confirm_message),
            confirmLabel = stringResource(R.string.settings_import),
            onConfirm = { actions.onImport(uri.toString()) },
            onDismiss = { pendingImport = null },
        )
    }
    if (confirmReset) {
        ConfirmDialog(
            title = stringResource(R.string.reset_confirm_title),
            message = stringResource(R.string.reset_confirm_message),
            confirmLabel = stringResource(R.string.settings_reset),
            onConfirm = actions.onReset,
            onDismiss = { confirmReset = false },
        )
    }
}
