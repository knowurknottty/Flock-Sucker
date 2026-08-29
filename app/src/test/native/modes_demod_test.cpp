#include "../../main/cpp/modes_demod_core.hpp"
#include <cassert>
#include <cstdio>
#include <cstring>
#include <string>
#include <vector>

static std::vector<uint8_t> fromHex(const char* hex) {
    std::vector<uint8_t> out;
    for (size_t i = 0; hex[i] && hex[i + 1]; i += 2) {
        unsigned value = 0;
        std::sscanf(hex + i, "%2x", &value);
        out.push_back(static_cast<uint8_t>(value));
    }
    return out;
}

static std::vector<uint8_t> synthIq(const std::vector<uint8_t>& frame) {
    const size_t lead = 40, samples = lead + 16 + 224 + 40;
    std::vector<uint8_t> iq(samples * 2, 127);
    auto high = [&](size_t sample) { iq[sample * 2] = 255; iq[sample * 2 + 1] = 127; };
    const int pulses[] = {0, 2, 7, 9};
    for (int p : pulses) high(lead + p);
    size_t d = lead + 16;
    for (int bit = 0; bit < 112; ++bit) {
        bool one = (frame[bit / 8] >> (7 - bit % 8)) & 1;
        high(d + bit * 2 + (one ? 0 : 1));
    }
    return iq;
}

int main() {
    auto valid = fromHex("8D40621D58C382D690C8AC2863A7");
    auto iq = synthIq(valid);
    auto decoded = demodulate(iq.data(), iq.size());
    assert(decoded.size() == 14);
    assert(std::equal(valid.begin(), valid.end(), decoded.begin()));

    valid[6] ^= 0x01;
    iq = synthIq(valid);
    decoded = demodulate(iq.data(), iq.size());
    assert(decoded.empty());
    std::puts("modes_demod_test: PASS");
    return 0;
}
