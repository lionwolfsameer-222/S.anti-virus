package com.smartguard.app

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import java.io.File

/**
 * SAFETY RULE: Auto-clean sirf in cheezon ko delete karta hai: app cache, 24 ghante se purani .tmp/.temp jaisi
 * temporary files aur .thumbnails. Photo, video, documents (jpg, mp4, pdf, docx...) ko kabhi
 * auto-delete nahi karta. Baaki sab "review" list mein aata hai jise delete karne se pehle permission li jaati hai.
 */
object Cleaner {
    private val junkExt = setOf("tmp", "temp", "thumbdata")

    data class Found(val file: File, val size: Long, val label: String)

    fun canAccessShared(c: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else c.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    private fun size(f: File): Long = if (f.isFile) f.length() else f.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    private fun deleteContents(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0
        var freed = 0L
        dir.listFiles()?.forEach { freed += size(it); it.deleteRecursively() }
        return freed
    }

    /** Har minute chalne wala halka clean: sirf SmartGuard ka apna cache. */
    fun lightClean(c: Context): Long {
        var f = deleteContents(c.cacheDir) + deleteContents(c.codeCacheDir)
        c.externalCacheDirs.forEach { f += deleteContents(it) }
        return f
    }

    // Dusri apps ke kaam ke folders auto-clean mein kabhi nahi chhue jaate
    private val protectedDirs = setOf("Android", "Download", "Downloads", "Documents", "WhatsApp", "Telegram", "Music", "Movies", "Recordings")

    private fun walkShared(protect: Boolean = false): Sequence<File> {
        val root = Environment.getExternalStorageDirectory()
        return root.walkTopDown().maxDepth(6).onEnter {
            !(it.parentFile == root && (it.name == "Android" || (protect && it.name in protectedDirs)))
        }
    }

    private fun isSafeJunk(f: File): Boolean =
        f.isFile && System.currentTimeMillis() - f.lastModified() > 24 * 3_600_000L &&
            (f.extension.lowercase() in junkExt || f.parentFile?.name == ".thumbnails")

    fun findSafeJunk(): List<Found> = try {
        walkShared(true).filter { isSafeJunk(it) }.map { Found(it, it.length(), "Junk") }.toList()
    } catch (e: Exception) { emptyList() }

    /** Sirf safe junk delete hota hai. */
    fun cleanSafeShared(): Long {
        var freed = 0L
        findSafeJunk().forEach { if (it.file.delete()) freed += it.size }
        return freed
    }

    /** Ye sab user ki permission ke bina delete NAHI hote. */
    fun findNeedsReview(c: Context): List<Found> {
        val out = mutableListOf<Found>()
        try {
            walkShared().filter { it.isFile }.forEach {
                val ext = it.extension.lowercase()
                if (ext == "apk") out += Found(it, it.length(), "Purani APK")
                else if (ext == "part" || ext == "crdownload") out += Found(it, it.length(), "Adhoori download")
                else if (it.length() > 200L * 1024 * 1024) out += Found(it, it.length(), "Badi file")
            }
        } catch (e: Exception) {}
        out += findOrphans(c)
        return out
    }

    /** Uninstall ho chuki apps ka bacha hua data (Android/data, Android/obb). */
    fun findOrphans(c: Context): List<Found> {
        val out = mutableListOf<Found>()
        val root = Environment.getExternalStorageDirectory()
        for (sub in listOf("Android/data", "Android/obb")) {
            File(root, sub).listFiles()?.forEach { d ->
                if (d.isDirectory && !installed(c, d.name)) out += Found(d, size(d), "Uninstalled app ka data")
            }
        }
        return out
    }

    private fun installed(c: Context, pkg: String) = try { c.packageManager.getPackageInfo(pkg, 0); true } catch (e: Exception) { false }

    fun mb(b: Long) = "%.1f MB".format(b / 1048576.0)
}
