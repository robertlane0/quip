#include <cassert>
#include <iostream>

#include <quip/endian.hpp>
#include <quip/protocol.hpp>
#include <quip/anti_replay.hpp>

void test_endian_conversions() {
    uint16_t val16 = 0x1234;
    uint32_t val32 = 0x12345678U;
    uint64_t val64 = 0x123456789ABCDEF0ULL;
    int16_t sval16 = -12345;

    uint16_t le16 = quip::endian::host_to_le16(val16);
    assert(quip::endian::le16_to_host(le16) == val16);

    uint32_t le32 = quip::endian::host_to_le32(val32);
    assert(quip::endian::le32_to_host(le32) == val32);

    uint64_t le64 = quip::endian::host_to_le64(val64);
    assert(quip::endian::le64_to_host(le64) == val64);

    int16_t sle16 = quip::endian::host_to_le16(sval16);
    assert(quip::endian::le16_to_host(sle16) == sval16);

    std::cout << "[Test OK] Endianness conversions passed.\n";
}

void test_packet_wire_endian() {
    quip::QuipPacket original{};
    original.header.ver_flags = quip::QuipHeader::make_ver_flags(1, quip::HeaderFlag::AckReq);
    original.header.type = quip::PacketType::InputBatch;
    original.header.sequence = 12345;
    original.header.timestamp_us = 987654321;
    original.header.nonce_prefix = 42;

    original.payload.digital_mask = 0xDEADBEEFCAFEBABEULL;
    original.payload.mouse_dx = -500;
    original.payload.mouse_dy = 1200;
    original.payload.left_stick_x = 100;
    original.payload.left_stick_y = -100;
    original.payload.gyro_yaw = 15;
    original.payload.gyro_pitch = -20;

    quip::QuipPacket wire = original;
    wire.to_wire_endian();

    quip::QuipPacket decoded = wire;
    decoded.from_wire_endian();

    assert(decoded.header.version() == 1);
    assert(decoded.header.flags() == quip::HeaderFlag::AckReq);
    assert(decoded.header.type == quip::PacketType::InputBatch);
    assert(decoded.header.sequence == 12345);
    assert(decoded.header.timestamp_us == 987654321);
    assert(decoded.header.nonce_prefix == 42);

    assert(decoded.payload.digital_mask == 0xDEADBEEFCAFEBABEULL);
    assert(decoded.payload.mouse_dx == -500);
    assert(decoded.payload.mouse_dy == 1200);
    assert(decoded.payload.left_stick_x == 100);
    assert(decoded.payload.left_stick_y == -100);
    assert(decoded.payload.gyro_yaw == 15);
    assert(decoded.payload.gyro_pitch == -20);

    std::cout << "[Test OK] Packet wire encoding/decoding passed.\n";
}

void test_anti_replay() {
    quip::SlidingWindowAntiReplay ar;

    // Normal in-order
    assert(ar.validate_and_update(10) == true);
    assert(ar.validate_and_update(11) == true);

    // Duplicate
    assert(ar.validate_and_update(11) == false);

    // Out of order inside window
    assert(ar.validate_and_update(9) == true);
    assert(ar.validate_and_update(9) == false);

    // Stale sequence outside window
    assert(ar.validate_and_update(100) == true);
    assert(ar.validate_and_update(10) == false); // Stale (offset 90 > 64)

    // Sequence number wraparound (65535 -> 0)
    quip::SlidingWindowAntiReplay ar_wrap;
    assert(ar_wrap.validate_and_update(65535) == true);
    assert(ar_wrap.validate_and_update(0) == true);
    assert(ar_wrap.validate_and_update(65535) == false); // Replay
    assert(ar_wrap.validate_and_update(1) == true);

    std::cout << "[Test OK] Anti-replay sliding window validation passed.\n";
}

int main() {
    test_endian_conversions();
    test_packet_wire_endian();
    test_anti_replay();
    std::cout << "[SUCCESS] All libquip unit tests passed successfully!\n";
    return 0;
}
