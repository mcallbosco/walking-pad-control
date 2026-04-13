# Walking Pad Control

[![CI](https://github.com/mcallbosco/walking-pad-control/actions/workflows/ci.yml/badge.svg)](https://github.com/mcallbosco/walking-pad-control/actions/workflows/ci.yml)
[![Release](https://github.com/mcallbosco/walking-pad-control/actions/workflows/release.yml/badge.svg)](https://github.com/mcallbosco/walking-pad-control/actions/workflows/release.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

A native Android app for controlling **PitPat BA02** walking pad treadmills over Bluetooth Low Energy.

Built with Kotlin and Jetpack Compose, following Material Design 3.

## Features

- Scan and connect to PitPat BLE treadmills
- Live dashboard: speed, distance, calories, duration, running state
- Start, pause, and stop the treadmill
- Adjust speed with +/- buttons or a slider (0.1 unit increments)
- Toggle between metric (km) and imperial (mi) units
- Auto-reconnect to the last connected device on launch
- Auto-reconnect on unexpected disconnect (3 attempts)
- Keep screen on while connected
- Detailed metrics view (firmware, motor data, serial number, etc.)

## Compatibility

- **Android 8.0 (API 26)** or newer
- Targeted at the **PitPat BA02** walking pad
- Should work with any PitPat treadmill that exposes the standard PitPat BLE GATT service (FF01 write / FF02 notify)

## Building

Requirements:
- JDK 21
- Android SDK with platform 35 and build-tools 35.0.0

```bash
export ANDROID_HOME=$HOME/Android/Sdk
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk

./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## Project Structure

```
app/src/main/java/com/walkingpad/control/
├── MainActivity.kt              # Entry point, permission handling
├── ble/
│   ├── BluetoothLeManager.kt    # BLE scan, connect, MTU, fragment reassembly
│   ├── TreadmillController.kt   # Command packet builder
│   └── TreadmillData.kt         # Notification payload parser
├── ui/
│   ├── theme/                   # Material 3 theme
│   ├── screens/
│   │   ├── ScanScreen.kt        # Device scan + reconnect UI
│   │   └── DashboardScreen.kt   # Live metrics + controls
│   └── components/
│       ├── ControlButtons.kt    # Speed slider, +/-, play/pause/stop
│       └── MetricCard.kt        # Reusable metric tile
└── viewmodel/
    └── TreadmillViewModel.kt    # State + auto-reconnect logic
```

## BLE Protocol Notes

- **Notify characteristic**: `0000ff02-0000-1000-8000-00805f9b34fb`
- **Write characteristic**: `0000ff01-0000-1000-8000-00805f9b34fb`
- The treadmill speaks a heartbeat-driven protocol — the app must reply to each notification with a heartbeat packet, and commands piggyback on heartbeats.
- Command packets are 23 bytes: `0x6A` start, length `0x17`, speed (big-endian), incline, weight, command type, user ID, XOR checksum, `0x43` end.
- Android requires MTU negotiation (request 512 bytes); without it, notifications come in 18-byte fragments that must be reassembled before parsing.

## License

MIT
