<p align="center">
    <img src="https://github.com/user-attachments/assets/188c42f8-d249-4a72-b27a-e2b4f10a00a8" alt="EduLearn Android Logo" width="480">
</p>

> [!WARNING]
> This project is currently in active development and has not been published or open-sourced. It has not received an external security review. Do not use it for sensitive use cases until it has been audited.

# EduLearn for Android

EduLearn is a collaborative learning platform built on **offline mesh networking**. It enables secure, decentralized peer-to-peer messaging and educational content sharing over Bluetooth LE — no internet, no servers, no phone numbers required.

The app operates in two modes:
- **Chat Mode** — Encrypted P2P messaging through a Bluetooth mesh network with IRC-style commands, channels, and private messaging.
- **Educational Mode** — A teacher-student platform for distributing quizzes, documents, and educational resources entirely offline via the mesh network.

## Current Status

| Item | Status |
|:---|:---|
| Google Play Store | 🔴 Not published |
| App Store (iOS) | 🔴 Not available |
| Open Source | 🔴 Not yet released |
| Development Stage | 🟡 Active development (v1.3.0) |

## Features

### Mesh Networking & Communication
- **Decentralized Bluetooth Mesh** — Automatic peer discovery and multi-hop message relay over Bluetooth LE (up to 7 hops)
- **End-to-End Encryption** — X25519 key exchange + AES-256-GCM for private messages
- **Channel-Based Chats** — Topic-based group messaging with optional password protection (Argon2id + AES-256-GCM)
- **Geohash Channels** — Location-based group channels at various geographic levels (block, neighborhood, city, province, region)
- **Store & Forward** — Messages cached for offline peers and delivered when they reconnect
- **Wi-Fi Direct** — Peer-to-peer file sharing over Wi-Fi Direct for large transfers
- **Message Compression** — LZ4 compression for messages >100 bytes (30-70% bandwidth savings)

### Educational Features
- **Teacher Dashboard** — Create and manage quizzes, monitor student progress, share documents
- **Quiz Distribution** — Create multi-question quizzes with time limits and distribute them over the mesh network
- **Student Quiz Interface** — Take quizzes with progress tracking, scoring, and result review
- **Document Sharing** — Share educational files (PDFs, documents) over Bluetooth and Wi-Fi Direct
- **Offline Video Library** — Built-in video player for locally stored educational content
- **Student Community Chat** — Dedicated chat space for student collaboration
- **Device Scanner** — Discover and connect to nearby devices in the mesh
- **File Manager** — Manage received and shared files

### Security & Privacy
- **No Registration** — No accounts, emails, or phone numbers required
- **Digital Signatures** — Ed25519 for message authenticity
- **Forward Secrecy** — New key pairs generated each session
- **Ephemeral by Default** — Messages exist only in device memory
- **Cover Traffic** — Random delays and dummy messages prevent traffic analysis
- **Emergency Wipe** — Triple-tap logo to instantly clear all data
- **Bundled Tor Support** — Built-in Tor network integration for enhanced privacy when internet is available
- **Encrypted Storage** — User settings secured with EncryptedSharedPreferences

### User Experience
- **Dual App Modes** — Switch between Chat Mode and Educational Mode
- **Teacher & Student Roles** — Role-based UI with dedicated dashboards
- **IRC-Style Commands** — `/join`, `/msg`, `/who`, `/block`, `/pass`, `/transfer`, `/save`, `/clear`
- **@ Mentions** — Mention users with autocomplete suggestions
- **Dark/Light Themes** — Switchable themes with terminal-inspired aesthetic
- **Multi-Language Support** — English, Hindi (हिंदी), and Punjabi (ਪੰਜਾਬੀ)
- **Adaptive Battery Management** — Performance, balanced, power saver, and ultra-low power modes
- **Nostr Integration** — Decentralized identity via Nostr protocol
- **Proof of Work** — Configurable PoW for spam prevention

## Tech Stack

| Component | Technology |
|:---|:---|
| Language | Kotlin |
| UI Framework | Jetpack Compose + Material Design 3 |
| Architecture | MVVM (ViewModel + LiveData + StateFlow) |
| Bluetooth | Nordic BLE Library + Android BLE APIs |
| Cryptography | BouncyCastle (X25519, Ed25519, AES-GCM) |
| Networking | OkHttp (WebSocket), Arti (Tor) |
| Storage | EncryptedSharedPreferences, GSON |
| Async | Kotlin Coroutines |
| Location | Google Play Services Location |
| Min SDK | API 26 (Android 8.0) |

## Project Structure

```
com.bitchat.android/
├── EduLearnApplication.kt          # App-level initialization
├── MainActivity.kt                 # Main activity, permissions, onboarding
├── MainViewModel.kt                # Onboarding state management
├── bluetooth/                      # Bluetooth document sharing
├── crypto/                         # Encryption services
├── education/                      # Educational content managers
├── favorites/                      # Favorites persistence
├── geohash/                        # Location-based channels
├── identity/                       # Secure identity management
├── mesh/                           # BLE mesh service, packet broadcaster
├── model/                          # Data models (messages, peers)
├── net/                            # Tor manager, network layer
├── noise/                          # Noise protocol encryption
├── nostr/                          # Nostr identity & relay directory
├── onboarding/                     # Permission & setup flow
├── protocol/                       # Binary protocol (13-byte header)
├── quiz/                           # Quiz distribution & UI
├── services/                       # Background services
├── storage/                        # Local storage management
├── sync/                           # Data synchronization
├── ui/                             # All Compose UI screens
│   ├── auth/                       # Login, registration, profile
│   ├── educational/                # Teacher & student screens
│   ├── theme/                      # Color scheme, typography
│   └── wifidirect/                 # Wi-Fi Direct UI
├── util/                           # Utility classes
├── utils/                          # Language manager, helpers
└── wifidirect/                     # Wi-Fi Direct manager
```

## Build Instructions

### Prerequisites

- **Android Studio** Hedgehog (2023.1) or newer
- **Android SDK** API level 26+
- **JDK** 17 or newer
- **Gradle** 8.13 (bundled via wrapper)

### Build & Run

```bash
# Build debug APK
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug

# Clean build artifacts
./gradlew clean
```

The debug APK will be generated at:
```
app/build/outputs/apk/debug/app-debug.apk
```

### Permissions Required

| Permission | Reason |
|:---|:---|
| Bluetooth | Core BLE mesh networking |
| Location | Required by Android for BLE scanning |
| Notifications | Message alerts and background updates |
| Battery Optimization | Maintain mesh connections in background |

### Hardware Requirements

- Android 8.0+ (API 26)
- Bluetooth LE support
- 2GB RAM recommended

## Usage

### Chat Mode Commands

| Command | Description |
|:---|:---|
| `/j #channel` | Join or create a channel |
| `/m @name message` | Send a private message |
| `/w` | List online users |
| `/channels` | Show all discovered channels |
| `/block @name` | Block a peer |
| `/unblock @name` | Unblock a peer |
| `/clear` | Clear chat messages |
| `/pass [password]` | Set/change channel password (owner only) |
| `/transfer @name` | Transfer channel ownership |
| `/save` | Toggle message retention (owner only) |

### Getting Started

1. **Install the APK** on your Android device (requires Android 8.0+)
2. **Grant permissions** for Bluetooth, Location, and Notifications when prompted
3. **Optionally disable battery optimization** for reliable background mesh operation
4. **Choose a mode** — Chat Mode or Educational Mode
5. **Start chatting** — the app auto-discovers nearby peers via Bluetooth mesh
6. **Messages relay** through the mesh network across multiple hops to reach distant peers

## Technical Architecture

### Binary Protocol
EduLearn uses an efficient binary protocol optimized for Bluetooth LE:
- Compact 13-byte packet header with 1-byte type field
- TTL-based message routing (max 7 hops)
- Automatic fragmentation for large messages
- Message deduplication via unique IDs and Bloom filters

### Mesh Networking
- Each device acts as both BLE central and peripheral simultaneously
- Automatic peer discovery and connection management
- Store-and-forward for offline message delivery
- Adaptive duty cycling based on battery state

### Core Components

| Component | File | Role |
|:---|:---|:---|
| Application | `EduLearnApplication.kt` | Tor, Nostr, theme initialization |
| Activity | `MainActivity.kt` | Permissions, onboarding, UI hosting |
| Mesh Service | `BluetoothMeshService.kt` | BLE scanning, advertising, packet relay |
| Chat Logic | `ChatViewModel.kt` | Message routing, state management |
| Protocol | `BinaryProtocol.kt` | Packet encoding/decoding |
| Encryption | `EncryptionService.kt` | X25519, Ed25519, AES-GCM |
| Quiz System | `QuizDistributionService.kt` | Quiz creation and mesh distribution |
| UI | `ChatScreen.kt` | Main chat interface |

## Roadmap

- [ ] Complete UI redesign with Lumina Learn design system
- [ ] Hardware mesh relay tags (ESP32-C3 based)
- [ ] Village relay nodes (solar-powered, extended range)
- [ ] Additional Indian language support (Tamil, Telugu, Bengali, Marathi, Kannada, Gujarati, Malayalam, Odia)
- [ ] Tag pairing system (8-digit code)
- [ ] Wi-Fi handoff for large file transfers (>5MB)
- [ ] AES-256 encrypted tag storage
- [ ] Google Play Store release
