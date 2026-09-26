# DBpro Central Monitor Android

Aplikasi Android native read-only untuk monitoring server DBpro.

## Membuka dan melihat preview

1. Install Android Studio.
2. Pilih **Open** lalu buka folder proyek ini.
3. Tunggu Gradle Sync selesai.
4. Jalankan pada emulator Android atau HP melalui USB debugging.

Tampilan utama dibuat secara programatis di:

`app/src/main/java/id/dbpro/central/monitor/MainActivity.java`

Warna tema dasar dan system bar berada di:

`app/src/main/res/values/styles.xml`

Logo berada di:

`app/src/main/res/drawable/dbpro_central_logo.jpg`

## Build

```bash
./gradlew assembleDebug
```

APK debug akan tersedia di `app/build/outputs/apk/debug/`.

## Keamanan

- APK tidak menyimpan API key Dokploy.
- Aplikasi hanya memakai login dan dashboard monitoring read-only.
- Backend wajib memvalidasi user dan menerbitkan access token singkat.
- Jangan mengekspos endpoint write, deploy, atau terminal melalui mobile API.

## API

Base URL saat ini: `https://central-app.dbpro.id/api/v1/`

- `POST auth/login`
- `GET monitoring/dashboard`
