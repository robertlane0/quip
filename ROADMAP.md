# QUIP (Quicksilver Input Protocol) — Development Roadmap

## Executive Summary & Goal
The primary objective of **QUIP** is to provide an ultra-low-latency, zero-trust mobile-to-PC input system for high-performance gaming. The final system requires:
1. **Shared C++ Protocol Core (`libquip`)**: Platform-agnostic packet parsing, crypto (`Noise_IK`), anti-replay, and network transport engines.
2. **Linux Host Driver (`quip_linux`)**: High-performance `uinput` virtual device server with full key, mouse, analog, and gyro injection.
3. **Windows Host Driver (`quip_windows`)**: Low-latency Windows driver using SendInput/ViGEmBus for keyboard, mouse, and virtual XInput gamepad emulation.
4. **Android Client (`quip-android`)**: Kotlin + NDK Android app with dynamic control layouts, high-frequency touch/gyro sampling, QR scanner pairing, and dual Wi-Fi/Bluetooth transport.

---

## 1. Current State Evaluation

### What Has Been Done So Far
- **Protocol Specification ([QUIP_SPECIFICATION.md](file:///home/unprivileged/code/quip/QUIP_SPECIFICATION.md))**:
  - Defined 12-byte header, 16-byte input payload, packet types (`0x01` Input Batch, `0x02` Heartbeat, `0x03` Crypto Handshake, `0x04` Key State Sync).
  - Resolved specification contradictions (header size corrected to 12 bytes; version field placed in bits 7–4 of `ver_flags`).
- **Linux Host Proof-of-Concept Driver ([quip_linux.cpp](file:///home/unprivileged/code/quip/quip_linux.cpp))**:
  - Implemented `/dev/uinput` virtual device creation and teardown.
  - Implemented non-blocking UDP socket server on port 9876 with `SO_REUSEADDR` support.
  - Implemented bitwise `digital_mask` delta detection and mapped 14 digital controls to Linux key/button events.
  - Implemented relative mouse motion (`REL_X`, `REL_Y`) injection.
  - Implemented clean shutdown (`SIGINT`/`SIGTERM`) and stuck-key safety cleanup (`ReleaseAllKeys`).
  - Added safety guard to reject packets with `FLAG_ENCRYPTED` set until decryption is wired up.
- **Permissions Script ([linux_perms.sh](file:///home/unprivileged/code/quip/linux_perms.sh))**:
  - Created idempotent udev permissions script with safe `SUDO_USER` resolution.

### Key Gaps & Missing Implementation
1. **Core / Security**:
   - No `Noise_IK` handshake (`0x03`) or AEAD payload encryption (`ChaCha20-Poly1305` / `AES-128-GCM`).
   - No sequence number tracking or 64-packet sliding window anti-replay buffer.
   - Endianness handling missing (assumes native little-endian layout everywhere without explicit conversions).
   - Analog stick coordinates (`left_stick_x/y`) and Gyroscope (`gyro_yaw/pitch`) are parsed but not mapped or injected.
2. **Transport Layer**:
   - Reliable stream channel (TCP / sequenced UDP) for `0x04` Key State Sync missing.
   - Bluetooth L2CAP transport and dynamic Wi-Fi/BT failover deduplication engine missing.
3. **Windows Host**:
   - Entirely uncreated (`quip_windows.cpp` or Visual Studio CMake project needed).
4. **Android App**:
   - Entirely uncreated (`quip-android` repository/module needed).

---

## 2. Detailed Task Roadmap

### Phase 1: Shared Core C++ Engine (`libquip`)
*Target: Refactor core logic into a header-only or lightweight cross-platform C++20 library.*

- [ ] **1.1 Core Structs & Endianness Safety**
  - Add explicit endianness conversion helpers (`htons`, `ntohs`, `htonl`, `ntohl`, `le64toh`) for network binary serialization across ARM/x86 architectures.
- [ ] **1.2 Anti-Replay Buffer (`quip::AntiReplay`)**
  - Implement a 64-bit sliding window sequence number validator to drop stale or replayed packets.
- [ ] **1.3 Crypto & Noise Handshake (`quip::CryptoEngine`)**
  - Integrate `libsodium` or `monocypher` for `Noise_IK` handshakes (`Curve25519` + `ChaCha20-Poly1305`).
  - Implement state machine for Handshake (`0x03`), session key derivation, and AEAD encryption/decryption of `0x01` Input Batch payloads.
- [ ] **1.4 Reliable Transport Channel & Heartbeat**
  - Implement TCP / Sequenced UDP worker for discrete state sync (`0x04`) and connection keep-alive (`0x02`).

---

### Phase 2: Linux Host Driver Hardening (`quip_linux`)
*Target: Full feature completion for Linux systems.*

- [ ] **2.1 Virtual Gamepad & Full Input Emulation**
  - Extend `uinput` config to expose virtual analog axes (`ABS_X`, `ABS_Y`) for left stick and mouse/gyro fusion.
  - Implement dynamic key mapping lookup table supporting all 64 bits of `digital_mask`.
- [ ] **2.2 Host Pairing & QR Generator Engine**
  - Integrate `libqrencode` or header-only QR generator to print host pairing QR code to terminal or GTK/Qt GUI.
  - Implement dynamic session token generation.
- [ ] **2.3 Dual Transport & Failover Engine**
  - Implement BlueZ Linux Bluetooth L2CAP socket receiver.
  - Build stream deduplication logic accepting incoming packets from Wi-Fi UDP and Bluetooth L2CAP simultaneously.

---

### Phase 3: Windows Host Driver (`quip_windows`)
*Target: Native Windows C++ driver with minimal latency.*

- [ ] **3.1 Win32 Server Framework & Socket Networking**
  - Create Windows C++20 WinSock2 server for UDP/9876 and TCP pairing channel.
- [ ] **3.2 Low-Latency Mouse & Keyboard Injection**
  - Implement mouse motion via `SendInput` / `SynthesizeInput` or raw input driver.
  - Implement bitwise `digital_mask` to Windows Virtual Key Code (`VK_*`) translation layer.
- [ ] **3.3 Virtual Xbox Controller Emulation (ViGEmBus)**
  - Integrate ViGEmClient API to create virtual XInput controller for analog sticks, triggers, and D-pad.
- [ ] **3.4 Windows Pairing Utility & System Tray App**
  - Create lightweight tray app displaying connection status and pairing QR code.

---

### Phase 4: Android Mobile Client (`quip-android`)
*Target: High-performance, low-latency Android app.*

- [ ] **4.1 Architecture & NDK Integration**
  - Set up Android Studio project (Kotlin + NDK C++ core via JNI).
  - Embed `libquip` C++ core for packet assembly, encryption, and low-overhead UDP socket sending.
- [ ] **4.2 Input Engine & High-Frequency Polling**
  - Touchscreen control layout engine: virtual joysticks, configurable buttons, touch-to-mouse drag area with 240Hz sub-pixel delta accumulation.
  - Android `SensorManager` integration for low-pass filtered gyroscope sampling (yaw/pitch).
- [ ] **4.3 Network & Bluetooth Failover Manager**
  - Implement Android Wi-Fi UDP datagram sender.
  - Implement Android Bluetooth L2CAP (`BluetoothSocket.TYPE_L2CAP`) sockets.
  - Add real-time network monitor to trigger dual-path transmission on Wi-Fi packet loss (>15%).
- [ ] **4.4 Pairing & UX Interface**
  - CameraX + ZXing QR code scanner to parse Host IP, Port, BT MAC, and Server Public Key.
  - Dynamic button layout editor with custom key binding configurations.

---

### Phase 5: Verification, Optimization & Testing
*Target: Latency benchmark < 1ms host processing, zero packet drop input smoothness.*

- [ ] **5.1 End-to-End Latency Benchmarking**
  - Build synthetic packet generator and latency profiler (measuring time from Android touch event to OS virtual input event).
- [ ] **5.2 Cross-Platform Security Audit**
  - Verify packet replay rejection, bad nonce rejection, and invalid ciphertext drop behavior.
- [ ] **5.3 Network Stress & Failover Testing**
  - Simulate 20% Wi-Fi packet loss / jitter to verify seamless Bluetooth failover without dropped inputs.

---

## 3. Technology Stack & Component Dependencies

| Component | Platform | Primary Technologies | Key Libraries |
| --- | --- | --- | --- |
| **Core Protocol** | Cross-Platform C++20 | CMake, C++20 | `monocypher` / `libsodium` |
| **Linux Host** | Linux (x86_64 / ARM64) | C++20, Linux Kernel API | `/dev/uinput`, `bluez` |
| **Windows Host** | Windows 10/11 | C++20, Win32 API | `WinSock2`, `ViGEmClient` |
| **Android Client** | Android 8.0+ | Kotlin, C++ NDK, JNI | `CameraX`, `Android Sensors` |
