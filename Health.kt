package com.smartguard.app

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.PowerManager
import android.os.Process
import android.os.StatFs
import android.os.SystemClock

object Health {
    @Suppress("DEPRECATION")
    fun hasUsageAccess(c: Context): Boolean {
        val ops = c.getSystemService(AppOpsManager::class.java)
        return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName) == AppOpsManager.MODE_ALLOWED
    }

    /** Returns (score 0-100, report lines). */
    fun report(c: Context): Pair<Int, List<String>> {
        var score = 100
        val r = mutableListOf<String>()

        val mi = ActivityManager.MemoryInfo()
        c.getSystemService(ActivityManager::class.java).getMemoryInfo(mi)
        val ramFree = mi.availMem * 100 / mi.totalMem
        r += "RAM: $ramFree% free (${mi.availMem shr 20} / ${mi.totalMem shr 20} MB)"
        if (ramFree < 15) { score -= 25; r += "⚠ RAM bahut kam hai: bahut saari apps background mein chal rahi hain. Phone hang hone ki sabse badi wajah ye hai." }

        val sf = StatFs(Environment.getDataDirectory().path)
        val stFree = sf.availableBytes * 100 / sf.totalBytes
        r += "Storage: $stFree% free (${sf.availableBytes shr 30} GB bacha)"
        if (stFree < 10) { score -= 25; r += "⚠ Storage lagbhag full hai: isse phone slow hota hai. Badi files/purani APK hatayein." }

        val b = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (b != null) {
            val temp = b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
            val lvl = b.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
            r += "Battery: $lvl%, temperature $temp°C"
            if (temp > 42) { score -= 20; r += "⚠ Phone garam hai: garmi se processor speed kam kar deta hai (throttling)." }
        }
        if (c.getSystemService(PowerManager::class.java).isPowerSaveMode) {
            score -= 10; r += "⚠ Battery Saver ON hai: performance limit ho jaati hai."
        }

        val days = SystemClock.elapsedRealtime() / 86_400_000
        r += "Last restart ko $days din hue"
        if (days >= 7) { score -= 10; r += "⚠ Phone 7+ din se restart nahi hua. Ek baar restart karein." }

        if (hasUsageAccess(c)) {
            val usm = c.getSystemService(UsageStatsManager::class.java)
            val now = System.currentTimeMillis()
            val recent = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 600_000, now)
                .count { it.lastTimeUsed > now - 600_000 && it.packageName != c.packageName }
            r += "Pichhle 10 min mein $recent apps active rahi"
            if (recent > 12) { score -= 10; r += "⚠ Bahut saari apps ek saath use hui hain." }
        } else r += "ℹ Usage Access dene par recent-app details bhi dikhengi."

        r += when { score >= 85 -> "✅ Phone ki condition: ACHHI"; score >= 60 -> "🟡 Phone ki condition: THEEK-THAAK"; else -> "🔴 Phone ki condition: KHARAB, neeche diye gaye sujhav follow karein" }
        return score.coerceAtLeast(0) to r
    }

    data class Snap(val ramUsed: Int, val storageUsed: Int, val battery: Int, val temp: Float)

    fun snap(c: Context): Snap {
        val mi = ActivityManager.MemoryInfo()
        c.getSystemService(ActivityManager::class.java).getMemoryInfo(mi)
        val ramUsed = (100 - mi.availMem * 100 / mi.totalMem).toInt()
        val sf = StatFs(Environment.getDataDirectory().path)
        val stUsed = (100 - sf.availableBytes * 100 / sf.totalBytes).toInt()
        val b = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val lvl = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
        val t = (b?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        return Snap(ramUsed, stUsed, lvl, t)
    }
}
