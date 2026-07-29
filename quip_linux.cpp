#include <array>
#include <atomic>
#include <bit>
#include <cerrno>
#include <cstdint>
#include <cstring>
#include <csignal>
#include <format>
#include <iostream>
#include <utility>

#include <fcntl.h>
#include <unistd.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <linux/uinput.h>

// --- QUIP Protocol Definitions ---

enum class PacketType : uint8_t {
    InputBatch      = 0x01,
    Heartbeat       = 0x02,
    CryptoHandshake = 0x03,
    KeyStateSync    = 0x04
};

namespace HeaderFlag {
    constexpr uint8_t Encrypted  = 0x01;  // Bit 0: Payload is encrypted
    constexpr uint8_t Compressed = 0x02;  // Bit 1: Payload is compressed
    constexpr uint8_t AckReq     = 0x04;  // Bit 2: Sender requests acknowledgment
}

#pragma pack(push, 1)
struct QuipHeader {
    uint8_t    ver_flags;      // Bits 7-4: Protocol Version, Bits 3-0: Flags
    PacketType type;           // Strongly typed packet type
    uint16_t   sequence;       // Monotonically increasing sequence number per channel
    uint32_t   timestamp_us;   // Client hardware clock in microseconds
    uint32_t   nonce_prefix;   // Nonce counter for AEAD decryption validation

    [[nodiscard]] constexpr uint8_t version() const noexcept {
        return (ver_flags >> 4) & 0x0F;
    }

    [[nodiscard]] constexpr uint8_t flags() const noexcept {
        return ver_flags & 0x0F;
    }

    [[nodiscard]] constexpr bool is_encrypted() const noexcept {
        return (flags() & HeaderFlag::Encrypted) != 0;
    }
};

struct QuipInputPayload {
    uint64_t digital_mask;   // Bitfield map for up to 64 discrete controls
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

// 64-Bit Control Map to Linux Input Key/Button Codes
constexpr std::array<uint16_t, 64> BIT_TO_KEY = {
    KEY_W,          // Bit 0: Forward
    KEY_A,          // Bit 1: Left
    KEY_S,          // Bit 2: Backward
    KEY_D,          // Bit 3: Right
    KEY_SPACE,      // Bit 4: Jump
    KEY_LEFTSHIFT,  // Bit 5: Sprint
    KEY_TAB,        // Bit 6: Scoreboard / Inventory
    KEY_ESC,        // Bit 7: Menu / Escape
    KEY_E,          // Bit 8: Interact
    KEY_R,          // Bit 9: Reload
    KEY_C,          // Bit 10: Crouch
    KEY_LEFTCTRL,   // Bit 11: Prone / Duck
    BTN_LEFT,       // Bit 12: Mouse Left Click
    BTN_RIGHT,      // Bit 13: Mouse Right Click
    BTN_MIDDLE,     // Bit 14: Mouse Middle Click
    BTN_SIDE,       // Bit 15: Mouse Side (Thumb 1)
    BTN_EXTRA,      // Bit 16: Mouse Extra (Thumb 2)
    KEY_Q,          // Bit 17: Ability 1 / Quick Switch
    KEY_F,          // Bit 18: Melee / Flashlight
    KEY_G,          // Bit 19: Grenade
    KEY_V,          // Bit 20: Push to Talk
    KEY_Z,          // Bit 21: Ping / Mark
    KEY_X,          // Bit 22: Emote / Drop
    KEY_B,          // Bit 23: Buy Menu
    KEY_1,          // Bit 24: Weapon 1
    KEY_2,          // Bit 25: Weapon 2
    KEY_3,          // Bit 26: Weapon 3
    KEY_4,          // Bit 27: Weapon 4
    KEY_5,          // Bit 28: Weapon 5
    KEY_6,          // Bit 29: Weapon 6
    KEY_LEFTALT,    // Bit 30: Alt Modifier
    KEY_CAPSLOCK,   // Bit 31: Caps Lock
    BTN_SOUTH,      // Bit 32: Gamepad A / Cross
    BTN_EAST,       // Bit 33: Gamepad B / Circle
    BTN_NORTH,      // Bit 34: Gamepad X / Square
    BTN_WEST,       // Bit 35: Gamepad Y / Triangle
    BTN_TL,         // Bit 36: Gamepad LB / L1
    BTN_TR,         // Bit 37: Gamepad RB / R1
    BTN_TL2,        // Bit 38: Gamepad LT / L2 Button
    BTN_TR2,        // Bit 39: Gamepad RT / R2 Button
    BTN_SELECT,     // Bit 40: Gamepad Select / Back
    BTN_START,      // Bit 41: Gamepad Start / Menu
    BTN_MODE,       // Bit 42: Gamepad Guide / Home
    BTN_THUMBL,     // Bit 43: Gamepad L3 (Left Stick Click)
    BTN_THUMBR,     // Bit 44: Gamepad R3 (Right Stick Click)
    BTN_DPAD_UP,    // Bit 45: D-Pad Up
    BTN_DPAD_DOWN,  // Bit 46: D-Pad Down
    BTN_DPAD_LEFT,  // Bit 47: D-Pad Left
    BTN_DPAD_RIGHT, // Bit 48: D-Pad Right
    KEY_UP,         // Bit 49: Arrow Up
    KEY_DOWN,       // Bit 50: Arrow Down
    KEY_LEFT,       // Bit 51: Arrow Left
    KEY_RIGHT,      // Bit 52: Arrow Right
    KEY_ENTER,      // Bit 53: Enter
    KEY_BACKSPACE,  // Bit 54: Backspace
    KEY_DELETE,     // Bit 55: Delete
    KEY_F1,         // Bit 56: F1
    KEY_F2,         // Bit 57: F2
    KEY_F3,         // Bit 58: F3
    KEY_F4,         // Bit 59: F4
    KEY_F5,         // Bit 60: F5
    KEY_F6,         // Bit 61: F6
    KEY_F11,        // Bit 62: F11
    KEY_F12         // Bit 63: F12
};

// 64-Packet Sliding Window Anti-Replay Validator
class SlidingWindowAntiReplay {
private:
    uint16_t max_seq = 0;
    uint64_t window = 0;
    bool initialized = false;

public:
    bool validate_and_update(uint16_t seq) {
        if (!initialized) {
            max_seq = seq;
            window = 1ULL;
            initialized = true;
            return true;
        }

        int16_t diff = static_cast<int16_t>(seq - max_seq);

        if (diff > 0) {
            if (diff >= 64) {
                window = 1ULL;
            } else {
                window = (window << diff) | 1ULL;
            }
            max_seq = seq;
            return true;
        } else {
            int offset = -diff;
            if (offset >= 64) {
                return false; // Stale packet outside 64-packet window
            }
            uint64_t bit = 1ULL << offset;
            if (window & bit) {
                return false; // Duplicate packet replayed inside window
            }
            window |= bit; // Valid out-of-order packet
            return true;
        }
    }

    void reset() {
        max_seq = 0;
        window = 0;
        initialized = false;
    }
};

// RAII File Descriptor wrapper
class UniqueFd {
    int fd_ = -1;

public:
    constexpr UniqueFd() noexcept = default;
    explicit UniqueFd(int fd) noexcept : fd_(fd) {}
    ~UniqueFd() { reset(); }

    UniqueFd(const UniqueFd&) = delete;
    UniqueFd& operator=(const UniqueFd&) = delete;

    UniqueFd(UniqueFd&& other) noexcept : fd_(std::exchange(other.fd_, -1)) {}
    UniqueFd& operator=(UniqueFd&& other) noexcept {
        if (this != &other) reset(other.release());
        return *this;
    }

    void reset(int new_fd = -1) noexcept {
        if (fd_ >= 0) ::close(fd_);
        fd_ = new_fd;
    }

    [[nodiscard]] int release() noexcept { return std::exchange(fd_, -1); }
    [[nodiscard]] int get() const noexcept { return fd_; }
    [[nodiscard]] bool valid() const noexcept { return fd_ >= 0; }
    explicit operator bool() const noexcept { return valid(); }
};

static std::atomic<bool> g_running{true};

static void signal_handler(int /*sig*/) {
    g_running.store(false, std::memory_order_relaxed);
}

class LinuxQuipHost {
private:
    UniqueFd uinput_fd;
    uint64_t last_digital_mask = 0;
    int8_t last_stick_x = 0;
    int8_t last_stick_y = 0;

    void emit_event(uint16_t type, uint16_t code, int32_t val) {
        input_event ie{};
        ie.type = type;
        ie.code = code;
        ie.value = val;

        ssize_t ret = write(uinput_fd.get(), &ie, sizeof(ie));
        if (ret < 0) {
            std::cerr << std::format("[Error] write to uinput failed: {}\n", strerror(errno));
        } else if (static_cast<size_t>(ret) != sizeof(ie)) {
            std::cerr << std::format("[Error] partial write to uinput: {}/{} bytes\n", ret, sizeof(ie));
        }
    }

    void sync_events() {
        emit_event(EV_SYN, SYN_REPORT, 0);
    }

public:
    bool InitVirtualDevice() {
        uinput_fd.reset(open("/dev/uinput", O_WRONLY | O_NONBLOCK));
        if (!uinput_fd) {
            std::cerr << std::format("[Error] Cannot open /dev/uinput: {}. Did you run linux_perms.sh or start with root privileges?\n", strerror(errno));
            return false;
        }

        // Register Key, Relative (Mouse/Gyro), and Absolute (Analog Stick) event types
        if (ioctl(uinput_fd.get(), UI_SET_EVBIT, EV_KEY) < 0 ||
            ioctl(uinput_fd.get(), UI_SET_EVBIT, EV_REL) < 0 ||
            ioctl(uinput_fd.get(), UI_SET_EVBIT, EV_ABS) < 0 ||
            ioctl(uinput_fd.get(), UI_SET_RELBIT, REL_X) < 0 ||
            ioctl(uinput_fd.get(), UI_SET_RELBIT, REL_Y) < 0 ||
            ioctl(uinput_fd.get(), UI_SET_ABSBIT, ABS_X) < 0 ||
            ioctl(uinput_fd.get(), UI_SET_ABSBIT, ABS_Y) < 0) {
            std::cerr << std::format("[Error] Failed to configure event bits: {}\n", strerror(errno));
            return false;
        }

        // Setup Absolute Axes for Left Analog Stick (-128 to +127)
#ifdef UI_ABS_SETUP
        uinput_abs_setup abs_x{};
        abs_x.code = ABS_X;
        abs_x.absinfo.minimum = -128;
        abs_x.absinfo.maximum = 127;
        if (ioctl(uinput_fd.get(), UI_ABS_SETUP, &abs_x) < 0) {
            std::cerr << std::format("[Warning] UI_ABS_SETUP for ABS_X failed: {}\n", strerror(errno));
        }

        uinput_abs_setup abs_y{};
        abs_y.code = ABS_Y;
        abs_y.absinfo.minimum = -128;
        abs_y.absinfo.maximum = 127;
        if (ioctl(uinput_fd.get(), UI_ABS_SETUP, &abs_y) < 0) {
            std::cerr << std::format("[Warning] UI_ABS_SETUP for ABS_Y failed: {}\n", strerror(errno));
        }
#endif

        // Register all non-zero keys/buttons from 64-bit control map
        for (uint16_t code : BIT_TO_KEY) {
            if (code != 0) {
                if (ioctl(uinput_fd.get(), UI_SET_KEYBIT, code) < 0) {
                    std::cerr << std::format("[Error] Failed to register key code {}: {}\n", code, strerror(errno));
                    return false;
                }
            }
        }

        uinput_setup usetup{};
        usetup.id.bustype = BUS_USB;
        usetup.id.vendor  = 0x1234;
        usetup.id.product = 0x5678;
        usetup.id.version = 1;

        constexpr std::string_view device_name = "QUIP Virtual Controller";
        std::format_to_n(usetup.name, UINPUT_MAX_NAME_SIZE - 1, "{}", device_name);

        if (ioctl(uinput_fd.get(), UI_DEV_SETUP, &usetup) < 0) {
            std::cerr << std::format("[Error] UI_DEV_SETUP failed: {}\n", strerror(errno));
            return false;
        }
        if (ioctl(uinput_fd.get(), UI_DEV_CREATE) < 0) {
            std::cerr << std::format("[Error] UI_DEV_CREATE failed: {}\n", strerror(errno));
            return false;
        }

        std::cout << "[QUIP Host Engine] Created Linux virtual input device with Gamepad, Gyro, and Mouse support.\n";
        return true;
    }

    void ProcessInput(const QuipInputPayload& payload) {
        bool needs_sync = false;

        // 1. Mouse & Gyroscope Fusion (Delta Addition)
        int32_t total_dx = static_cast<int32_t>(payload.mouse_dx) + static_cast<int32_t>(payload.gyro_yaw);
        int32_t total_dy = static_cast<int32_t>(payload.mouse_dy) + static_cast<int32_t>(payload.gyro_pitch);

        if (total_dx != 0 || total_dy != 0) {
            if (total_dx != 0) emit_event(EV_REL, REL_X, total_dx);
            if (total_dy != 0) emit_event(EV_REL, REL_Y, total_dy);
            needs_sync = true;
        }

        // 2. Analog Stick Input (ABS_X, ABS_Y)
        if (payload.left_stick_x != last_stick_x) {
            emit_event(EV_ABS, ABS_X, payload.left_stick_x);
            last_stick_x = payload.left_stick_x;
            needs_sync = true;
        }
        if (payload.left_stick_y != last_stick_y) {
            emit_event(EV_ABS, ABS_Y, payload.left_stick_y);
            last_stick_y = payload.left_stick_y;
            needs_sync = true;
        }

        // 3. Digital Key Mask Bitwise Delta Processing
        uint64_t changed_bits = payload.digital_mask ^ last_digital_mask;
        if (changed_bits != 0) {
            while (changed_bits != 0) {
                int i = std::countr_zero(changed_bits);
                if (i < static_cast<int>(BIT_TO_KEY.size())) {
                    uint64_t bit = (1ULL << i);
                    bool is_pressed = (payload.digital_mask & bit) != 0;
                    uint16_t key_code = BIT_TO_KEY[i];

                    if (key_code != 0) {
                        emit_event(EV_KEY, key_code, is_pressed ? 1 : 0);
                        needs_sync = true;
                    }
                }
                changed_bits &= changed_bits - 1;
            }
            last_digital_mask = payload.digital_mask;
        }

        if (needs_sync) {
            sync_events();
        }
    }

    void ProcessKeyStateSync(uint64_t target_digital_mask) {
        if (target_digital_mask == last_digital_mask) return;

        std::cout << std::format("[QUIP Host Engine] Reconciling key state sync mask: 0x{:016x}\n", target_digital_mask);
        QuipInputPayload payload{};
        payload.digital_mask = target_digital_mask;
        payload.left_stick_x = last_stick_x;
        payload.left_stick_y = last_stick_y;
        ProcessInput(payload);
    }

    void ReleaseAllKeys() {
        if (!uinput_fd || (last_digital_mask == 0 && last_stick_x == 0 && last_stick_y == 0)) return;

        bool needs_sync = false;
        uint64_t mask = last_digital_mask;

        while (mask != 0) {
            int i = std::countr_zero(mask);
            if (i < static_cast<int>(BIT_TO_KEY.size())) {
                uint16_t key_code = BIT_TO_KEY[i];
                if (key_code != 0) {
                    emit_event(EV_KEY, key_code, 0);
                    needs_sync = true;
                }
            }
            mask &= mask - 1;
        }

        if (last_stick_x != 0) {
            emit_event(EV_ABS, ABS_X, 0);
            last_stick_x = 0;
            needs_sync = true;
        }
        if (last_stick_y != 0) {
            emit_event(EV_ABS, ABS_Y, 0);
            last_stick_y = 0;
            needs_sync = true;
        }

        if (needs_sync) {
            sync_events();
        }
        last_digital_mask = 0;
    }

    ~LinuxQuipHost() {
        if (uinput_fd) {
            ReleaseAllKeys();
            ioctl(uinput_fd.get(), UI_DEV_DESTROY);
        }
    }
};

int main() {
    struct sigaction sa{};
    sa.sa_handler = signal_handler;
    sigaction(SIGINT, &sa, nullptr);
    sigaction(SIGTERM, &sa, nullptr);

    LinuxQuipHost host;
    if (!host.InitVirtualDevice()) return 1;

    UniqueFd server_fd{socket(AF_INET, SOCK_DGRAM, 0)};
    if (!server_fd) {
        std::cerr << std::format("[Error] Failed to create UDP socket: {}\n", strerror(errno));
        return 1;
    }

    int optval = 1;
    if (setsockopt(server_fd.get(), SOL_SOCKET, SO_REUSEADDR, &optval, sizeof(optval)) < 0) {
        std::cerr << std::format("[Warning] Failed to set SO_REUSEADDR: {}\n", strerror(errno));
    }

    sockaddr_in address{};
    address.sin_family = AF_INET;
    address.sin_port = htons(9876);
    address.sin_addr.s_addr = INADDR_ANY;

    if (bind(server_fd.get(), reinterpret_cast<struct sockaddr*>(&address), sizeof(address)) < 0) {
        std::cerr << std::format("[Error] Failed to bind to UDP port 9876: {}\n", strerror(errno));
        return 1;
    }

    std::cout << "[QUIP Host Engine] Listening for inputs on UDP port 9876...\n";

    SlidingWindowAntiReplay anti_replay;
    QuipPacket packet{};

    while (g_running.load(std::memory_order_relaxed)) {
        ssize_t bytes = recv(server_fd.get(), &packet, sizeof(packet), 0);
        if (bytes < 0) {
            if (errno == EINTR) {
                continue;
            }
            std::cerr << std::format("[Error] recv() failed: {}\n", strerror(errno));
            break;
        }
        if (static_cast<size_t>(bytes) != sizeof(QuipPacket)) {
            continue;
        }

        if (packet.header.version() != 0x01) {
            continue;
        }

        if (packet.header.is_encrypted()) {
            continue;
        }

        // Anti-replay window check
        if (!anti_replay.validate_and_update(packet.header.sequence)) {
            // Drop replayed or stale sequence numbers
            continue;
        }

        switch (packet.header.type) {
            case PacketType::InputBatch:
                host.ProcessInput(packet.payload);
                break;
            case PacketType::Heartbeat:
                // Keep-alive heartbeat acknowledgement
                break;
            case PacketType::KeyStateSync:
                host.ProcessKeyStateSync(packet.payload.digital_mask);
                break;
            default:
                break;
        }
    }

    std::cout << "[QUIP Host Engine] Shutting down...\n";
    return 0;
}

