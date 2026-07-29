#include <iostream>
#include <cstdint>
#include <cstring>
#include <cerrno>
#include <csignal>
#include <fcntl.h>
#include <unistd.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <linux/uinput.h>

// --- QUIP Protocol Definitions ---
#pragma pack(push, 1)
struct QuipHeader {
    uint8_t  ver_flags;      // Bits 7-4: Protocol Version (v1=0x1), Bits 3-0: Flags (Encrypted, Compressed, ACK-Req)
    uint8_t  type;           // 0x01: Input Batch, 0x02: Heartbeat, 0x03: Crypto Handshake, 0x04: Key State Sync
    uint16_t sequence;       // Monotonically increasing sequence number per channel
    uint32_t timestamp_us;   // Client hardware clock in microseconds (relative to session start)
    uint32_t nonce_prefix;   // Nonce counter used for AEAD decryption validation
};

// Flag bit definitions (lower nibble of ver_flags)
static constexpr uint8_t FLAG_ENCRYPTED  = 0x01;  // Bit 0: Payload is encrypted
static constexpr uint8_t FLAG_COMPRESSED = 0x02;  // Bit 1: Payload is compressed
static constexpr uint8_t FLAG_ACK_REQ    = 0x04;  // Bit 2: Sender requests acknowledgment

struct QuipInputPayload {
    uint64_t digital_mask;   // Bitfield map for up to 64 discrete controls (WASD, Shift, Space, Mouse1-5, etc.)
    int16_t  mouse_dx;       // Relative Mouse Motion X (-32768 to +32767)
    int16_t  mouse_dy;       // Relative Mouse Motion Y
    int8_t   left_stick_x;   // Analog Movement X (-128 to +127)
    int8_t   left_stick_y;   // Analog Movement Y (-128 to +127)
    int8_t   gyro_yaw;       // Gyroscope delta yaw
    int8_t   gyro_pitch;     // Gyroscope delta pitch
};

struct QuipPacket {
    QuipHeader       header;
    QuipInputPayload payload;
};
#pragma pack(pop)

// Map bit positions to Linux input key codes (from <linux/input-event-codes.h>)
static const uint16_t BIT_TO_KEY[14] = {
    KEY_W,          // Bit 0: Forward
    KEY_A,          // Bit 1: Left
    KEY_S,          // Bit 2: Backward
    KEY_D,          // Bit 3: Right
    KEY_SPACE,      // Bit 4: Jump
    KEY_LEFTSHIFT,  // Bit 5: Sprint
    0, 0,           // Bits 6-7 (Reserved)
    KEY_E,          // Bit 8: Interact
    KEY_R,          // Bit 9: Reload
    KEY_C,          // Bit 10: Crouch
    KEY_LEFTCTRL,   // Bit 11: Ctrl
    BTN_LEFT,       // Bit 12: Mouse Left Click
    BTN_RIGHT       // Bit 13: Mouse Right Click
};

static volatile sig_atomic_t g_running = 1;

static void signal_handler(int /*sig*/) {
    g_running = 0;
}

class LinuxQuipHost {
private:
    int uinput_fd = -1;
    uint64_t last_digital_mask = 0;

    void emit_event(uint16_t type, uint16_t code, int32_t val) {
        struct input_event ie{};
        ie.type = type;
        ie.code = code;
        ie.value = val;
        ssize_t ret = write(uinput_fd, &ie, sizeof(ie));
        if (ret < 0) {
            std::cerr << "[Error] write to uinput failed: " << strerror(errno) << "\n";
        } else if (static_cast<size_t>(ret) != sizeof(ie)) {
            std::cerr << "[Error] partial write to uinput: " << ret << "/" << sizeof(ie) << " bytes\n";
        }
    }

    void sync_events() {
        emit_event(EV_SYN, SYN_REPORT, 0);
    }

public:
    bool InitVirtualDevice() {
        uinput_fd = open("/dev/uinput", O_WRONLY | O_NONBLOCK);
        if (uinput_fd < 0) {
            std::cerr << "[Error] Cannot open /dev/uinput: " << strerror(errno)
                      << ". Did you run linux_perms.sh or start with root privileges?\n";
            return false;
        }

        // Enable Event Types: Key presses & Relative mouse motion
        if (ioctl(uinput_fd, UI_SET_EVBIT, EV_KEY) < 0 ||
            ioctl(uinput_fd, UI_SET_EVBIT, EV_REL) < 0 ||
            ioctl(uinput_fd, UI_SET_RELBIT, REL_X) < 0 ||
            ioctl(uinput_fd, UI_SET_RELBIT, REL_Y) < 0) {
            std::cerr << "[Error] Failed to configure event bits: " << strerror(errno) << "\n";
            close(uinput_fd);
            uinput_fd = -1;
            return false;
        }

        // Register keys in kernel virtual device table
        for (uint16_t code : BIT_TO_KEY) {
            if (code != 0) {
                if (ioctl(uinput_fd, UI_SET_KEYBIT, code) < 0) {
                    std::cerr << "[Error] Failed to register key code " << code
                              << ": " << strerror(errno) << "\n";
                    close(uinput_fd);
                    uinput_fd = -1;
                    return false;
                }
            }
        }

        // Configure Virtual Device Descriptor
        struct uinput_setup usetup{};
        usetup.id.bustype = BUS_USB;
        usetup.id.vendor  = 0x1234; // Custom QUIP Vendor ID
        usetup.id.product = 0x5678; // Custom QUIP Product ID
        usetup.id.version = 1;
        snprintf(usetup.name, UINPUT_MAX_NAME_SIZE, "QUIP Virtual Controller");

        if (ioctl(uinput_fd, UI_DEV_SETUP, &usetup) < 0) {
            std::cerr << "[Error] UI_DEV_SETUP failed: " << strerror(errno) << "\n";
            close(uinput_fd);
            uinput_fd = -1;
            return false;
        }
        if (ioctl(uinput_fd, UI_DEV_CREATE) < 0) {
            std::cerr << "[Error] UI_DEV_CREATE failed: " << strerror(errno) << "\n";
            close(uinput_fd);
            uinput_fd = -1;
            return false;
        }

        std::cout << "[QUIP Host Engine] Created Linux virtual input device successfully.\n";
        return true;
    }

    void ProcessInput(const QuipInputPayload& payload) {
        bool needs_sync = false;

        // 1. Mouse relative movement
        if (payload.mouse_dx != 0 || payload.mouse_dy != 0) {
            if (payload.mouse_dx != 0) emit_event(EV_REL, REL_X, payload.mouse_dx);
            if (payload.mouse_dy != 0) emit_event(EV_REL, REL_Y, payload.mouse_dy);
            needs_sync = true;
        }

        // 2. Digital keys (XOR Bit-diff mapping)
        uint64_t changed_bits = payload.digital_mask ^ last_digital_mask;
        if (changed_bits != 0) {
            for (int i = 0; i < 14; ++i) {
                uint64_t bit = (1ULL << i);
                if (changed_bits & bit) {
                    bool is_pressed = (payload.digital_mask & bit) != 0;
                    uint16_t key_code = BIT_TO_KEY[i];

                    if (key_code != 0) {
                        emit_event(EV_KEY, key_code, is_pressed ? 1 : 0);
                        needs_sync = true;
                    }
                }
            }
            last_digital_mask = payload.digital_mask;
        }

        // Batch commit all input state changes to kernel in one frame sync
        if (needs_sync) {
            sync_events();
        }
    }

    // Release every key that is currently held, preventing stuck keys on shutdown
    void ReleaseAllKeys() {
        if (uinput_fd < 0 || last_digital_mask == 0) return;

        bool needs_sync = false;
        for (int i = 0; i < 14; ++i) {
            uint64_t bit = (1ULL << i);
            if (last_digital_mask & bit) {
                uint16_t key_code = BIT_TO_KEY[i];
                if (key_code != 0) {
                    emit_event(EV_KEY, key_code, 0);
                    needs_sync = true;
                }
            }
        }
        if (needs_sync) {
            sync_events();
        }
        last_digital_mask = 0;
    }

    ~LinuxQuipHost() {
        if (uinput_fd >= 0) {
            ReleaseAllKeys();
            ioctl(uinput_fd, UI_DEV_DESTROY);
            close(uinput_fd);
        }
    }
};

int main() {
    // Register signal handlers for clean shutdown.
    // Do NOT set SA_RESTART — recv() must be interrupted so the loop can re-check g_running.
    struct sigaction sa{};
    sa.sa_handler = signal_handler;
    sigemptyset(&sa.sa_mask);
    sa.sa_flags = 0;
    sigaction(SIGINT, &sa, nullptr);
    sigaction(SIGTERM, &sa, nullptr);

    LinuxQuipHost host;
    if (!host.InitVirtualDevice()) return 1;

    int server_fd = socket(AF_INET, SOCK_DGRAM, 0);
    if (server_fd < 0) {
        std::cerr << "[Error] Failed to create UDP socket: " << strerror(errno) << "\n";
        return 1;
    }

    // Allow rapid restarts without EADDRINUSE
    int optval = 1;
    if (setsockopt(server_fd, SOL_SOCKET, SO_REUSEADDR, &optval, sizeof(optval)) < 0) {
        std::cerr << "[Warning] Failed to set SO_REUSEADDR: " << strerror(errno) << "\n";
    }

    sockaddr_in address{};
    address.sin_family = AF_INET;
    address.sin_addr.s_addr = INADDR_ANY;
    address.sin_port = htons(9876);

    if (bind(server_fd, (struct sockaddr*)&address, sizeof(address)) < 0) {
        std::cerr << "[Error] Failed to bind to UDP port 9876: " << strerror(errno) << "\n";
        close(server_fd);
        return 1;
    }

    std::cout << "[QUIP Host Engine] Listening for inputs on UDP port 9876...\n";

    QuipPacket packet;
    while (g_running) {
        ssize_t bytes = recv(server_fd, &packet, sizeof(packet), 0);
        if (bytes < 0) {
            if (errno == EINTR) {
                continue;  // Interrupted by signal — re-check g_running
            }
            std::cerr << "[Error] recv() failed: " << strerror(errno) << "\n";
            break;
        }
        if (static_cast<size_t>(bytes) != sizeof(QuipPacket)) {
            continue;  // Malformed or truncated packet — drop
        }

        // Extract version (upper nibble) and flags (lower nibble)
        uint8_t version = (packet.header.ver_flags >> 4) & 0x0F;
        uint8_t flags   = packet.header.ver_flags & 0x0F;

        if (version != 0x01) {
            continue;  // Unknown protocol version
        }

        // Encryption is not yet implemented — reject encrypted packets rather than
        // misinterpreting ciphertext as raw input data.
        // TODO: Implement ChaCha20-Poly1305 / AES-128-GCM decryption (spec §4)
        if (flags & FLAG_ENCRYPTED) {
            continue;
        }

        if (packet.header.type == 0x01) {
            host.ProcessInput(packet.payload);
        }
        // TODO: Handle type 0x02 (Heartbeat), 0x03 (Crypto Handshake), 0x04 (Key State Sync)
    }

    std::cout << "[QUIP Host Engine] Shutting down...\n";
    close(server_fd);
    // LinuxQuipHost destructor releases held keys and destroys virtual device
    return 0;
}
