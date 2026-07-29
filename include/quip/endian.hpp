#ifndef QUIP_ENDIAN_HPP
#define QUIP_ENDIAN_HPP

#include <bit>
#include <cstdint>

namespace quip::endian {

    [[nodiscard]] constexpr uint16_t host_to_le16(uint16_t val) noexcept {
        if constexpr (std::endian::native == std::endian::little) {
            return val;
        } else {
            return static_cast<uint16_t>((val >> 8) | (val << 8));
        }
    }

    [[nodiscard]] constexpr uint32_t host_to_le32(uint32_t val) noexcept {
        if constexpr (std::endian::native == std::endian::little) {
            return val;
        } else {
            return ((val >> 24) & 0x000000FFU) |
                   ((val >> 8)  & 0x0000FF00U) |
                   ((val << 8)  & 0x00FF0000U) |
                   ((val << 24) & 0xFF000000U);
        }
    }

    [[nodiscard]] constexpr uint64_t host_to_le64(uint64_t val) noexcept {
        if constexpr (std::endian::native == std::endian::little) {
            return val;
        } else {
            return ((val >> 56) & 0x00000000000000FFULL) |
                   ((val >> 40) & 0x000000000000FF00ULL) |
                   ((val >> 24) & 0x0000000000FF0000ULL) |
                   ((val >> 8)  & 0x00000000FF000000ULL) |
                   ((val << 8)  & 0x000000FF00000000ULL) |
                   ((val << 24) & 0x0000FF0000000000ULL) |
                   ((val << 40) & 0x00FF000000000000ULL) |
                   ((val << 56) & 0xFF00000000000000ULL);
        }
    }

    [[nodiscard]] constexpr uint16_t le16_to_host(uint16_t val) noexcept { return host_to_le16(val); }
    [[nodiscard]] constexpr uint32_t le32_to_host(uint32_t val) noexcept { return host_to_le32(val); }
    [[nodiscard]] constexpr uint64_t le64_to_host(uint64_t val) noexcept { return host_to_le64(val); }

    [[nodiscard]] constexpr int16_t host_to_le16(int16_t val) noexcept {
        return static_cast<int16_t>(host_to_le16(static_cast<uint16_t>(val)));
    }
    [[nodiscard]] constexpr int16_t le16_to_host(int16_t val) noexcept {
        return static_cast<int16_t>(le16_to_host(static_cast<uint16_t>(val)));
    }

} // namespace quip::endian

#endif // QUIP_ENDIAN_HPP
