# 3D Parallax Live Wallpaper (Kotlin)

A minimal Android live wallpaper using `WallpaperService`, a Canvas engine, and `SensorManager.TYPE_ROTATION_VECTOR`. No third-party runtime libraries are used.

## Included
- `ParallaxWallpaperService.kt`: sensor input, low-pass smoothing, 60 fps visible-only rendering, parallax layers, glow pulse and foreground shadow.
- `SettingsActivity.kt`: depth strength slider (0.5x–2.0x) and system wallpaper picker launch.
- `res/drawable/background.png`, `glow.png`, `foreground.png`: sample layered artwork. Replace these with your own PNGs, keeping the exact resource names.
- Wallpaper service metadata and Android manifest.

## Image preparation
Use three PNG files with the same canvas dimensions and composition:
1. `background.png`: opaque background.
2. `glow.png`: transparent PNG containing only the glow/light element.
3. `foreground.png`: transparent PNG containing the foreground subject.

The sample assets are 1080 × 2400. For best results, make the transparent layers align exactly with the background. Keep important content away from the extreme edges because the parallax translation crops beyond the display bounds.

## Build in Android Studio
1. Install Android Studio (a current stable release) and the Android SDK Platform 35.
2. Extract this project ZIP.
3. In Android Studio choose **Open**, then select the extracted `ParallaxLiveWallpaper` folder.
4. Allow Gradle sync to finish. Use JDK 17 (Android Studio's embedded JBR is suitable).
5. Connect an Android device with USB debugging enabled, or create an emulator. A physical device with a rotation-vector sensor is recommended for the tilt effect.
6. Select the `app` run configuration and press **Run**. This installs the companion settings screen.
7. Open **3D Parallax Wallpaper**, adjust Depth strength, then tap **Set live wallpaper**.
8. In Android's wallpaper preview, choose **Set wallpaper** and select Home screen (or both, if offered).

## Notes / tuning
- The sensor values are normalized and clamped before translation. `115 ms` is the low-pass smoothing time constant; increase it for softer/slower movement.
- The glow alpha follows a 4-second sine wave. The foreground shadow is drawn as a slightly offset translucent silhouette with a Canvas shadow layer.
- The renderer schedules frames every 16 ms while visible and unregisters the sensor listener when hidden.
- Some devices rotate the display coordinate system differently. If tilt feels reversed, swap the signs of `targetX` / `targetY` in `onSensorChanged`.
- Android may throttle wallpaper rendering under thermal or power constraints; 60 fps is a target, not a guarantee.
- The platform's Canvas shadow blur can vary by device/GPU. If a device does not render `setShadowLayer` as expected, use a pre-blurred transparent shadow PNG as an additional layer.


## Build an APK automatically with GitHub Actions (no Android Studio needed)

1. Sign in to GitHub and create a new **private** or public repository, for example `parallax-live-wallpaper`.
2. Upload the contents of this project folder to the repository (make sure the `.github/workflows/android.yml` file is included).
3. Open the repository's **Actions** tab. If prompted, allow GitHub Actions.
4. The workflow runs automatically after you push to `main`/`master`. You can also choose **Build Android APK** → **Run workflow**.
5. Open the completed workflow run. Under **Artifacts**, download `parallax-live-wallpaper-debug-apk`.
6. Extract the downloaded artifact ZIP. It contains `app-debug.apk`. Transfer it to your Android phone and tap it to install. You may need to allow your browser or file manager to **Install unknown apps**.
7. Open the installed app, adjust depth strength, tap **Set live wallpaper**, and confirm in Android's wallpaper picker.

The workflow compiles a debug APK signed with Android's standard debug key. It is suitable for personal testing and installation; it is not a Play Store release. GitHub Actions artifact downloads are available for 14 days.
