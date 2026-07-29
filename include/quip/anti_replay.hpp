#ifndef QUIP_ANTI_REPLAY_HPP
#define QUIP_ANTI_REPLAY_HPP

#include <cstdint>

namespace quip {

    // 64-Packet Sliding Window Anti-Replay Validator
    class SlidingWindowAntiReplay {
    private:
        uint16_t max_seq = 0;
        uint64_t window = 0;
        bool initialized = false;

    public:
        constexpr SlidingWindowAntiReplay() noexcept = default;

        [[nodiscard]] bool validate_and_update(uint16_t seq) noexcept {
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

        void reset() noexcept {
            max_seq = 0;
            window = 0;
            initialized = false;
        }

        [[nodiscard]] uint16_t get_max_sequence() const noexcept { return max_seq; }
        [[nodiscard]] bool is_initialized() const noexcept { return initialized; }
    };

} // namespace quip

#endif // QUIP_ANTI_REPLAY_HPP
