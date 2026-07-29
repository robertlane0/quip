Complete architectural specification for **QUIP (Quicksilver Input Protocol)** — a bespoke, low-latency, hybrid transport protocol engineered specifically for mobile-to-PC real-time game control over local Wi-Fi and Bluetooth.

---

## 1. System Architecture Overview

Standard protocols like HTTP, WebSockets, or generic TCP carry heavy protocol overhead, head-of-line blocking, and unpredictable buffering latencies. **QUIP** bypasses these limitations by implementing a **dual-channel hybrid architecture**:

```
             +-------------------------------------------------+
             |             Mobile Control Engine               |
             +-------------------------------------------------+
                                      |
               +----------------------+----------------------+
               |                                             |
   [ Channel A: Unreliable Stream ]             [ Channel B: Reliable Stream ]
   - Ultra-low latency UDP / BT CoC            - TCP / Reliable UDP / BT L2CAP
   - High-freq polling (120Hz-240Hz)           - Low-freq event-driven
   - Analog Motion, Gyro, Touch Deltas          - Key Toggles, UI Configs, Re-key
               |                                             |
               +----------------------+----------------------+
                                      |
             +-------------------------------------------------+
             |              PC Host Driver / Emulator          |
             +-------------------------------------------------+

```

1. **Unreliable Channel (UDP / BT L2CAP CoC):** Handles high-frequency continuous inputs (mouse X/Y deltas, joystick coordinates, gyroscope motion). If a 120Hz mouse packet drops, the server drops it silently — fresh data arrives in ~8ms anyway.
2. **Reliable Channel (Sequenced UDP w/ Fast ACK or BT L2CAP SDU):** Handles state-critical discrete inputs (key presses, crouch toggles, weapon switches, macro triggers) requiring guaranteed in-order delivery without head-of-line blocking.

---

## 2. Binary Packet Format & Bit-Packed Frame Design

To keep packet processing under **0.5 ms** on mobile ARM hardware, QUIP utilizes fixed 16-byte headers with bit-packed payloads. It completely avoids JSON/Protobuf parsing overhead in favor of raw binary serialization.

### The QUIP Master Packet Layout

```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
| Ver | Flags   |  Packet Type  |        Sequence Number        |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                       Timestamp (Microseconds)                |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                       Session Nonce Prefix                    |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                      Payload / Auth Tag...                    |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+

```

### C Struct Definition

```c
#include <stdint.h>

// Fixed Header: 12 Bytes
typedef struct __attribute__((__packed__)) {
    uint8_t  ver_flags;      // Bits 0-3: Protocol Version (v1=0x1), Bits 4-7: Flags (Encrypted, Compressed, ACK-Req)
    uint8_t  type;           // 0x01: Input Batch, 0x02: Heartbeat, 0x03: Crypto Handshake, 0x04: Key State Sync
    uint16_t sequence;       // Monotonically increasing sequence number per channel
    uint32_t timestamp_us;   // Client hardware clock in microseconds (relative to session start)
    uint32_t nonce_prefix;   // Nonce counter used for AEAD decryption validation
} quip_header_t;

// Compact Real-Time Input Payload (16 Bytes)
typedef struct __attribute__((__packed__)) {
    uint64_t digital_mask;   // Bitfield map for up to 64 discrete controls (WASD, Shift, Space, Mouse1-5, etc.)
    int16_t  mouse_dx;       // Relative Mouse Motion X (-32768 to +32767)
    int16_t  mouse_dy;       // Relative Mouse Motion Y
    int8_t   left_stick_x;   // Analog Movement X (-128 to +127)
    int8_t   left_stick_y;   // Analog Movement Y (-128 to +127)
    int8_t   gyro_yaw;       // Gyroscope delta yaw
    int8_t   gyro_pitch;     // Gyroscope delta pitch
} quip_input_payload_t;

```

---

## 3. Latency Optimization & Input Mechanics

### Digital Key Masking (Zero-Parse Keyboard Emulation)

Instead of sending strings or key codes (e.g., `{"key": "W", "state": "down"}`), QUIP maps all core gaming keys directly to a **64-bit integer bitfield (`digital_mask`)**:

| Bit Position | Mapped Action | Bit Position | Mapped Action |
| --- | --- | --- | --- |
| `Bit 0` | Key `W` (Forward) | `Bit 8` | Key `E` (Interact) |
| `Bit 1` | Key `A` (Left) | `Bit 9` | Key `R` (Reload) |
| `Bit 2` | Key `S` (Backward) | `Bit 10` | Key `C` (Crouch) |
| `Bit 3` | Key `D` (Right) | `Bit 11` | Left Ctrl |
| `Bit 4` | Spacebar (Jump) | `Bit 12` | Mouse Left Click |
| `Bit 5` | Left Shift (Sprint) | `Bit 13` | Mouse Right Click |

> **Performance Gain:** The PC driver evaluates `current_mask ^ previous_mask` using native CPU hardware bitwise operations in **< 1 nanosecond**, generating virtual keyboard inputs with almost no latency.

### High-Polling Mouse Delta Aggregation

Touchscreens natively generate high-frequency touch events (~120Hz–240Hz). QUIP batches touch delta accumulation on the client side at a target interval (e.g., every 4ms / 250Hz max rate), accumulating sub-pixel drag movements and flushing them directly into the `mouse_dx` / `mouse_dy` fields.

---

## 4. Zero-Trust Security Architecture

Gaming over local networks is vulnerable to packet sniffing, keystroke injection, and replay attacks. QUIP incorporates modern cryptography based on the **Noise Protocol Framework (`Noise_IK`)**.

```
Mobile App (Client)                                   PC Host (Server)
    |                                                         |
    |---- 1. Scan PC QR Code (Contains Static Public Key) ----|
    |                                                         |
    |---- 2. Ephemeral Key Exchange + AES-GCM Auth Tag ------>|
    |<--- 3. Server Ack + Authenticated Session Token --------|
    |                                                         |
    |== Session Established: Encrypted via ChaCha20-Poly1305 =|

```

### Security Layers

1. **Out-of-Band Zero-Touch Pairing:**
* The PC host app generates a dynamic QR code on screen containing: `IP Address`, `Port`, `Bluetooth MAC`, and `PC Static Public Key (Curve25519)`.
* Scanning the QR code establishes mutual authentication without relying on weak user passwords or broadcast discovery vulnerabilities.


2. **Hardware-Accelerated Encryption:**
* Payload data uses **ChaCha20-Poly1305** (optimized for ARM mobile processors without hardware AES) or **AES-128-GCM** (if ARMv8 Crypto Extensions are detected).
* Authenticated Encryption with Associated Data (AEAD) ensures that tampering with key mask bits in transit invalidates the packet instantly.


3. **Replay & Injection Prevention:**
* Each packet contains a 16-bit sequence number and a 32-bit timestamp.
* The PC host maintains a **Sliding Window Anti-Replay Buffer (64 packets wide)**. Out-of-order or duplicate sequence numbers outside the window are immediately dropped.



---

## 5. Network Failover & Transport Layer Strategy

QUIP maintains a persistent heartbeat between Wi-Fi and Bluetooth to handle network instability.

```
       Primary Path: Wi-Fi UDP Datagrams (Low Jitter, High Bandwidth)
       ==============================================================>
   Mobile                                                          PC Host
       -------------------------------------------------------------->
       Fallback Path: Bluetooth L2CAP CoC (Zero-Wi-Fi Interference)

```

| Transport | Primary Use Case | Throughput | Latency | Resiliency |
| --- | --- | --- | --- | --- |
| **Wi-Fi 6 / 5GHz (UDP)** | Home Network Gaming | High | **1–3 ms** | Moderate (subject to Wi-Fi traffic) |
| **Bluetooth LE 5.x (L2CAP CoC)** | Peer-to-Peer / Congested Wi-Fi | Low | **4–8 ms** | High (frequency hopping) |
| **Wi-Fi Direct / Hotspot** | On-the-go (Laptop + Phone) | High | **2–4 ms** | High |

### Seamless Dynamic Failover Strategy

* If Wi-Fi packet loss exceeds **15% over a 100ms window**, QUIP silently mirrors the `Unreliable Input Stream` over the Bluetooth L2CAP socket.
* The PC host's deduplication engine receives both streams, processes whichever arrives first, and discards duplicate sequence numbers. This guarantees seamless controls even during Wi-Fi drops.
