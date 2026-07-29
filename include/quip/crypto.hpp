#ifndef QUIP_CRYPTO_HPP
#define QUIP_CRYPTO_HPP

#include <array>
#include <cstdint>
#include <span>
#include <string_view>

namespace quip {

    constexpr size_t KEY_SIZE = 32;
    constexpr size_t MAC_SIZE = 16;

    using KeyBuffer = std::array<uint8_t, KEY_SIZE>;
    using AuthTag = std::array<uint8_t, MAC_SIZE>;

    // Placeholder CryptoEngine preparing for Noise_IK ChaCha20-Poly1305 integration
    class CryptoEngine {
    private:
        KeyBuffer static_public_key_{};
        KeyBuffer static_private_key_{};
        KeyBuffer session_key_{};
        bool is_paired_ = false;

    public:
        constexpr CryptoEngine() noexcept = default;

        bool generate_keypair() noexcept {
            // Scaffolding: Noise_IK Curve25519 key generation
            return true;
        }

        [[nodiscard]] const KeyBuffer& public_key() const noexcept {
            return static_public_key_;
        }

        [[nodiscard]] bool is_paired() const noexcept {
            return is_paired_;
        }

        // Placeholder AEAD Encrypt / Decrypt signature checks
        [[nodiscard]] bool decrypt_payload(
            std::span<const uint8_t> ciphertext,
            std::span<uint8_t> plaintext,
            uint64_t nonce
        ) noexcept {
            (void)ciphertext; (void)plaintext; (void)nonce;
            if (!is_paired_) return false;
            return false;
        }
    };

} // namespace quip

#endif // QUIP_CRYPTO_HPP
