package com.smartguard.app

import android.app.*
import android.content.*
import android.content.pm.ApplicationInfo
import android.content.pm.ServiceInfo
import android.os.*

class GuardService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var count = 0
    private var screenOffAt = 0L
    private var killedThisOff = false
    private var lastMsg = "Protection active"
    private var deepPending = false
    private var lastAlert = 0L
    private val busy = java.util.concurrent.atomic.AtomicBoolean(false)

    private val screenRx = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (i.action == Intent.ACTION_SCREEN_OFF) { screenOffAt = System.currentTimeMillis(); killedThisOff = false }
            else { screenOffAt = 0; killedThisOff = false }
        }
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("sg", "SmartGuard", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel("sg_alert", "Alerts", NotificationManager.IMPORTANCE_DEFAULT))
        registerReceiver(screenRx, IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON) })
        if (!getSystemService(PowerManager::class.java).isInteractive) screenOffAt = System.currentTimeMillis()
    }

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        val n = notif()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
        handler.removeCallbacksAndMessages(null)
        handler.post { tick() }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        try { unregisterReceiver(screenRx) } catch (e: Exception) {}
        super.onDestroy()
    }

    /** Smart alert: storage/RAM bahut kam ho to notification (2 ghante mein ek baar se zyada nahi). */
    private fun maybeAlert() {
        val now = System.currentTimeMillis()
        if (now - lastAlert < 2 * 3_600_000L) return
        val mi = ActivityManager.MemoryInfo()
        getSystemService(ActivityManager::class.java).getMemoryInfo(mi)
        val ram = mi.availMem * 100 / mi.totalMem
        val sf = StatFs(Environment.getDataDirectory().path)
        val st = sf.availableBytes * 100 / sf.totalBytes
        val msg: String = if (st < 8) "Storage lagbhag full hai ($st% free). Phone slow ho sakta hai - Files Review kholein."
        else if (ram < 8) "RAM bahut kam hai ($ram% free). RAM Boost try karein."
        else return
        val pi = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, "sg_alert")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("SmartGuard alert")
            .setContentText(msg).setContentIntent(pi).setAutoCancel(true).build()
        getSystemService(NotificationManager::class.java).notify(2, n)
        lastAlert = now
    }

    private fun notif(): Notification {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, "sg")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("SmartGuard chal raha hai")
            .setContentText(lastMsg).setContentIntent(pi).setOngoing(true).build()
    }

    /** Har 1 minute mein chalta hai. */
    private fun tick() {
        Thread {
            if (!busy.compareAndSet(false, true)) return@Thread   // pichhla kaam chal raha ho to overlap nahi
            try {
                count++
                var freed = Cleaner.lightClean(this)
                // Poore storage ka safe-junk scan har 60 minute mein, aur sirf jab screen band ho (phone use mein na ho)
                if (count % 60 == 0) deepPending = true
                if (deepPending && screenOffAt > 0 && Cleaner.canAccessShared(this)) { freed += Cleaner.cleanSafeShared(); deepPending = false }
                Smart.addFreed(this, freed)
                maybeAlert()
                val killed = maybeKillIdle()
                lastMsg = "Cleaned ${Cleaner.mb(freed)}" + if (killed > 0) " • $killed idle apps band" else ""
                getSystemService(NotificationManager::class.java).notify(1, notif())
            } catch (e: Exception) {
            } finally { busy.set(false) }
        }.start()
        handler.postDelayed({ tick() }, 60_000)
    }

    /**
     * Apps tabhi band hoti hain jab screen 15 minute se band ho (phone use mein nahi).
     * Use ke dauran ya multiple apps khuli hon tab kuch bhi turant band nahi hota.
     */
    private fun maybeKillIdle(): Int {
        if (!getSharedPreferences("sg", 0).getBoolean("idlekill", true)) return 0
        if (screenOffAt == 0L || killedThisOff) return 0
        if (System.currentTimeMillis() - screenOffAt < 15 * 60_000) return 0
        killedThisOff = true
        val am = getSystemService(ActivityManager::class.java)
        var n = 0
        for (ai in packageManager.getInstalledApplications(0)) {
            if (ai.flags and ApplicationInfo.FLAG_SYSTEM != 0 || ai.packageName == packageName) continue
            am.killBackgroundProcesses(ai.packageName); n++
        }
        return n
    }
}
