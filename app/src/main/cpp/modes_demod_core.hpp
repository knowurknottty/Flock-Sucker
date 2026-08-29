#pragma once
#include <algorithm>
#include <array>
#include <cmath>
#include <cstddef>
#include <cstdint>
#include <vector>
constexpr int kFrameBytes = 14;
constexpr int kFrameBits = 112;
constexpr int kPreambleSamples = 16;  // 8 us at 2 Msps
constexpr uint32_t kModeSPolynomial = 0xFFF409u;

inline uint16_t magnitude(uint8_t i, uint8_t q) {
    const int di = static_cast<int>(i) - 127;
    const int dq = static_cast<int>(q) - 127;
    return static_cast<uint16_t>(std::min(65535.0, std::sqrt(static_cast<double>(di * di + dq * dq)) * 256.0));
}

uint32_t crc88(const uint8_t* msg) {
    uint32_t crc = 0;
    for (int bit = 0; bit < 88; ++bit) {
        const uint32_t input = (msg[bit / 8] >> (7 - (bit % 8))) & 1u;
        const uint32_t top = (crc >> 23) & 1u;
        crc = (crc << 1) & 0xFFFFFFu;
        if (top ^ input) crc ^= kModeSPolynomial;
    }
    return crc;
}

bool crcValidExtendedSquitter(const uint8_t* msg) {
    const int df = msg[0] >> 3;
    if (df != 17 && df != 18) return false;
    const uint32_t parity = (static_cast<uint32_t>(msg[11]) << 16) |
                            (static_cast<uint32_t>(msg[12]) << 8) |
                            static_cast<uint32_t>(msg[13]);
    return crc88(msg) == parity;
}

bool plausiblePreamble(const std::vector<uint16_t>& m, size_t p) {
    const int hiIdx[] = {0, 2, 7, 9};
    const int loIdx[] = {1, 3, 4, 5, 6, 8, 10, 11, 12, 13, 14, 15};
    uint64_t hiSum = 0, loSum = 0;
    uint16_t hiMin = UINT16_MAX, loMax = 0;
    for (int idx : hiIdx) {
        const uint16_t v = m[p + idx];
        hiSum += v;
        hiMin = std::min(hiMin, v);
    }
    for (int idx : loIdx) {
        const uint16_t v = m[p + idx];
        loSum += v;
        loMax = std::max(loMax, v);
    }
    const double hiAvg = static_cast<double>(hiSum) / 4.0;
    const double loAvg = static_cast<double>(loSum) / 12.0;
    if (hiAvg < 900.0) return false;
    if (hiAvg < loAvg * 2.4) return false;
    if (hiMin <= loAvg * 1.35) return false;
    if (loMax > hiAvg * 0.92) return false;
    return true;
}

bool demodFrame(const std::vector<uint16_t>& m, size_t dataStart, std::array<uint8_t, kFrameBytes>& out) {
    out.fill(0);
    uint64_t strength = 0;
    uint64_t ambiguity = 0;
    for (int bit = 0; bit < kFrameBits; ++bit) {
        const uint16_t a = m[dataStart + bit * 2];
        const uint16_t b = m[dataStart + bit * 2 + 1];
        const uint16_t high = std::max(a, b);
        const uint16_t low = std::min(a, b);
        strength += high;
        ambiguity += low;
        if (high < 600 || static_cast<uint32_t>(high) * 10u < static_cast<uint32_t>(low) * 13u) return false;
        if (a > b) out[bit / 8] |= static_cast<uint8_t>(1u << (7 - (bit % 8)));
    }
    if (strength <= ambiguity * 13 / 10) return false;
    return crcValidExtendedSquitter(out.data());
}

inline std::vector<uint8_t> demodulate(const uint8_t* iq, size_t bytes) {
    const size_t samples = bytes / 2;
    std::vector<uint16_t> mag(samples);
    for (size_t s = 0; s < samples; ++s) mag[s] = magnitude(iq[s * 2], iq[s * 2 + 1]);

    std::vector<uint8_t> frames;
    const size_t need = kPreambleSamples + kFrameBits * 2;
    for (size_t p = 0; p + need <= samples;) {
        if (!plausiblePreamble(mag, p)) { ++p; continue; }
        std::array<uint8_t, kFrameBytes> frame{};
        if (demodFrame(mag, p + kPreambleSamples, frame)) {
            frames.insert(frames.end(), frame.begin(), frame.end());
            p += need;
        } else {
            ++p;
        }
    }
    return frames;
}