package com.smartguard.app

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView

/** Pehli baar app kholne par permissions ek-ek karke, wajah ke saath maangta hai. */
class SetupActivity : Activity() {
    private lateinit var p: Pal
    private lateinit var list: LinearLayout

    private fun d(v: Int) = Ui.dp(this, v)

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        p = Pal(this)
        window.statusBarColor = p.bg
        window.navigationBarColor = p.bg
        if (!p.dark) window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(d(16), d(40), d(16), d(28))
        root.addView(Ui.text(this, "Shuru karne se pehle", 26f, p.text, true))
        val intro = Ui.text(this, "SmartGuard ko neeche ki permissions chahiye. Har ek ke saath likha hai ki kyon. Aap inhe kabhi bhi Settings se band kar sakte hain. App internet use nahi karti aur aapka koi data kahin nahi bhejti.", 14f, p.sub)
        intro.setPadding(0, d(8), 0, d(4))
        root.addView(intro)
        list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        root.addView(list)
        root.addView(Ui.primary(this, p, "Aage badhein  \u2192") { finishSetup() })
        val sv = ScrollView(this)
        sv.setBackgroundColor(p.bg)
        sv.addView(root)
        setContentView(sv)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun finishSetup() {
        getSharedPreferences("sg", 0).edit().putBoolean("setup_done", true).apply()
        finish()
    }

    override fun onBackPressed() { finishSetup() }

    private fun render() {
        list.removeAllViews()
        val notif = Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        row("\uD83D\uDD14", "Notifications",
            "Protection chalu hai ye dikhane aur zaroori alerts bhejne ke liye (jaise storage full ho jaye).", notif) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        row("\uD83D\uDCC1", "Files access",
            "Junk/temp files dhoondhne aur badi ya purani files hatane ke liye. Photos, videos, documents bina aapki ijazat ke kabhi delete nahi hote.",
            Cleaner.canAccessShared(this)) {
            if (Build.VERSION.SDK_INT >= 30) {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
            } else {
                requestPermissions(arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), 2)
            }
        }
        row("\uD83D\uDCCA", "Usage access",
            "Ye jaanne ke liye ki kaun si app zyada chal rahi hai ya kai din se use nahi hui. RAM Boost mein abhi use ho rahi apps ko na chhedne ke liye bhi.",
            Health.hasUsageAccess(this)) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        val pm = getSystemService(PowerManager::class.java)
        row("\uD83D\uDD0B", "Battery optimization se bahar",
            "Taaki Android protection service ko beech mein band na kar de aur auto clean chalta rahe.",
            pm.isIgnoringBatteryOptimizations(packageName)) {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        }
    }

    private fun row(emoji: String, title: String, why: String, granted: Boolean, grant: () -> Unit) {
        val card = Ui.card(this, p)
        val head = LinearLayout(this)
        head.gravity = Gravity.CENTER_VERTICAL
        head.addView(Ui.text(this, emoji, 24f, p.text))
        val t = Ui.text(this, title, 16f, p.text, true)
        t.setPadding(d(10), 0, 0, 0)
        head.addView(t)
        card.addView(head)
        val w = Ui.text(this, why, 13f, p.sub)
        w.setPadding(0, d(6), 0, 0)
        card.addView(w)
        if (granted) {
            val ok = Ui.text(this, "\u2713 Allowed", 14f, p.good, true)
            ok.setPadding(0, d(8), 0, 0)
            card.addView(ok)
        } else {
            card.addView(Ui.ghost(this, p, "Allow karein") { grant() })
        }
        list.addView(card)
    }
}
