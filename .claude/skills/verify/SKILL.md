---
name: verify
description: Build, install and drive the Transport app on the transport-test emulator to verify changes end-to-end.
---

# Verifying Transport app changes

Always use the emulator AVD `transport-test`; never drive a connected physical phone.
A physical device is often attached, so every adb command needs `-s emulator-5554`.

## Build + launch

```bash
./gradlew :app:assembleDebug   # APK: app/build/outputs/apk/debug/dt-<version>-DEBUG-universal.apk
/home/dakil/Android/Sdk/emulator/emulator -avd transport-test -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect &   # headless is fine for screencap
adb -s emulator-5554 shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 2; done'
adb -s emulator-5554 install -r app/build/outputs/apk/debug/dt-*-DEBUG-universal.apk
adb -s emulator-5554 shell pm grant pl.dakil.transport.debug android.permission.ACCESS_FINE_LOCATION
adb -s emulator-5554 shell pm grant pl.dakil.transport.debug android.permission.ACCESS_COARSE_LOCATION
adb -s emulator-5554 emu geo fix <lon> <lat>   # e.g. 21.0122 52.2297 = Warsaw center (good transit density, live data)
adb -s emulator-5554 shell am start -n pl.dakil.transport.debug/pl.dakil.transport.MainActivity
```

## Drive + capture

- Screenshot: `adb -s emulator-5554 exec-out screencap -p > shot.png`; taps via `input tap x y` (1080x2400 screen).
- The map auto-centers on the location fix ~10s after launch; stops need zoom >= 13 and appear a moment after the camera settles.
- A geo fix sent before first launch may not take effect until the next app start.
- Vehicles/live data: Warsaw has real-time feeds (green-stroked markers); useful for map/trips verification.
