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

// Enum class for strongly-typed protocol packet types
enum class PacketType : uint8_t {
    InputBatch      = 0x01,
    Heartbeat       = 0x02,
    CryptoHandshake = 0x03,
    KeyStateSync    = 0x04
};

// Scoped flag constants
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

    // Helper methods for nibble extraction and flag checking
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

// Modern constexpr std::array lookup map for input key codes
constexpr std::array<uint16_t, 14> BIT_TO_KEY = {
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

// Lightweight C++20 RAII File Descriptor wrapper
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

// Modern lock-free std::atomic for signal safety
static std::atomic<bool> g_running{true};

static void signal_handler(int /*sig*/) {
    g_running.store(false, std::memory_order_relaxed);
}

class LinuxQuipHost {
private:
    UniqueFd uinput_fd;
    uint64_t last_digital_mask = 0;

    void emit_event(uint16_t type, uint16_t code, int32_t val) {
        // C++20 Designated Initializers
        input_event ie{
            .type = type,
            .code = code,
            .value = val
        };
        
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

        // Enable Event Types: Key presses & Relative mouse motion
        if (ioctl(uinput_fd.get(), UI_SET_EVBIT, EV_KEY) < 0 ||
            ioctl(uinput_fd.get(), UI_SET_EVBIT, EV_REL) < 0 ||
            ioctl(uinput_fd.get(), UI_SET_RELBIT, REL_X) < 0 ||
            ioctl(uinput_fd.get(), UI_SET_RELBIT, REL_Y) < 0) {
            std::cerr << std::format("[Error] Failed to configure event bits: {}\n", strerror(errno));
            return false;
        }

        // Register keys in kernel virtual device table
        for (uint16_t code : BIT_TO_KEY) {
            if (code != 0) {
                if (ioctl(uinput_fd.get(), UI_SET_KEYBIT, code) < 0) {
                    std::cerr << std::format("[Error] Failed to register key code {}: {}\n", code, strerror(errno));
                    return false;
                }
            }
        }

        // C++20 Designated Initializers for nested structs
        uinput_setup usetup{
            .id = {
                .bustype = BUS_USB,
                .vendor  = 0x1234, // Custom QUIP Vendor ID
                .product = 0x5678, // Custom QUIP Product ID
                .version = 1
            }
        };

        // Modern safe string formatting into character array
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

        // 2. Digital keys using C++20 <bit> std::countr_zero for bitmask processing
        uint64_t changed_bits = payload.digital_mask ^ last_digital_mask;
        if (changed_bits != 0) {
            while (changed_bits != 0) {
                // Find trailing zeros to directly jump to changed bit index
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
                // Clear lowest set bit
                changed_bits &= changed_bits - 1;
            }
            last_digital_mask = payload.digital_mask;
        }

        // Batch commit input state changes
        if (needs_sync) {
            sync_events();
        }
    }

    void ReleaseAllKeys() {
        if (!uinput_fd || last_digital_mask == 0) return;

        bool needs_sync = false;
        uint64_t mask = last_digital_mask;

        // Efficiently process set bits with std::countr_zero
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
    // Register signal handlers using Designated Initializers
    struct sigaction sa{.sa_handler = signal_handler};
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

    sockaddr_in address{
        .sin_family = AF_INET,
        .sin_port = htons(9876),
        .sin_addr = {.s_addr = INADDR_ANY}
    };

    if (bind(server_fd.get(), reinterpret_cast<struct sockaddr*>(&address), sizeof(address)) < 0) {
        std::cerr << std::format("[Error] Failed to bind to UDP port 9876: {}\n", strerror(errno));
        return 1;
    }

    std::cout << "[QUIP Host Engine] Listening for inputs on UDP port 9876...\n";

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

        if (packet.header.type == PacketType::InputBatch) {
            host.ProcessInput(packet.payload);
        }
    }

    std::cout << "[QUIP Host Engine] Shutting down...\n";
    return 0;
}
