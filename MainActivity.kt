package com.smartguard.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var p: Pal
    private lateinit var gauge: GaugeView
    private lateinit var verdict: TextView
    private lateinit var headSub: TextView
    private lateinit var ram: BarRow
    private lateinit var storage: BarRow
    private lateinit var battery: BarRow
    private lateinit var insights: LinearLayout
    private lateinit var result: TextView
    private lateinit var freedTv: TextView
    private val prefs by lazy { getSharedPreferences("sg", 0) }

    private fun d(v: Int) = Ui.dp(this, v)

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        p = Pal(this)
        window.statusBarColor = p.bg
        window.navigationBarColor = p.bg
        if (!p.dark) window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(d(16), d(36), d(16), d(28))

        // ---- Header ----
        val head = LinearLayout(this)
        head.gravity = Gravity.CENTER_VERTICAL
        val logo = ImageView(this)
        logo.setImageResource(R.drawable.ic_launcher)
        logo.layoutParams = LinearLayout.LayoutParams(d(46), d(46))
        head.addView(logo)
        val ht = LinearLayout(this)
        ht.orientation = LinearLayout.VERTICAL
        ht.setPadding(d(12), 0, 0, 0)
        ht.addView(Ui.text(this, "SmartGuard", 22f, p.text, true))
        headSub = Ui.text(this, "", 13f, p.sub)
        ht.addView(headSub)
        head.addView(ht)
        root.addView(head)

        // ---- Hero: health gauge ----
        val hero = Ui.card(this, p)
        gauge = GaugeView(this, p)
        val gp = LinearLayout.LayoutParams(d(190), d(190))
        gp.gravity = Gravity.CENTER_HORIZONTAL
        gp.topMargin = d(4)
        gauge.layoutParams = gp
        verdict = Ui.text(this, "Scan ho raha hai...", 16f, p.text, true)
        verdict.gravity = Gravity.CENTER
        verdict.setPadding(0, d(6), 0, 0)
        hero.addView(gauge)
        hero.addView(verdict)
        hero.addView(Ui.primary(this, p, "\uD83D\uDD0D  Full Scan") { fullScan() })
        root.addView(hero)

        // ---- Phone ki sehat ----
        val stats = Ui.card(this, p)
        stats.addView(Ui.text(this, "Phone ki sehat", 16f, p.text, true))
        ram = BarRow(this, p, "RAM")
        storage = BarRow(this, p, "Storage")
        battery = BarRow(this, p, "Battery")
        stats.addView(ram.view)
        stats.addView(storage.view)
        stats.addView(battery.view)
        root.addView(stats)

        // ---- Quick actions ----
        val qa = Ui.text(this, "Quick Actions", 16f, p.text, true)
        qa.setPadding(d(4), d(18), 0, 0)
        root.addView(qa)
        root.addView(tileRow(
            Ui.tile(this, p, "\uD83E\uDDF9", "Safe Clean", "Junk saaf") { cleanNow() },
            Ui.tile(this, p, "\u26A1", "RAM Boost", "Background apps") { ramBoost() }))
        root.addView(tileRow(
            Ui.tile(this, p, "\uD83D\uDCC2", "Files Review", "Badi/purani files") { review() },
            Ui.tile(this, p, "\uD83D\uDD75", "App Audit", "Sandigdh apps") { audit() }))

        // ---- Smart insights ----
        insights = Ui.card(this, p)
        root.addView(insights)

        // ---- Protection ----
        val prot = Ui.card(this, p)
        prot.addView(Ui.text(this, "Protection", 16f, p.text, true))
        prot.addView(sw("Auto Protection (hamesha background mein)", "auto") { on -> setAuto(on) })
        prot.addView(sw("Phone 15 min idle ho to background apps band", "idlekill") { })
        root.addView(prot)

        // ---- Activity ----
        val act = Ui.card(this, p)
        act.addView(Ui.text(this, "Activity", 16f, p.text, true))
        result = Ui.text(this, "Abhi koi activity nahi.", 13f, p.sub)
        result.setPadding(0, d(6), 0, 0)
        freedTv = Ui.text(this, "", 13f, p.sub)
        freedTv.setPadding(0, d(4), 0, 0)
        act.addView(result)
        act.addView(freedTv)
        root.addView(act)

        root.addView(Ui.ghost(this, p, "\uD83D\uDD11  Permissions / Setup guide") {
            startActivity(Intent(this, SetupActivity::class.java))
        })
        val foot = Ui.text(this, "SmartGuard offline hai: internet use nahi karti.", 12f, p.sub)
        foot.gravity = Gravity.CENTER
        foot.setPadding(0, d(14), 0, 0)
        root.addView(foot)

        val sv = ScrollView(this)
        sv.setBackgroundColor(p.bg)
        sv.addView(root)
        setContentView(sv)

        if (prefs.getBoolean("auto", true)) startGuard()
    }

    override fun onResume() {
        super.onResume()
        if (!prefs.getBoolean("setup_done", false)) {
            startActivity(Intent(this, SetupActivity::class.java))
            return
        }
        updateHeader()
        freedTv.text = "Ab tak total saaf kiya: " + Cleaner.mb(Smart.totalFreed(this))
        refresh()
    }

    // ---------- helpers ----------
    private fun tileRow(a: View, b: View): LinearLayout {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.addView(a)
        r.addView(b)
        return r
    }

    private fun sw(label: String, key: String, extra: (Boolean) -> Unit): Switch {
        val s = Switch(this)
        s.text = label
        s.textSize = 14f
        s.setTextColor(p.text)
        s.setPadding(0, d(10), 0, d(4))
        s.isChecked = prefs.getBoolean(key, true)
        s.setOnCheckedChangeListener { _, on ->
            prefs.edit().putBoolean(key, on).apply()
            extra(on)
        }
        return s
    }

    private fun startGuard() {
        try { startForegroundService(Intent(this, GuardService::class.java)) } catch (e: Exception) { }
    }

    private fun setAuto(on: Boolean) {
        if (on) startGuard() else stopService(Intent(this, GuardService::class.java))
        updateHeader()
    }

    private fun updateHeader() {
        val on = prefs.getBoolean("auto", true)
        headSub.text = if (on) "\u25CF Protection ON" else "\u25CB Protection OFF"
        headSub.setTextColor(if (on) p.good else p.sub)
    }

    private fun say(s: String) { runOnUiThread { result.text = s } }

    private fun usedColor(u: Int): Int = if (u < 70) p.good else if (u < 88) p.warn else p.bad

    // ---------- dashboard refresh ----------
    private fun refresh() {
        Thread {
            val s = Health.snap(this)
            val score = Health.report(this).first
            val access = Health.hasUsageAccess(this)
            val top: List<Smart.AppUse>? = if (access) {
                try { Smart.topApps(this, 5) } catch (e: Exception) { emptyList<Smart.AppUse>() }
            } else null
            val unused: List<Smart.AppUse>? = if (access) {
                try { Smart.unusedApps(this, 30) } catch (e: Exception) { emptyList<Smart.AppUse>() }
            } else null
            runOnUiThread { render(s, score, top, unused) }
        }.start()
    }

    private fun render(s: Health.Snap, score: Int, top: List<Smart.AppUse>?, unused: List<Smart.AppUse>?) {
        gauge.setScore(score, p.scoreColor(score), "HEALTH")
        verdict.text = if (score >= 80) "Phone ki condition: Achhi \u2705"
        else if (score >= 55) "Phone ki condition: Theek-thaak \uD83D\uDFE1"
        else "Phone ki condition: Kharab \uD83D\uDD34"
        ram.set(s.ramUsed, usedColor(s.ramUsed), "${s.ramUsed}% use mein")
        storage.set(s.storageUsed, usedColor(s.storageUsed), "${s.storageUsed}% bhara")
        battery.set(s.battery, if (s.battery < 20) p.bad else p.good, "${s.battery}%  \u2022  ${s.temp}\u00B0C")
        fillInsights(top, unused)
    }

    private fun fillInsights(top: List<Smart.AppUse>?, unused: List<Smart.AppUse>?) {
        insights.removeAllViews()
        insights.addView(Ui.text(this, "Smart Insights", 16f, p.text, true))
        if (top == null || unused == null) {
            val t = Ui.text(this, "Aaj kaun si apps zyada chali aur kaun si kai din se use nahi hui, ye dekhne ke liye Usage Access allow karein.", 13f, p.sub)
            t.setPadding(0, d(6), 0, 0)
            insights.addView(t)
            insights.addView(Ui.ghost(this, p, "\uD83D\uDCCA  Usage Access allow karein") {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            })
            return
        }
        val h = Ui.text(this, "Aaj sabse zyada use hui apps", 13f, p.sub)
        h.setPadding(0, d(6), 0, d(2))
        insights.addView(h)
        if (top.isEmpty()) insights.addView(Ui.text(this, "Abhi koi data nahi.", 14f, p.text))
        for (a in top) insights.addView(Ui.text(this, "\u2022 ${a.label}  -  ${Smart.fmt(a.millis)}", 14f, p.text))
        if (unused.isNotEmpty()) {
            insights.addView(Ui.ghost(this, p, "\uD83D\uDDD1  ${unused.size} apps 30+ din se use nahi hui - dekhein") { showUnused(unused) })
        }
    }

    // ---------- actions ----------
    private fun fullScan() {
        say("Scan ho raha hai...")
        Thread {
            val rep = Health.report(this)
            val score = rep.first
            val junk = if (Cleaner.canAccessShared(this)) Cleaner.findSafeJunk().sumOf { it.size } else -1L
            val txt = "Health Score: $score/100\n\n" + rep.second.joinToString("\n") +
                "\n\nJunk files: " + (if (junk >= 0L) Cleaner.mb(junk) else "(Files access permission chahiye)")
            runOnUiThread {
                result.text = "Last scan: score $score/100"
                AlertDialog.Builder(this).setTitle("Phone Health Report").setMessage(txt).setPositiveButton("OK", null).show()
            }
            refresh()
        }.start()
    }

    private fun cleanNow() {
        say("Safai chal rahi hai...")
        Thread {
            var f = Cleaner.lightClean(this)
            if (Cleaner.canAccessShared(this)) f += Cleaner.cleanSafeShared()
            Smart.addFreed(this, f)
            runOnUiThread {
                result.text = "\u2705 ${Cleaner.mb(f)} junk saaf hua. Photos/videos/documents safe hain."
                freedTv.text = "Ab tak total saaf kiya: " + Cleaner.mb(Smart.totalFreed(this))
            }
        }.start()
    }

    private fun ramBoost() {
        Thread {
            val free = Smart.ramFreePct(this)
            if (free >= 40) {
                say("RAM pehle se theek hai ($free% free). Boost ki zaroorat nahi, isse phone aur slow ho sakta tha.")
                return@Thread
            }
            say("Boost ho raha hai...")
            val r = Smart.boost(this)
            val gain = r.second - r.first
            if (gain > 0) say("\u26A1 RAM free: ${r.first} MB \u2192 ${r.second} MB (+$gain MB). ${r.third} background apps band hui.")
            else say("${r.third} background apps band ki, par RAM mein zyada farak nahi aaya. Phone restart karna behtar rahega.")
            refresh()
        }.start()
    }

    private fun review() {
        if (!Cleaner.canAccessShared(this)) {
            startActivity(Intent(this, SetupActivity::class.java))
            return
        }
        say("Files dhoondh raha hoon...")
        Thread {
            val items = Cleaner.findNeedsReview(this)
            runOnUiThread {
                if (items.isEmpty()) {
                    result.text = "Koi file review ke liye nahi mili \u2705"
                    return@runOnUiThread
                }
                result.text = "${items.size} items review ke liye mile."
                val names = items.map { "[${it.label}] ${it.file.name} (${Cleaner.mb(it.size)})" }.toTypedArray()
                val checked = BooleanArray(items.size)
                AlertDialog.Builder(this).setTitle("Jo delete karna hai wo chunein")
                    .setMultiChoiceItems(names, checked) { _, i, v -> checked[i] = v }
                    .setPositiveButton("Delete") { _, _ ->
                        var freed = 0L
                        items.forEachIndexed { i, f -> if (checked[i] && f.file.deleteRecursively()) freed += f.size }
                        Smart.addFreed(this, freed)
                        result.text = "Delete hua: ${Cleaner.mb(freed)}"
                    }
                    .setNegativeButton("Rehne do", null).show()
            }
        }.start()
    }

    private fun audit() {
        say("Apps check ho rahi hain...")
        Thread {
            val list = Audit.scan(this)
            runOnUiThread {
                if (list.isEmpty()) {
                    result.text = "Koi sandigdh app nahi mili \u2705"
                    return@runOnUiThread
                }
                result.text = "${list.size} apps par dhyan dein."
                AlertDialog.Builder(this).setTitle("Sandigdh apps (tap = uninstall)")
                    .setItems(list.map { "${it.label}\n${it.why}" }.toTypedArray()) { _, i ->
                        startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${list[i].pkg}")))
                    }
                    .setNegativeButton("Band karein", null).show()
            }
        }.start()
    }

    private fun showUnused(list: List<Smart.AppUse>) {
        AlertDialog.Builder(this).setTitle("30+ din se use nahi hui (${list.size})")
            .setItems(list.map { it.label }.toTypedArray()) { _, i ->
                startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${list[i].pkg}")))
            }
            .setNegativeButton("Band karein", null).show()
    }
}
