SmartGuard - Android Antivirus & Phone Booster

KAISE USE KAREIN
1. Is zip ki saari files GitHub repo mein upload karein (sab ek hi jagah, folder banane ki zaroorat nahi).
2. Agar ".github" folder upload na ho paye: GitHub par Add file > Create new file,
   naam mein  .github/workflows/build.yml  likhein aur "build.yml" ka text paste karein.
3. Actions tab > Build APK > chalne dein (3-5 min).
4. Artifacts > SmartGuard-APK download karein aur phone mein install karein.

FILES (sab ke naam alag-alag hain)
root_build.gradle.kts / app_build.gradle.kts  -> setup.sh inhe sahi jagah rakh deta hai
MainActivity.kt, SetupActivity.kt, Ui.kt, Smart.kt, GuardService.kt, Cleaner.kt, Health.kt, Audit.kt, BootReceiver.kt -> app ka code
AndroidManifest.xml, strings.xml, ic_launcher.xml -> app settings aur icon
logo.png / logo.svg -> SmartGuard ka logo
