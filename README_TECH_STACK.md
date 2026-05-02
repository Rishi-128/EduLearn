# 🚀 EduLearn Tech Stack

A comprehensive guide to the frontend and backend technologies used in this Android educational app.

## 📱 **Frontend Technologies**

### **Primary UI Framework**

- **🎨 Jetpack Compose** - Modern declarative UI toolkit for Android
- **📱 Material Design 3** - Google's latest design system with dynamic theming
- **🌙 Dark/Light Mode** - Automatic theme switching based on system preferences

### **UI Components & Libraries**

```kotlin
// Core Compose Libraries
androidx.compose.material3    // Buttons, Cards, TextFields, etc.
androidx.compose.ui          // Core UI components and layouts
androidx.compose.foundation  // Foundation layouts (Column, Row, Box)
androidx.compose.runtime     // State management (@Composable, remember, StateFlow)
```

### **Navigation & Architecture**

- **🧭 Navigation Compose** - Modern navigation between screens
- **🏗️ MVVM Architecture** - ViewModel + LiveData/StateFlow pattern
- **🔄 State Management** - Reactive UI with StateFlow and Compose state

### **Key UI Features**

- **📐 Responsive Layouts** - Adapts to different screen sizes
- **🎭 Material Icons** - Consistent iconography throughout the app
- **🎨 Custom Theming** - EduLearnTheme with dynamic colors
- **⚡ Real-time Updates** - Live progress tracking in educational features

---

## 🔧 **Backend Architecture**

> **Note**: This app uses a **decentralized P2P architecture** - NO traditional backend servers!

### **📊 Local Data Storage**

```kotlin
// Storage Technologies
SharedPreferences           // User settings, preferences, small data
EncryptedSharedPreferences  // Secure storage for sensitive data
Local File System          // Documents, videos, educational content
JSON (Gson)                // Data serialization for complex objects
```

### **🌐 Networking & Communication**

```kotlin
// P2P Communication Stack
Bluetooth Low Energy (BLE)  // Mesh networking between devices
Nostr Protocol             // Decentralized messaging protocol
Tor Network (Arti)         // Anonymous internet routing
WebSocket (OkHttp)         // Real-time communication with Nostr relays
```

### **🔒 Security & Cryptography**

```kotlin
// Security Libraries
Bouncy Castle              // Advanced cryptographic operations
Tink Android               // Google's cryptography library
Noise Protocol             // Secure key exchange and encryption
EncryptedSharedPreferences // Local data encryption
```

### **📚 Educational Backend Features**

```kotlin
// Educational Data Management
OfflineDataManager         // Student progress, quiz scores, video completion
EduLearnFileStorage        // Local file management for documents/videos
StateFlow                  // Real-time progress updates
JSON Persistence           // Educational data serialization
```

---

## 🏗️ **Architecture Overview**

### **Data Flow Pattern**

```
📱 UI (Compose) ↔ 🎯 ViewModel ↔ 📊 Repository ↔ 💾 Local Storage
                                     ↓
                           🌐 P2P Network (Bluetooth/Nostr)
```

### **Key Architectural Decisions**

- **🌍 Offline-First** - All features work without internet
- **🔗 P2P Mesh Networking** - Direct device-to-device communication
- **📱 Decentralized** - No central servers or databases
- **🔒 Privacy-Focused** - Data stays on device, Tor routing for anonymity

---

## 📦 **Dependencies & Libraries**

### **Frontend Dependencies**

```kotlin
// UI & Compose
androidx.compose.bom:2025.06.01
androidx.compose.material3
androidx.activity.compose
androidx.navigation.compose

// Architecture
androidx.lifecycle.viewmodel-compose
androidx.lifecycle.runtime-ktx
kotlinx.coroutines.android
```

### **Backend Dependencies**

```kotlin
// Networking
okhttp:4.12.0                    // WebSocket support
nordic-ble:2.6.1                 // Bluetooth Low Energy

// Security
bouncycastle:1.70                // Cryptography
tink-android:1.10.0              // Google's crypto library
androidx.security.crypto         // Encrypted storage

// Data
gson:2.13.1                      // JSON serialization
```

### **Platform & Tools**

```kotlin
// Android Platform
compileSdk: 35
minSdk: 26                       // API 26 for proper BLE support
targetSdk: 34
kotlin: 2.2.0
```

---

## 🚀 **Getting Started**

### **Prerequisites**

- Android Studio Arctic Fox or newer
- Android SDK 26+ (for Bluetooth Low Energy support)
- Kotlin 2.2.0+

### **Build & Run**

```bash
# Clone the repository
git clone <repository-url>

# Build debug APK
./gradlew assembleDebug

# Install on device
./gradlew installDebug
```

### **Key Features**

- **🗨️ Chat Mode** - Secure P2P messaging with mesh networking
- **👨‍🎓 Student Mode** - Interactive learning with offline progress tracking
- **👨‍🏫 Teacher Mode** - Educational content management and student monitoring

---

## 💡 **Unique Technical Highlights**

### **What Makes This App Special**

1. **🌍 Completely Serverless** - Each device acts as both client and server
2. **📱 Offline Educational Platform** - Full learning environment without internet
3. **🔗 Bluetooth Mesh Network** - Revolutionary P2P communication
4. **🔒 Privacy by Design** - Tor routing, local storage, no data collection
5. **⚡ Real-time Reactivity** - Live updates using StateFlow and Compose

### **Educational Innovation**

- **📊 Live Progress Tracking** - Real-time quiz scores and video progress
- **🎥 Offline Video Player** - Built-in player with progress tracking
- **📄 Document Viewer** - PDF viewer with zoom and navigation
- **📁 File Upload System** - Teachers can share content offline
- **💾 Local Data Persistence** - All progress saved locally using JSON

---

## 🛠️ **Development Notes**

### **Code Organization**

```
📁 app/src/main/java/com/edulearn/android/
├── 🎨 ui/                    # Frontend (Compose UI)
├── 📊 data/                  # Data management
├── 🔒 identity/              # Security & cryptography
├── 🌐 mesh/                  # Bluetooth networking
├── 📡 nostr/                 # Nostr protocol
├── 💾 storage/               # File management
└── 📚 educational/           # Educational features
```

### **Build Configuration**

- **🔧 Gradle Kotlin DSL** - Modern build configuration
- **📦 Version Catalogs** - Centralized dependency management
- **🛡️ ProGuard** - Code obfuscation for release builds
- **📱 APK Optimization** - Resource shrinking and compression

---

## 📄 **License**

This project demonstrates modern Android development with innovative P2P networking and offline-first educational features.

---

_Built with ❤️ using Jetpack Compose and decentralized technologies_
