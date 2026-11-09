# X6886 Freq Monitor

A small rooted Android app that displays CPU and GPU current, maximum and minimum frequencies. It is read-only and refreshes every 1.5 seconds.

## Build APK

1. Create a GitHub repository and upload the contents of this folder.
2. Open **Actions** → **Build APK** → **Run workflow**.
3. Download the `X6886-Freq-Monitor-debug-apk` artifact from the completed run. The APK is `app-debug.apk`.

Requires Android Studio/Android SDK to build locally (compile SDK 35, JDK 17, Gradle 8.9). On first launch, grant the root request.

GPU values are read from `/proc/gpufreqv2/` (with a limited sysfs fallback); CPU values are read from per-core cpufreq sysfs nodes. A missing kernel node is shown as unavailable rather than guessed.
