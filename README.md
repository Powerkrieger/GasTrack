# GasTrack ⛽

A minimal Android app for logging fuel purchases and tracking costs over time.

[![Build APK](https://github.com/Powerkrieger/GasTrack/actions/workflows/build.yml/badge.svg)](https://github.com/Powerkrieger/GasTrack/actions/workflows/build.yml)
[![Latest Release](https://img.shields.io/github/v/release/Powerkrieger/GasTrack)](https://github.com/Powerkrieger/GasTrack/releases/latest)

---

## Features

- **Log fill-ups** — station name, liters, euros, and kilometers since last fill-up
- **GPS location** — automatically detects your city and looks up the nearest fuel station via OpenStreetMap
- **Receipt photos** — attach a photo of the receipt to any entry
- **History** — browse all past entries, tap one to see full details including the receipt photo
- **Statistics** — line charts for price per liter, liters per fill-up, fuel efficiency (L/100km), and cost per km
- **Live calculations** — see €/L, L/100km and €/km update as you type

## Screenshots

> *(Add screenshots here)*

## Installation

Download the latest APK from the [Releases](https://github.com/Powerkrieger/GasTrack/releases/latest) page and sideload it onto your device.

> Enable **Install from unknown sources** in your Android settings if prompted.

## Building from source

Requirements: Android Studio Meerkat or newer, JDK 21.

```bash
git clone https://github.com/Powerkrieger/GasTrack.git
cd GasTrack
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## Tech stack

| | |
|---|---|
| Language | Kotlin 2.0.21 |
| UI | Jetpack Compose + Material 3 |
| Database | SQLite (SQLiteOpenHelper) |
| Location | Android LocationManager |
| Geocoding | Android Geocoder |
| Station lookup | OpenStreetMap Overpass API |
| Min SDK | Android 7.0 (API 24) |

## Permissions

| Permission | Why |
|---|---|
| `ACCESS_FINE_LOCATION` | Detect current location for automatic city and station lookup |
| `CAMERA` | Take a photo of the fuel receipt |
| `INTERNET` | Query the OpenStreetMap Overpass API for nearby stations |

---

*Built with the assistance of Claude AI.*
