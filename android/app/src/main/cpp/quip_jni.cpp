#include <jni.h>
#include <string>
#include <atomic>
#include <cstdint>
#include <cstring>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <unistd.h>

#ifdef __ANDROID__
#include <android/log.h>
#define LOG_TAG "QUIP_JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#else
#include <cstdio>
#define LOGI(...) (std::printf("[QUIP_JNI INFO] " __VA_ARGS__), std::printf("\n"))
#define LOGE(...) (std::fprintf(stderr, "[QUIP_JNI ERROR] " __VA_ARGS__), std::fprintf(stderr, "\n"))
#endif

#include <quip/protocol.hpp>
#include <quip/endian.hpp>

namespace {
    int g_socket_fd = -1;
    sockaddr_in g_server_addr{};
    std::atomic<uint16_t> g_sequence{1};
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_quip_client_NativeQuipClient_initClient(
        JNIEnv* env,
        jobject /* this */,
        jstring host_ip,
        jint port) {
    
    const char* ip_str = env->GetStringUTFChars(host_ip, nullptr);
    if (!ip_str) return JNI_FALSE;

    if (g_socket_fd >= 0) {
        close(g_socket_fd);
        g_socket_fd = -1;
    }

    g_socket_fd = socket(AF_INET, SOCK_DGRAM, 0);
    if (g_socket_fd < 0) {
        LOGE("Failed to create UDP socket");
        env->ReleaseStringUTFChars(host_ip, ip_str);
        return JNI_FALSE;
    }

    std::memset(&g_server_addr, 0, sizeof(g_server_addr));
    g_server_addr.sin_family = AF_INET;
    g_server_addr.sin_port = htons(static_cast<uint16_t>(port));
    
    if (inet_pton(AF_INET, ip_str, &g_server_addr.sin_addr) <= 0) {
        LOGE("Invalid IP address: %s", ip_str);
        close(g_socket_fd);
        g_socket_fd = -1;
        env->ReleaseStringUTFChars(host_ip, ip_str);
        return JNI_FALSE;
    }

    env->ReleaseStringUTFChars(host_ip, ip_str);
    g_sequence.store(1, std::memory_order_relaxed);
    LOGI("QUIP Native Client initialized for target %s:%d", ip_str, port);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_quip_client_NativeQuipClient_sendInputPacket(
        JNIEnv* /* env */,
        jobject /* this */,
        jlong digital_mask,
        jint mouse_dx,
        jint mouse_dy,
        jint left_stick_x,
        jint left_stick_y,
        jint gyro_yaw,
        jint gyro_pitch,
        jlong timestamp_us) {

    if (g_socket_fd < 0) return JNI_FALSE;

    quip::QuipPacket packet{};
    packet.header.ver_flags = quip::QuipHeader::make_ver_flags(1, 0);
    packet.header.type = quip::PacketType::InputBatch;
    packet.header.sequence = g_sequence.fetch_add(1, std::memory_order_relaxed);
    packet.header.timestamp_us = static_cast<uint32_t>(timestamp_us);
    packet.header.nonce_prefix = 0;

    packet.payload.digital_mask = static_cast<uint64_t>(digital_mask);
    packet.payload.mouse_dx = static_cast<int16_t>(mouse_dx);
    packet.payload.mouse_dy = static_cast<int16_t>(mouse_dy);
    packet.payload.left_stick_x = static_cast<int8_t>(left_stick_x);
    packet.payload.left_stick_y = static_cast<int8_t>(left_stick_y);
    packet.payload.gyro_yaw = static_cast<int8_t>(gyro_yaw);
    packet.payload.gyro_pitch = static_cast<int8_t>(gyro_pitch);

    // Apply Little-Endian wire serialization
    packet.to_wire_endian();

    ssize_t sent = sendto(g_socket_fd, &packet, sizeof(packet), 0,
                          reinterpret_cast<sockaddr*>(&g_server_addr), sizeof(g_server_addr));

    return (sent == sizeof(packet)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_quip_client_NativeQuipClient_sendHeartbeat(
        JNIEnv* /* env */,
        jobject /* this */,
        jlong timestamp_us) {

    if (g_socket_fd < 0) return JNI_FALSE;

    quip::QuipPacket packet{};
    packet.header.ver_flags = quip::QuipHeader::make_ver_flags(1, 0);
    packet.header.type = quip::PacketType::Heartbeat;
    packet.header.sequence = g_sequence.fetch_add(1, std::memory_order_relaxed);
    packet.header.timestamp_us = static_cast<uint32_t>(timestamp_us);
    packet.header.nonce_prefix = 0;

    packet.to_wire_endian();

    ssize_t sent = sendto(g_socket_fd, &packet, sizeof(packet), 0,
                          reinterpret_cast<sockaddr*>(&g_server_addr), sizeof(g_server_addr));

    return (sent == sizeof(packet)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_quip_client_NativeQuipClient_closeClient(
        JNIEnv* /* env */,
        jobject /* this */) {
    if (g_socket_fd >= 0) {
        close(g_socket_fd);
        g_socket_fd = -1;
        LOGI("QUIP Native Client closed");
    }
}

} // extern "C"
