package com.smartguard.app

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.Settings

object Audit {
    data class Risky(val pkg: String, val label: String, val why: String, val score: Int)

    private val risky = setOf(
        "android.permission.READ_SMS", "android.permission.RECEIVE_SMS", "android.permission.SEND_SMS",
        "android.permission.RECORD_AUDIO", "android.permission.READ_CONTACTS", "android.permission.CAMERA",
        "android.permission.ACCESS_FINE_LOCATION", "android.permission.SYSTEM_ALERT_WINDOW",
        "android.permission.READ_CALL_LOG", "android.permission.REQUEST_INSTALL_PACKAGES"
    )
    private val trustedStores = setOf("com.android.vending", "com.sec.android.app.samsungapps", "com.amazon.venezia", "com.heytap.market", "com.xiaomi.mipicks")

    @Suppress("DEPRECATION")
    fun scan(c: Context): List<Risky> {
        val pm = c.packageManager
        val a11y = (Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: "")
        val out = mutableListOf<Risky>()
        for (p in pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)) {
            val ai = p.applicationInfo ?: continue
            if (ai.flags and ApplicationInfo.FLAG_SYSTEM != 0 || p.packageName == c.packageName) continue
            val perms = p.requestedPermissions?.count { it in risky } ?: 0
            val src = try { pm.getInstallerPackageName(p.packageName) } catch (e: Exception) { null }
            val side = src == null || src !in trustedStores
            val a11 = a11y.contains(p.packageName)
            val s = perms + (if (side) 2 else 0) + (if (a11) 4 else 0)
            if (s >= 5) {
                val why = buildList {
                    if (side) add("unknown source")
                    if (a11) add("Accessibility access ON (screen padh sakti hai)")
                    if (perms > 0) add("$perms sensitive permissions")
                }.joinToString(", ")
                out += Risky(p.packageName, pm.getApplicationLabel(ai).toString(), why, s)
            }
        }
        return out.sortedByDescending { it.score }
    }
}
