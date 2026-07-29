#ifndef QUIP_PROTOCOL_HPP
#define QUIP_PROTOCOL_HPP

#include <cstdint>
#include <quip/endian.hpp>

namespace quip {

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

        [[nodiscard]] constexpr static uint8_t make_ver_flags(uint8_t version, uint8_t flags) noexcept {
            return static_cast<uint8_t>(((version & 0x0F) << 4) | (flags & 0x0F));
        }

        void to_wire_endian() noexcept {
            sequence = endian::host_to_le16(sequence);
            timestamp_us = endian::host_to_le32(timestamp_us);
            nonce_prefix = endian::host_to_le32(nonce_prefix);
        }

        void from_wire_endian() noexcept {
            sequence = endian::le16_to_host(sequence);
            timestamp_us = endian::le32_to_host(timestamp_us);
            nonce_prefix = endian::le32_to_host(nonce_prefix);
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

        void to_wire_endian() noexcept {
            digital_mask = endian::host_to_le64(digital_mask);
            mouse_dx = endian::host_to_le16(mouse_dx);
            mouse_dy = endian::host_to_le16(mouse_dy);
        }

        void from_wire_endian() noexcept {
            digital_mask = endian::le64_to_host(digital_mask);
            mouse_dx = endian::le16_to_host(mouse_dx);
            mouse_dy = endian::le16_to_host(mouse_dy);
        }
    };

    struct QuipPacket {
        QuipHeader       header;
        QuipInputPayload payload;

        void to_wire_endian() noexcept {
            header.to_wire_endian();
            payload.to_wire_endian();
        }

        void from_wire_endian() noexcept {
            header.from_wire_endian();
            payload.from_wire_endian();
        }
    };
#pragma pack(pop)

} // namespace quip

#endif // QUIP_PROTOCOL_HPP
