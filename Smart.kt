package com.smartguard.app

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import java.util.Calendar

/** Smart features: heavy apps, unused apps, RAM boost, total-cleaned counter. */
object Smart {
    data class AppUse(val pkg: String, val label: String, val millis: Long)

    private fun label(c: Context, pkg: String): String? {
        return try {
            val pm = c.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) { null }
    }

    fun fmt(ms: Long): String =
        if (ms >= 3_600_000L) "${ms / 3_600_000L}h ${ms % 3_600_000L / 60_000L}m" else "${maxOf(1L, ms / 60_000L)} min"

    /** Aaj sabse zyada use hui apps (Usage Access chahiye). */
    fun topApps(c: Context, n: Int = 5): List<AppUse> {
        val usm = c.getSystemService(UsageStatsManager::class.java)
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
        val agg = usm.queryAndAggregateUsageStats(cal.timeInMillis, System.currentTimeMillis())
        val out = ArrayList<AppUse>()
        for (s in agg.values) {
            val pkg = s.packageName
            if (s.totalTimeInForeground < 60_000L || pkg == c.packageName) continue
            if (pkg == "com.android.systemui" || pkg.contains("launcher")) continue
            val l = label(c, pkg) ?: continue
            out.add(AppUse(pkg, l, s.totalTimeInForeground))
        }
        return out.sortedByDescending { it.millis }.take(n)
    }

    /** Pichhle [days] din se jo apps khuli hi nahi (foreground use ke hisaab se). */
    fun unusedApps(c: Context, days: Int = 30): List<AppUse> {
        val usm = c.getSystemService(UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        val from = now - days * 86_400_000L
        val agg = usm.queryAndAggregateUsageStats(from, now)
        val pm = c.packageManager
        val out = ArrayList<AppUse>()
        for (ai in pm.getInstalledApplications(0)) {
            if ((ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0 || ai.packageName == c.packageName) continue
            val used = agg[ai.packageName]?.totalTimeInForeground ?: 0L
            if (used > 0L) continue
            val installed = try { pm.getPackageInfo(ai.packageName, 0).firstInstallTime } catch (e: Exception) { now }
            if (installed > from) continue   // nayi installed app ko unused na maanein
            out.add(AppUse(ai.packageName, pm.getApplicationLabel(ai).toString(), 0L))
        }
        return out.sortedBy { it.label.lowercase() }
    }

    private fun avail(c: Context): Long {
        val mi = ActivityManager.MemoryInfo()
        c.getSystemService(ActivityManager::class.java).getMemoryInfo(mi)
        return mi.availMem
    }

    fun ramFreePct(c: Context): Int {
        val mi = ActivityManager.MemoryInfo()
        c.getSystemService(ActivityManager::class.java).getMemoryInfo(mi)
        return (mi.availMem * 100 / mi.totalMem).toInt()
    }

    /**
     * Manual RAM Boost. Chalti hui (foreground service wali: music, call, navigation) apps band nahi hoti,
     * aur pichhle 5 minute mein use hui apps ko bhi chhoda jaata hai (agar Usage Access mili ho).
     * Returns Triple(pehle MB, baad MB, band hui apps).
     */
    fun boost(c: Context): Triple<Long, Long, Int> {
        val am = c.getSystemService(ActivityManager::class.java)
        val before = avail(c)
        val keep = HashSet<String>()
        if (Health.hasUsageAccess(c)) {
            val usm = c.getSystemService(UsageStatsManager::class.java)
            val now = System.currentTimeMillis()
            for (s in usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 3_600_000L, now)) {
                if (s.lastTimeUsed > now - 5 * 60_000L) keep.add(s.packageName)
            }
        }
        var n = 0
        for (ai in c.packageManager.getInstalledApplications(0)) {
            if ((ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0 || ai.packageName == c.packageName) continue
            if (ai.packageName in keep) continue
            am.killBackgroundProcesses(ai.packageName)
            n++
        }
        Thread.sleep(1500)
        return Triple(before shr 20, avail(c) shr 20, n)
    }

    fun addFreed(c: Context, bytes: Long) {
        if (bytes <= 0L) return
        val sp = c.getSharedPreferences("sg", 0)
        sp.edit().putLong("freed", sp.getLong("freed", 0L) + bytes).apply()
    }

    fun totalFreed(c: Context): Long = c.getSharedPreferences("sg", 0).getLong("freed", 0L)
}
