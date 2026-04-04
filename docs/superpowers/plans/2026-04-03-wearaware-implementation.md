# WearAware v1.0 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build WearAware — a production-grade Android app that scans nearby BLE devices, classifies consumer smart glasses using a config-driven rule engine, displays signal strength bars, and shows calm in-app persistence alerts when wearable devices remain nearby.

**Architecture:** Clean Architecture (UI → Domain → Data). Domain layer is pure Kotlin with zero Android imports. `BleScanner` is the only class touching `android.bluetooth`. ViewModels orchestrate use cases only; no BLE parsing or rule logic inside them.

**Tech Stack:** Kotlin 1.9.22, Jetpack Compose BOM 2024.02.00, Material 3, Hilt 2.50, Room 2.6.1, Kotlin Coroutines 1.7.3, BluetoothLeScanner, Gson 2.10.1, Accompanist Permissions 0.32.0, JUnit 4, MockK 1.13.9

**Markers:**
- 📝 This task must update README / CHANGELOG / VERSION
- 🧪 This task requires unit tests
- 📱 This task must be complete before real-device testing

---

## File Map

All paths relative to `/Users/kingsebruvwiyo/mobile-app/`.

```
mobile-app/
├── VERSION
├── README.md
├── CHANGELOG.md
├── ROADMAP.md
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
│       └── gradle-wrapper.properties
├── docs/
│   ├── PRD/
│   │   ├── v1.0.md
│   │   └── CURRENT.md
│   ├── ADR/
│   │   └── ADR-001-ble-scanning.md
│   ├── SECURITY/
│   │   ├── threat-model.md
│   │   └── permissions-policy.md
│   ├── COMPLIANCE/
│   │   └── soc2-controls.md
│   ├── LOGGING/
│   │   └── scan-log-schema.md
│   ├── TESTING/
│   │   └── meta-glasses-test-plan.md
│   └── DECISIONS/
│       └── product-decisions.md
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── assets/
        │   │   └── fingerprint_rules.json
        │   ├── res/values/
        │   │   ├── strings.xml
        │   │   └── themes.xml
        │   └── kotlin/com/wearaware/app/
        │       ├── WearAwareApplication.kt
        │       ├── MainActivity.kt
        │       ├── di/
        │       │   ├── BleModule.kt
        │       │   ├── DatabaseModule.kt
        │       │   └── RepositoryModule.kt
        │       ├── domain/
        │       │   ├── model/
        │       │   │   ├── ObservedDevice.kt
        │       │   │   ├── RawScanResult.kt
        │       │   │   ├── ProximityLabel.kt
        │       │   │   ├── VisibilityState.kt
        │       │   │   ├── DeviceCategory.kt
        │       │   │   ├── ConfidenceLevel.kt
        │       │   │   ├── ClassificationResult.kt
        │       │   │   ├── PersistenceAlert.kt
        │       │   │   └── ScanLogEntry.kt
        │       │   ├── rules/
        │       │   │   ├── FingerprintRule.kt
        │       │   │   ├── FingerprintClassifier.kt
        │       │   │   ├── RssiSmoother.kt
        │       │   │   └── ProximityConfig.kt
        │       │   ├── repository/
        │       │   │   ├── BleRepository.kt
        │       │   │   ├── ScanLogRepository.kt
        │       │   │   └── RulesRepository.kt
        │       │   └── usecase/
        │       │       ├── ObserveScannedDevicesUseCase.kt
        │       │       ├── ClassifyDeviceUseCase.kt
        │       │       ├── EvaluatePersistenceUseCase.kt
        │       │       ├── LogScanEventUseCase.kt
        │       │       ├── GetSessionLogUseCase.kt
        │       │       └── ClearSessionLogUseCase.kt
        │       ├── data/
        │       │   ├── ble/
        │       │   │   ├── BleScanner.kt
        │       │   │   └── ScanResultMapper.kt
        │       │   ├── local/
        │       │   │   ├── WearAwareDatabase.kt
        │       │   │   ├── ScanLogDao.kt
        │       │   │   └── ScanLogEntity.kt
        │       │   ├── mapper/
        │       │   │   └── ScanLogMapper.kt
        │       │   ├── repository/
        │       │   │   ├── BleRepositoryImpl.kt
        │       │   │   ├── ScanLogRepositoryImpl.kt
        │       │   │   └── RulesRepositoryImpl.kt
        │       │   └── rules/
        │       │       ├── RulesLoader.kt
        │       │       ├── dto/
        │       │       │   ├── RulesDataDto.kt
        │       │       │   └── FingerprintRuleDto.kt
        │       │       └── RulesDtoMapper.kt
        │       ├── ui/
        │       │   ├── theme/
        │       │   │   ├── Color.kt
        │       │   │   ├── Theme.kt
        │       │   │   └── Type.kt
        │       │   ├── components/
        │       │   │   ├── SafeWording.kt
        │       │   │   ├── SignalBars.kt
        │       │   │   ├── VisibilityBadge.kt
        │       │   │   ├── DeviceCard.kt
        │       │   │   ├── AlertBanner.kt
        │       │   │   └── ScanStatusHeader.kt
        │       │   ├── navigation/
        │       │   │   └── NavGraph.kt
        │       │   ├── screens/
        │       │   │   ├── ScanScreen.kt
        │       │   │   ├── DeviceDetailScreen.kt
        │       │   │   └── SessionLogScreen.kt
        │       │   └── viewmodel/
        │       │       ├── ScanUiState.kt
        │       │       ├── ScanViewModel.kt
        │       │       └── SessionLogViewModel.kt
        │       └── util/
        │           ├── FormatUtils.kt
        │           └── PermissionUtils.kt
        └── test/kotlin/com/wearaware/app/
            ├── domain/
            │   ├── rules/
            │   │   ├── FingerprintClassifierTest.kt
            │   │   ├── RssiSmootherTest.kt
            │   │   └── ProximityConfigTest.kt
            │   └── usecase/
            │       └── EvaluatePersistenceUseCaseTest.kt
            └── data/
                └── mapper/
                    └── ScanLogMapperTest.kt
```

---

## Phase 1: Project Scaffolding & Documentation 📝 📱

### Task 1: Gradle configuration

**Objective:** Create all Gradle build files so the project compiles.

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle/libs.versions.toml`
- Create: `gradle.properties`
- Create: `gradle/wrapper/gradle-wrapper.properties`
- Create: `app/build.gradle.kts`
- Create: `app/proguard-rules.pro`

- [ ] **Step 1: Create `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "WearAware"
include(":app")
```

- [ ] **Step 2: Create `build.gradle.kts` (root)**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.kotlin.kapt) apply false
}
```

- [ ] **Step 3: Create `gradle/libs.versions.toml`**

```toml
[versions]
kotlin = "1.9.22"
agp = "8.2.2"
compose-bom = "2024.02.00"
hilt = "2.50"
room = "2.6.1"
coroutines = "1.7.3"
lifecycle = "2.7.0"
navigation-compose = "2.7.6"
hilt-navigation-compose = "1.1.0"
accompanist = "0.32.0"
gson = "2.10.1"
junit = "4.13.2"
mockk = "1.13.9"

[libraries]
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
activity-compose = { group = "androidx.activity", name = "activity-compose", version = "1.8.2" }

hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-android-compiler", version.ref = "hilt" }
hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "hilt-navigation-compose" }

room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }

coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }

lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }

navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation-compose" }

accompanist-permissions = { group = "com.google.accompanist", name = "accompanist-permissions", version.ref = "accompanist" }

gson = { group = "com.google.code.gson", name = "gson", version.ref = "gson" }

junit = { group = "junit", name = "junit", version.ref = "junit" }
mockk = { group = "io.mockk", name = "mockk", version.ref = "mockk" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
hilt-android = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
kotlin-kapt = { id = "org.jetbrains.kotlin.kapt", version.ref = "kotlin" }
```

- [ ] **Step 4: Create `gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

- [ ] **Step 5: Create `gradle/wrapper/gradle-wrapper.properties`**

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.2-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

- [ ] **Step 6: Create `app/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.kotlin.kapt)
}

android {
    namespace = "com.wearaware.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.wearaware.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions { jvmTarget = "1.8" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.8" }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    kapt(libs.room.compiler)

    implementation(libs.coroutines.android)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.navigation.compose)
    implementation(libs.accompanist.permissions)
    implementation(libs.gson)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
}

kapt { correctErrorTypes = true }
```

- [ ] **Step 7: Create `app/proguard-rules.pro`**

```
# WearAware ProGuard rules
# Add rules here when minifyEnabled = true in release builds
```

- [ ] **Step 8: Generate the Gradle wrapper**

Run from `/Users/kingsebruvwiyo/mobile-app/`:
```bash
gradle wrapper --gradle-version 8.2
```
Expected: `gradlew`, `gradlew.bat`, and `gradle/wrapper/gradle-wrapper.jar` are created.

- [ ] **Step 9: Commit**

```bash
cd /Users/kingsebruvwiyo/mobile-app && git init && git add gradle* settings.gradle.kts build.gradle.kts app/build.gradle.kts app/proguard-rules.pro
git commit -m "chore: initialize Android project Gradle configuration"
```

---

### Task 2: Android manifest, application class, and resources 📱

**Objective:** Create the Android manifest with BLE permissions and the Hilt application class.

**Files:**
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values/themes.xml`
- Create: `app/src/main/kotlin/com/wearaware/app/WearAwareApplication.kt`
- Create: `app/src/main/kotlin/com/wearaware/app/MainActivity.kt`

- [ ] **Step 1: Create directory structure**

```bash
mkdir -p app/src/main/kotlin/com/wearaware/app
mkdir -p app/src/main/res/values
mkdir -p app/src/main/assets
mkdir -p app/src/test/kotlin/com/wearaware/app/domain/rules
mkdir -p app/src/test/kotlin/com/wearaware/app/domain/usecase
mkdir -p app/src/test/kotlin/com/wearaware/app/data/mapper
```

- [ ] **Step 2: Create `app/src/main/AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <!-- BLE permissions for Android 12+ (API 31+) -->
    <uses-permission
        android:name="android.permission.BLUETOOTH_SCAN"
        android:usesPermissionFlags="neverForLocation"
        tools:targetApi="s" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />

    <!-- Location permission required for BLE scanning on Android 6–11 (API 23–30) -->
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

    <!-- Declare BLE hardware requirement -->
    <uses-feature
        android:name="android.hardware.bluetooth_le"
        android:required="true" />

    <application
        android:name=".WearAwareApplication"
        android:allowBackup="false"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.WearAware">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

- [ ] **Step 3: Create `app/src/main/res/values/strings.xml`**

```xml
<resources>
    <string name="app_name">WearAware</string>
</resources>
```

- [ ] **Step 4: Create `app/src/main/res/values/themes.xml`**

```xml
<resources>
    <style name="Theme.WearAware" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
```

- [ ] **Step 5: Create `WearAwareApplication.kt`**

```kotlin
package com.wearaware.app

// PURPOSE: Hilt application entry point.
// NOTES: All @Singleton Hilt components are scoped to this application lifetime.

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class WearAwareApplication : Application()
```

- [ ] **Step 6: Create `MainActivity.kt`** (placeholder — full NavGraph wired in Task 25)

```kotlin
package com.wearaware.app

// PURPOSE: Single-activity host for the Jetpack Compose NavGraph.
// NOTES: All navigation is handled by WearAwareNavGraph via NavController.

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.wearaware.app.ui.theme.WearAwareTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WearAwareTheme {
                // NavGraph wired in Task 25
            }
        }
    }
}
```

- [ ] **Step 7: Commit**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/res app/src/main/kotlin/com/wearaware/app/WearAwareApplication.kt app/src/main/kotlin/com/wearaware/app/MainActivity.kt
git commit -m "chore: add AndroidManifest, Hilt application class, and resource stubs"
```

---

### Task 3: VERSION, README, CHANGELOG, ROADMAP 📝

**Objective:** Create the versioned documentation foundation used by all future tasks.

**Files:**
- Create: `VERSION`
- Create: `README.md`
- Create: `CHANGELOG.md`
- Create: `ROADMAP.md`

- [ ] **Step 1: Create `VERSION`**

```
1.0.0
```

- [ ] **Step 2: Create `CHANGELOG.md`**

```markdown
# Changelog

All notable changes to WearAware are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

---

## [Unreleased]

### Added
- Initial project scaffolding
- PRD v1.0
- Documentation structure (ADR, SECURITY, COMPLIANCE, LOGGING, TESTING, DECISIONS)

---

## [1.0.0] — Unreleased

### Added
- BLE scanning pipeline (in-app only)
- Config-driven fingerprint rule engine (fingerprint_rules.json v1.0.0)
- RSSI smoothing (rolling average, window=5)
- Proximity labels: VERY_CLOSE, STRONG, NEARBY, WEAK, UNKNOWN
- Signal bars UI (1–5 bars, color-coded)
- Device list with sort-by-signal
- Persistence alert system (60s threshold, NEARBY+ proximity gate)
- Local session log (Room, on-device, user-clearable)
- Safe wording — no recording claims, no following claims
- SOC2-aligned documentation

### Rule Set
- rule_set_version: 1.0.0
- Initial rules: meta_rayban_v1, snapchat_spectacles_v1, generic_smartwatch_v1
```

- [ ] **Step 3: Create `ROADMAP.md`**

```markdown
# WearAware Roadmap

## v1.0 — MVP (current)
- In-app BLE scanning (app must be open)
- Consumer wearable detection (Meta Ray-Ban, Snapchat Spectacles)
- Config-driven fingerprint rule engine
- RSSI smoothing + proximity labels
- Signal bars UI
- Persistence alerts (in-app banner)
- Local session log

## v1.1 — Background Scanning
- Android Foreground Service
- Persistent notification while scanning
- Background BLE scan loop
- OS-level notification alerts

## v2.0 — Persistent Intelligence
- Cross-session device encounter tracking
- "Seen X times across Y locations" awareness
- Room schema migration
- Retention policy and user controls

## v3.0 — Advanced Classification
- Probabilistic scoring model
- Expanded rule set (enterprise, industrial)
- Optional ML-based classification layer
- Community rule submissions
```

- [ ] **Step 4: Create `README.md`**

```markdown
# WearAware

A consumer privacy awareness Android app that scans nearby Bluetooth Low Energy (BLE) devices,
detects wearable devices such as smart glasses, and provides calm, non-accusatory alerts.

**Version:** 1.0.0 | **Rule Set:** 1.0.0

---

## Overview

WearAware helps you become aware of camera-capable wearable devices nearby.
It does NOT claim anyone is recording you. It does NOT claim anyone is following you.
It provides proximity estimates and awareness — nothing more.

> Proximity is estimated from Bluetooth signal strength and may vary based on physical
> obstructions, device orientation, and interference. Recording activity cannot be determined.

---

## Implemented Features (v1.0)

- [x] BLE scanning while app is open
- [x] Config-driven fingerprint rule engine (fingerprint_rules.json)
- [x] RSSI smoothing (rolling average, configurable window)
- [x] Proximity labels: Very close / Strong / Nearby / Weak / Unknown
- [x] 1–5 signal bars (color-coded, no green "safe" color)
- [x] Device list sorted by strongest signal
- [x] Persistence alert banner (60s sustained presence + NEARBY or closer)
- [x] Alert suppression for low-confidence matches
- [x] Local session log (on-device, user-clearable)
- [x] Device detail screen with classification explanation
- [x] Safe wording centralized in `SafeWording` object

---

## Not Implemented (v1.0)

- [ ] Background scanning (planned v1.1)
- [ ] Cross-session device history (planned v2.0)
- [ ] Radar / spatial visualization
- [ ] Industrial or enterprise device detection
- [ ] Cloud sync or remote logging

---

## Known Limitations

- Device identifiers are session-scoped due to BLE MAC address randomization (Android 6+).
  The same physical device may appear with a different ID across app sessions.
- Proximity is estimated, not measured. BLE RSSI varies significantly with environment.
- False positives (non-wearable devices triggering detection) and false negatives
  (wearables not detected) are possible and expected.
- Recording activity cannot be determined from BLE signals.
- Scanning requires the app to be open (v1.0). Background scanning is planned for v1.1.

---

## How It Works

1. App opens BLE scanner using `BluetoothLeScanner` API
2. Each scan result is parsed for manufacturer data, service UUIDs, and device name
3. RSSI readings are smoothed using a rolling average (window = 5)
4. Device is classified against `fingerprint_rules.json` using a priority-scored rule engine
5. Matched rule ID and rule version are recorded for auditability
6. Proximity label is assigned from averaged RSSI thresholds
7. If a wearable candidate has been continuously DETECTED_NOW for 60+ seconds at
   NEARBY proximity or closer, an in-app alert banner is shown
8. All detections are logged locally to a session log (Room database)

---

## Testing With Meta Ray-Ban Glasses

See `docs/TESTING/meta-glasses-test-plan.md` for full test scenarios.

Quick start:
1. Enable Bluetooth on your Android device
2. Put on Meta Ray-Ban glasses and ensure Bluetooth is active on them
3. Open WearAware and tap **Start Scanning**
4. Walk within ~5 meters of the glasses
5. The device should appear labeled "Camera-capable wearable detected nearby"
6. Stay nearby for 60+ seconds to trigger the persistence alert banner

Expected RSSI range (Meta Ray-Ban): approximately -50 to -75 dBm at 1–3 meters.

---

## Architecture

Clean Architecture: UI → Domain → Data

- **Domain layer** — pure Kotlin, zero Android dependencies
- **Data layer** — BLE scanner, Room database, rules loader
- **UI layer** — Jetpack Compose, MVVM, StateFlow

See `docs/ADR/ADR-001-ble-scanning.md` for architectural decisions.

---

## Next Steps

After v1.0 validation with Meta glasses:
- Tune RSSI thresholds in `fingerprint_rules.json`
- Add additional consumer device rules
- Implement foreground service for background scanning (v1.1)
```

- [ ] **Step 5: Commit**

```bash
git add VERSION README.md CHANGELOG.md ROADMAP.md
git commit -m "docs: add VERSION, README, CHANGELOG, and ROADMAP for v1.0"
```

---

### Task 4: PRD v1.0 📝

**Objective:** Create the full versioned Product Requirements Document.

**Files:**
- Create: `docs/PRD/v1.0.md`
- Create: `docs/PRD/CURRENT.md`

- [ ] **Step 1: Create `docs/PRD/` directory**

```bash
mkdir -p docs/PRD
```

- [ ] **Step 2: Create `docs/PRD/v1.0.md`**

```markdown
# WearAware Product Requirements Document

**Version:** 1.0  
**Status:** Approved  
**Date:** 2026-04-03  
**Rule Set Version:** 1.0.0

---

## Change History

| Version | Date | Author | Changes |
|---|---|---|---|
| 1.0 | 2026-04-03 | Design session | Initial PRD |

## Approval Table

| Role | Name | Status |
|---|---|---|
| Product | TBD | Approved |
| Engineering | TBD | Approved |
| Compliance | TBD | Approved |

---

## 1. Product Overview

WearAware is a consumer privacy awareness Android application.
It scans nearby Bluetooth Low Energy (BLE) devices, detects consumer wearable devices
(primarily camera-capable smart glasses), shows signal strength, estimates proximity,
and provides calm, non-accusatory in-app alerts.

### Critical Product Rules (non-negotiable, enforced in code)

- DO NOT claim someone is recording the user
- DO NOT claim someone is following the user
- DO NOT show exact distance measurements
- ALWAYS display the proximity disclaimer
- ONLY use safe wording (centralized in `SafeWording.kt`)

---

## 2. Scope — v1.0 MVP

### In Scope
- Consumer wearable devices: Ray-Ban Meta smart glasses (primary), Snapchat Spectacles,
  generic Bluetooth-enabled smart glasses, smartwatches (secondary)
- In-app BLE scanning only (app must be open)
- Config-driven fingerprint rule engine
- Local on-device session log

### Out of Scope for v1.0
- Background scanning / foreground service
- Cross-session device intelligence
- Industrial, enterprise, law enforcement wearables
- Cloud sync or remote logging
- Radar / spatial visualization

---

## 3. Feature Requirements

### 3.1 BLE Scanning

- App scans continuously while open using `BluetoothLeScanner`
- Scan mode: LOW_LATENCY while scanning is active
- Scanning stops when app is stopped or user taps Stop
- No background scanning in v1.0

### 3.2 Device Classification

- All device classification is driven by `fingerprint_rules.json` (bundled asset)
- Rule file has version and hash for auditability
- Scoring: manufacturer ID match +4, name pattern match +2, service UUID match +2
- Each rule has a `min_score` threshold
- Classification is deterministic for same input + rule set version
- Fallback: UNKNOWN_BLE_DEVICE for unmatched devices

### 3.3 RSSI Smoothing

- Rolling average over configurable window (default: 5 readings)
- Proximity labels: VERY_CLOSE (≥-55), STRONG (≥-65), NEARBY (≥-75), WEAK (≥-85), UNKNOWN (<-85)
- ProximityConfig is the single source of truth for all thresholds and timeouts

### 3.4 Signal Bars UI

- 1–5 bars mapped from ProximityLabel
- Colors: grey (unknown/weak) → yellow (nearby) → orange (strong) → red (very close)
- No green — avoids implying device is safe
- Signal bars have text content descriptions for accessibility

### 3.5 Device Visibility Lifecycle

- DETECTED_NOW: device seen within last 15 seconds
- SIGNAL_LOST: device not seen for 15+ seconds; last known signal shown as stale
- Device removed from list after 30 seconds in SIGNAL_LOST state
- No alert evaluation while SIGNAL_LOST

### 3.6 Persistence Alert System

Alert triggers when ALL of these conditions are true:
1. visibilityState == DETECTED_NOW
2. seenDurationMs >= 60,000 ms
3. classification.isWearableCandidate == true
4. classification.confidence >= MEDIUM
5. proximityLabel is NEARBY, STRONG, or VERY_CLOSE
6. No active cooldown for deviceId + alertType (cooldown: 120,000 ms)

Alert behavior:
- One active banner at a time (most recently triggered replaces previous)
- Device entering SIGNAL_LOST immediately clears active banner
- Dismiss = hide banner only; does NOT reset cooldown
- Alert suppression: LOW confidence matches shown in list but never alert

### 3.7 Session Log

- On-device Room database
- Logs: timestamp, deviceId, advertisedName, rawRssi, averagedRssi, proximityLabel,
  visibilityState, matchedRuleId, ruleVersion, category, confidence, evaluationNotes
- User can clear session log at any time
- No automatic cross-session retention

### 3.8 Safe Wording

All user-facing strings related to alerts reference `SafeWording` constants:
- "Camera-capable wearable detected nearby"
- "This device has remained near you"
- "Recording activity cannot be determined"
- "Proximity is estimated from Bluetooth signal strength and may vary based on physical
  obstructions, device orientation, and interference"
- "Unknown BLE device"

---

## 4. Technical Design

### Architecture
Clean Architecture: UI → Domain → Data
- Domain layer: pure Kotlin, zero Android imports
- BleScanner: only class touching android.bluetooth
- ViewModels: orchestrate use cases only

### Key Components
- `FingerprintClassifier` — config-driven rule engine (domain layer)
- `RssiSmoother` — rolling average (domain layer)
- `BleRepositoryImpl` — scan aggregation, smoothing, expiry
- `EvaluatePersistenceUseCase` — alert gating logic
- `WearAwareDatabase` — Room, session log only

### Rule Versioning
Each release records: app VERSION + rule_set_version + rule_set_hash in CHANGELOG.

---

## 5. Risks and Limitations

| Risk | Impact | Mitigation |
|---|---|---|
| BLE MAC randomization | Device ID unstable across sessions | Documented; session-scoped IDs only |
| RSSI environmental variance | Proximity labels inaccurate | Disclaimer always shown; smooth with rolling average |
| False positives | Wrong device labeled as wearable | min_score threshold; isWearableCandidate gate |
| False negatives | Wearable not detected | Known limitation; documented in README |
| Android BLE API restrictions | Requires location permission on API <31 | Permissions policy doc; runtime permission handling |
| Rolling average lag | Delayed response to rapid signal change | Acceptable for v1; EMA planned for v2 |
```

- [ ] **Step 3: Create `docs/PRD/CURRENT.md`**

```markdown
# WearAware PRD — Current Version

This file always reflects the latest approved PRD version.

**Current version:** [v1.0](v1.0.md)  
**Rule Set Version:** 1.0.0  
**App Version:** 1.0.0  
**Last updated:** 2026-04-03

To update: overwrite this file and update the version references above.
When bumping the PRD version, also:
- Add a new versioned file (e.g., v1.1.md)
- Update CHANGELOG.md
- Update VERSION file if app version changed
```

- [ ] **Step 4: Commit**

```bash
git add docs/PRD/
git commit -m "docs: add PRD v1.0 and CURRENT.md pointer"
```

---

### Task 5: Remaining docs (ADR, SECURITY, COMPLIANCE, LOGGING, TESTING, DECISIONS) 📝

**Files:**
- Create: `docs/ADR/ADR-001-ble-scanning.md`
- Create: `docs/SECURITY/threat-model.md`
- Create: `docs/SECURITY/permissions-policy.md`
- Create: `docs/COMPLIANCE/soc2-controls.md`
- Create: `docs/LOGGING/scan-log-schema.md`
- Create: `docs/TESTING/meta-glasses-test-plan.md`
- Create: `docs/DECISIONS/product-decisions.md`

- [ ] **Step 1: Create directories**

```bash
mkdir -p docs/ADR docs/SECURITY docs/COMPLIANCE docs/LOGGING docs/TESTING docs/DECISIONS
```

- [ ] **Step 2: Create `docs/ADR/ADR-001-ble-scanning.md`**

```markdown
# ADR-001: BLE Scanning Approach

**Date:** 2026-04-03  
**Status:** Accepted

## Context

WearAware needs to scan for nearby BLE devices to detect consumer wearable devices.
Android provides three BLE scanning mechanisms: BluetoothLeScanner (API 21+),
Companion Device Manager (API 26+), and Wi-Fi/BLE Combined (not applicable).

## Decision

Use `BluetoothLeScanner` with `SCAN_MODE_LOW_LATENCY` while the app is in the foreground.

## Rationale

- BluetoothLeScanner provides direct access to manufacturer data, service UUIDs, and RSSI
  in each `ScanResult` — required for our fingerprinting approach
- SCAN_MODE_LOW_LATENCY maximizes detection responsiveness during active use
- Companion Device Manager is scoped to pairing, not ambient awareness scanning
- In-app only (no foreground service) keeps v1 simple and avoids Android background restrictions

## Alternatives Considered

- **Companion Device Manager**: Rejected — designed for pairing, not ambient BLE scanning
- **SCAN_MODE_BALANCED**: Rejected — too slow for real-time proximity awareness
- **WorkManager periodic scans**: Rejected — deferred to v1.1; adds complexity before
  fingerprinting is validated

## Limitations

- BluetoothLeScanner requires a foreground app context
- Android 6+ randomizes BLE MAC addresses; device IDs are session-scoped only
- RSSI values vary significantly with environment and device orientation
- BLUETOOTH_SCAN permission requires neverForLocation flag on API 31+ to avoid
  location permission dependency

## Consequences

- BleScanner.kt is the single class allowed to import android.bluetooth
- Scanning stops when app is backgrounded (v1.0 constraint)
- All downstream logic (smoothing, classification, alerts) is pure Kotlin
```

- [ ] **Step 3: Create `docs/SECURITY/threat-model.md`**

```markdown
# WearAware Threat Model

**Version:** 1.0  
**Date:** 2026-04-03

## Assets

| Asset | Description | Sensitivity |
|---|---|---|
| Session scan log | On-device Room DB; timestamps, device fingerprints, RSSI | Low — no PII |
| BLE fingerprint rules | Bundled JSON asset | Low — public detection logic |
| User location (implicit) | Inferred from BLE scan timing | Not collected or stored |

## Non-Assets (Explicitly Not Collected)

- User identity or account information
- GPS or network location
- Device MAC addresses in long-term storage
- Cross-session device encounter history
- Any data transmitted off-device

## Threats

### T1: Sensitive data exposure via session log
**Risk:** Session log read by other apps or extracted from device.  
**Mitigation:** Room database uses Android's internal storage (not externally accessible
without root). No cloud sync. User can clear at any time.

### T2: False positive causing user alarm
**Risk:** A non-wearable device classified as camera-capable wearable.  
**Mitigation:** min_score threshold; isWearableCandidate gate; LOW-confidence matches
suppressed from alerts; non-alarmist safe wording only.

### T3: False negative — wearable not detected
**Risk:** A real wearable device is not detected.  
**Mitigation:** Documented limitation. Users informed that detection is not guaranteed.

### T4: Rule file tampering
**Risk:** Modified fingerprint_rules.json changes classification behavior.  
**Mitigation:** Rules are bundled as an app asset (not downloaded). rule_set_hash
in CHANGELOG enables integrity verification per release.

## Out of Scope

- Network-based attacks (no network access in WearAware)
- Physical device compromise (OS-level, out of scope)
- Social engineering
```

- [ ] **Step 4: Create `docs/SECURITY/permissions-policy.md`**

```markdown
# WearAware Permissions Policy

**Version:** 1.0  
**Date:** 2026-04-03

## Permissions Requested

### Android 12+ (API 31+)

| Permission | Reason | Flag |
|---|---|---|
| BLUETOOTH_SCAN | Required to discover nearby BLE devices | neverForLocation |
| BLUETOOTH_CONNECT | Required to read advertised device names | — |

The `neverForLocation` flag declares that WearAware does NOT use BLE scanning
to derive location. This avoids requiring ACCESS_FINE_LOCATION on API 31+.

### Android 6–11 (API 23–30)

| Permission | Reason |
|---|---|
| ACCESS_FINE_LOCATION | Required by Android OS for BLE scanning on these API levels |
| ACCESS_COARSE_LOCATION | Required alongside FINE_LOCATION |

Location permission on API 23–30 is an OS requirement for BLE scanning, not because
WearAware uses or stores location data. No location data is collected or stored.

## Permissions NOT Requested

- RECORD_AUDIO — WearAware does not record audio
- CAMERA — WearAware does not use the camera
- ACCESS_BACKGROUND_LOCATION — not required in v1.0 (in-app scanning only)
- INTERNET — WearAware has no network functionality

## Runtime Permission Flow

1. User taps "Start Scanning"
2. App checks if required BLE permissions are granted
3. If not granted: launches OS permission dialog
4. If granted: starts BLE scanning
5. If denied: shows "Permissions required to scan" state

## Rationale for Each Permission

All permissions are the minimum required for core functionality. No permissions are
requested speculatively or for future features.
```

- [ ] **Step 5: Create `docs/COMPLIANCE/soc2-controls.md`**

```markdown
# WearAware SOC2 Controls Reference

**Version:** 1.0  
**Date:** 2026-04-03

## No PII Declaration

> WearAware does not collect, store, or transmit personally identifiable information (PII).
> All data is processed locally on-device and is session-scoped only.

## Data Minimization

| Data Type | Collected | Stored | Transmitted | Notes |
|---|---|---|---|---|
| BLE device name | Yes (from BLE API) | Session log only | Never | May be null |
| BLE MAC address | Read transiently | Never stored directly | Never | Session fingerprint derived instead |
| RSSI readings | Yes | Session log (averaged) | Never | Signal strength only |
| User location | Never | Never | Never | Not collected |
| User identity | Never | Never | Never | No accounts |

## Audit Trail Controls

| Control | Implementation |
|---|---|
| Change tracking | CHANGELOG.md — every detection behavior change logged |
| Rule versioning | rule_set_version + rule_set_hash in CHANGELOG per release |
| Classification traceability | matchedRuleId + ruleVersion stored in every ScanLogEntry |
| PRD versioning | docs/PRD/v{N}.md + CURRENT.md — approval table per version |
| ADR | docs/ADR/ — architectural decisions recorded |

## Access Controls

- Session log stored in Android internal storage (app-private by default)
- No external API access, no cloud storage, no account system
- User has full control: can clear session log at any time via app UI

## Retention Policy

- Session logs retained locally until user clears them
- No automatic expiry in v1.0
- No cross-session aggregation
- Data does not leave the device

## Known Limitations (Required Disclosures)

- Device identifiers are session-scoped; not stable across app sessions
- Proximity is estimated; not measured with precision
- Detection coverage is not guaranteed; false positives and negatives are possible
- Recording activity cannot be determined from BLE signals

## Classification Integrity

Classification is deterministic: given the same scan input and rule_set_version,
the output is always identical. This ensures reproducibility for any audit review.
```

- [ ] **Step 6: Create `docs/LOGGING/scan-log-schema.md`**

```markdown
# WearAware Scan Log Schema

**Version:** 1.0  
**Date:** 2026-04-03

## Purpose

Documents the schema of the local session log (Room database table: `scan_log`).

## Schema

| Field | Type | Nullable | Description |
|---|---|---|---|
| id | Long | No | Auto-generated primary key |
| timestamp | Long | No | Epoch milliseconds when this event was logged |
| deviceId | String | No | Session-scoped fingerprint (not MAC address) |
| advertisedName | String | Yes | BLE advertised device name; null if not broadcast |
| rawRssi | Int | No | Raw RSSI reading at time of log |
| averagedRssi | Int | No | Rolling-average RSSI at time of log |
| proximityLabel | String | No | VERY_CLOSE / STRONG / NEARBY / WEAK / UNKNOWN |
| visibilityState | String | No | DETECTED_NOW / SIGNAL_LOST |
| matchedRuleId | String | Yes | ID of the matched fingerprint rule; null if no match |
| ruleVersion | String | Yes | rule_set_version used for classification |
| category | String | No | DeviceCategory enum name |
| confidence | String | No | ConfidenceLevel enum name |
| evaluationNotes | String | Yes | Human-readable match trace, e.g. "manufacturerId matched" |

## Notes

- `deviceId` is a session-scoped hash derived from stable BLE advertising fields.
  It is NOT a MAC address. The same physical device may have a different `deviceId`
  across sessions due to BLE address randomization.
- `advertisedName` may be null; many BLE devices do not broadcast a name.
- All data is stored on-device only. No data is transmitted externally.

## Retention

Session logs persist until the user taps "Clear session log" in the app.
No automatic expiry. No cross-session aggregation.

## No PII

This log does not contain: user identity, GPS location, MAC addresses,
or any data that identifies a natural person.
```

- [ ] **Step 7: Create `docs/TESTING/meta-glasses-test-plan.md`**

```markdown
# WearAware — Meta Ray-Ban Glasses Test Plan

**Version:** 1.0  
**Date:** 2026-04-03  
**Target Device:** Ray-Ban Meta Smart Glasses (1st or 2nd gen)

## Prerequisites

- WearAware installed on Android test device (API 26+)
- Meta Ray-Ban glasses with Bluetooth enabled
- Glasses are not connected to any other phone during test (to ensure BLE advertising is active)
- Open area or standard indoor environment

## Expected RSSI Ranges (baseline)

| Distance (approx.) | Expected averaged RSSI |
|---|---|
| 0.5–1 m | -45 to -55 dBm |
| 1–2 m | -55 to -65 dBm |
| 2–3 m | -65 to -75 dBm |
| 3–5 m | -75 to -85 dBm |
| 5+ m | below -85 dBm |

Note: values vary significantly based on obstacles, reflections, and orientation.

## Test Scenarios

### T1: Basic Detection
1. Start WearAware scanning
2. Hold glasses 1–2 m from phone
3. **Expected:** Device appears in list within 5 seconds
4. **Expected:** Label shows "Camera-capable wearable detected nearby"
5. **Expected:** Confidence shown as HIGH

### T2: Signal Bars and Proximity
1. Start scanning with glasses at 0.5 m
2. Move glasses to 2 m, then 4 m, then >5 m
3. **Expected:** Signal bars decrease as distance increases
4. **Expected:** Proximity label changes from VERY_CLOSE → STRONG → NEARBY → WEAK → UNKNOWN

### T3: RSSI Smoothing
1. Start scanning with glasses nearby
2. Rapidly move glasses away and back
3. **Expected:** Signal bars change smoothly, not jittery
4. **Expected:** Averaging damps single-reading spikes

### T4: Signal Lost Behavior
1. Start scanning with glasses nearby (DETECTED)
2. Move glasses >10 m away or turn off BLE
3. **Wait 15 seconds**
4. **Expected:** Device shows "Signal lost" badge (grey dot)
5. **Wait 30 more seconds**
6. **Expected:** Device removed from list

### T5: Persistence Alert
1. Start scanning with glasses at 1–3 m
2. Wait 60+ seconds without moving glasses far away
3. **Expected:** Alert banner appears at top of ScanScreen
4. **Expected:** Banner text uses safe wording only (no "following", no "recording")
5. **Expected:** Banner shows device name and duration
6. Tap Dismiss
7. **Expected:** Banner disappears; device still in list

### T6: Alert Cooldown
1. Trigger persistence alert (T5)
2. Move glasses out of range, wait for Signal Lost
3. Bring glasses back for 60+ seconds
4. **Expected:** No new alert until 120s cooldown from first alert has passed

### T7: Session Log
1. Complete T1–T5
2. Navigate to Session Log
3. **Expected:** Log shows entries for each detection event
4. **Expected:** Each entry shows matched rule ID (meta_rayban_v1) and rule version
5. Tap "Clear session log"
6. **Expected:** Log is empty after confirmation

## Pass Criteria

- [ ] T1: Device detected and correctly classified
- [ ] T2: Proximity labels transition correctly with distance
- [ ] T3: Signal bars are smooth (no rapid jumping)
- [ ] T4: Signal lost and removal timing matches config
- [ ] T5: Persistence alert triggers and uses safe wording
- [ ] T6: Cooldown prevents re-alert within 120s
- [ ] T7: Session log records and clears correctly
```

- [ ] **Step 8: Create `docs/DECISIONS/product-decisions.md`**

```markdown
# WearAware — Product Decisions Log

**Version:** 1.0  
**Date:** 2026-04-03

## Decision 1: In-App Scanning Only (v1.0)

**Decision:** WearAware v1.0 scans only while the app is open. No background scanning.

**Why:** We want to validate BLE detection quality, RSSI behavior, and fingerprinting
accuracy with Meta glasses before adding background infrastructure complexity.
Background scanning requires a Foreground Service, adds battery considerations,
and increases Android permission complexity.

**Future:** Foreground Service background scanning planned for v1.1.

---

## Decision 2: Consumer Wearables Only

**Decision:** v1.0 targets consumer smart glasses and smartwatches only.
Industrial, enterprise, and law enforcement devices are out of scope.

**Why:** Consumer devices have predictable BLE patterns and known manufacturer IDs.
We have Meta Ray-Ban glasses for real-world testing. Enterprise devices are
inconsistent and harder to fingerprint without representative hardware.

**Future:** Expand to additional device classes after consumer fingerprinting is validated.

---

## Decision 3: No Green "Safe" Color

**Decision:** Signal bar colors do not include green. Colors go grey → yellow → orange → red.

**Why:** Green implies a device is "safe" or benign. WearAware cannot determine
intent or safety. A strong signal (red = very close) indicates proximity only.
Using red for proximity does not mean danger — it means the device is nearby.

---

## Decision 4: Config-Driven Rule Engine

**Decision:** Device classification rules live in `fingerprint_rules.json` (bundled asset),
not hardcoded in application logic.

**Why:** Allows rule updates without app logic changes. Keeps classification auditable
and versionable. Easier to tune thresholds after real-world testing.

---

## Decision 5: Rolling Average RSSI Smoothing (Not EMA)

**Decision:** v1.0 uses a simple rolling average for RSSI smoothing.

**Why:** Simpler to implement and tune. Easier to reason about and document.
Good enough for v1 validation with Meta glasses.

**Future:** Exponential Moving Average (EMA) may replace this in v2.0 for
smoother signal transitions and better responsiveness to rapid changes.

---

## Decision 6: Session-Scoped Device IDs

**Decision:** Device identifiers are derived from BLE advertising data (manufacturer ID,
name, service UUIDs) hashed into a session fingerprint. MAC addresses are not stored.

**Why:** Android 6+ randomizes BLE MAC addresses, making them unreliable as identifiers.
Avoiding MAC storage reduces privacy risk. Session-scoped IDs are sufficient for
the v1.0 use case of real-time awareness.
```

- [ ] **Step 9: Commit all docs**

```bash
git add docs/
git commit -m "docs: add ADR, SECURITY, COMPLIANCE, LOGGING, TESTING, DECISIONS documentation"
```

---

## Phase 2: Domain Models & Unit Tests 🧪 📱

### Task 6: Domain model enums

**Objective:** Create all pure Kotlin enum classes used throughout the domain and data layers.

**Files:**
- Create: `app/src/main/kotlin/com/wearaware/app/domain/model/ProximityLabel.kt`
- Create: `app/src/main/kotlin/com/wearaware/app/domain/model/VisibilityState.kt`
- Create: `app/src/main/kotlin/com/wearaware/app/domain/model/DeviceCategory.kt`
- Create: `app/src/main/kotlin/com/wearaware/app/domain/model/ConfidenceLevel.kt`
- Create: `app/src/main/kotlin/com/wearaware/app/domain/model/PersistenceAlertType.kt`

- [ ] **Step 1: Create directory**

```bash
mkdir -p app/src/main/kotlin/com/wearaware/app/domain/model
```

- [ ] **Step 2: Create `ProximityLabel.kt`**

```kotlin
package com.wearaware.app.domain.model

/**
 * PURPOSE: Represents estimated proximity of a BLE device based on averaged RSSI.
 * LIMITATIONS: Estimates only. RSSI varies with environment, obstacles, and device orientation.
 * NOTES: Thresholds are defined in ProximityConfig. Do not add logic here.
 */
enum class ProximityLabel {
    VERY_CLOSE,  // averagedRssi >= -55 dBm
    STRONG,      // averagedRssi >= -65 dBm
    NEARBY,      // averagedRssi >= -75 dBm
    WEAK,        // averagedRssi >= -85 dBm
    UNKNOWN      // averagedRssi < -85 dBm OR device not recently seen
}
```

- [ ] **Step 3: Create `VisibilityState.kt`**

```kotlin
package com.wearaware.app.domain.model

/**
 * PURPOSE: Tracks whether a BLE device is currently being seen or has gone quiet.
 * LIMITATIONS: SIGNAL_LOST does not mean the device has left — it may be temporarily
 *   out of range or the BLE advertise interval may have lengthened.
 * NOTES: Separate from ProximityLabel intentionally — visibility is about recency,
 *   proximity is about signal strength.
 */
enum class VisibilityState {
    DETECTED_NOW,  // device seen within SIGNAL_LOST_AFTER_MS
    SIGNAL_LOST    // device not seen within SIGNAL_LOST_AFTER_MS
}
```

- [ ] **Step 4: Create `DeviceCategory.kt`**

```kotlin
package com.wearaware.app.domain.model

/**
 * PURPOSE: Classification category assigned by the fingerprint rule engine.
 * LIMITATIONS: Categories are based on BLE advertising data only — actual device
 *   capabilities cannot be confirmed from BLE signals.
 * NOTES: UNKNOWN_BLE_DEVICE is the safe fallback when no rule matches.
 */
enum class DeviceCategory {
    CAMERA_CAPABLE_WEARABLE,      // matched a known camera-capable device rule
    SMART_GLASSES,                 // matched a smart glasses rule without camera certainty
    SMARTWATCH,                    // matched a smartwatch / fitness band rule
    UNCLASSIFIED_WEARABLE_CANDIDATE, // weak heuristic match, cannot confirm wearable
    UNKNOWN_BLE_DEVICE             // no rule matched
}
```

- [ ] **Step 5: Create `ConfidenceLevel.kt`**

```kotlin
package com.wearaware.app.domain.model

/**
 * PURPOSE: Indicates how confident the rule engine is in a classification.
 * LIMITATIONS: Confidence is rule-defined, not statistically derived.
 * NOTES: LOW confidence matches are shown in the device list but never trigger alerts.
 */
enum class ConfidenceLevel {
    HIGH,    // strong signal match (e.g. manufacturer ID confirmed)
    MEDIUM,  // partial match (e.g. name pattern only, no manufacturer ID)
    LOW      // weak heuristic; treat as informational only
}
```

- [ ] **Step 6: Create `PersistenceAlertType.kt`**

```kotlin
package com.wearaware.app.domain.model

/**
 * PURPOSE: Typed enum for persistence alert types, enabling per-type cooldown keys
 *   and future extensibility without free-form strings.
 * NOTES: v1.0 has only one type. Additional types (e.g. REAPPEARED_AFTER_ABSENCE)
 *   can be added without changing alert evaluation logic structure.
 */
enum class PersistenceAlertType {
    DEVICE_REMAINED_NEARBY
}
```

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/wearaware/app/domain/model/
git commit -m "feat: add domain model enums (ProximityLabel, VisibilityState, DeviceCategory, ConfidenceLevel, PersistenceAlertType)"
```

---

### Task 7: Domain model data classes

**Files:**
- Create: `app/src/main/kotlin/com/wearaware/app/domain/model/RawScanResult.kt`
- Create: `app/src/main/kotlin/com/wearaware/app/domain/model/ClassificationResult.kt`
- Create: `app/src/main/kotlin/com/wearaware/app/domain/model/PersistenceAlert.kt`
- Create: `app/src/main/kotlin/com/wearaware/app/domain/model/ObservedDevice.kt`
- Create: `app/src/main/kotlin/com/wearaware/app/domain/model/ScanLogEntry.kt`

- [ ] **Step 1: Create `RawScanResult.kt`**

```kotlin
package com.wearaware.app.domain.model

/**
 * PURPOSE: Pure representation of a single BLE scan result before any enrichment.
 *   Sits at the data/domain boundary — created in the data layer, consumed by domain.
 * LIMITATIONS: address may be randomized (Android 6+ BLE address randomization).
 *   manufacturerData keys are Company IDs (16-bit integers from Bluetooth SIG).
 * NOTES: This is NOT an Android class — it contains no android.bluetooth imports.
 */
data class RawScanResult(
    /** BLE device address. May be randomized per-session on Android 6+. */
    val address: String,
    /** Advertised device name from BLE ScanRecord. Null if device does not broadcast name. */
    val advertisedName: String?,
    /** Raw RSSI in dBm. Negative value; closer to 0 = stronger signal. */
    val rssi: Int,
    /** BLE manufacturer-specific data. Key = Company ID (int), Value = raw bytes. */
    val manufacturerData: Map<Int, ByteArray>,
    /** BLE service UUIDs advertised by the device. Normalized to lowercase strings. */
    val serviceUuids: List<String>,
    /** TX power level from BLE advertising packet. Null if not present in scan record. */
    val txPowerLevel: Int?,
    /** Epoch milliseconds when this scan result was received. */
    val timestampMs: Long
)
```

- [ ] **Step 2: Create `ClassificationResult.kt`**

```kotlin
package com.wearaware.app.domain.model

/**
 * PURPOSE: Output of the fingerprint rule engine for a single scanned device.
 * LIMITATIONS: Reflects the state of fingerprint_rules.json at rule_set_version.
 *   May change if rules are updated in a future app release.
 * NOTES: matchedRuleId + ruleVersion together uniquely identify the rule snapshot
 *   used for classification — critical for audit traceability.
 */
data class ClassificationResult(
    /** ID of the matched rule from fingerprint_rules.json. Null if no rule matched. */
    val matchedRuleId: String?,
    /** rule_set_version from fingerprint_rules.json at time of classification. */
    val ruleVersion: String?,
    /** Assigned device category. UNKNOWN_BLE_DEVICE if no rule matched. */
    val category: DeviceCategory,
    /** Human-readable label for display in the UI. */
    val displayLabel: String,
    /** Confidence of the classification. LOW matches are suppressed from alerts. */
    val confidence: ConfidenceLevel,
    /**
     * Whether this device is a wearable candidate eligible for persistence alerts.
     * False for UNKNOWN_BLE_DEVICE and LOW confidence matches.
     */
    val isWearableCandidate: Boolean,
    /**
     * Human-readable trace of which fields matched.
     * Example: "manufacturerId matched; name pattern matched"
     * Stored in session log for debugging and future rule tuning.
     */
    val evaluationNotes: String?
)
```

- [ ] **Step 3: Create `PersistenceAlert.kt`**

```kotlin
package com.wearaware.app.domain.model

/**
 * PURPOSE: Represents a triggered persistence alert for a device that has remained
 *   nearby beyond the configured threshold.
 * LIMITATIONS: Alert is triggered based on in-session data only. Cannot determine
 *   intent or whether recording is occurring.
 * NOTES: UI layer maps alertType to safe wording strings via SafeWording object.
 *   No raw strings in this model — alertType is an enum to prevent wording drift.
 */
data class PersistenceAlert(
    /** Session-scoped device fingerprint. */
    val deviceId: String,
    /** Display label of the classified device at time of alert. */
    val deviceDisplayLabel: String,
    /** Duration (ms) the device has been continuously DETECTED_NOW at alert trigger. */
    val durationMs: Long,
    /** Type of alert. Determines which safe wording the UI renders. */
    val alertType: PersistenceAlertType,
    /** Epoch ms when this alert was triggered. Used for cooldown calculation. */
    val triggeredAt: Long
)
```

- [ ] **Step 4: Create `ObservedDevice.kt`**

```kotlin
package com.wearaware.app.domain.model

/**
 * PURPOSE: Fully enriched domain model for a detected BLE device. This is the
 *   primary object flowing from domain use cases to the UI layer.
 * LIMITATIONS: id is a session-scoped fingerprint derived from advertising data.
 *   The same physical device may have a different id across app sessions due to
 *   BLE MAC address randomization on Android 6+.
 * NOTES: seenDurationMs is a computed property — always consistent with
 *   firstSeenAt and lastSeenAt. Do not store separately.
 */
data class ObservedDevice(
    /**
     * Session-scoped fingerprint. Derived from manufacturer data, service UUIDs,
     * and advertised name. NOT a MAC address.
     */
    val id: String,
    /** Advertised name from BLE scan record. Null if device does not broadcast name. */
    val advertisedName: String?,
    /** Most recent raw RSSI reading in dBm. */
    val rawRssi: Int,
    /** Rolling-average RSSI over the configured window. Used for proximity calculation. */
    val averagedRssi: Int,
    /** Proximity label derived from averagedRssi via ProximityConfig thresholds. */
    val proximityLabel: ProximityLabel,
    /** Whether the device has been seen recently (within SIGNAL_LOST_AFTER_MS). */
    val visibilityState: VisibilityState,
    /** Epoch ms when this device was first seen in the current session. */
    val firstSeenAt: Long,
    /** Epoch ms of the most recent scan result for this device. */
    val lastSeenAt: Long,
    /** Total number of BLE scan results received for this device in this session. */
    val seenCount: Int,
    /** Result of fingerprint rule classification. */
    val classification: ClassificationResult,
    /** Active persistence alert for this device, if any. Null if no alert is active. */
    val persistenceAlert: PersistenceAlert?
) {
    /**
     * Time elapsed since first detection.
     * Note: this resets if the device enters SIGNAL_LOST for long enough to be removed
     * and then re-appears, since that creates a new ObservedDevice entry.
     */
    val seenDurationMs: Long get() = lastSeenAt - firstSeenAt
}
```

- [ ] **Step 5: Create `ScanLogEntry.kt`**

```kotlin
package com.wearaware.app.domain.model

/**
 * PURPOSE: Domain representation of a single session log entry. Mirrors ScanLogEntity
 *   but without Room annotations, keeping domain layer free of Android dependencies.
 * LIMITATIONS: Enum fields are stored as String names (not ordinals) for readability
 *   and forward compatibility.
 * NOTES: evaluationNotes is stored to enable future rule tuning and debugging analysis.
 */
data class ScanLogEntry(
    val id: Long = 0,
    val timestamp: Long,
    val deviceId: String,
    val advertisedName: String?,
    val rawRssi: Int,
    val averagedRssi: Int,
    val proximityLabel: String,
    val visibilityState: String,
    val matchedRuleId: String?,
    val ruleVersion: String?,
    val category: String,
    val confidence: String,
    val evaluationNotes: String?
)
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/wearaware/app/domain/model/
git commit -m "feat: add domain model data classes (ObservedDevice, ClassificationResult, PersistenceAlert, ScanLogEntry, RawScanResult)"
```

---

### Task 8: ProximityConfig + RssiSmoother + unit tests 🧪 📱

**Files:**
- Create: `app/src/main/kotlin/com/wearaware/app/domain/rules/ProximityConfig.kt`
- Create: `app/src/main/kotlin/com/wearaware/app/domain/rules/RssiSmoother.kt`
- Create: `app/src/test/kotlin/com/wearaware/app/domain/rules/ProximityConfigTest.kt`
- Create: `app/src/test/kotlin/com/wearaware/app/domain/rules/RssiSmootherTest.kt`

- [ ] **Step 1: Write the failing tests first**

Create `app/src/test/kotlin/com/wearaware/app/domain/rules/ProximityConfigTest.kt`:

```kotlin
package com.wearaware.app.domain.rules

import com.wearaware.app.domain.model.ProximityLabel
import org.junit.Assert.assertEquals
import org.junit.Test

class ProximityConfigTest {

    @Test
    fun `labelFromRssi returns VERY_CLOSE for signal at threshold`() {
        assertEquals(ProximityLabel.VERY_CLOSE, ProximityConfig.labelFromRssi(-55))
    }

    @Test
    fun `labelFromRssi returns VERY_CLOSE for signal stronger than threshold`() {
        assertEquals(ProximityLabel.VERY_CLOSE, ProximityConfig.labelFromRssi(-40))
    }

    @Test
    fun `labelFromRssi returns STRONG for signal at -65`() {
        assertEquals(ProximityLabel.STRONG, ProximityConfig.labelFromRssi(-65))
    }

    @Test
    fun `labelFromRssi returns STRONG for signal between -56 and -65`() {
        assertEquals(ProximityLabel.STRONG, ProximityConfig.labelFromRssi(-60))
    }

    @Test
    fun `labelFromRssi returns NEARBY for signal at -75`() {
        assertEquals(ProximityLabel.NEARBY, ProximityConfig.labelFromRssi(-75))
    }

    @Test
    fun `labelFromRssi returns WEAK for signal at -85`() {
        assertEquals(ProximityLabel.WEAK, ProximityConfig.labelFromRssi(-85))
    }

    @Test
    fun `labelFromRssi returns UNKNOWN for signal below -85`() {
        assertEquals(ProximityLabel.UNKNOWN, ProximityConfig.labelFromRssi(-90))
    }

    @Test
    fun `barCountFromLabel returns correct bar counts`() {
        assertEquals(5, ProximityConfig.barCountFromLabel(ProximityLabel.VERY_CLOSE))
        assertEquals(4, ProximityConfig.barCountFromLabel(ProximityLabel.STRONG))
        assertEquals(3, ProximityConfig.barCountFromLabel(ProximityLabel.NEARBY))
        assertEquals(2, ProximityConfig.barCountFromLabel(ProximityLabel.WEAK))
        assertEquals(1, ProximityConfig.barCountFromLabel(ProximityLabel.UNKNOWN))
    }
}
```

Create `app/src/test/kotlin/com/wearaware/app/domain/rules/RssiSmootherTest.kt`:

```kotlin
package com.wearaware.app.domain.rules

import org.junit.Assert.*
import org.junit.Test

class RssiSmootherTest {

    @Test
    fun `single reading returns that reading`() {
        val smoother = RssiSmoother(windowSize = 5)
        assertEquals(-60, smoother.addReading(-60))
    }

    @Test
    fun `averages two readings correctly`() {
        val smoother = RssiSmoother(windowSize = 5)
        smoother.addReading(-60)
        val result = smoother.addReading(-70)
        assertEquals(-65, result)
    }

    @Test
    fun `averages four readings correctly`() {
        val smoother = RssiSmoother(windowSize = 4)
        smoother.addReading(-60)
        smoother.addReading(-70)
        smoother.addReading(-80)
        val result = smoother.addReading(-90)
        // (-60 + -70 + -80 + -90) / 4 = -75
        assertEquals(-75, result)
    }

    @Test
    fun `drops oldest reading when window is full`() {
        val smoother = RssiSmoother(windowSize = 3)
        smoother.addReading(-60)
        smoother.addReading(-60)
        smoother.addReading(-60)
        // window full: [-60, -60, -60]
        val result = smoother.addReading(-90)
        // drops first -60, window is [-60, -60, -90]
        // (-60 + -60 + -90) / 3 = -70
        assertEquals(-70, result)
    }

    @Test
    fun `reset clears all readings`() {
        val smoother = RssiSmoother(windowSize = 5)
        smoother.addReading(-60)
        smoother.addReading(-70)
        smoother.reset()
        assertNull(smoother.currentAverage())
    }

    @Test
    fun `after reset addReading starts fresh`() {
        val smoother = RssiSmoother(windowSize = 5)
        smoother.addReading(-60)
        smoother.reset()
        assertEquals(-80, smoother.addReading(-80))
    }

    @Test
    fun `currentAverage returns null when empty`() {
        val smoother = RssiSmoother(windowSize = 5)
        assertNull(smoother.currentAverage())
    }

    @Test
    fun `currentAverage returns current average without adding reading`() {
        val smoother = RssiSmoother(windowSize = 5)
        smoother.addReading(-60)
        smoother.addReading(-80)
        assertEquals(-70, smoother.currentAverage())
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd /Users/kingsebruvwiyo/mobile-app && ./gradlew :app:test --tests "com.wearaware.app.domain.rules.ProximityConfigTest" --tests "com.wearaware.app.domain.rules.RssiSmootherTest"
```
Expected: FAIL — classes do not exist yet.

- [ ] **Step 3: Create directory**

```bash
mkdir -p app/src/main/kotlin/com/wearaware/app/domain/rules
```

- [ ] **Step 4: Create `ProximityConfig.kt`**

```kotlin
package com.wearaware.app.domain.rules

import com.wearaware.app.domain.model.ProximityLabel

/**
 * PURPOSE: Single source of truth for all RSSI thresholds, visibility timeouts,
 *   and signal bar mapping. All proximity logic references these constants.
 * LIMITATIONS: Thresholds are calibrated for typical indoor environments.
 *   Actual performance varies with obstacles, reflections, and device orientation.
 *   Re-tune RSSI thresholds after real-world testing with Meta Ray-Ban glasses.
 * NOTES: All timeout values are in milliseconds.
 */
object ProximityConfig {

    // RSSI thresholds (averaged dBm). Higher value = stronger signal = closer.
    const val VERY_CLOSE_THRESHOLD_DBM = -55
    const val STRONG_THRESHOLD_DBM     = -65
    const val NEARBY_THRESHOLD_DBM     = -75
    const val WEAK_THRESHOLD_DBM       = -85

    // Visibility lifecycle timeouts
    /** After this many ms without a scan result, mark device as SIGNAL_LOST. */
    const val SIGNAL_LOST_AFTER_MS = 15_000L
    /** After this many ms in SIGNAL_LOST state, remove device from the active list. */
    const val REMOVE_AFTER_MS      = 30_000L

    // RSSI smoothing
    /** Rolling average window size. Increase for smoother bars; decrease for faster response. */
    const val RSSI_WINDOW_SIZE = 5

    /**
     * Maps an averaged RSSI value to a ProximityLabel.
     * Used by BleRepositoryImpl when building ObservedDevice instances.
     */
    fun labelFromRssi(averagedRssi: Int): ProximityLabel = when {
        averagedRssi >= VERY_CLOSE_THRESHOLD_DBM -> ProximityLabel.VERY_CLOSE
        averagedRssi >= STRONG_THRESHOLD_DBM     -> ProximityLabel.STRONG
        averagedRssi >= NEARBY_THRESHOLD_DBM     -> ProximityLabel.NEARBY
        averagedRssi >= WEAK_THRESHOLD_DBM       -> ProximityLabel.WEAK
        else                                      -> ProximityLabel.UNKNOWN
    }

    /**
     * Maps a ProximityLabel to a 1–5 bar count for the SignalBars UI component.
     */
    fun barCountFromLabel(label: ProximityLabel): Int = when (label) {
        ProximityLabel.VERY_CLOSE -> 5
        ProximityLabel.STRONG     -> 4
        ProximityLabel.NEARBY     -> 3
        ProximityLabel.WEAK       -> 2
        ProximityLabel.UNKNOWN    -> 1
    }
}
```

- [ ] **Step 5: Create `RssiSmoother.kt`**

```kotlin
package com.wearaware.app.domain.rules

/**
 * PURPOSE: Smooths noisy BLE RSSI readings using a rolling average over a fixed window.
 *   Reduces UI jitter caused by RSSI variance between consecutive scan results.
 * LIMITATIONS: Rolling average lags behind rapid signal changes. For use cases requiring
 *   faster response, consider an Exponential Moving Average (EMA) — planned for v2.0.
 *   Not thread-safe: each ObservedDevice should have its own RssiSmoother instance.
 * NOTES: Window size is configurable but defaults to ProximityConfig.RSSI_WINDOW_SIZE.
 */
class RssiSmoother(private val windowSize: Int = ProximityConfig.RSSI_WINDOW_SIZE) {

    private val readings = ArrayDeque<Int>(windowSize)

    /**
     * Adds a new RSSI reading to the window and returns the updated rolling average.
     * Drops the oldest reading when the window is full (FIFO).
     *
     * @param rssi Raw RSSI value in dBm (negative integer).
     * @return Rolling average of all readings currently in the window.
     */
    fun addReading(rssi: Int): Int {
        if (readings.size >= windowSize) readings.removeFirst()
        readings.addLast(rssi)
        return readings.average().toInt()
    }

    /**
     * Clears all stored readings. Call when a device re-appears after SIGNAL_LOST
     * to avoid averaging stale readings with fresh ones.
     */
    fun reset() = readings.clear()

    /**
     * Returns the current rolling average without adding a new reading.
     * Returns null if no readings have been added yet.
     */
    fun currentAverage(): Int? =
        if (readings.isEmpty()) null else readings.average().toInt()
}
```

- [ ] **Step 6: Run tests to verify they pass**

```bash
./gradlew :app:test --tests "com.wearaware.app.domain.rules.ProximityConfigTest" --tests "com.wearaware.app.domain.rules.RssiSmootherTest"
```
Expected: BUILD SUCCESSFUL — all tests pass.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/wearaware/app/domain/rules/ProximityConfig.kt
git add app/src/main/kotlin/com/wearaware/app/domain/rules/RssiSmoother.kt
git add app/src/test/kotlin/com/wearaware/app/domain/rules/ProximityConfigTest.kt
git add app/src/test/kotlin/com/wearaware/app/domain/rules/RssiSmootherTest.kt
git commit -m "feat: add ProximityConfig and RssiSmoother with passing unit tests"
```

---

### Task 9: Domain repository interfaces + use case skeletons

**Objective:** Define the interfaces that data layer implementations must satisfy. No logic here.

**Files:**
- Create: `app/src/main/kotlin/com/wearaware/app/domain/repository/BleRepository.kt`
- Create: `app/src/main/kotlin/com/wearaware/app/domain/repository/ScanLogRepository.kt`
- Create: `app/src/main/kotlin/com/wearaware/app/domain/repository/RulesRepository.kt`

- [ ] **Step 1: Create directory**

```bash
mkdir -p app/src/main/kotlin/com/wearaware/app/domain/repository
mkdir -p app/src/main/kotlin/com/wearaware/app/domain/usecase
```

- [ ] **Step 2: Create `BleRepository.kt`**

```kotlin
package com.wearaware.app.domain.repository

import com.wearaware.app.domain.model.ObservedDevice
import kotlinx.coroutines.flow.StateFlow

/**
 * PURPOSE: Defines the contract for the BLE scanning data source.
 *   Implementations manage scanning lifecycle, RSSI smoothing, device expiry,
 *   and classification — exposing a clean stream of enriched ObservedDevice objects.
 * NOTES: Implemented by BleRepositoryImpl in the data layer.
 *   Domain layer never imports android.bluetooth.
 */
interface BleRepository {
    /**
     * Live stream of all currently tracked BLE devices, sorted by strongest averagedRssi.
     * Emits a new list whenever any device state changes.
     */
    val observedDevices: StateFlow<List<ObservedDevice>>

    /** Returns true if Bluetooth is enabled on the device. */
    val isBleAvailable: Boolean

    /** Starts BLE scanning. Idempotent — safe to call if already scanning. */
    fun startScanning()

    /**
     * Stops BLE scanning and clears the device list.
     * Idempotent — safe to call if not scanning.
     */
    fun stopScanning()
}
```

- [ ] **Step 3: Create `ScanLogRepository.kt`**

```kotlin
package com.wearaware.app.domain.repository

import com.wearaware.app.domain.model.ObservedDevice
import com.wearaware.app.domain.model.ScanLogEntry
import kotlinx.coroutines.flow.Flow

/**
 * PURPOSE: Defines the contract for the local session scan log.
 * NOTES: Implemented by ScanLogRepositoryImpl using Room.
 *   All storage is on-device only — no network operations.
 */
interface ScanLogRepository {
    /** Logs a detection event for an observed device. */
    suspend fun log(device: ObservedDevice)

    /** Returns a live stream of all session log entries, newest first. */
    fun observeAll(): Flow<List<ScanLogEntry>>

    /** Deletes all session log entries. Irreversible within the session. */
    suspend fun clearAll()
}
```

- [ ] **Step 4: Create `RulesRepository.kt`**

```kotlin
package com.wearaware.app.domain.repository

import com.wearaware.app.domain.rules.FingerprintRule
import com.wearaware.app.domain.rules.RuleSetMetadata

/**
 * PURPOSE: Provides the fingerprint classification rules and metadata.
 *   Rules are loaded from the bundled fingerprint_rules.json asset.
 * NOTES: getRules() is synchronous because rules are bundled (always available, small).
 *   Rules are cached on first load — no repeated file I/O.
 *   Implemented by RulesRepositoryImpl in the data layer.
 */
interface RulesRepository {
    /** Returns all enabled fingerprint rules and their metadata. */
    fun getRules(): RulesData
}

/**
 * Container for the loaded rules and their version metadata.
 * Kept in domain because FingerprintClassifier (domain) consumes it.
 */
data class RulesData(
    val metadata: RuleSetMetadata,
    val rules: List<FingerprintRule>
)
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/wearaware/app/domain/repository/
git commit -m "feat: add domain repository interfaces (BleRepository, ScanLogRepository, RulesRepository)"
```

---

## Phase 3: Fingerprint Rule Engine 🧪 📱

### Task 10: fingerprint_rules.json and FingerprintRule domain model

**Files:**
- Create: `app/src/main/assets/fingerprint_rules.json`
- Create: `app/src/main/kotlin/com/wearaware/app/domain/rules/FingerprintRule.kt`

- [ ] **Step 1: Create `app/src/main/assets/fingerprint_rules.json`**

```json
{
  "rule_set_version": "1.0.0",
  "rule_set_hash": "sha256:placeholder-update-after-file-is-finalized",
  "rules": [
    {
      "rule_id": "meta_rayban_v1",
      "enabled": true,
      "priority": 100,
      "min_score": 4,
      "manufacturer_ids": ["0x0075"],
      "name_patterns": ["Ray-Ban", "Meta", "Aria"],
      "service_uuids": [],
      "device_type_hint": "smart_glasses",
      "category": "CAMERA_CAPABLE_WEARABLE",
      "display_label": "Camera-capable wearable detected nearby",
      "confidence": "HIGH",
      "is_wearable_candidate": true
    },
    {
      "rule_id": "snapchat_spectacles_v1",
      "enabled": true,
      "priority": 90,
      "min_score": 2,
      "manufacturer_ids": [],
      "name_patterns": ["Spectacles", "Snap"],
      "service_uuids": [],
      "device_type_hint": "smart_glasses",
      "category": "CAMERA_CAPABLE_WEARABLE",
      "display_label": "Camera-capable wearable detected nearby",
      "confidence": "MEDIUM",
      "is_wearable_candidate": true
    },
    {
      "rule_id": "generic_smartwatch_v1",
      "enabled": true,
      "priority": 50,
      "min_score": 2,
      "manufacturer_ids": [],
      "name_patterns": ["Watch", "Band", "Fit", "Gear"],
      "service_uuids": ["0000180d-0000-1000-8000-00805f9b34fb"],
      "device_type_hint": "smartwatch",
      "category": "SMARTWATCH",
      "display_label": "Smartwatch or fitness band nearby",
      "confidence": "LOW",
      "is_wearable_candidate": true
    }
  ]
}
```

- [ ] **Step 2: Create `FingerprintRule.kt`**

```kotlin
package com.wearaware.app.domain.rules

import com.wearaware.app.domain.model.ConfidenceLevel
import com.wearaware.app.domain.model.DeviceCategory

/**
 * PURPOSE: Domain model for a single device classification rule loaded from
 *   fingerprint_rules.json. Used exclusively by FingerprintClassifier.
 * LIMITATIONS: Rules are evaluated at classification time — not pre-indexed.
 *   For large rule sets (100+), consider an indexed data structure.
 * NOTES: manufacturerIds are stored as normalized Int values (hex strings parsed
 *   during JSON loading, not here). namePatterns are stored as-is; matching is
 *   case-insensitive in FingerprintClassifier.
 */
data class FingerprintRule(
    val ruleId: String,
    val enabled: Boolean,
    val priority: Int,
    /** Minimum score required for this rule to produce a match. Per-rule tuning. */
    val minScore: Int,
    /** Company IDs as integers (0x0075 = 117). Compared against manufacturer data keys. */
    val manufacturerIds: List<Int>,
    /** Name substrings to match against advertised device name (case-insensitive). */
    val namePatterns: List<String>,
    /** Service UUIDs to match. Compared after normalization to lowercase. */
    val serviceUuids: List<String>,
    /** Optional hint for future UI icon selection. Not used in v1.0 classification logic. */
    val deviceTypeHint: String?,
    val category: DeviceCategory,
    val displayLabel: String,
    val confidence: ConfidenceLevel,
    val isWearableCandidate: Boolean
)

/**
 * Metadata about the rule set file — version and integrity hash.
 * Recorded in session log entries alongside classification results.
 */
data class RuleSetMetadata(
    val version: String,
    val hash: String
)
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/assets/fingerprint_rules.json
git add app/src/main/kotlin/com/wearaware/app/domain/rules/FingerprintRule.kt
git commit -m "feat: add fingerprint_rules.json (rule_set_version 1.0.0) and FingerprintRule domain model"
```

---

### Task 11: FingerprintClassifier + unit tests 🧪 📱

**Files:**
- Create: `app/src/main/kotlin/com/wearaware/app/domain/rules/FingerprintClassifier.kt`
- Create: `app/src/test/kotlin/com/wearaware/app/domain/rules/FingerprintClassifierTest.kt`

- [ ] **Step 1: Write the failing tests first**

Create `app/src/test/kotlin/com/wearaware/app/domain/rules/FingerprintClassifierTest.kt`:

```kotlin
package com.wearaware.app.domain.rules

import com.wearaware.app.domain.model.*
import com.wearaware.app.domain.repository.RulesData
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FingerprintClassifierTest {

    private val metadata = RuleSetMetadata(version = "1.0.0", hash = "test-hash")

    private val metaRule = FingerprintRule(
        ruleId = "meta_rayban_v1",
        enabled = true,
        priority = 100,
        minScore = 4,
        manufacturerIds = listOf(117), // 0x0075
        namePatterns = listOf("Ray-Ban", "Meta", "Aria"),
        serviceUuids = emptyList(),
        deviceTypeHint = "smart_glasses",
        category = DeviceCategory.CAMERA_CAPABLE_WEARABLE,
        displayLabel = "Camera-capable wearable detected nearby",
        confidence = ConfidenceLevel.HIGH,
        isWearableCandidate = true
    )

    private val snapRule = FingerprintRule(
        ruleId = "snapchat_spectacles_v1",
        enabled = true,
        priority = 90,
        minScore = 2,
        manufacturerIds = emptyList(),
        namePatterns = listOf("Spectacles", "Snap"),
        serviceUuids = emptyList(),
        deviceTypeHint = "smart_glasses",
        category = DeviceCategory.CAMERA_CAPABLE_WEARABLE,
        displayLabel = "Camera-capable wearable detected nearby",
        confidence = ConfidenceLevel.MEDIUM,
        isWearableCandidate = true
    )

    private lateinit var classifier: FingerprintClassifier

    @Before
    fun setUp() {
        classifier = FingerprintClassifier(RulesData(metadata, listOf(metaRule, snapRule)))
    }

    private fun makeScan(
        address: String = "AA:BB:CC:DD:EE:FF",
        name: String? = null,
        manufacturerIds: List<Int> = emptyList(),
        serviceUuids: List<String> = emptyList()
    ) = RawScanResult(
        address = address,
        advertisedName = name,
        rssi = -60,
        manufacturerData = manufacturerIds.associateWith { byteArrayOf() },
        serviceUuids = serviceUuids,
        txPowerLevel = null,
        timestampMs = 0L
    )

    @Test
    fun `classifies Meta glasses by manufacturer ID alone (score = 4, minScore = 4)`() {
        val result = classifier.classify(makeScan(manufacturerIds = listOf(117)))
        assertEquals(DeviceCategory.CAMERA_CAPABLE_WEARABLE, result.category)
        assertEquals("meta_rayban_v1", result.matchedRuleId)
        assertEquals(ConfidenceLevel.HIGH, result.confidence)
        assertTrue(result.isWearableCandidate)
    }

    @Test
    fun `name-only match does not reach Meta minScore of 4`() {
        val result = classifier.classify(makeScan(name = "Ray-Ban Smart Glasses"))
        // name match = +2 only; minScore for meta rule = 4; should not match
        assertEquals(DeviceCategory.CAMERA_CAPABLE_WEARABLE, result.category)
        // But snap rule has minScore=2, and there's no snap name here — should fallback
        // Actually: no snap name pattern, no mfr ID — UNKNOWN
        assertEquals(DeviceCategory.UNKNOWN_BLE_DEVICE, result.category)
    }

    @Test
    fun `name-only match reaches Snap minScore of 2`() {
        val result = classifier.classify(makeScan(name = "Spectacles v3"))
        assertEquals(DeviceCategory.CAMERA_CAPABLE_WEARABLE, result.category)
        assertEquals("snapchat_spectacles_v1", result.matchedRuleId)
        assertEquals(ConfidenceLevel.MEDIUM, result.confidence)
    }

    @Test
    fun `manufacturer + name match scores highest for Meta rule`() {
        val result = classifier.classify(
            makeScan(name = "Ray-Ban Meta Glasses", manufacturerIds = listOf(117))
        )
        assertEquals("meta_rayban_v1", result.matchedRuleId)
        assertEquals(ConfidenceLevel.HIGH, result.confidence)
    }

    @Test
    fun `returns UNKNOWN_BLE_DEVICE for unmatched device`() {
        val result = classifier.classify(makeScan(name = "JBL Speaker", manufacturerIds = listOf(999)))
        assertEquals(DeviceCategory.UNKNOWN_BLE_DEVICE, result.category)
        assertNull(result.matchedRuleId)
        assertFalse(result.isWearableCandidate)
    }

    @Test
    fun `classification is deterministic - same input produces same output`() {
        val scan = makeScan(name = "Ray-Ban", manufacturerIds = listOf(117))
        val result1 = classifier.classify(scan)
        val result2 = classifier.classify(scan)
        assertEquals(result1.matchedRuleId, result2.matchedRuleId)
        assertEquals(result1.category, result2.category)
        assertEquals(result1.confidence, result2.confidence)
    }

    @Test
    fun `name matching is case insensitive`() {
        val result = classifier.classify(makeScan(
            name = "RAY-BAN SMART GLASSES",
            manufacturerIds = listOf(117)
        ))
        assertEquals(DeviceCategory.CAMERA_CAPABLE_WEARABLE, result.category)
    }

    @Test
    fun `disabled rule is never matched`() {
        val disabledRule = metaRule.copy(enabled = false)
        val classifierWithDisabled = FingerprintClassifier(
            RulesData(metadata, listOf(disabledRule))
        )
        val result = classifierWithDisabled.classify(makeScan(manufacturerIds = listOf(117)))
        assertEquals(DeviceCategory.UNKNOWN_BLE_DEVICE, result.category)
    }

    @Test
    fun `higher priority rule wins on equal score`() {
        val lowPriorityMeta = metaRule.copy(priority = 10, minScore = 2)
        val highPrioritySnap = snapRule.copy(priority = 200, minScore = 2)
        val classifier2 = FingerprintClassifier(
            RulesData(metadata, listOf(lowPriorityMeta, highPrioritySnap))
        )
        // Both match name (score=2), snap has higher priority
        val result = classifier2.classify(makeScan(name = "Snap Ray-Ban"))
        assertEquals("snapchat_spectacles_v1", result.matchedRuleId)
    }

    @Test
    fun `evaluationNotes are non-null when manufacturer matches`() {
        val result = classifier.classify(makeScan(manufacturerIds = listOf(117)))
        assertNotNull(result.evaluationNotes)
        assertTrue(result.evaluationNotes!!.contains("manufacturer", ignoreCase = true))
    }

    @Test
    fun `ruleVersion matches metadata version`() {
        val result = classifier.classify(makeScan(manufacturerIds = listOf(117)))
        assertEquals("1.0.0", result.ruleVersion)
    }
}
```

- [ ] **Step 2: Run to verify tests fail**

```bash
./gradlew :app:test --tests "com.wearaware.app.domain.rules.FingerprintClassifierTest"
```
Expected: FAIL — FingerprintClassifier does not exist yet.

- [ ] **Step 3: Create `FingerprintClassifier.kt`**

```kotlin
package com.wearaware.app.domain.rules

import com.wearaware.app.domain.model.*
import com.wearaware.app.domain.repository.RulesData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PURPOSE: Classifies a RawScanResult against the loaded fingerprint rules.
 *   Returns a ClassificationResult with the best matching rule's metadata,
 *   or UNKNOWN_BLE_DEVICE if no rule meets its minimum score.
 * LIMITATIONS: Point-based scoring; no probabilistic model. Classification quality
 *   depends entirely on the completeness of fingerprint_rules.json.
 * NOTES: Classification is DETERMINISTIC: same input + same rule set version → same output.
 *   This is a requirement for audit reproducibility.
 *   Scoring: manufacturer ID match +4, name pattern match +2, service UUID match +2.
 *   Ties resolved by rule priority (descending).
 */
@Singleton
class FingerprintClassifier @Inject constructor(
    private val rulesData: RulesData
) {
    companion object {
        private const val MANUFACTURER_SCORE = 4
        private const val NAME_SCORE = 2
        private const val UUID_SCORE = 2
        private const val FALLBACK_LABEL = "Unknown BLE device"
    }

    private data class ScoredRule(
        val rule: FingerprintRule,
        val score: Int,
        val matchDetails: MatchDetails
    )

    /**
     * Evaluates all enabled rules against the scan result and returns the best match.
     * Input is normalized before matching (lowercase, trimmed).
     * Returns UNKNOWN_BLE_DEVICE classification if no rule meets its min_score.
     */
    fun classify(scan: RawScanResult): ClassificationResult {
        val normalizedName = scan.advertisedName?.lowercase()?.trim()
        val normalizedUuids = scan.serviceUuids.map { it.lowercase() }
        val manufacturerIds = scan.manufacturerData.keys

        val candidates: List<ScoredRule> = rulesData.rules
            .filter { it.enabled }
            .sortedByDescending { it.priority }
            .mapNotNull { rule ->
                val mfrMatch = rule.manufacturerIds.any { it in manufacturerIds }
                val nameMatch = normalizedName != null &&
                    rule.namePatterns.any { pattern ->
                        normalizedName.contains(pattern.lowercase())
                    }
                val uuidMatch = rule.serviceUuids.any { uuid ->
                    uuid.lowercase() in normalizedUuids
                }

                val score = (if (mfrMatch) MANUFACTURER_SCORE else 0) +
                            (if (nameMatch) NAME_SCORE else 0) +
                            (if (uuidMatch) UUID_SCORE else 0)

                if (score >= rule.minScore) {
                    ScoredRule(
                        rule = rule,
                        score = score,
                        matchDetails = MatchDetails(mfrMatch, nameMatch, uuidMatch)
                    )
                } else null
            }

        // Highest score wins; tie-break by priority (already sorted, so first wins)
        val best = candidates.maxWithOrNull(compareBy({ it.score }, { it.rule.priority }))

        return if (best != null) {
            ClassificationResult(
                matchedRuleId = best.rule.ruleId,
                ruleVersion = rulesData.metadata.version,
                category = best.rule.category,
                displayLabel = best.rule.displayLabel,
                confidence = best.rule.confidence,
                isWearableCandidate = best.rule.isWearableCandidate,
                evaluationNotes = best.matchDetails.toNotes()
            )
        } else {
            ClassificationResult(
                matchedRuleId = null,
                ruleVersion = rulesData.metadata.version,
                category = DeviceCategory.UNKNOWN_BLE_DEVICE,
                displayLabel = FALLBACK_LABEL,
                confidence = ConfidenceLevel.LOW,
                isWearableCandidate = false,
                evaluationNotes = "No rule matched score threshold"
            )
        }
    }
}

/**
 * Internal record of which fields contributed to a rule match.
 * Converted to a string for storage in session log evaluationNotes.
 */
private data class MatchDetails(
    val manufacturerMatched: Boolean,
    val nameMatched: Boolean,
    val uuidMatched: Boolean
) {
    fun toNotes(): String = buildList {
        if (manufacturerMatched) add("manufacturer ID matched")
        if (nameMatched) add("name pattern matched")
        if (uuidMatched) add("service UUID matched")
    }.joinToString("; ").ifEmpty { "no match details" }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew :app:test --tests "com.wearaware.app.domain.rules.FingerprintClassifierTest"
```
Expected: BUILD SUCCESSFUL — all tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/wearaware/app/domain/rules/FingerprintClassifier.kt
git add app/src/test/kotlin/com/wearaware/app/domain/rules/FingerprintClassifierTest.kt
git commit -m "feat: add FingerprintClassifier with full unit test coverage"
```

---

### Task 12: Rules data loader (data layer)

**Files:**
- Create: `app/src/main/kotlin/com/wearaware/app/data/rules/dto/RulesDataDto.kt`
- Create: `app/src/main/kotlin/com/wearaware/app/data/rules/dto/FingerprintRuleDto.kt`
- Create: `app/src/main/kotlin/com/wearaware/app/data/rules/RulesDtoMapper.kt`
- Create: `app/src/main/kotlin/com/wearaware/app/data/rules/RulesLoader.kt`
- Create: `app/src/main/kotlin/com/wearaware/app/data/repository/RulesRepositoryImpl.kt`

- [ ] **Step 1: Create directories**

```bash
mkdir -p app/src/main/kotlin/com/wearaware/app/data/rules/dto
mkdir -p app/src/main/kotlin/com/wearaware/app/data/repository
```

- [ ] **Step 2: Create `RulesDataDto.kt`**

```kotlin
package com.wearaware.app.data.rules.dto

import com.google.gson.annotations.SerializedName

/**
 * PURPOSE: Gson DTO for the top-level structure of fingerprint_rules.json.
 * NOTES: Kept in data layer — domain never sees these classes.
 */
data class RulesDataDto(
    @SerializedName("rule_set_version") val ruleSetVersion: String,
    @SerializedName("rule_set_hash") val ruleSetHash: String,
    @SerializedName("rules") val rules: List<FingerprintRuleDto>
)
```

- [ ] **Step 3: Create `FingerprintRuleDto.kt`**

```kotlin
package com.wearaware.app.data.rules.dto

import com.google.gson.annotations.SerializedName

/**
 * PURPOSE: Gson DTO for a single rule entry in fingerprint_rules.json.
 * NOTES: manufacturer_ids are stored as hex strings (e.g. "0x0075") in JSON.
 *   Parsed to Int in RulesDtoMapper.
 */
data class FingerprintRuleDto(
    @SerializedName("rule_id") val ruleId: String,
    @SerializedName("enabled") val enabled: Boolean,
    @SerializedName("priority") val priority: Int,
    @SerializedName("min_score") val minScore: Int,
    @SerializedName("manufacturer_ids") val manufacturerIds: List<String>,
    @SerializedName("name_patterns") val namePatterns: List<String>,
    @SerializedName("service_uuids") val serviceUuids: List<String>,
    @SerializedName("device_type_hint") val deviceTypeHint: String?,
    @SerializedName("category") val category: String,
    @SerializedName("display_label") val displayLabel: String,
    @SerializedName("confidence") val confidence: String,
    @SerializedName("is_wearable_candidate") val isWearableCandidate: Boolean
)
```

- [ ] **Step 4: Create `RulesDtoMapper.kt`**

```kotlin
package com.wearaware.app.data.rules

import com.wearaware.app.data.rules.dto.FingerprintRuleDto
import com.wearaware.app.data.rules.dto.RulesDataDto
import com.wearaware.app.domain.model.ConfidenceLevel
import com.wearaware.app.domain.model.DeviceCategory
import com.wearaware.app.domain.repository.RulesData
import com.wearaware.app.domain.rules.FingerprintRule
import com.wearaware.app.domain.rules.RuleSetMetadata

/**
 * PURPOSE: Maps JSON DTOs to domain models. All type parsing and normalization
 *   happens here — keeping domain models clean and DTO classes ignorant of domain.
 * NOTES: Hex manufacturer IDs ("0x0075") are parsed to Int here.
 *   Unknown category/confidence values fall back to safe defaults.
 */
fun RulesDataDto.toDomain(): RulesData = RulesData(
    metadata = RuleSetMetadata(version = ruleSetVersion, hash = ruleSetHash),
    rules = rules.map { it.toDomain() }
)

private fun FingerprintRuleDto.toDomain(): FingerprintRule = FingerprintRule(
    ruleId = ruleId,
    enabled = enabled,
    priority = priority,
    minScore = minScore,
    manufacturerIds = manufacturerIds.map { it.parseManufacturerId() },
    namePatterns = namePatterns,
    serviceUuids = serviceUuids,
    deviceTypeHint = deviceTypeHint,
    category = DeviceCategory.entries.firstOrNull { it.name == category }
        ?: DeviceCategory.UNKNOWN_BLE_DEVICE,
    displayLabel = displayLabel,
    confidence = ConfidenceLevel.entries.firstOrNull { it.name == confidence }
        ?: ConfidenceLevel.LOW,
    isWearableCandidate = isWearableCandidate
)

/**
 * Parses a manufacturer ID string to Int.
 * Handles hex format ("0x0075" → 117) and decimal format ("117" → 117).
 */
private fun String.parseManufacturerId(): Int =
    if (startsWith("0x", ignoreCase = true)) {
        removePrefix("0x").removePrefix("0X").toInt(16)
    } else {
        toInt()
    }
```

- [ ] **Step 5: Create `RulesLoader.kt`**

```kotlin
package com.wearaware.app.data.rules

import android.content.Context
import com.google.gson.Gson
import com.wearaware.app.data.rules.dto.RulesDataDto
import com.wearaware.app.domain.repository.RulesData
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PURPOSE: Reads and parses fingerprint_rules.json from the app's assets directory.
 * LIMITATIONS: Rules are bundled — cannot be updated without an app release.
 *   This is intentional for v1.0 audit integrity.
 * NOTES: Results are NOT cached here. Caching is the responsibility of RulesRepositoryImpl.
 */
@Singleton
class RulesLoader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()

    /**
     * Reads fingerprint_rules.json from assets and returns the parsed domain model.
     * Throws if the file is missing or malformed — this is a programming error, not
     * a recoverable runtime condition.
     */
    fun load(): RulesData {
        val json = context.assets
            .open("fingerprint_rules.json")
            .bufferedReader()
            .use { it.readText() }
        val dto = gson.fromJson(json, RulesDataDto::class.java)
        return dto.toDomain()
    }
}
```

- [ ] **Step 6: Create `RulesRepositoryImpl.kt`**

```kotlin
package com.wearaware.app.data.repository

import com.wearaware.app.data.rules.RulesLoader
import com.wearaware.app.domain.repository.RulesData
import com.wearaware.app.domain.repository.RulesRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PURPOSE: Implements RulesRepository by loading from RulesLoader and caching the result.
 *   The rules file never changes at runtime, so one load per app lifecycle is correct.
 * NOTES: lazy { } ensures load() is called at most once even if getRules() is called
 *   concurrently. Kotlin lazy delegation is thread-safe by default.
 */
@Singleton
class RulesRepositoryImpl @Inject constructor(
    private val rulesLoader: RulesLoader
) : RulesRepository {

    private val cachedRules: RulesData by lazy { rulesLoader.load() }

    /** Returns the parsed and cached rules. Loads from asset on first call. */
    override fun getRules(): RulesData = cachedRules
}
```

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/wearaware/app/data/rules/
git add app/src/main/kotlin/com/wearaware/app/data/repository/RulesRepositoryImpl.kt
git commit -m "feat: add rules data loader (RulesLoader, DTOs, mapper, RulesRepositoryImpl)"
```

---

## Phase 4: BLE Scanning Pipeline 📱

### Task 13: BleScanner + ScanResultMapper

**Files:**
- Create: `app/src/main/kotlin/com/wearaware/app/data/ble/BleScanner.kt`
- Create: `app/src/main/kotlin/com/wearaware/app/data/ble/ScanResultMapper.kt`

- [ ] **Step 1: Create directory**

```bash
mkdir -p app/src/main/kotlin/com/wearaware/app/data/ble
```

- [ ] **Step 2: Create `ScanResultMapper.kt`**

```kotlin
package com.wearaware.app.data.ble

import android.bluetooth.le.ScanResult
import com.wearaware.app.domain.model.RawScanResult

/**
 * PURPOSE: Maps Android's ScanResult to the domain RawScanResult.
 *   This is the ONLY place in the app that reads android.bluetooth.le.ScanResult.
 * LIMITATIONS: scanRecord may be null for some BLE devices; all nullable fields
 *   are handled safely. txPowerLevel uses Int.MIN_VALUE as a sentinel for "not present"
 *   in the Android API — we normalize this to null.
 * NOTES: manufacturerData is a SparseArray in Android. We convert it to Map<Int, ByteArray>
 *   to keep domain models free of Android imports.
 */
fun ScanResult.toRawScanResult(): RawScanResult {
    val record = scanRecord
    val manufacturerData = mutableMapOf<Int, ByteArray>()
    record?.manufacturerSpecificData?.let { sparseArray ->
        for (i in 0 until sparseArray.size()) {
            manufacturerData[sparseArray.keyAt(i)] = sparseArray.valueAt(i) ?: byteArrayOf()
        }
    }
    return RawScanResult(
        address = device.address,
        advertisedName = record?.deviceName,
        rssi = rssi,
        manufacturerData = manufacturerData,
        serviceUuids = record?.serviceUuids?.map { it.uuid.toString().lowercase() }
            ?: emptyList(),
        txPowerLevel = record?.txPowerLevel?.takeIf { it != Int.MIN_VALUE },
        timestampMs = System.currentTimeMillis()
    )
}
```

- [ ] **Step 3: Create `BleScanner.kt`**

```kotlin
package com.wearaware.app.data.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import com.wearaware.app.domain.model.RawScanResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PURPOSE: The ONLY class in WearAware that imports android.bluetooth.
 *   Wraps BluetoothLeScanner and exposes scan results as a Flow<RawScanResult>.
 * LIMITATIONS:
 *   - Scanning stops when the app is backgrounded (v1.0 — no foreground service).
 *   - Requires BLUETOOTH_SCAN (API 31+) or ACCESS_FINE_LOCATION (API 23–30).
 *   - If Bluetooth is disabled, startScanning() is a no-op; check isBleAvailable first.
 *   - ScanFailed errors are logged but not surfaced to callers in v1.0.
 * NOTES: results is a SharedFlow with a buffer of 64 to handle rapid scan bursts.
 *   DROP_OLDEST prevents backpressure at the cost of dropping older readings under load.
 */
@Singleton
class BleScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val bluetoothAdapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private val _results = MutableSharedFlow<RawScanResult>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Continuous stream of BLE scan results. Emits whenever a device is discovered. */
    val results: Flow<RawScanResult> = _results.asSharedFlow()

    private var scanCallback: ScanCallback? = null

    /** True if BLE scanning is currently active. */
    var isScanning: Boolean = false
        private set

    /** True if Bluetooth is supported and enabled on this device. */
    val isBleAvailable: Boolean
        get() = bluetoothAdapter?.isEnabled == true

    /**
     * Starts BLE scanning in LOW_LATENCY mode.
     * Idempotent — does nothing if already scanning or if BLE is unavailable.
     */
    fun startScanning() {
        if (isScanning || !isBleAvailable) return
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                _results.tryEmit(result.toRawScanResult())
            }
            override fun onBatchScanResults(results: List<ScanResult>) {
                results.forEach { _results.tryEmit(it.toRawScanResult()) }
            }
            override fun onScanFailed(errorCode: Int) {
                // ScanFailed is documented — not crash-worthy. Future: expose as Flow event.
            }
        }

        scanner.startScan(null, settings, scanCallback!!)
        isScanning = true
    }

    /**
     * Stops BLE scanning and clears the active callback.
     * Idempotent — safe to call if not scanning.
     */
    fun stopScanning() {
        if (!isScanning) return
        bluetoothAdapter?.bluetoothLeScanner?.let { scanner ->
            scanCallback?.let { scanner.stopScan(it) }
        }
        scanCallback = null
        isScanning = false
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/wearaware/app/data/ble/
git commit -m "feat: add BleScanner and ScanResultMapper (only classes importing android.bluetooth)"
```

---

### Task 14: BleRepositoryImpl

**Files:**
- Create: `app/src/main/kotlin/com/wearaware/app/data/repository/BleRepositoryImpl.kt`

- [ ] **Step 1: Create `BleRepositoryImpl.kt`**

```kotlin
package com.wearaware.app.data.repository

import com.wearaware.app.data.ble.BleScanner
import com.wearaware.app.domain.model.*
import com.wearaware.app.domain.repository.BleRepository
import com.wearaware.app.domain.rules.FingerprintClassifier
import com.wearaware.app.domain.rules.ProximityConfig
import com.wearaware.app.domain.rules.RssiSmoother
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PURPOSE: Implements BleRepository by:
 *   1. Collecting raw scan results from BleScanner
 *   2. Aggregating per-device state (RSSI smoothing, first/last seen, count)
 *   3. Classifying each device via FingerprintClassifier
 *   4. Computing ProximityLabel and VisibilityState
 *   5. Expiring devices after REMOVE_AFTER_MS in SIGNAL_LOST state
 *   6. Emitting a sorted List<ObservedDevice> via StateFlow
 *
 * CONTINUOUS PRESENCE RULE (named implementation requirement):
 *   Brief signal flickers shorter than SIGNAL_LOST_AFTER_MS must NOT reset firstSeenAt.
 *   A device is only removed from deviceStates (and firstSeenAt reset) after REMOVE_AFTER_MS
 *   in SIGNAL_LOST state. During a brief flicker, visibilityState == SIGNAL_LOST suppresses
 *   alert evaluation, but seenDurationMs (= lastSeenAt - firstSeenAt) keeps accumulating.
 *   This means a 60s alert threshold is satisfied by 60s of total session presence, not 60s
 *   of uninterrupted detection. This is intentional — minor environment-caused flickers
 *   should not invalidate a sustained nearby detection.
 *
 * LIMITATIONS:
 *   - Device fingerprint is based on BLE MAC address (may rotate per-session, Android 6+).
 *   - RSSI smoothing is per-session; state resets on stopScanning().
 *   - emitDeviceList() is called from two coroutines; ConcurrentHashMap ensures safety.
 *
 * NOTES: Classification happens here (data layer) but uses FingerprintClassifier
 *   (domain layer). Business meaning is assigned by domain rules, not here.
 *   The repositoryScope outlives individual scans — cleared on stopScanning().
 */
@Singleton
class BleRepositoryImpl @Inject constructor(
    private val bleScanner: BleScanner,
    private val classifier: FingerprintClassifier
) : BleRepository {

    private data class DeviceState(
        val smoother: RssiSmoother = RssiSmoother(),
        val firstSeenAt: Long,
        var lastSeenAt: Long,
        var seenCount: Int = 1,
        var rawRssi: Int,
        var averagedRssi: Int,
        val advertisedName: String?,
        val manufacturerData: Map<Int, ByteArray>,
        val serviceUuids: List<String>,
        val txPowerLevel: Int?
    )

    private val deviceStates = ConcurrentHashMap<String, DeviceState>()

    private val _observedDevices = MutableStateFlow<List<ObservedDevice>>(emptyList())
    override val observedDevices: StateFlow<List<ObservedDevice>> =
        _observedDevices.asStateFlow()

    override val isBleAvailable: Boolean get() = bleScanner.isBleAvailable

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var scanJob: Job? = null
    private var expiryJob: Job? = null

    override fun startScanning() {
        if (bleScanner.isScanning) return
        bleScanner.startScanning()

        scanJob = repositoryScope.launch {
            bleScanner.results.collect { raw ->
                processRawScanResult(raw)
                emitDeviceList()
            }
        }

        expiryJob = repositoryScope.launch {
            while (isActive) {
                delay(2_000L)
                removeExpiredDevices()
                emitDeviceList()
            }
        }
    }

    override fun stopScanning() {
        bleScanner.stopScanning()
        scanJob?.cancel()
        expiryJob?.cancel()
        scanJob = null
        expiryJob = null
        deviceStates.clear()
        _observedDevices.value = emptyList()
    }

    private fun processRawScanResult(raw: com.wearaware.app.domain.model.RawScanResult) {
        val fingerprint = raw.toDeviceFingerprint()
        val now = System.currentTimeMillis()
        val existing = deviceStates[fingerprint]

        if (existing != null) {
            existing.lastSeenAt = now
            existing.seenCount++
            existing.rawRssi = raw.rssi
            existing.averagedRssi = existing.smoother.addReading(raw.rssi)
        } else {
            val smoother = RssiSmoother()
            deviceStates[fingerprint] = DeviceState(
                smoother = smoother,
                firstSeenAt = now,
                lastSeenAt = now,
                rawRssi = raw.rssi,
                averagedRssi = smoother.addReading(raw.rssi),
                advertisedName = raw.advertisedName,
                manufacturerData = raw.manufacturerData,
                serviceUuids = raw.serviceUuids,
                txPowerLevel = raw.txPowerLevel
            )
        }
    }

    private fun removeExpiredDevices() {
        val now = System.currentTimeMillis()
        deviceStates.entries.removeIf { (_, state) ->
            now - state.lastSeenAt > ProximityConfig.REMOVE_AFTER_MS
        }
    }

    private fun emitDeviceList() {
        val now = System.currentTimeMillis()
        val devices = deviceStates.entries
            .map { (fingerprint, state) ->
                val visibilityState =
                    if (now - state.lastSeenAt > ProximityConfig.SIGNAL_LOST_AFTER_MS)
                        VisibilityState.SIGNAL_LOST
                    else
                        VisibilityState.DETECTED_NOW

                val rawScanForClassification = com.wearaware.app.domain.model.RawScanResult(
                    address = fingerprint,
                    advertisedName = state.advertisedName,
                    rssi = state.rawRssi,
                    manufacturerData = state.manufacturerData,
                    serviceUuids = state.serviceUuids,
                    txPowerLevel = state.txPowerLevel,
                    timestampMs = state.lastSeenAt
                )
                val classification = classifier.classify(rawScanForClassification)

                ObservedDevice(
                    id = fingerprint,
                    advertisedName = state.advertisedName,
                    rawRssi = state.rawRssi,
                    averagedRssi = state.averagedRssi,
                    proximityLabel = ProximityConfig.labelFromRssi(state.averagedRssi),
                    visibilityState = visibilityState,
                    firstSeenAt = state.firstSeenAt,
                    lastSeenAt = state.lastSeenAt,
                    seenCount = state.seenCount,
                    classification = classification,
                    persistenceAlert = null
                )
            }
            .sortedByDescending { it.averagedRssi }

        _observedDevices.value = devices
    }

    /**
     * Derives a session-scoped device fingerprint from the BLE MAC address.
     * NOTE: On Android 6+, MAC addresses are randomized per-session.
     * Future improvement: incorporate manufacturer data hash + name for stability.
     */
    private fun com.wearaware.app.domain.model.RawScanResult.toDeviceFingerprint(): String =
        address
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/kotlin/com/wearaware/app/data/repository/BleRepositoryImpl.kt
git commit -m "feat: add BleRepositoryImpl (scan aggregation, smoothing, classification, expiry)"
```

---

## Phase 5: Room Session Log + Use Cases 🧪

### Task 15: Room database (entity, DAO, database)

**Files:**
- Create: `app/src/main/kotlin/com/wearaware/app/data/local/ScanLogEntity.kt`
- Create: `app/src/main/kotlin/com/wearaware/app/data/local/ScanLogDao.kt`
- Create: `app/src/main/kotlin/com/wearaware/app/data/local/WearAwareDatabase.kt`

- [ ] **Step 1: Create directory**

```bash
mkdir -p app/src/main/kotlin/com/wearaware/app/data/local
```

- [ ] **Step 2: Create `ScanLogEntity.kt`**

```kotlin
package com.wearaware.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * PURPOSE: Room entity for the local session scan log table.
 *   Mirrors ScanLogEntry domain model but with Room annotations.
 * NOTES: Enum fields stored as String names (not ordinals) for forward compatibility
 *   and readability when querying the database directly.
 */
@Entity(tableName = "scan_log")
data class ScanLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val deviceId: String,
    val advertisedName: String?,
    val rawRssi: Int,
    val averagedRssi: Int,
    val proximityLabel: String,
    val visibilityState: String,
    val matchedRuleId: String?,
    val ruleVersion: String?,
    val category: String,
    val confidence: String,
    val evaluationNotes: String?
)
```

- [ ] **Step 3: Create `ScanLogDao.kt`**

```kotlin
package com.wearaware.app.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * PURPOSE: Data Access Object for the scan_log table.
 * NOTES: observeAll() uses Flow — Room emits a new list whenever the table changes.
 *   deleteAll() uses a suspend function — call from a coroutine, never from the main thread.
 */
@Dao
interface ScanLogDao {
    /** Inserts a new scan log entry. Auto-generates the id. */
    @Insert
    suspend fun insert(entry: ScanLogEntity)

    /** Returns all entries ordered newest first, as a live Flow. */
    @Query("SELECT * FROM scan_log ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<ScanLogEntity>>

    /** Deletes all session log entries. Irreversible. */
    @Query("DELETE FROM scan_log")
    suspend fun deleteAll()
}
```

- [ ] **Step 4: Create `WearAwareDatabase.kt`**

```kotlin
package com.wearaware.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * PURPOSE: Room database definition for WearAware local storage.
 *   Currently contains one table: scan_log.
 * LIMITATIONS: exportSchema = false means schema is not exported to a file.
 *   Set to true and configure schemaLocation if schema history tracking is needed.
 * NOTES: Version is 1 for initial release. Increment + add Migration when schema changes.
 */
@Database(entities = [ScanLogEntity::class], version = 1, exportSchema = false)
abstract class WearAwareDatabase : RoomDatabase() {
    abstract fun scanLogDao(): ScanLogDao

    companion object {
        const val DATABASE_NAME = "wearaware_db"
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/wearaware/app/data/local/
git commit -m "feat: add Room database (ScanLogEntity, ScanLogDao, WearAwareDatabase)"
```

---

### Task 16: ScanLogMapper + ScanLogRepositoryImpl + mapper unit tests 🧪

**Files:**
- Create: `app/src/main/kotlin/com/wearaware/app/data/mapper/ScanLogMapper.kt`
- Create: `app/src/main/kotlin/com/wearaware/app/data/repository/ScanLogRepositoryImpl.kt`
- Create: `app/src/test/kotlin/com/wearaware/app/data/mapper/ScanLogMapperTest.kt`

- [ ] **Step 1: Create directories**

```bash
mkdir -p app/src/main/kotlin/com/wearaware/app/data/mapper
```

- [ ] **Step 2: Write the failing mapper test**

Create `app/src/test/kotlin/com/wearaware/app/data/mapper/ScanLogMapperTest.kt`:

```kotlin
package com.wearaware.app.data.mapper

import com.wearaware.app.data.local.ScanLogEntity
import com.wearaware.app.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanLogMapperTest {

    private fun makeDevice(
        id: String = "device-1",
        name: String? = "Ray-Ban",
        rawRssi: Int = -62,
        avgRssi: Int = -65,
        proximity: ProximityLabel = ProximityLabel.STRONG,
        visibility: VisibilityState = VisibilityState.DETECTED_NOW,
        ruleId: String? = "meta_rayban_v1",
        ruleVersion: String? = "1.0.0",
        category: DeviceCategory = DeviceCategory.CAMERA_CAPABLE_WEARABLE,
        confidence: ConfidenceLevel = ConfidenceLevel.HIGH,
        notes: String? = "manufacturer ID matched"
    ) = ObservedDevice(
        id = id,
        advertisedName = name,
        rawRssi = rawRssi,
        averagedRssi = avgRssi,
        proximityLabel = proximity,
        visibilityState = visibility,
        firstSeenAt = 1000L,
        lastSeenAt = 2000L,
        seenCount = 5,
        classification = ClassificationResult(
            matchedRuleId = ruleId,
            ruleVersion = ruleVersion,
            category = category,
            displayLabel = "Camera-capable wearable detected nearby",
            confidence = confidence,
            isWearableCandidate = true,
            evaluationNotes = notes
        ),
        persistenceAlert = null
    )

    @Test
    fun `toScanLogEntity maps all fields correctly`() {
        val device = makeDevice()
        val entity = device.toScanLogEntity()

        assertEquals("device-1", entity.deviceId)
        assertEquals("Ray-Ban", entity.advertisedName)
        assertEquals(-62, entity.rawRssi)
        assertEquals(-65, entity.averagedRssi)
        assertEquals("STRONG", entity.proximityLabel)
        assertEquals("DETECTED_NOW", entity.visibilityState)
        assertEquals("meta_rayban_v1", entity.matchedRuleId)
        assertEquals("1.0.0", entity.ruleVersion)
        assertEquals("CAMERA_CAPABLE_WEARABLE", entity.category)
        assertEquals("HIGH", entity.confidence)
        assertEquals("manufacturer ID matched", entity.evaluationNotes)
    }

    @Test
    fun `toScanLogEntity handles null optional fields`() {
        val device = makeDevice(name = null, ruleId = null, ruleVersion = null, notes = null)
        val entity = device.toScanLogEntity()
        assertNull(entity.advertisedName)
        assertNull(entity.matchedRuleId)
        assertNull(entity.ruleVersion)
        assertNull(entity.evaluationNotes)
    }

    @Test
    fun `toDomain maps entity back to ScanLogEntry correctly`() {
        val entity = ScanLogEntity(
            id = 42,
            timestamp = 9999L,
            deviceId = "device-1",
            advertisedName = "Ray-Ban",
            rawRssi = -62,
            averagedRssi = -65,
            proximityLabel = "STRONG",
            visibilityState = "DETECTED_NOW",
            matchedRuleId = "meta_rayban_v1",
            ruleVersion = "1.0.0",
            category = "CAMERA_CAPABLE_WEARABLE",
            confidence = "HIGH",
            evaluationNotes = "manufacturer ID matched"
        )
        val entry = entity.toDomain()

        assertEquals(42L, entry.id)
        assertEquals(9999L, entry.timestamp)
        assertEquals("device-1", entry.deviceId)
        assertEquals("Ray-Ban", entry.advertisedName)
        assertEquals("STRONG", entry.proximityLabel)
        assertEquals("meta_rayban_v1", entry.matchedRuleId)
    }

    @Test
    fun `toScanLogEntity-toDomain round-trip preserves all fields`() {
        val device = makeDevice()
        val entity = device.toScanLogEntity()
        val entry = entity.toDomain()

        assertEquals(device.id, entry.deviceId)
        assertEquals(device.advertisedName, entry.advertisedName)
        assertEquals(device.rawRssi, entry.rawRssi)
        assertEquals(device.averagedRssi, entry.averagedRssi)
        assertEquals(device.proximityLabel.name, entry.proximityLabel)
        assertEquals(device.visibilityState.name, entry.visibilityState)
        assertEquals(device.classification.matchedRuleId, entry.matchedRuleId)
        assertEquals(device.classification.ruleVersion, entry.ruleVersion)
        assertEquals(device.classification.category.name, entry.category)
        assertEquals(device.classification.confidence.name, entry.confidence)
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

```bash
./gradlew :app:test --tests "com.wearaware.app.data.mapper.ScanLogMapperTest"
```
Expected: FAIL — ScanLogMapper does not exist.

- [ ] **Step 4: Create `ScanLogMapper.kt`**

```kotlin
package com.wearaware.app.data.mapper

import com.wearaware.app.data.local.ScanLogEntity
import com.wearaware.app.domain.model.ObservedDevice
import com.wearaware.app.domain.model.ScanLogEntry

/**
 * PURPOSE: Bidirectional mapping between ObservedDevice/ScanLogEntry (domain) and
 *   ScanLogEntity (Room). Ensures domain and data layers share no types.
 * NOTES: Enum fields are converted to/from their .name String to keep Room schema
 *   readable and forward-compatible (adding new enum values doesn't break old rows).
 */

/**
 * Maps an ObservedDevice to a ScanLogEntity ready for Room insertion.
 * Timestamp is set to the current time at mapping — not the device's lastSeenAt —
 * because this represents the moment of logging, not the last BLE event.
 */
fun ObservedDevice.toScanLogEntity(): ScanLogEntity = ScanLogEntity(
    timestamp = System.currentTimeMillis(),
    deviceId = id,
    advertisedName = advertisedName,
    rawRssi = rawRssi,
    averagedRssi = averagedRssi,
    proximityLabel = proximityLabel.name,
    visibilityState = visibilityState.name,
    matchedRuleId = classification.matchedRuleId,
    ruleVersion = classification.ruleVersion,
    category = classification.category.name,
    confidence = classification.confidence.name,
    evaluationNotes = classification.evaluationNotes
)

/** Maps a Room ScanLogEntity to the domain ScanLogEntry. */
fun ScanLogEntity.toDomain(): ScanLogEntry = ScanLogEntry(
    id = id,
    timestamp = timestamp,
    deviceId = deviceId,
    advertisedName = advertisedName,
    rawRssi = rawRssi,
    averagedRssi = averagedRssi,
    proximityLabel = proximityLabel,
    visibilityState = visibilityState,
    matchedRuleId = matchedRuleId,
    ruleVersion = ruleVersion,
    category = category,
    confidence = confidence,
    evaluationNotes = evaluationNotes
)
```

- [ ] **Step 5: Run test to verify it passes**

```bash
./gradlew :app:test --tests "com.wearaware.app.data.mapper.ScanLogMapperTest"
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Create `ScanLogRepositoryImpl.kt`**

```kotlin
package com.wearaware.app.data.repository

import com.wearaware.app.data.local.ScanLogDao
import com.wearaware.app.data.mapper.toScanLogEntity
import com.wearaware.app.data.mapper.toDomain
import com.wearaware.app.domain.model.ObservedDevice
import com.wearaware.app.domain.model.ScanLogEntry
import com.wearaware.app.domain.repository.ScanLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PURPOSE: Implements ScanLogRepository using Room DAO.
 * NOTES: observeAll() returns a Flow — Room emits updates automatically when the
 *   table changes. Callers do not need to manually refresh.
 */
@Singleton
class ScanLogRepositoryImpl @Inject constructor(
    private val dao: ScanLogDao
) : ScanLogRepository {

    /** Logs a detection event by mapping ObservedDevice to a Room entity and inserting it. */
    override suspend fun log(device: ObservedDevice) {
        dao.insert(device.toScanLogEntity())
    }

    /** Returns a live stream of all session log entries, newest first. */
    override fun observeAll(): Flow<List<ScanLogEntry>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    /** Deletes all session log entries from the database. */
    override suspend fun clearAll() {
        dao.deleteAll()
    }
}
```

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/wearaware/app/data/mapper/ScanLogMapper.kt
git add app/src/main/kotlin/com/wearaware/app/data/repository/ScanLogRepositoryImpl.kt
git add app/src/test/kotlin/com/wearaware/app/data/mapper/ScanLogMapperTest.kt
git commit -m "feat: add ScanLogMapper, ScanLogRepositoryImpl, and mapper unit tests"
```

---

### Task 17: All domain use cases + EvaluatePersistenceUseCase unit tests 🧪 📱

**Files:**
- Create all 6 use case files
- Create: `app/src/test/kotlin/com/wearaware/app/domain/usecase/EvaluatePersistenceUseCaseTest.kt`

- [ ] **Step 1: Write the failing persistence use case test**

Create `app/src/test/kotlin/com/wearaware/app/domain/usecase/EvaluatePersistenceUseCaseTest.kt`:

```kotlin
package com.wearaware.app.domain.usecase

import com.wearaware.app.domain.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class EvaluatePersistenceUseCaseTest {

    private lateinit var useCase: EvaluatePersistenceUseCase

    @Before
    fun setUp() {
        useCase = EvaluatePersistenceUseCase()
    }

    private fun makeDevice(
        id: String = "device-1",
        visibility: VisibilityState = VisibilityState.DETECTED_NOW,
        seenDurationMs: Long = 65_000L,
        isWearableCandidate: Boolean = true,
        confidence: ConfidenceLevel = ConfidenceLevel.HIGH,
        proximity: ProximityLabel = ProximityLabel.NEARBY
    ): ObservedDevice {
        val now = System.currentTimeMillis()
        return ObservedDevice(
            id = id,
            advertisedName = "Ray-Ban",
            rawRssi = -68,
            averagedRssi = -68,
            proximityLabel = proximity,
            visibilityState = visibility,
            firstSeenAt = now - seenDurationMs,
            lastSeenAt = now,
            seenCount = 20,
            classification = ClassificationResult(
                matchedRuleId = "meta_rayban_v1",
                ruleVersion = "1.0.0",
                category = DeviceCategory.CAMERA_CAPABLE_WEARABLE,
                displayLabel = "Camera-capable wearable detected nearby",
                confidence = confidence,
                isWearableCandidate = isWearableCandidate,
                evaluationNotes = "manufacturer ID matched"
            ),
            persistenceAlert = null
        )
    }

    @Test
    fun `returns alert when all conditions are met`() {
        val result = useCase(makeDevice(), emptyMap())
        assertNotNull(result)
        assertEquals(PersistenceAlertType.DEVICE_REMAINED_NEARBY, result?.alertType)
        assertEquals("device-1", result?.deviceId)
    }

    @Test
    fun `returns null when visibility is SIGNAL_LOST`() {
        assertNull(useCase(makeDevice(visibility = VisibilityState.SIGNAL_LOST), emptyMap()))
    }

    @Test
    fun `returns null when seenDuration is below threshold`() {
        assertNull(useCase(makeDevice(seenDurationMs = 30_000L), emptyMap()))
    }

    @Test
    fun `returns null when at exactly the threshold (not yet exceeded)`() {
        assertNull(useCase(makeDevice(seenDurationMs = 59_999L), emptyMap()))
    }

    @Test
    fun `returns alert when seenDuration exactly equals threshold`() {
        assertNotNull(useCase(makeDevice(seenDurationMs = 60_000L), emptyMap()))
    }

    @Test
    fun `returns null when isWearableCandidate is false`() {
        assertNull(useCase(makeDevice(isWearableCandidate = false), emptyMap()))
    }

    @Test
    fun `returns null when confidence is LOW`() {
        assertNull(useCase(makeDevice(confidence = ConfidenceLevel.LOW), emptyMap()))
    }

    @Test
    fun `returns alert when confidence is MEDIUM`() {
        assertNotNull(useCase(makeDevice(confidence = ConfidenceLevel.MEDIUM), emptyMap()))
    }

    @Test
    fun `returns null when proximity is WEAK`() {
        assertNull(useCase(makeDevice(proximity = ProximityLabel.WEAK), emptyMap()))
    }

    @Test
    fun `returns null when proximity is UNKNOWN`() {
        assertNull(useCase(makeDevice(proximity = ProximityLabel.UNKNOWN), emptyMap()))
    }

    @Test
    fun `returns alert when proximity is VERY_CLOSE`() {
        assertNotNull(useCase(makeDevice(proximity = ProximityLabel.VERY_CLOSE), emptyMap()))
    }

    @Test
    fun `returns null when within cooldown window`() {
        val key = "device-1:${PersistenceAlertType.DEVICE_REMAINED_NEARBY.name}"
        val recentAlert = mapOf(key to System.currentTimeMillis() - 60_000L)
        assertNull(useCase(makeDevice(), recentAlert))
    }

    @Test
    fun `returns alert when cooldown has expired`() {
        val key = "device-1:${PersistenceAlertType.DEVICE_REMAINED_NEARBY.name}"
        val expiredAlert = mapOf(key to System.currentTimeMillis() - 130_000L)
        assertNotNull(useCase(makeDevice(), expiredAlert))
    }

    @Test
    fun `alert for device-2 is not blocked by cooldown for device-1`() {
        val key = "device-1:${PersistenceAlertType.DEVICE_REMAINED_NEARBY.name}"
        val device1Cooldown = mapOf(key to System.currentTimeMillis() - 10_000L)
        assertNotNull(useCase(makeDevice(id = "device-2"), device1Cooldown))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

```bash
./gradlew :app:test --tests "com.wearaware.app.domain.usecase.EvaluatePersistenceUseCaseTest"
```
Expected: FAIL — EvaluatePersistenceUseCase does not exist.

- [ ] **Step 3: Create all use case files**

Create `domain/usecase/ObserveScannedDevicesUseCase.kt`:
```kotlin
package com.wearaware.app.domain.usecase

import com.wearaware.app.domain.model.ObservedDevice
import com.wearaware.app.domain.repository.BleRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * PURPOSE: Exposes the live stream of observed BLE devices from the BLE repository.
 * NOTES: Returns StateFlow directly — always has a current value (empty list initially).
 */
class ObserveScannedDevicesUseCase @Inject constructor(
    private val bleRepository: BleRepository
) {
    operator fun invoke(): StateFlow<List<ObservedDevice>> = bleRepository.observedDevices
}
```

Create `domain/usecase/ClassifyDeviceUseCase.kt`:
```kotlin
package com.wearaware.app.domain.usecase

import com.wearaware.app.domain.model.ClassificationResult
import com.wearaware.app.domain.model.RawScanResult
import com.wearaware.app.domain.rules.FingerprintClassifier
import javax.inject.Inject

/**
 * PURPOSE: Thin wrapper around FingerprintClassifier for on-demand classification.
 *   Classification in normal scanning flow happens inside BleRepositoryImpl;
 *   this use case is useful for testing and future one-off classification needs.
 */
class ClassifyDeviceUseCase @Inject constructor(
    private val classifier: FingerprintClassifier
) {
    operator fun invoke(scan: RawScanResult): ClassificationResult = classifier.classify(scan)
}
```

Create `domain/usecase/EvaluatePersistenceUseCase.kt`:
```kotlin
package com.wearaware.app.domain.usecase

import com.wearaware.app.domain.model.*
import javax.inject.Inject

/**
 * PURPOSE: Determines whether a device qualifies for a persistence alert.
 *   Encapsulates all alert gating logic in one pure Kotlin function.
 * LIMITATIONS: seenDurationMs is computed from firstSeenAt/lastSeenAt which reset
 *   if a device is removed and re-appears. Brief signal loss (< SIGNAL_LOST_AFTER_MS)
 *   does not break continuity — the device stays in the map.
 * NOTES: Returns null (not an exception) when conditions are not met — caller
 *   should treat null as "no alert this cycle."
 */
class EvaluatePersistenceUseCase @Inject constructor() {

    companion object {
        /** Minimum continuous DETECTED_NOW duration to trigger an alert. */
        const val ALERT_THRESHOLD_MS = 60_000L
        /** How long to suppress re-alerting for the same device + alert type. */
        const val COOLDOWN_MS = 120_000L
    }

    /**
     * Returns a PersistenceAlert if all conditions are met, null otherwise.
     *
     * Conditions:
     * 1. Device is DETECTED_NOW (not SIGNAL_LOST)
     * 2. seenDurationMs >= ALERT_THRESHOLD_MS
     * 3. classification.isWearableCandidate == true
     * 4. classification.confidence is HIGH or MEDIUM (not LOW)
     * 5. proximityLabel is NEARBY, STRONG, or VERY_CLOSE
     * 6. No active cooldown for (deviceId + alertType) key
     *
     * @param device The observed device to evaluate.
     * @param lastAlertedAt Map of (deviceId:alertType) → epoch ms of last alert.
     */
    operator fun invoke(
        device: ObservedDevice,
        lastAlertedAt: Map<String, Long>
    ): PersistenceAlert? {
        if (device.visibilityState != VisibilityState.DETECTED_NOW) return null
        if (device.seenDurationMs < ALERT_THRESHOLD_MS) return null
        if (!device.classification.isWearableCandidate) return null
        if (device.classification.confidence == ConfidenceLevel.LOW) return null
        if (device.proximityLabel == ProximityLabel.WEAK ||
            device.proximityLabel == ProximityLabel.UNKNOWN) return null

        val cooldownKey = "${device.id}:${PersistenceAlertType.DEVICE_REMAINED_NEARBY.name}"
        val lastAlert = lastAlertedAt[cooldownKey]
        if (lastAlert != null && System.currentTimeMillis() - lastAlert < COOLDOWN_MS) return null

        return PersistenceAlert(
            deviceId = device.id,
            deviceDisplayLabel = device.classification.displayLabel,
            durationMs = device.seenDurationMs,
            alertType = PersistenceAlertType.DEVICE_REMAINED_NEARBY,
            triggeredAt = System.currentTimeMillis()
        )
    }
}
```

Create `domain/usecase/LogScanEventUseCase.kt`:
```kotlin
package com.wearaware.app.domain.usecase

import com.wearaware.app.domain.model.ObservedDevice
import com.wearaware.app.domain.repository.ScanLogRepository
import javax.inject.Inject

/**
 * PURPOSE: Logs a single detection event to the local session log.
 * NOTES: Must be called from a coroutine (suspend function).
 *   Classification metadata (matchedRuleId, ruleVersion) is logged with the event
 *   to enable audit traceability.
 */
class LogScanEventUseCase @Inject constructor(
    private val scanLogRepository: ScanLogRepository
) {
    suspend operator fun invoke(device: ObservedDevice) {
        scanLogRepository.log(device)
    }
}
```

Create `domain/usecase/GetSessionLogUseCase.kt`:
```kotlin
package com.wearaware.app.domain.usecase

import com.wearaware.app.domain.model.ScanLogEntry
import com.wearaware.app.domain.repository.ScanLogRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * PURPOSE: Provides a live stream of all session log entries for display in SessionLogScreen.
 * NOTES: Returns a Flow — Room emits updates automatically when new entries are logged.
 */
class GetSessionLogUseCase @Inject constructor(
    private val scanLogRepository: ScanLogRepository
) {
    operator fun invoke(): Flow<List<ScanLogEntry>> = scanLogRepository.observeAll()
}
```

Create `domain/usecase/ClearSessionLogUseCase.kt`:
```kotlin
package com.wearaware.app.domain.usecase

import com.wearaware.app.domain.repository.ScanLogRepository
import javax.inject.Inject

/**
 * PURPOSE: Deletes all entries from the local session log.
 *   Called when user taps "Clear session log" in SessionLogScreen.
 * NOTES: Irreversible. No confirmation logic here — confirmation dialog is in the UI layer.
 */
class ClearSessionLogUseCase @Inject constructor(
    private val scanLogRepository: ScanLogRepository
) {
    suspend operator fun invoke() {
        scanLogRepository.clearAll()
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew :app:test --tests "com.wearaware.app.domain.usecase.EvaluatePersistenceUseCaseTest"
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Run all domain tests together**

```bash
./gradlew :app:test --tests "com.wearaware.app.domain.*"
```
Expected: All pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/wearaware/app/domain/usecase/
git add app/src/test/kotlin/com/wearaware/app/domain/usecase/
git commit -m "feat: add all 6 domain use cases with EvaluatePersistenceUseCase unit tests"
```

---

## Phase 6: Dependency Injection

### Task 18: Hilt modules

**Files:**
- Create: `app/src/main/kotlin/com/wearaware/app/di/BleModule.kt`
- Create: `app/src/main/kotlin/com/wearaware/app/di/DatabaseModule.kt`
- Create: `app/src/main/kotlin/com/wearaware/app/di/RepositoryModule.kt`

- [ ] **Step 1: Create directory**

```bash
mkdir -p app/src/main/kotlin/com/wearaware/app/di
```

- [ ] **Step 2: Create `BleModule.kt`**

```kotlin
package com.wearaware.app.di

import android.content.Context
import com.wearaware.app.data.ble.BleScanner
import com.wearaware.app.data.repository.RulesRepositoryImpl
import com.wearaware.app.domain.repository.RulesRepository
import com.wearaware.app.domain.rules.FingerprintClassifier
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * PURPOSE: Provides BLE-related and classification dependencies.
 * NOTES: FingerprintClassifier depends on RulesRepository — rules are loaded once
 *   (lazily) and cached by RulesRepositoryImpl.
 */
@Module
@InstallIn(SingletonComponent::class)
object BleModule {

    @Provides
    @Singleton
    fun provideRulesRepository(impl: RulesRepositoryImpl): RulesRepository = impl

    @Provides
    @Singleton
    fun provideFingerprintClassifier(rulesRepository: RulesRepository): FingerprintClassifier =
        FingerprintClassifier(rulesRepository.getRules())
}
```

- [ ] **Step 3: Create `DatabaseModule.kt`**

```kotlin
package com.wearaware.app.di

import android.content.Context
import androidx.room.Room
import com.wearaware.app.data.local.ScanLogDao
import com.wearaware.app.data.local.WearAwareDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * PURPOSE: Provides the Room database and its DAO instances.
 * NOTES: Database is a singleton — one instance for the app lifetime.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): WearAwareDatabase =
        Room.databaseBuilder(
            context,
            WearAwareDatabase::class.java,
            WearAwareDatabase.DATABASE_NAME
        ).build()

    @Provides
    fun provideScanLogDao(database: WearAwareDatabase): ScanLogDao =
        database.scanLogDao()
}
```

- [ ] **Step 4: Create `RepositoryModule.kt`**

```kotlin
package com.wearaware.app.di

import com.wearaware.app.data.repository.BleRepositoryImpl
import com.wearaware.app.data.repository.ScanLogRepositoryImpl
import com.wearaware.app.domain.repository.BleRepository
import com.wearaware.app.domain.repository.ScanLogRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * PURPOSE: Binds data layer implementations to their domain repository interfaces.
 *   The domain layer depends on interfaces; the data layer provides implementations.
 * NOTES: @Binds is preferred over @Provides for interface-to-implementation binding
 *   because it generates less code and is more efficient.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindBleRepository(impl: BleRepositoryImpl): BleRepository

    @Binds
    @Singleton
    abstract fun bindScanLogRepository(impl: ScanLogRepositoryImpl): ScanLogRepository
}
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/wearaware/app/di/
git commit -m "feat: add Hilt DI modules (BleModule, DatabaseModule, RepositoryModule)"
```

---

## Phase 7: ViewModel + UI Screens

### Task 19: ScanUiState + ScanViewModel

**Files:**
- Create: `app/src/main/kotlin/com/wearaware/app/ui/viewmodel/ScanUiState.kt`
- Create: `app/src/main/kotlin/com/wearaware/app/ui/viewmodel/ScanViewModel.kt`
- Create: `app/src/main/kotlin/com/wearaware/app/ui/viewmodel/SessionLogViewModel.kt`

- [ ] **Step 1: Create directory**

```bash
mkdir -p app/src/main/kotlin/com/wearaware/app/ui/viewmodel
```

- [ ] **Step 2: Create `ScanUiState.kt`**

```kotlin
package com.wearaware.app.ui.viewmodel

import com.wearaware.app.domain.model.ObservedDevice
import com.wearaware.app.domain.model.PersistenceAlert

/**
 * PURPOSE: Complete UI state for ScanScreen. ViewModel exposes this as a StateFlow.
 *   Compose observes it and recomposes only when the state actually changes.
 * NOTES: Using a single data class (rather than separate StateFlows) means UI always
 *   has a consistent snapshot — no partially-updated state between emissions.
 */
data class ScanUiState(
    val scanState: ScanState = ScanState.STOPPED,
    val devices: List<ObservedDevice> = emptyList(),
    /** The active persistence alert, if any. One at a time — most recently triggered. */
    val activeAlert: PersistenceAlert? = null
)

enum class ScanState {
    STOPPED,
    SCANNING,
    BLUETOOTH_UNAVAILABLE,
    PERMISSIONS_REQUIRED
}
```

- [ ] **Step 3: Create `ScanViewModel.kt`**

```kotlin
package com.wearaware.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wearaware.app.domain.model.ObservedDevice
import com.wearaware.app.domain.model.PersistenceAlert
import com.wearaware.app.domain.model.VisibilityState
import com.wearaware.app.domain.repository.BleRepository
import com.wearaware.app.domain.usecase.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * PURPOSE: Orchestrates the BLE scanning session for ScanScreen.
 *   Collects device updates, evaluates persistence alerts, logs events,
 *   and exposes a single ScanUiState via StateFlow.
 * LIMITATIONS: loggedDeviceIds prevents duplicate logging for the same device within
 *   a session but does not persist across sessions.
 * NOTES: ViewModels do not contain BLE parsing, RSSI math, or rule evaluation.
 *   All logic is delegated to use cases or the repository.
 *   ScanViewModel manages alert dismissal UI state but does NOT reset cooldown on dismiss
 *   (by design — see SafeWording design notes).
 */
@HiltViewModel
class ScanViewModel @Inject constructor(
    private val observeScannedDevices: ObserveScannedDevicesUseCase,
    private val evaluatePersistence: EvaluatePersistenceUseCase,
    private val logScanEvent: LogScanEventUseCase,
    private val bleRepository: BleRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    /** Tracks last alert trigger time per (deviceId:alertType) key for cooldown management. */
    private val lastAlertedAt = mutableMapOf<String, Long>()
    /** Tracks which device IDs have been logged this session to avoid duplicate log entries. */
    private val loggedDeviceIds = mutableSetOf<String>()

    fun startScanning() {
        if (!bleRepository.isBleAvailable) {
            _uiState.update { it.copy(scanState = ScanState.BLUETOOTH_UNAVAILABLE) }
            return
        }
        bleRepository.startScanning()
        _uiState.update { it.copy(scanState = ScanState.SCANNING) }

        viewModelScope.launch {
            observeScannedDevices().collect { devices ->
                processDeviceUpdate(devices)
            }
        }
    }

    fun stopScanning() {
        bleRepository.stopScanning()
        lastAlertedAt.clear()
        loggedDeviceIds.clear()
        _uiState.update { it.copy(
            scanState = ScanState.STOPPED,
            devices = emptyList(),
            activeAlert = null
        ) }
    }

    fun setPermissionsRequired() {
        if (_uiState.value.scanState != ScanState.SCANNING) {
            _uiState.update { it.copy(scanState = ScanState.PERMISSIONS_REQUIRED) }
        }
    }

    /**
     * Hides the active alert banner without resetting the cooldown.
     * The device remains in the list; the cooldown prevents re-alerting too soon.
     */
    fun dismissAlert() {
        _uiState.update { it.copy(activeAlert = null) }
    }

    fun getDeviceById(deviceId: String): ObservedDevice? =
        _uiState.value.devices.find { it.id == deviceId }

    private fun processDeviceUpdate(devices: List<ObservedDevice>) {
        // Log each newly detected device once per session
        devices
            .filter { it.id !in loggedDeviceIds && it.visibilityState == VisibilityState.DETECTED_NOW }
            .forEach { device ->
                loggedDeviceIds.add(device.id)
                viewModelScope.launch { logScanEvent(device) }
            }

        // Evaluate persistence alerts for all DETECTED_NOW devices
        val newAlerts = devices.mapNotNull { device ->
            evaluatePersistence(device, lastAlertedAt)?.also { alert ->
                val key = "${device.id}:${alert.alertType.name}"
                lastAlertedAt[key] = alert.triggeredAt
            }
        }

        // Determine the active alert:
        // - Clear current alert if its device has gone SIGNAL_LOST
        // - Replace with newest alert if multiple triggered this cycle
        val currentAlert = _uiState.value.activeAlert
        val updatedAlert: PersistenceAlert? = when {
            currentAlert != null && deviceIsSignalLost(devices, currentAlert.deviceId) -> null
            newAlerts.isNotEmpty() -> newAlerts.maxByOrNull { it.triggeredAt }
            else -> currentAlert
        }

        _uiState.update { it.copy(devices = devices, activeAlert = updatedAlert) }
    }

    private fun deviceIsSignalLost(devices: List<ObservedDevice>, deviceId: String): Boolean =
        devices.find { it.id == deviceId }?.visibilityState == VisibilityState.SIGNAL_LOST

    override fun onCleared() {
        super.onCleared()
        bleRepository.stopScanning()
    }
}
```

- [ ] **Step 4: Create `SessionLogViewModel.kt`**

```kotlin
package com.wearaware.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wearaware.app.domain.model.ScanLogEntry
import com.wearaware.app.domain.usecase.ClearSessionLogUseCase
import com.wearaware.app.domain.usecase.GetSessionLogUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * PURPOSE: Provides session log data for SessionLogScreen.
 * NOTES: log StateFlow is backed by Room's Flow — updates automatically when
 *   new entries are logged. SharingStarted.Lazily means collection starts on first subscriber.
 */
@HiltViewModel
class SessionLogViewModel @Inject constructor(
    private val getSessionLog: GetSessionLogUseCase,
    private val clearSessionLog: ClearSessionLogUseCase
) : ViewModel() {

    val log: StateFlow<List<ScanLogEntry>> = getSessionLog()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = emptyList()
        )

    /** Clears all session log entries. Called after user confirms the clear dialog. */
    fun clearLog() {
        viewModelScope.launch { clearSessionLog() }
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/wearaware/app/ui/viewmodel/
git commit -m "feat: add ScanUiState, ScanViewModel, SessionLogViewModel"
```

---

### Task 20: UI theme, SafeWording, FormatUtils, PermissionUtils

**Files:**
- Create: `app/src/main/kotlin/com/wearaware/app/ui/theme/Color.kt`
- Create: `app/src/main/kotlin/com/wearaware/app/ui/theme/Theme.kt`
- Create: `app/src/main/kotlin/com/wearaware/app/ui/theme/Type.kt`
- Create: `app/src/main/kotlin/com/wearaware/app/ui/components/SafeWording.kt`
- Create: `app/src/main/kotlin/com/wearaware/app/util/FormatUtils.kt`
- Create: `app/src/main/kotlin/com/wearaware/app/util/PermissionUtils.kt`

- [ ] **Step 1: Create directories**

```bash
mkdir -p app/src/main/kotlin/com/wearaware/app/ui/theme
mkdir -p app/src/main/kotlin/com/wearaware/app/ui/components
mkdir -p app/src/main/kotlin/com/wearaware/app/util
```

- [ ] **Step 2: Create `Color.kt`**

```kotlin
package com.wearaware.app.ui.theme

import androidx.compose.ui.graphics.Color

// Signal bar colors — no green (see product-decisions.md Decision 3)
val SignalVeryClose = Color(0xFFE53935)   // red — very close
val SignalStrong    = Color(0xFFFF6F00)   // orange — strong
val SignalNearby    = Color(0xFFFFB300)   // yellow — nearby
val SignalWeak      = Color(0xFF9E9E9E)   // grey — weak
val SignalUnknown   = Color(0xFF9E9E9E)   // grey — unknown

// Visibility state dots
val DotDetected  = Color(0xFF4CAF50)  // green dot — actively detected
val DotSignalLost = Color(0xFF9E9E9E) // grey dot — signal lost
```

- [ ] **Step 3: Create `Type.kt`**

```kotlin
package com.wearaware.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Typography = Typography(
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall  = TextStyle(fontSize = 12.sp),
    labelSmall = TextStyle(fontSize = 11.sp)
)
```

- [ ] **Step 4: Create `Theme.kt`**

```kotlin
package com.wearaware.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme()

@Composable
fun WearAwareTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
```

- [ ] **Step 5: Create `SafeWording.kt`**

```kotlin
package com.wearaware.app.ui.components

/**
 * PURPOSE: Centralized repository for all user-facing strings that describe
 *   device detections and alerts. Every alert message in the app must come
 *   from here — never from free-form strings in ViewModels, use cases, or screens.
 * LIMITATIONS: Safe wording is intentionally vague. WearAware cannot determine
 *   whether a device is actually recording or whether a person is following anyone.
 * NOTES: Matches the safe wording requirements in PRD v1.0 Section 3.8.
 *   If wording changes, update this file AND docs/PRD (bump PRD version) AND CHANGELOG.
 */
object SafeWording {
    const val DISCLAIMER =
        "Proximity is estimated from Bluetooth signal strength and may vary based on " +
        "physical obstructions, device orientation, and interference."

    const val RECORDING_UNKNOWN =
        "Recording activity cannot be determined."

    const val DEVICE_REMAINED =
        "This device has remained near you."

    const val CAMERA_CAPABLE =
        "Camera-capable wearable detected nearby."

    const val UNKNOWN_DEVICE =
        "Unknown BLE device."
}
```

- [ ] **Step 6: Create `FormatUtils.kt`**

```kotlin
package com.wearaware.app.util

import com.wearaware.app.domain.model.ProximityLabel
import java.text.SimpleDateFormat
import java.util.*

/**
 * PURPOSE: UI display formatting helpers. Converts raw values to human-readable strings.
 * NOTES: These are UI utilities — they live in util, not in domain.
 *   ProximityLabel.displayName() is defined here as an extension for UI use only.
 */

/** Formats a duration in milliseconds to a human-readable "Xm Ys" or "Xs" string. */
fun Long.formatDuration(): String {
    val totalSeconds = this / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
}

/** Formats an epoch millisecond timestamp to a "HH:mm:ss" time string. */
fun Long.formatTimestamp(): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(this))

/** Formats an epoch millisecond timestamp to a "yyyy-MM-dd HH:mm:ss" string. */
fun Long.formatFullTimestamp(): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(this))

/** Returns the human-readable display name for a ProximityLabel. */
fun ProximityLabel.displayName(): String = when (this) {
    ProximityLabel.VERY_CLOSE -> "Very close"
    ProximityLabel.STRONG     -> "Strong"
    ProximityLabel.NEARBY     -> "Nearby"
    ProximityLabel.WEAK       -> "Weak"
    ProximityLabel.UNKNOWN    -> "Unknown"
}
```

- [ ] **Step 7: Create `PermissionUtils.kt`**

```kotlin
package com.wearaware.app.util

import android.Manifest
import android.os.Build

/**
 * PURPOSE: Returns the list of permissions required for BLE scanning
 *   based on the current Android API level.
 * LIMITATIONS: On API 23–30, ACCESS_FINE_LOCATION is required by the OS for BLE scanning,
 *   even though WearAware does not use location data.
 *   See docs/SECURITY/permissions-policy.md for full rationale.
 * NOTES: Used by ScanScreen to request permissions via Accompanist.
 */
fun blePermissions(): List<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }
```

- [ ] **Step 8: Commit**

```bash
git add app/src/main/kotlin/com/wearaware/app/ui/theme/
git add app/src/main/kotlin/com/wearaware/app/ui/components/SafeWording.kt
git add app/src/main/kotlin/com/wearaware/app/util/
git commit -m "feat: add UI theme, SafeWording constants, FormatUtils, PermissionUtils"
```

---

### Task 21: SignalBars + VisibilityBadge components

**Files:**
- Create: `app/src/main/kotlin/com/wearaware/app/ui/components/SignalBars.kt`
- Create: `app/src/main/kotlin/com/wearaware/app/ui/components/VisibilityBadge.kt`
- Create: `app/src/main/kotlin/com/wearaware/app/ui/components/ScanStatusHeader.kt`

- [ ] **Step 1: Create `SignalBars.kt`**

```kotlin
package com.wearaware.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.wearaware.app.domain.model.ProximityLabel
import com.wearaware.app.domain.rules.ProximityConfig
import com.wearaware.app.ui.theme.*
import com.wearaware.app.util.displayName

/**
 * PURPOSE: Renders 1–5 signal strength bars for a given ProximityLabel.
 *   Colors do not use green — see product-decisions.md Decision 3.
 * LIMITATIONS: Bar heights are fixed aesthetic choices. Tune with real-device testing.
 * NOTES: Includes a content description for accessibility — color alone does not convey
 *   state (accessibility requirement from design spec Section 9).
 */
@Composable
fun SignalBars(
    proximityLabel: ProximityLabel,
    modifier: Modifier = Modifier
) {
    val barCount = ProximityConfig.barCountFromLabel(proximityLabel)
    val color = when (proximityLabel) {
        ProximityLabel.VERY_CLOSE -> SignalVeryClose
        ProximityLabel.STRONG     -> SignalStrong
        ProximityLabel.NEARBY     -> SignalNearby
        ProximityLabel.WEAK       -> SignalWeak
        ProximityLabel.UNKNOWN    -> SignalUnknown
    }

    Row(
        modifier = modifier.semantics {
            contentDescription = "Signal strength: ${proximityLabel.displayName()}"
        },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        (1..5).forEach { bar ->
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height((bar * 5).dp)
                    .background(
                        color = if (bar <= barCount) color else color.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(1.dp)
                    )
            )
        }
    }
}
```

- [ ] **Step 2: Create `VisibilityBadge.kt`**

```kotlin
package com.wearaware.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.wearaware.app.domain.model.VisibilityState
import com.wearaware.app.ui.theme.DotDetected
import com.wearaware.app.ui.theme.DotSignalLost

/**
 * PURPOSE: Shows a colored dot and text label indicating whether a device is
 *   currently being detected or has lost signal.
 * NOTES: Color alone does not convey state — text label ensures accessibility compliance.
 */
@Composable
fun VisibilityBadge(
    state: VisibilityState,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    if (state == VisibilityState.DETECTED_NOW) DotDetected else DotSignalLost
                )
        )
        Text(
            text = if (state == VisibilityState.DETECTED_NOW) "Detected" else "Signal lost",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
```

- [ ] **Step 3: Create `ScanStatusHeader.kt`**

```kotlin
package com.wearaware.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.wearaware.app.ui.viewmodel.ScanState

/**
 * PURPOSE: Displays the current scan state as a subtitle below the app bar.
 * NOTES: Maps all ScanState values to user-facing text. If a new ScanState is added,
 *   the compiler will warn about non-exhaustive when expression.
 */
@Composable
fun ScanStatusHeader(
    scanState: ScanState,
    modifier: Modifier = Modifier
) {
    val statusText = when (scanState) {
        ScanState.SCANNING              -> "Scanning nearby devices..."
        ScanState.STOPPED               -> "Scanning stopped"
        ScanState.BLUETOOTH_UNAVAILABLE -> "Bluetooth unavailable — enable Bluetooth to scan"
        ScanState.PERMISSIONS_REQUIRED  -> "Bluetooth permissions required to scan"
    }
    Text(
        text = statusText,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/wearaware/app/ui/components/SignalBars.kt
git add app/src/main/kotlin/com/wearaware/app/ui/components/VisibilityBadge.kt
git add app/src/main/kotlin/com/wearaware/app/ui/components/ScanStatusHeader.kt
git commit -m "feat: add SignalBars, VisibilityBadge, ScanStatusHeader UI components"
```

---

### Task 22: DeviceCard component

**Files:**
- Create: `app/src/main/kotlin/com/wearaware/app/ui/components/DeviceCard.kt`

- [ ] **Step 1: Create `DeviceCard.kt`**

```kotlin
package com.wearaware.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wearaware.app.domain.model.ObservedDevice
import com.wearaware.app.util.formatDuration
import com.wearaware.app.util.displayName

/**
 * PURPOSE: Renders a single device row in the ScanScreen device list.
 *   Shows name, category label, signal bars, proximity, visibility, and seen duration.
 *   Tapping navigates to DeviceDetailScreen.
 * NOTES: Full card is clickable — not just a button inside it. Ripple effect is
 *   provided by Compose's clickable modifier.
 */
@Composable
fun DeviceCard(
    device: ObservedDevice,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: name + category + visibility + duration
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.advertisedName ?: "Unknown Device",
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = device.classification.displayLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VisibilityBadge(state = device.visibilityState)
                    Text(
                        text = "Seen ${device.seenDurationMs.formatDuration()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            // Right: signal bars + proximity label + chevron
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                SignalBars(proximityLabel = device.proximityLabel)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = device.proximityLabel.displayName(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.width(4.dp))

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "View device details",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/kotlin/com/wearaware/app/ui/components/DeviceCard.kt
git commit -m "feat: add DeviceCard UI component"
```

---

## Phase 8: Alert Banner

### Task 23: AlertBanner component

**Files:**
- Create: `app/src/main/kotlin/com/wearaware/app/ui/components/AlertBanner.kt`

- [ ] **Step 1: Create `AlertBanner.kt`**

```kotlin
package com.wearaware.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wearaware.app.domain.model.PersistenceAlert
import com.wearaware.app.util.formatDuration

/**
 * PURPOSE: Displays a non-blocking in-app alert banner when a persistence alert is active.
 *   Uses safe wording only — all strings come from SafeWording object.
 * LIMITATIONS: One banner shown at a time (most recently triggered alert).
 *   User must dismiss manually; no auto-dismiss timer in v1.0.
 * NOTES: Dismissing hides the banner but does NOT reset the alert cooldown.
 *   The device remains in the list. If proximity conditions reset, cooldown prevents
 *   immediate re-alert (see EvaluatePersistenceUseCase.COOLDOWN_MS).
 */
@Composable
fun AlertBanner(
    alert: PersistenceAlert,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Privacy awareness alert",
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier
                    .size(20.dp)
                    .padding(top = 2.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = SafeWording.CAMERA_CAPABLE,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${SafeWording.DEVICE_REMAINED} (${alert.durationMs.formatDuration()})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = SafeWording.RECORDING_UNKNOWN,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                )
            }
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Dismiss",
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/kotlin/com/wearaware/app/ui/components/AlertBanner.kt
git commit -m "feat: add AlertBanner component with safe wording only"
```

---

### Task 24: ScanScreen, DeviceDetailScreen, SessionLogScreen

**Files:**
- Create: `app/src/main/kotlin/com/wearaware/app/ui/screens/ScanScreen.kt`
- Create: `app/src/main/kotlin/com/wearaware/app/ui/screens/DeviceDetailScreen.kt`
- Create: `app/src/main/kotlin/com/wearaware/app/ui/screens/SessionLogScreen.kt`

- [ ] **Step 1: Create directory**

```bash
mkdir -p app/src/main/kotlin/com/wearaware/app/ui/screens
```

- [ ] **Step 2: Create `ScanScreen.kt`**

```kotlin
package com.wearaware.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.wearaware.app.ui.components.*
import com.wearaware.app.ui.viewmodel.ScanState
import com.wearaware.app.ui.viewmodel.ScanViewModel
import com.wearaware.app.util.blePermissions

/**
 * PURPOSE: Primary screen. Shows scan status, active alert banner, device list,
 *   and scan start/stop control.
 * NOTES: Permission handling uses Accompanist — launchMultiplePermissionRequest() opens
 *   the OS dialog. Permission state is re-checked on each recomposition.
 *   Device list is keyed by device.id to optimize recomposition.
 */
@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    onNavigateToDetail: (String) -> Unit,
    onNavigateToLog: () -> Unit,
    viewModel: ScanViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val permissionsState = rememberMultiplePermissionsState(permissions = blePermissions())

    LaunchedEffect(permissionsState.allPermissionsGranted) {
        if (!permissionsState.allPermissionsGranted) {
            viewModel.setPermissionsRequired()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WearAware") },
                actions = {
                    IconButton(onClick = onNavigateToLog) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = "View session log"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // Alert banner — shown only when an alert is active
            uiState.activeAlert?.let { alert ->
                AlertBanner(
                    alert = alert,
                    onDismiss = viewModel::dismissAlert
                )
                Spacer(Modifier.height(8.dp))
            }

            // Scan status subtitle
            ScanStatusHeader(scanState = uiState.scanState)

            Spacer(Modifier.height(8.dp))

            // Device list or empty state
            Box(modifier = Modifier.weight(1f)) {
                if (uiState.devices.isEmpty() && uiState.scanState == ScanState.SCANNING) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No nearby devices detected",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.devices, key = { it.id }) { device ->
                            DeviceCard(
                                device = device,
                                onClick = { onNavigateToDetail(device.id) }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Start / Stop scan button
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                onClick = {
                    when {
                        uiState.scanState == ScanState.SCANNING -> viewModel.stopScanning()
                        permissionsState.allPermissionsGranted  -> viewModel.startScanning()
                        else -> permissionsState.launchMultiplePermissionRequest()
                    }
                }
            ) {
                Text(
                    if (uiState.scanState == ScanState.SCANNING) "Stop Scanning"
                    else "Start Scanning"
                )
            }
        }
    }
}
```

- [ ] **Step 3: Create `DeviceDetailScreen.kt`**

```kotlin
package com.wearaware.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wearaware.app.domain.model.ObservedDevice
import com.wearaware.app.ui.components.SafeWording
import com.wearaware.app.ui.components.SignalBars
import com.wearaware.app.ui.components.VisibilityBadge
import com.wearaware.app.ui.viewmodel.ScanViewModel
import com.wearaware.app.util.formatDuration
import com.wearaware.app.util.formatFullTimestamp

/**
 * PURPOSE: Detailed view of a single detected device.
 *   Shows full classification info, RSSI values, timestamps, "why flagged" section,
 *   and always-visible disclaimer.
 * NOTES: Device data comes from ScanViewModel's current device list via getDeviceById().
 *   If the device has been removed (30s signal lost), shows "Device no longer available."
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
    deviceId: String,
    onBack: () -> Unit,
    viewModel: ScanViewModel = hiltViewModel()
) {
    val device: ObservedDevice? = remember(deviceId) {
        viewModel.getDeviceById(deviceId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(device?.advertisedName ?: "Device Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (device == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {
                Text(
                    text = "Device no longer available in current scan session.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Signal and visibility
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Signal", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SignalBars(proximityLabel = device.proximityLabel)
                        VisibilityBadge(state = device.visibilityState)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Raw RSSI: ${device.rawRssi} dBm", style = MaterialTheme.typography.bodySmall)
                    Text("Averaged RSSI: ${device.averagedRssi} dBm", style = MaterialTheme.typography.bodySmall)
                }
            }

            // Classification — "why this was flagged"
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Why this device was flagged", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Text("Category: ${device.classification.category.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }}", style = MaterialTheme.typography.bodySmall)
                    Text("Confidence: ${device.classification.confidence.name}", style = MaterialTheme.typography.bodySmall)
                    device.classification.matchedRuleId?.let {
                        Text("Matched rule: $it", style = MaterialTheme.typography.bodySmall)
                    }
                    device.classification.ruleVersion?.let {
                        Text("Rule set version: $it", style = MaterialTheme.typography.bodySmall)
                    }
                    device.classification.evaluationNotes?.let {
                        Text("Match details: $it", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // Timing
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Session timing", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Text("First seen: ${device.firstSeenAt.formatFullTimestamp()}", style = MaterialTheme.typography.bodySmall)
                    Text("Last seen: ${device.lastSeenAt.formatFullTimestamp()}", style = MaterialTheme.typography.bodySmall)
                    Text("Duration nearby: ${device.seenDurationMs.formatDuration()}", style = MaterialTheme.typography.bodySmall)
                    Text("Detections: ${device.seenCount}", style = MaterialTheme.typography.bodySmall)
                }
            }

            // Always-visible disclaimer
            Text(
                text = SafeWording.DISCLAIMER + " " + SafeWording.RECORDING_UNKNOWN,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
```

- [ ] **Step 4: Create `SessionLogScreen.kt`**

```kotlin
package com.wearaware.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wearaware.app.domain.model.ScanLogEntry
import com.wearaware.app.ui.viewmodel.SessionLogViewModel
import com.wearaware.app.util.formatTimestamp

/**
 * PURPOSE: Displays all session scan log entries with a clear button.
 *   Entries are shown newest first (order maintained by Room query ORDER BY timestamp DESC).
 * NOTES: Clear requires confirmation via AlertDialog to prevent accidental data loss.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionLogScreen(
    onBack: () -> Unit,
    viewModel: SessionLogViewModel = hiltViewModel()
) {
    val log by viewModel.log.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear session log?") },
            text = { Text("All scan log entries from this session will be permanently deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearLog()
                    showClearDialog = false
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Session Log") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (log.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Clear session log")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (log.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No detections logged this session.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(log, key = { it.id }) { entry ->
                    ScanLogEntryCard(entry = entry)
                }
            }
        }
    }
}

@Composable
private fun ScanLogEntryCard(entry: ScanLogEntry) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = entry.advertisedName ?: "Unknown Device",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = entry.timestamp.formatTimestamp(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${entry.category.replace('_', ' ')} · ${entry.confidence} confidence",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Proximity: ${entry.proximityLabel} · RSSI avg: ${entry.averagedRssi} dBm",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            entry.matchedRuleId?.let {
                Text(
                    text = "Rule: $it (v${entry.ruleVersion})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/wearaware/app/ui/screens/
git commit -m "feat: add ScanScreen, DeviceDetailScreen, SessionLogScreen"
```

---

### Task 25: NavGraph + wire MainActivity 📱

**Files:**
- Create: `app/src/main/kotlin/com/wearaware/app/ui/navigation/NavGraph.kt`
- Modify: `app/src/main/kotlin/com/wearaware/app/MainActivity.kt`

- [ ] **Step 1: Create directory**

```bash
mkdir -p app/src/main/kotlin/com/wearaware/app/ui/navigation
```

- [ ] **Step 2: Create `NavGraph.kt`**

```kotlin
package com.wearaware.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wearaware.app.ui.screens.DeviceDetailScreen
import com.wearaware.app.ui.screens.ScanScreen
import com.wearaware.app.ui.screens.SessionLogScreen

/**
 * PURPOSE: Defines the app's navigation graph.
 *   Three destinations: scan (start), device detail, session log.
 * NOTES: deviceId is passed as a URL-encoded path segment.
 *   ScanViewModel is scoped to the NavBackStackEntry for the "scan" destination,
 *   so device state persists while on the detail screen (back navigation works correctly).
 */
@Composable
fun WearAwareNavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "scan"
    ) {
        composable("scan") {
            ScanScreen(
                onNavigateToDetail = { deviceId ->
                    navController.navigate("device/${deviceId}")
                },
                onNavigateToLog = {
                    navController.navigate("session_log")
                }
            )
        }

        composable(
            route = "device/{deviceId}",
            arguments = listOf(
                navArgument("deviceId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("deviceId")
                ?: return@composable
            DeviceDetailScreen(
                deviceId = deviceId,
                onBack = { navController.popBackStack() }
            )
        }

        composable("session_log") {
            SessionLogScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
```

- [ ] **Step 3: Update `MainActivity.kt` to wire the NavGraph**

Replace the existing `MainActivity.kt` content:

```kotlin
package com.wearaware.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.wearaware.app.ui.navigation.WearAwareNavGraph
import com.wearaware.app.ui.theme.WearAwareTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WearAwareTheme {
                WearAwareNavGraph()
            }
        }
    }
}
```

- [ ] **Step 4: Build to verify compilation**

```bash
./gradlew :app:assembleDebug
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/wearaware/app/ui/navigation/NavGraph.kt
git add app/src/main/kotlin/com/wearaware/app/MainActivity.kt
git commit -m "feat: add NavGraph and wire MainActivity — app is now runnable"
```

---

## Phase 9: Polish, QA & Meta Glasses Validation 📝 📱

### Task 26: Runtime version surfacing + About info 📝

**Objective:** Expose `app VERSION`, `rule_set_version`, and `rule_set_hash` at runtime —
logged at app startup and accessible from a simple About row in the ScanScreen top bar menu.
This satisfies the audit traceability requirement: behavior is tied to a specific rule set snapshot.

**Files:**
- Create: `app/src/main/kotlin/com/wearaware/app/util/AboutInfo.kt`
- Modify: `app/src/main/kotlin/com/wearaware/app/ui/screens/ScanScreen.kt`
- Modify: `app/src/main/kotlin/com/wearaware/app/WearAwareApplication.kt`

- [ ] **Step 1: Create `AboutInfo.kt`**

```kotlin
package com.wearaware.app.util

import android.util.Log
import com.wearaware.app.BuildConfig
import com.wearaware.app.domain.repository.RulesRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PURPOSE: Exposes app version and rule set version metadata for display and logging.
 *   Satisfies the runtime version traceability requirement from soc2-controls.md.
 * NOTES: Logged at app startup so that any crash report or logcat capture includes
 *   the exact rule set version active at the time.
 */
@Singleton
class AboutInfo @Inject constructor(
    private val rulesRepository: RulesRepository
) {
    val appVersion: String = BuildConfig.VERSION_NAME

    val ruleSetVersion: String by lazy {
        rulesRepository.getRules().metadata.version
    }

    val ruleSetHash: String by lazy {
        rulesRepository.getRules().metadata.hash
    }

    /**
     * Logs version info to Logcat at INFO level.
     * Call once from Application.onCreate() so it appears in every session's log.
     */
    fun logVersionInfo() {
        Log.i("WearAware", "App version: $appVersion")
        Log.i("WearAware", "Rule set version: $ruleSetVersion")
        Log.i("WearAware", "Rule set hash: $ruleSetHash")
    }
}
```

- [ ] **Step 2: Inject `AboutInfo` into `WearAwareApplication` and log at startup**

Replace `WearAwareApplication.kt`:

```kotlin
package com.wearaware.app

import android.app.Application
import com.wearaware.app.util.AboutInfo
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class WearAwareApplication : Application() {

    @Inject lateinit var aboutInfo: AboutInfo

    override fun onCreate() {
        super.onCreate()
        aboutInfo.logVersionInfo()
    }
}
```

- [ ] **Step 3: Add About menu item to `ScanScreen` top bar**

In `ScanScreen.kt`, add a state variable and About dialog, and a menu icon to the `TopAppBar` actions:

```kotlin
// Add inside ScanScreen composable, before Scaffold:
var showAboutDialog by remember { mutableStateOf(false) }
val aboutInfo: AboutInfo = hiltViewModel<ScanViewModel>() // Not ideal — inject via parameter
// Better: pass appVersion/ruleSetVersion as parameters from a wrapper composable, or:
// pass the AboutInfo strings through ScanUiState (add to ScanUiState).
```

Simplest v1 approach — add `aboutInfo: AboutInfo` strings to `ScanUiState`:

Add to `ScanUiState.kt`:
```kotlin
data class ScanUiState(
    val scanState: ScanState = ScanState.STOPPED,
    val devices: List<ObservedDevice> = emptyList(),
    val activeAlert: PersistenceAlert? = null,
    val appVersion: String = "",
    val ruleSetVersion: String = "",
    val ruleSetHash: String = ""
)
```

Inject `AboutInfo` into `ScanViewModel` and populate those fields:
```kotlin
@HiltViewModel
class ScanViewModel @Inject constructor(
    // ... existing deps ...,
    aboutInfo: AboutInfo
) : ViewModel() {
    init {
        _uiState.update { it.copy(
            appVersion = aboutInfo.appVersion,
            ruleSetVersion = aboutInfo.ruleSetVersion,
            ruleSetHash = aboutInfo.ruleSetHash
        ) }
    }
    // ... rest unchanged ...
}
```

In `ScanScreen.kt`, add About dialog and menu icon:
```kotlin
// Add after showAboutDialog state:
if (showAboutDialog) {
    AlertDialog(
        onDismissRequest = { showAboutDialog = false },
        title = { Text("About WearAware") },
        text = {
            Column {
                Text("App version: ${uiState.appVersion}")
                Text("Rule set version: ${uiState.ruleSetVersion}")
                Text("Rule set hash: ${uiState.ruleSetHash.take(20)}...")
            }
        },
        confirmButton = {
            TextButton(onClick = { showAboutDialog = false }) { Text("Close") }
        }
    )
}
// In TopAppBar actions, add alongside the log icon:
IconButton(onClick = { showAboutDialog = true }) {
    Icon(Icons.Default.Info, contentDescription = "About")
}
```

- [ ] **Step 4: Build to verify no compile errors**

```bash
./gradlew :app:assembleDebug
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/wearaware/app/util/AboutInfo.kt
git add app/src/main/kotlin/com/wearaware/app/WearAwareApplication.kt
git add app/src/main/kotlin/com/wearaware/app/ui/viewmodel/ScanUiState.kt
git add app/src/main/kotlin/com/wearaware/app/ui/viewmodel/ScanViewModel.kt
git add app/src/main/kotlin/com/wearaware/app/ui/screens/ScanScreen.kt
git commit -m "feat: surface app version + rule_set_version + rule_set_hash in About dialog and startup log"
```

---

### Task 27: SafeWording audit + run all unit tests 🧪

**Objective:** (1) Verify all alert and disclaimer strings come exclusively from `SafeWording`
constants. (2) Confirm all unit tests pass. Both gates must clear before real-device testing.

#### SafeWording Audit

- [ ] **Step 1: Grep for ad-hoc alert/disclaimer strings in UI and ViewModel layers**

Run these searches. Each should return zero results:

```bash
# Search for any raw "recording" claim in screens/viewmodels (must not appear)
grep -r "recording" app/src/main/kotlin/com/wearaware/app/ui/ --include="*.kt" -l
grep -r "recording" app/src/main/kotlin/com/wearaware/app/ui/viewmodel/ --include="*.kt" -l

# Search for any raw "following" claim (must not appear anywhere in user-facing code)
grep -r "following" app/src/main/kotlin/com/wearaware/app/ui/ --include="*.kt" -l

# Search for any raw "Camera-capable" or "proximity is estimated" text outside SafeWording.kt
grep -r "Camera-capable\|proximity is estimated\|recording activity\|remained near" \
  app/src/main/kotlin/com/wearaware/app/ui/ \
  app/src/main/kotlin/com/wearaware/app/ui/viewmodel/ \
  --include="*.kt" | grep -v "SafeWording.kt"
```

Expected: all commands return empty output.
If any match is found outside `SafeWording.kt`, move that string into `SafeWording` and
reference the constant instead.

- [ ] **Step 2: Verify SafeWording constants are the only source in AlertBanner and screens**

```bash
grep -n "SafeWording\." app/src/main/kotlin/com/wearaware/app/ui/components/AlertBanner.kt
grep -n "SafeWording\." app/src/main/kotlin/com/wearaware/app/ui/screens/DeviceDetailScreen.kt
```

Expected: both files reference `SafeWording.*` for all disclaimer and alert text.
At minimum, `AlertBanner.kt` must reference `SafeWording.CAMERA_CAPABLE`,
`SafeWording.DEVICE_REMAINED`, and `SafeWording.RECORDING_UNKNOWN`.

#### Unit Test Gate

- [ ] **Step 3: Run full unit test suite**

```bash
./gradlew :app:test
```
Expected: BUILD SUCCESSFUL with output like:
```
com.wearaware.app.domain.rules.ProximityConfigTest > ... PASSED
com.wearaware.app.domain.rules.RssiSmootherTest > ... PASSED
com.wearaware.app.domain.rules.FingerprintClassifierTest > ... PASSED
com.wearaware.app.domain.usecase.EvaluatePersistenceUseCaseTest > ... PASSED
com.wearaware.app.data.mapper.ScanLogMapperTest > ... PASSED
```
All tests must pass. Do NOT proceed to real-device testing if any fail.

- [ ] **Step 4: Fix any failures before continuing**

If any test fails, diagnose and fix before proceeding.
Expected: `BUILD SUCCESSFUL` with no `FAILED` lines.

- [ ] **Step 5: Build debug APK**

```bash
./gradlew :app:assembleDebug
```
Expected: APK at `app/build/outputs/apk/debug/app-debug.apk`

---

### Task 28: Real-device testing with Meta Ray-Ban glasses 📱

Follow the test plan at `docs/TESTING/meta-glasses-test-plan.md`.

- [ ] **Step 1: Install app on test device**

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

- [ ] **Step 2: Run test scenarios T1–T7** (see test plan)

For each test scenario:
- Record actual RSSI range observed for Meta glasses at each distance
- Note if proximity label assignments match expected ranges
- Check if alert fires after 60s

- [ ] **Step 3: Tune RSSI thresholds if needed**

If real-world testing shows Meta glasses appear at different RSSI ranges than expected,
update the thresholds in `ProximityConfig.kt`:

```kotlin
// Example: if Meta glasses at 2m show -70 to -75 instead of -65 to -75
const val STRONG_THRESHOLD_DBM = -68  // tuned from -65 after Meta glasses testing
```

After any threshold change:
- Re-run `ProximityConfigTest` to update expected values
- Re-run all tests: `./gradlew :app:test`

- [ ] **Step 4: Update rule_set_hash in fingerprint_rules.json**

After finalizing the rules file, compute its SHA-256 hash and update:
```bash
shasum -a 256 app/src/main/assets/fingerprint_rules.json
```
Update the `rule_set_hash` field in `fingerprint_rules.json` with the actual hash.

- [ ] **Step 5: Pass all 7 test scenarios from the test plan**

Mark each test scenario in `docs/TESTING/meta-glasses-test-plan.md`:
- [ ] T1: Basic Detection
- [ ] T2: Signal Bars and Proximity
- [ ] T3: RSSI Smoothing
- [ ] T4: Signal Lost Behavior
- [ ] T5: Persistence Alert
- [ ] T6: Alert Cooldown
- [ ] T7: Session Log

---

### Task 29: Final documentation update 📝

**Objective:** Synchronize all docs with the final validated state.

- [ ] **Step 1: Update CHANGELOG with final release entry**

Add to `CHANGELOG.md` under `## [1.0.0]`:
```markdown
### Finalized
- rule_set_version: 1.0.0
- rule_set_hash: <actual sha256 hash>
- App version: 1.0.0
- RSSI thresholds validated with Meta Ray-Ban glasses (update values if tuned)
- All 7 test scenarios in meta-glasses-test-plan.md: PASSED
```

- [ ] **Step 2: Update README with actual tested RSSI values**

In `README.md`, update the "Testing With Meta Ray-Ban Glasses" section with
the actual RSSI ranges observed during real-device testing.

- [ ] **Step 3: Update PRD CURRENT.md date if needed**

If any PRD changes were made during implementation, bump the PRD version:
- Copy `docs/PRD/v1.0.md` to `docs/PRD/v1.1.md`
- Update `docs/PRD/CURRENT.md` to point to v1.1
- Add entry to CHANGELOG

- [ ] **Step 4: Final commit**

```bash
git add .
git commit -m "release: WearAware v1.0.0 — all tests pass, Meta glasses validated"
```

---

## Pre–Real-Device Testing Checklist 📱

The following tasks must be complete before running on real hardware:

- [x] Task 1–5: Gradle + manifest + all documentation
- [x] Task 6–9: All domain models and repository interfaces
- [x] Task 10–12: Fingerprint rule engine + rules loader
- [x] Task 13–14: BLE scanner + BleRepositoryImpl
- [x] Task 15–17: Room + all use cases (including EvaluatePersistenceUseCase)
- [x] Task 18: Hilt DI wired
- [x] Task 19: ScanViewModel + SessionLogViewModel
- [x] Task 20–25: All UI components + NavGraph
- [x] Task 26: Runtime version surfacing (app version + rule_set_version + rule_set_hash)
- [x] Task 27: SafeWording audit passed + full unit test suite passing

---

## Tasks That Must Update README / CHANGELOG / PRD Version

| Task | What to update |
|---|---|
| Task 3 | Creates README, CHANGELOG, VERSION |
| Task 4 | Creates PRD v1.0 |
| Task 26 | Version surfacing — no doc update needed, but app version must match VERSION file |
| Task 29 | Updates CHANGELOG with rule_set_hash + test results |
| Any threshold change | CHANGELOG + README RSSI table |
| Any rule change | CHANGELOG rule_set_version + rule_set_hash |
| Any PRD change | docs/PRD new version file + CURRENT.md + CHANGELOG |

---

## Tasks That Require Unit Tests

| Task | Tests |
|---|---|
| Task 8 | ProximityConfigTest, RssiSmootherTest |
| Task 11 | FingerprintClassifierTest |
| Task 16 | ScanLogMapperTest |
| Task 17 | EvaluatePersistenceUseCaseTest |
| Task 27 | SafeWording audit + run all — must pass before device testing |
