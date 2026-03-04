<p align="center">
    🛡️
</p>

<h1 align="center">SafeRelay</h1>
<p align="center"><strong>Decentralized Disaster Communication</strong></p>
<p align="center">
    A peer-to-peer emergency communication app that works without internet — using Bluetooth mesh networking to connect nearby people during disasters.
</p>

---

## What is SafeRelay?

SafeRelay is a **disaster communication app** that lets people send and receive messages over **Bluetooth mesh networks** — no internet, no servers, no phone numbers required.

During natural disasters, earthquakes, floods, or network outages, traditional communication fails. SafeRelay keeps you connected with people nearby so you can:

- 🆘 **Broadcast SOS alerts** with your location and battery status
- 💬 **Send messages** to anyone within Bluetooth range
- 🔗 **Relay messages** through the mesh — your message hops from device to device
- ✅ **Mark yourself as SAFE** so others know you're okay
- 📞 **Quick-dial emergency services** — Ambulance (108), Fire (101), Police (100)

## Features

- **📡 Bluetooth Mesh Networking** — Automatic peer discovery, no internet needed
- **🔒 End-to-End Encryption** — X25519 key exchange + AES-256-GCM
- **🆘 SOS Broadcasting** — Hold to send emergency alerts with GPS location
- **✅ Safety Status** — One-tap "I'm SAFE" broadcast to nearby devices
- **👤 User Profiles** — Set your name, blood group, emergency contacts
- **🔋 Battery Optimized** — Adaptive power modes for extended emergency use
- **📍 Location Sharing** — Attach GPS coordinates to emergency messages
- **🔄 Store & Forward** — Messages cached for offline peers, delivered on reconnect
- **🌐 Cross-Platform** — Works with both Android and iOS devices

## Getting Started

### Install

Download the latest APK from the [Releases](../../releases) page, or build from source.

### Build from Source

```bash
git clone https://github.com/Yugha13/SafeRelay-Android.git
cd SafeRelay-Android
./gradlew assembleDebug
```

### Install on Device

```bash
./gradlew installDebug
```

### Requirements

- **Android 8.0+** (API level 26)
- **Bluetooth LE** capable device
- Permissions: Bluetooth, Location (for BLE scanning), Notifications

## How It Works

1. **Open SafeRelay** — Bluetooth mesh starts automatically
2. **Set your profile** — Add your name, blood group, and emergency contacts
3. **Chat tab** — Send messages to the single broadcast channel
4. **Nearby tab** — See devices within Bluetooth range
5. **SOS** — Hold the SOS button for 3 seconds to broadcast an emergency alert

Messages travel through the mesh network — each device relays messages to others, extending the range beyond direct Bluetooth connection (~30-100m per hop, up to 7 hops).

## Technical Stack

| Component | Technology |
|---|---|
| **UI** | Jetpack Compose + Material Design 3 |
| **Networking** | Bluetooth Low Energy (BLE) GATT |
| **Encryption** | BouncyCastle (X25519, Ed25519, AES-GCM) |
| **Compression** | LZ4 for bandwidth optimization |
| **Architecture** | MVVM with Kotlin Coroutines |
| **Storage** | EncryptedSharedPreferences |

## Contributing

Contributions are welcome! Key areas:

- 🔋 Battery optimization and connection reliability
- 🎨 UI/UX improvements
- 🧪 Test coverage
- 📖 Documentation

## Support

- **Bug Reports**: [Create an issue](../../issues)
- **Feature Requests**: [Start a discussion](../../discussions)
