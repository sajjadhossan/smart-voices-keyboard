# 🎙️ Smart Voice Keyboard By Sajjad Hossan

[![Cross-Platform](https://img.shields.io/badge/Platform-Android%20%7C%20Windows%20%7C%20macOS-blue.svg)](https://github.com)
[![License: MIT](https://img.shields.io/badge/License-MIT-emerald.svg)](LICENSE)
[![Speech-to-Text](https://img.shields.io/badge/STT-Open--Source%20Whisper%20%2F%20Native-orange.svg)](core/stt)
[![Gemini AI](https://img.shields.io/badge/AI-Google%20Gemini%201.5%20Flash-purple.svg)](https://aistudio.google.com/)

A modern, commercial-grade, cross-platform voice keyboard application for **Android**, **Windows**, and **macOS**. Featuring free high-accuracy open-source speech-to-text, real-time Gemini AI translation between English and Bangla, an unlimited persistent clipboard manager, and intuitive Bangla, English, and Banglish (phonetic) typing modes.

---

## ✨ Key Features

1. **🎙️ Free, High-Accuracy Open-Source Speech-to-Text (STT)**:
   - Bengali (`bn-BD`) and English (`en-US`) continuous dictation.
   - Dual-engine architecture: Local Open-Source Whisper / Native System Speech engine with zero licensing costs and zero latency streaming.

2. **🌐 Google Gemini AI Translation & Voice Commands**:
   - Live bidirectional translation on-the-fly: **English ➔ Bengali** and **Bengali ➔ English**.
   - Natural language commands: Voice dictate "Translate to Bengali: Let's meet tomorrow" and receive authentic translated text directly injected into your active app.
   - Grammar correction, formal/casual tone rewriting, and text summarization.

3. **⌨️ Multi-Mode Typing Engine**:
   - **English (QWERTY)**: Standard layout with smart punctation and capitalization.
   - **Bangla (বাংলা)**: Authentic Unicode Bengali character map and conjunct layout.
   - **Banglish (ফোনেটিক)**: Pure phonetic typing (e.g. typing `ami bangla bhalobashi` automatically outputs `আমি বাংলা ভালোবাসি`).

4. **📋 Unlimited Clipboard Manager**:
   - Persistent storage with deduplication, pinning (`★`), character counts, and full-text search.
   - Global auto-paste: Tap any historical clip or press hotkey to immediately inject it into Word, Notepad, Chrome, WhatsApp, or any focused application.

5. **🪟 Desktop Floating Popup (Windows & macOS)**:
   - Minimalist glassmorphism floating bar triggered by global hotkeys (`Ctrl+Alt+Space` or `Cmd+Alt+Space`).
   - System tray integration with quick actions: Dictate, Translate, Clipboard, Settings.

6. **📱 Native Android Input Method Service (IME)**:
   - Complete system-level Android keyboard implementation with an integrated Gboard-style toolbar, voice mic, clipboard drawer, and one-tap live translation.

7. **⚡ Gboard-Style Live Inline Translation & Intelligent Prediction**:
   - Live bidirectional translation buffer with automatic typing injection.
   - Smart N-gram word prediction, autocorrect for English and Bengali typos, dynamic dictionary learning, and context emojis.

8. **🎨 Dynamic 7-Theme Switcher (Mobile, Desktop & Web)**:
   - 7 handcrafted color themes: **Dark Slate** (default), **AMOLED Black** (pure black for battery savings), **Modern Light**, **Cyber Neon** (cyberpunk violet), **Ocean Blue**, **Forest Emerald**, and **Sunset Flame**.
   - Instant real-time live preview upon selection with zero reload needed.

9. **🎙️ Gemini Multimodal Acoustic Voice Dictation & Auto-Punctuation (Phase 4)**:
   - Built-in Voice Activity Detection (VAD) with automatic silence detection (2.2s silence auto-stop).
   - Natural spoken punctuation conversion (দাড়ি, কমা, জিজ্ঞাসা, new line) and contextual automatic punctuation for complete sentences.
   - Natural voice commands: "মুছে দাও", "নতুন লাইন", "বাংলা করো: ...", "ইংরেজি করো: ...".
   - Direct toolbar voice language toggle (🇧🇩 BN ⇄ 🇺🇸 EN) for instant multilingual voice typing.

10. **🔒 Cross-Platform Backup/Restore & AES-256-GCM Security (Phase 5)**:
    - Encrypted and unencrypted JSON backup export & restore across Android, Windows, macOS, and Web.
    - Automatic deduplication and conflict-free merging of clipboard history and custom user dictionary.
    - Centralized authenticated backup API (`POST /api/backup/export` and `POST /api/backup/import`).

---

## 🏗️ Project Architecture

```
Smart Voices Keyboard By SajjadHossan/
├── core/                              # Shared cross-platform engines
│   ├── phonetic/                      # Rule-based Bengali phonetic transliteration
│   │   └── phonetic-engine.js
│   ├── gemini/                        # Gemini 1.5 Flash translation & voice command client
│   │   └── translator.js
│   ├── stt/                           # Speech-to-Text manager (Whisper / Native)
│   │   └── stt-engine.js
│   └── clipboard/                     # Unlimited persistent clipboard store
│       └── clipboard-store.js
├── desktop/                           # Windows & macOS Electron application
│   ├── electron-builder.yml           # Packaging config for NSIS .exe, .dmg, .zip
│   └── src/
│       ├── main/                      # Main process, tray, global shortcuts, active app injection
│       │   ├── index.js
│       │   └── preload.js
│       └── renderer/                  # Sleek desktop popup UI (HTML/CSS/JS)
│           ├── index.html
│           ├── styles.css
│           └── app.js
├── android/                           # Native Android InputMethodService (IME) App
│   ├── app/src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── res/                       # Keyboard layouts, strings, themes
│   │   └── java/com/smartvoices/keyboard/
│   │       ├── SmartKeyboardService.kt # System keyboard service & toolbar
│   │       ├── phonetic/PhoneticEngine.kt
│   │       ├── clipboard/ClipboardDbHelper.kt
│   │       ├── gemini/GeminiService.kt
│   │       └── ui/SettingsActivity.kt
├── .github/workflows/                 # Automated CI/CD
│   ├── android-build.yml              # Automatic release APK compilation
│   ├── desktop-build.yml              # Windows .exe & macOS .dmg matrix build
│   └── release.yml                    # Multiplatform unified GitHub Release
└── docs/                              # Manuals & Architecture
    ├── ARCHITECTURE.md
    └── USER_MANUAL_BN.md
```

---

## 🚀 Quick Start Guide

### 1. Prerequisites
- **Node.js**: v18.0.0 or higher
- **Android SDK & JDK 17** (for Android APK building)

### 2. Desktop (Windows & macOS) Installation & Run
```bash
# Clone the repository
git clone https://github.com/username/smart-voices-keyboard.git
cd "Smart Voices Keyboard By SajjadHossan"

# Install dependencies
npm install

# Run core tests
npm test

# Start Desktop Application in development mode
npm run dev:desktop
```

### 3. Build Desktop Installers
```bash
# Windows (creates .exe installer & portable binary in release/)
npm run build:windows

# macOS (creates universal .dmg and .zip in release/)
npm run build:mac
```

### 4. Build Android APK
```bash
cd android
./gradlew assembleRelease
# The generated APK is located at:
# android/app/build/outputs/apk/release/app-release.apk
```

---

## ⚙️ Configuration (.env)

Create a `.env` file in the project root:
```env
GEMINI_API_KEY=your_gemini_api_key_here
STT_ENGINE=native
DEFAULT_TYPING_MODE=bangla
AUTO_PASTE_ENABLED=true
```

---

## ⌨️ Desktop Global Hotkeys

| Action | Windows Shortcut | macOS Shortcut |
| :--- | :--- | :--- |
| **Toggle Floating Keyboard & Popup** | `Ctrl + Alt + Space` | `Cmd + Alt + Space` |
| **Open Clipboard History** | `Ctrl + Alt + V` | `Cmd + Alt + V` |
| **Quick Translate In Active App** | `Ctrl + Alt + T` | `Cmd + Alt + T` |

---

## 📄 License
This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
