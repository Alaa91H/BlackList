package com.blacklist.app.domain.diagnostics

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.blacklist.app.data.local.BlackListDatabase

data class DiagnosticResult(
    val check: String,
    val status: Status,
    val message: String,
    val fix: String? = null
) {
    enum class Status { PASS, WARNING, FAIL }
}

/** Local-only protection health checks. */
class DiagnosticsService(
    private val context: Context,
    private val db: BlackListDatabase
) {
    suspend fun runDiagnostics(): List<DiagnosticResult> {
        val results = mutableListOf<DiagnosticResult>()

        val roleHeld = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true
        } else {
            false
        }
        results += DiagnosticResult(
            check = "Call screening role",
            status = if (roleHeld) DiagnosticResult.Status.PASS else DiagnosticResult.Status.FAIL,
            message = if (roleHeld) "Call screening role is active." else "Call screening role is not held.",
            fix = if (roleHeld) null else "Choose BlackList as the call screening app in Android settings."
        )

        val contactsGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        results += DiagnosticResult(
            check = "Contacts matching (optional)",
            status = if (contactsGranted) DiagnosticResult.Status.PASS else DiagnosticResult.Status.WARNING,
            message = if (contactsGranted) "Saved-contact matching is available." else "Unsaved-number matching is disabled; other local rules continue working.",
            fix = if (contactsGranted) null else "Grant Contacts permission only if you use the unsaved-number rule."
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notificationsGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            results += DiagnosticResult(
                check = "Blocked-call summaries (optional)",
                status = if (notificationsGranted) DiagnosticResult.Status.PASS else DiagnosticResult.Status.WARNING,
                message = if (notificationsGranted) "Private notification summaries are available." else "Notifications are disabled; blocking continues normally.",
                fix = if (notificationsGranted) null else "Grant Notifications permission if you want summaries."
            )
        }

        try {
            db.blacklistRuleDao().getAll()
            results += DiagnosticResult("Local policy database", DiagnosticResult.Status.PASS, "Local rules database is accessible.")
        } catch (_: Exception) {
            results += DiagnosticResult(
                "Local policy database",
                DiagnosticResult.Status.FAIL,
                "Local rules database could not be read.",
                "Restart the app or restore a manual encrypted backup."
            )
        }

        val powerManager = context.getSystemService(PowerManager::class.java)
        val ignoringOptimization = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
        results += DiagnosticResult(
            "Battery optimization (optional)",
            if (ignoringOptimization) DiagnosticResult.Status.PASS else DiagnosticResult.Status.WARNING,
            if (ignoringOptimization) "Battery optimization exemption is active." else "Most devices work without this exemption.",
            if (ignoringOptimization) null else "Change this only if Android reports a reliability issue."
        )

        val manufacturer = Build.MANUFACTURER.lowercase()
        val oemRisk = when {
            manufacturer.contains("xiaomi") -> DiagnosticResult.Status.WARNING to "This device vendor may apply aggressive background limits."
            manufacturer.contains("huawei") -> DiagnosticResult.Status.WARNING to "This device vendor may apply aggressive background limits."
            else -> DiagnosticResult.Status.PASS to "No vendor-specific reliability warning is configured."
        }
        results += DiagnosticResult("Device compatibility", oemRisk.first, oemRisk.second)

        return results
    }
}
