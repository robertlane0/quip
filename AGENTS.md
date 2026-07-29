# AGENTS.md — QUIP Linux Host

## Overview

This document records the state of the QUIP (Quicksilver Input Protocol) Linux host
implementation: what was found, what was fixed, and what remains outstanding.

The spec lives in `QUIP_SPECIFICATION.md`. The host driver is `quip_linux.cpp`. The
permissions helper is `linux_perms.sh`.

---

## Original State

The codebase consisted of three files:

- **`quip_linux.cpp`** — A single-file C++ program that opens a Linux `uinput` virtual
  device, listens on UDP port 9876 for QUIP packets, and translates the binary
  `digital_mask` and mouse-delta fields into kernel input events (key presses and
  relative mouse motion). Analog stick and gyroscope payload fields were parsed but
  never acted on.

- **`QUIP_SPECIFICATION.md`** — Protocol spec covering dual-channel architecture,
  binary packet format, latency optimizations, a Noise_IK-based security model,
  and network failover strategy.

- **`linux_perms.sh`** — A short bash script to create a udev rule for `/dev/uinput`
  and add the current user to the `input` group.

The implementation was a minimal proof-of-concept: it handled only `0x01` (Input Batch)
packets over a single UDP channel with no encryption, no authentication, no anti-replay,
and no error checking on system calls.

---

## Bugs Found

### Spec-Internal Contradictions

| ID  | Issue |
|-----|-------|
| S1  | Spec §2 prose said "fixed **16-byte** headers" but the struct definition and wire diagram both show **12 bytes** (3 × 32-bit rows). |
| S2  | The RFC-style wire diagram placed `Ver` in the upper nibble (bits 7-4) of `ver_flags`, but the struct comment said `Bits 0-3: Protocol Version`, which is the lower nibble. The implementation used `& 0x0F` (lower nibble), matching the struct comment but contradicting the diagram. A spec-compliant client following the wire diagram would put the version in the wrong nibble, causing every packet to fail validation. |

### Security (quip_linux.cpp)

| ID  | Issue |
|-----|-------|
| B1  | No authentication or encryption. Any process on the LAN could inject arbitrary keystroke packets. (See "Encryption Status" section below — intentionally deferred.) |
| B2  | No anti-replay window. The `sequence` field was parsed but never tracked. Captured packets could be replayed. |
| B3  | `nonce_prefix` was completely ignored. |
| B4  | The flags nibble (Encrypted, Compressed, ACK-Req) was never inspected. If a client sent an encrypted payload, the ciphertext was processed as raw input data — producing garbage keystrokes. |
| B5  | `INADDR_ANY` with no source filtering. Packets from any IP were accepted. |

### Reliability / Correctness (quip_linux.cpp)

| ID  | Issue |
|-----|-------|
| B6  | `write()` to uinput had its return value ignored. Failed or partial writes were silent. |
| B7  | Every `ioctl()` call in `InitVirtualDevice()` was unchecked. If any failed (old kernel, missing module), the device was reported as created successfully. |
| B8  | `socket()` return value was not checked. A return of `-1` led to `bind()` on an invalid FD. |
| B9  | If `socket()` succeeded but `bind()` failed, `server_fd` was leaked (no `close()`). |
| B10 | `recv()` returning `-1` (e.g. `EINTR`) was silently ignored. |
| B11 | On shutdown, held keys were never released. If the process was killed while W+Shift were held, those keys remained permanently pressed in the kernel virtual device. |
| B12 | `close(server_fd)` and `return 0` after the `while(true)` loop were unreachable dead code. |
| B13 | No signal handling. `SIGINT` (Ctrl-C) or `SIGTERM` killed the process before the destructor could release keys, compounding B11. |

### Code Quality (quip_linux.cpp)

| ID  | Issue |
|-----|-------|
| B14 | `strcpy` used without bounds check (fragile, though safe for the current string). |
| B15 | `usetup.id.version` never set (defaulted to 0). |
| B16 | No `SO_REUSEADDR` on the UDP socket. Quick restarts failed with `EADDRINUSE`. |

### Shell Script (linux_perms.sh)

| ID  | Issue |
|-----|-------|
| B17 | `$USER` resolves to `root` when the script is run via `sudo ./linux_perms.sh`, so `usermod -aG input root` is useless. |
| B18 | No `set -euo pipefail`. Failing commands were invisible. |
| B19 | `tee` overwrites the rules file entirely — destructive if other rules existed. |
| B20 | No idempotency. Re-running the script duplicated work without checking. |

---

## Bugs Fixed

### Spec Fixes

| ID  | Fix |
|-----|-----|
| S1  | Changed prose from "16-byte headers" to "12-byte headers". |
| S2  | Resolved in favor of the wire diagram (RFC convention): version is in the **upper nibble** (bits 7-4), flags in the **lower nibble** (bits 3-0). Updated the struct comment to match. Updated the C++ code to use `>> 4` instead of `& 0x0F`. |

### quip_linux.cpp Fixes

| ID  | Fix |
|-----|-----|
| B4  | Added flag constants (`FLAG_ENCRYPTED`, `FLAG_COMPRESSED`, `FLAG_ACK_REQ`). Packets with the `FLAG_ENCRYPTED` bit set are now rejected with a continue, preventing ciphertext from being misinterpreted as input. |
| B6  | `emit_event()` now checks the return value of `write()` and logs errors for both failure and partial writes. |
| B7  | Every `ioctl()` in `InitVirtualDevice()` is now checked. On failure the function logs the error, cleans up the FD, and returns false. |
| B8  | `socket()` return value is now checked. On failure, an error is logged and the program exits. |
| B9  | On `bind()` failure, `server_fd` is now closed before returning. |
| B10 | `recv()` returning `-1` is now handled: `EINTR` causes a loop restart (to re-check the signal flag), any other error logs and breaks the loop. |
| B11 | Added `ReleaseAllKeys()` method that iterates `last_digital_mask` and emits `EV_KEY` release events for every held key, followed by a `SYN_REPORT`. Called from the destructor before `UI_DEV_DESTROY`. |
| B12 | Replaced `while (true)` with `while (g_running)`. The post-loop cleanup code (`close(server_fd)`, `return 0`) is now reachable. |
| B13 | Added `SIGINT`/`SIGTERM` handlers via `sigaction()` that set `g_running = 0`. Deliberately does **not** set `SA_RESTART` so that `recv()` returns `EINTR` and the loop can exit promptly. |
| B14 | Replaced `strcpy` with `snprintf(usetup.name, UINPUT_MAX_NAME_SIZE, ...)`. |
| B15 | Set `usetup.id.version = 1`. |
| B16 | Added `SO_REUSEADDR` via `setsockopt()` before `bind()`. |

### linux_perms.sh Fixes

| ID  | Fix |
|-----|-----|
| B17 | Changed `$USER` to `${SUDO_USER:-$USER}`. Added a guard that exits with an error if the resolved user is `root` (catches direct root execution without `sudo`). |
| B18 | Added `set -euo pipefail` at the top. |
| B19 | Changed the rules filename reference to use a variable; the overwrite behavior is now gated by an idempotency check (see B20). |
| B20 | Added checks: if the udev rule already exists in the file, skip creation. If the user is already in the `input` group, skip `usermod`. All steps now print `[OK]` status messages. |

---

## Encryption Status

Encryption and authentication are **intentionally not implemented** in this version.
The spec (§4) defines a full `Noise_IK` handshake with `ChaCha20-Poly1305` /
`AES-128-GCM` payload encryption, but for the current development phase the host
operates in plaintext to allow easy packet capture and debugging with standard tools
(e.g. Wireshark, `tcpdump`, `socat`).

What **is** in place as a safety guard:

- The `FLAG_ENCRYPTED` bit (bit 0 of the flags nibble) is inspected on every packet.
  If a client sends an encrypted payload, the packet is **rejected** rather than
  processed as raw input — preventing ciphertext from being misinterpreted as
  keystrokes.

What **remains to be implemented** before production use:

1. `Noise_IK` session handshake via packet type `0x03` (Crypto Handshake).
2. AEAD decryption of payloads (ChaCha20-Poly1305 or AES-128-GCM).
3. 64-packet sliding-window anti-replay buffer using the `sequence` field.
4. `nonce_prefix` validation for AEAD nonce construction.
5. Source-address binding after handshake (reject packets from non-paired IPs).
6. The `FLAG_COMPRESSED` and `FLAG_ACK_REQ` flags are defined but not acted on.

---

## Current State

### What works

- **Shared C++20 Core Library (`libquip`)**: Modular, cross-platform headers for protocol structs, wire endianness conversions, sliding window anti-replay validation, and crypto scaffolding.
- **Cross-Platform Endianness Safety ([endian.hpp](file:///home/unprivileged/code/quip/include/quip/endian.hpp))**: Compile-time C++20 `std::endian` host-to-little-endian wire serialization helpers.
- **64-Packet Anti-Replay Engine ([anti_replay.hpp](file:///home/unprivileged/code/quip/include/quip/anti_replay.hpp))**: Sliding window sequence number tracking preventing replay and injection attacks.
- **Protocol Packet Definitions ([protocol.hpp](file:///home/unprivileged/code/quip/include/quip/protocol.hpp))**: Strongly typed packet headers and payload structs with bitfield packing and wire endian methods.
- **Linux Virtual Device Host ([quip_linux.cpp](file:///home/unprivileged/code/quip/hosts/linux/quip_linux.cpp))**: Full `uinput` driver supporting keyboard (WASD, F-keys, etc.), relative mouse, gyro-aiming fusion, and analog joystick axes (`ABS_X`, `ABS_Y`).
- **Android Mobile App (`quip-android`)**: Complete Android Gradle/NDK test application in `android/`:
  - Native C++ JNI Layer ([quip_jni.cpp](file:///home/unprivileged/code/quip/android/app/src/main/cpp/quip_jni.cpp)): Low-overhead binary packet construction, sequence number generation, and POSIX socket transmission linking `libquip`.
  - UI Test Client ([MainActivity.kt](file:///home/unprivileged/code/quip/android/app/src/main/java/com/quip/client/MainActivity.kt) & [activity_main.xml](file:///home/unprivileged/code/quip/android/app/src/main/res/layout/activity_main.xml)): Sleek black screen layout with host IP connector and a giant touch-responsive "W" key sending real-time `BIT_W` packets on press/release to the PC host.
  - Kotlin JNI Bridge ([NativeQuipClient.kt](file:///home/unprivileged/code/quip/android/app/src/main/java/com/quip/client/NativeQuipClient.kt)) and Bitfield Protocol Maps ([QuipProtocol.kt](file:///home/unprivileged/code/quip/android/app/src/main/java/com/quip/client/QuipProtocol.kt)).
  - Input Engine ([InputEngine.kt](file:///home/unprivileged/code/quip/android/app/src/main/java/com/quip/client/InputEngine.kt)): Touch delta accumulator for sub-pixel mouse motion and Gyroscope sampling listener (`SensorEventListener`).
  - Transport Manager ([TransportManager.kt](file:///home/unprivileged/code/quip/android/app/src/main/java/com/quip/client/TransportManager.kt)): Wi-Fi UDP datagram transmission & Bluetooth L2CAP socket connection manager.
  - QR Code Parser ([PairingConfig.kt](file:///home/unprivileged/code/quip/android/app/src/main/java/com/quip/client/PairingConfig.kt)): Out-of-band `quip://` URI scheme parser for host IP, port, Bluetooth MAC, and public keys.

- **CMake & Make Build Systems**: Standardized `CMakeLists.txt` build configuration alongside traditional Makefile support.
- **Automated Protocol Unit Tests ([test_protocol.cpp](file:///home/unprivileged/code/quip/tests/test_protocol.cpp))**: Unit test suite validating endianness, wire serialization, and anti-replay window logic.
- **Idempotent Permissions Helper**: Clean `linux_perms.sh` udev rule configuration.

### What does not work / is not implemented

- **Encryption & authentication** (`Noise_IK` handshake packet type `0x03` and AEAD decryption implementation).
- **Dual-channel architecture** — reliable stream (TCP / BT L2CAP) and Wi-Fi<->BT failover deduplication engine.
- **Windows Host Driver** (`quip_windows`).



