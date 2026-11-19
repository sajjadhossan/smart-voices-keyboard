# 🚀 Deployment and Build Guide

This document outlines the build, test, and release deployment instructions for Smart Voice Keyboard across Android, Windows, and macOS.

---

## 1. Automated GitHub Actions Workflows

The repository contains three GitHub Actions workflows in `.github/workflows/`:

| Workflow File | Description | Trigger | Output Artifacts |
| :--- | :--- | :--- | :--- |
| `android-build.yml` | Builds Android Release APK | Push to `main`, PR, Tag `v*`, Manual | `SmartVoiceKeyboard-v1.0.0-android.apk` |
| `desktop-build.yml` | Builds Windows & macOS Desktop Binaries | Push to `main`, PR, Tag `v*`, Manual | `.exe` (NSIS & Portable), `.dmg`, `.zip` |
| `release.yml` | Builds all platforms and publishes a GitHub Release | Push tag `v*` or Manual dispatch | Published Release on GitHub Releases |

### Triggering Manual Builds from GitHub UI
1. Navigate to **Actions** in your GitHub repository.
2. Select **Android APK Build**, **Desktop Build**, or **Multi-Platform Release**.
3. Click **Run workflow** -> Select `main` branch -> Click **Run workflow**.

---

## 2. Local Desktop Build Instructions

### Windows Build (.exe)
```bash
npm install
npm run build:windows
```
*Outputs will be generated in `release/`:*
- `Smart Voice Keyboard-v1.0.0-windows-x64.exe` (Installer)
- `Smart Voice Keyboard-v1.0.0-windows-x64.zip` (Portable)

### macOS Build (.dmg & .zip)
```bash
npm install
npm run build:mac
```
*Outputs will be generated in `release/`:*
- `Smart Voice Keyboard-v1.0.0-macOS-x64.dmg` / `.zip`
- `Smart Voice Keyboard-v1.0.0-macOS-arm64.dmg` / `.zip`

---

## 3. Local Android APK Build Instructions

### Prerequisites
- JDK 17 installed and configured (`JAVA_HOME` pointing to JDK 17).
- Android SDK CLI tools or Android Studio installed.

### Build Steps
```bash
cd android

# Generate debug keystore for signing if needed:
cd app
keytool -genkey -v -keystore debug.keystore -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -storepass android -keypass android -dname "CN=Android Debug,O=Android,C=US"
cd ..

# Build APK
./gradlew assembleRelease
```
*APK output path:*
`android/app/build/outputs/apk/release/app-release.apk`
