#include <iostream>
#include <cstdint>
#include <cstring>
#include <fcntl.h>
#include <unistd.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <linux/uinput.h>

// --- QUIP Protocol Definitions ---
#pragma pack(push, 1)
struct QuipHeader {
    uint8_t  ver_flags;
    uint8_t  type;
    uint16_t sequence;
    uint32_t timestamp_us;
    uint32_t nonce_prefix;
};

struct QuipInputPayload {
    uint64_t digital_mask;
    int16_t  mouse_dx;
    int16_t  mouse_dy;
    int8_t   left_stick_x;
    int8_t   left_stick_y;
    int8_t   gyro_yaw;
    int8_t   gyro_pitch;
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

class LinuxQuipHost {
private:
    int uinput_fd = -1;
    uint64_t last_digital_mask = 0;

    void emit_event(uint16_t type, uint16_t code, int32_t val) {
        struct input_event ie{};
        ie.type = type;
        ie.code = code;
        ie.value = val;
        write(uinput_fd, &ie, sizeof(ie));
    }

    void sync_events() {
        emit_event(EV_SYN, SYN_REPORT, 0);
    }

public:
    bool InitVirtualDevice() {
        uinput_fd = open("/dev/uinput", O_WRONLY | O_NONBLOCK);
        if (uinput_fd < 0) {
            std::cerr << "[Error] Cannot open /dev/uinput. Did you run with udev rules or root privileges?\n";
            return false;
        }

        // Enable Event Types: Key presses & Relative mouse motion
        ioctl(uinput_fd, UI_SET_EVBIT, EV_KEY);
        ioctl(uinput_fd, UI_SET_EVBIT, EV_REL);
        ioctl(uinput_fd, UI_SET_RELBIT, REL_X);
        ioctl(uinput_fd, UI_SET_RELBIT, REL_Y);

        // Register keys in kernel virtual device table
        for (uint16_t code : BIT_TO_KEY) {
            if (code != 0) {
                ioctl(uinput_fd, UI_SET_KEYBIT, code);
            }
        }

        // Configure Virtual Device Descriptor
        struct uinput_setup usetup{};
        usetup.id.bustype = BUS_USB;
        usetup.id.vendor  = 0x1234; // Custom QUIP Vendor ID
        usetup.id.product = 0x5678; // Custom QUIP Product ID
        strcpy(usetup.name, "QUIP Virtual Controller");

        ioctl(uinput_fd, UI_DEV_SETUP, &usetup);
        ioctl(uinput_fd, UI_DEV_CREATE);

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

    ~LinuxQuipHost() {
        if (uinput_fd >= 0) {
            ioctl(uinput_fd, UI_DEV_DESTROY);
            close(uinput_fd);
        }
    }
};

int main() {
    LinuxQuipHost host;
    if (!host.InitVirtualDevice()) return 1;

    int server_fd = socket(AF_INET, SOCK_DGRAM, 0);
    sockaddr_in address{};
    address.sin_family = AF_INET;
    address.sin_addr.s_addr = INADDR_ANY;
    address.sin_port = htons(9876);

    if (bind(server_fd, (struct sockaddr*)&address, sizeof(address)) < 0) {
        std::cerr << "[Error] Failed to bind to UDP port 9876\n";
        return 1;
    }

    std::cout << "[QUIP Host Engine] Listening for inputs on UDP port 9876...\n";

    QuipPacket packet;
    while (true) {
        ssize_t bytes = recv(server_fd, &packet, sizeof(packet), 0);
        if (bytes == sizeof(QuipPacket)) {
            if ((packet.header.ver_flags & 0x0F) == 0x01 && packet.header.type == 0x01) {
                host.ProcessInput(packet.payload);
            }
        }
    }

    close(server_fd);
    return 0;
}
