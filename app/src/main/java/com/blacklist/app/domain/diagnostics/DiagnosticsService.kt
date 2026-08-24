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

class DiagnosticsService(
    private val context: Context,
    private val db: BlackListDatabase
) {
    suspend fun runDiagnostics(): List<DiagnosticResult> {
        val results = mutableListOf<DiagnosticResult>()
        // Call Screening
        val roleHeld = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = context.getSystemService(RoleManager::class.java)
            rm?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true
        } else false
        results.add(DiagnosticResult("Call Screening", if (roleHeld) DiagnosticResult.Status.PASS else DiagnosticResult.Status.FAIL, if (roleHeld) "Role granted" else "Call screening role not held", "Grant via Settings"))

        // Permissions
        listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_PHONE_STATE).forEach { perm ->
            val granted = ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
            results.add(DiagnosticResult("Permission $perm", if (granted) DiagnosticResult.Status.PASS else DiagnosticResult.Status.WARNING, if (granted) "Granted" else "Not granted", if (!granted) "Grant permission" else null))
        }

        // Database
        try {
            db.blockedNumberDao().getAll()
            results.add(DiagnosticResult("Database", DiagnosticResult.Status.PASS, "Room accessible"))
        } catch (e: Exception) {
            results.add(DiagnosticResult("Database", DiagnosticResult.Status.FAIL, "Error: ${e.message}"))
        }

        // Battery optimization
        val pm = context.getSystemService(PowerManager::class.java)
        val ignoring = pm?.isIgnoringBatteryOptimizations(context.packageName) == true
        results.add(DiagnosticResult("Battery Optimization", if (ignoring) DiagnosticResult.Status.PASS else DiagnosticResult.Status.WARNING, if (ignoring) "Ignoring optimizations" else "Battery optimization active", "Disable battery optimization for reliable screening"))

        // Backend availability
        results.add(DiagnosticResult("Root Backend", DiagnosticResult.Status.WARNING, "Optional - not required", "Install Magisk for privileged enforcement"))
        results.add(DiagnosticResult("Shizuku Backend", DiagnosticResult.Status.WARNING, "Optional - not required", "Install Shizuku"))

        // OEM
        val manufacturer = Build.MANUFACTURER.lowercase()
        val oemRisk = when {
            manufacturer.contains("xiaomi") -> DiagnosticResult.Status.WARNING to "Xiaomi MIUI aggressive battery"
            manufacturer.contains("samsung") -> DiagnosticResult.Status.PASS to "Samsung OneUI OK"
            manufacturer.contains("huawei") -> DiagnosticResult.Status.WARNING to "Huawei EMUI background limits"
            else -> DiagnosticResult.Status.PASS to "Standard Android"
        }
        results.add(DiagnosticResult("OEM Compatibility (${Build.MANUFACTURER})", oemRisk.first, oemRisk.second))

        return results
    }
}
