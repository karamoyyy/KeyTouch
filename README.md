# KeyTouch

Main game Android pakai keyboard & mouse fisik, tanpa Shizuku / root.
Cara kerja: AccessibilityService membaca tombol keyboard, overlay 1x1 px memegang pointer capture
untuk mouse, lalu semuanya diubah jadi sentuhan multi-jari lewat `dispatchGesture`.

## Build APK
- Android Studio: buka folder ini -> Sync -> Build > Build APK(s)
- Terminal: `./gradlew assembleDebug` -> `app/build/outputs/apk/debug/app-debug.apk`
- GitHub: push ke repo, workflow `Build APK` menghasilkan APK di tab Actions

## Pakai
1. Pasang APK, buka KeyTouch, aktifkan layanan aksesibilitas "KeyTouch".
   (Android 13+ untuk APK di luar Play Store: Info Aplikasi > menu titik tiga > "Izinkan setelan terbatas" dulu.)
2. Sambungkan keyboard & mouse.
3. Buka game, ketuk ATUR di chip mengambang, geser analog WASD / zona MOUSE / tombol ke posisi kontrol asli di game.
4. Simpan & Main. Tekan ` (backtick) untuk MAIN / STOP kapan saja.
