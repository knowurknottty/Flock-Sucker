#include <jni.h>
#include "modes_demod_core.hpp"

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_flockyou_adversarial_ModeSNativeBridge_nativeDemodulate(
    JNIEnv* env, jobject, jbyteArray iqArray, jint validLength) {
    if (!iqArray || validLength <= 0) return env->NewByteArray(0);
    const jsize arrayLength = env->GetArrayLength(iqArray);
    const jsize n = std::min<jsize>(arrayLength, validLength) & ~1;
    if (n <= 0) return env->NewByteArray(0);
    std::vector<jbyte> input(static_cast<size_t>(n));
    env->GetByteArrayRegion(iqArray, 0, n, input.data());
    if (env->ExceptionCheck()) return nullptr;
    const auto frames = demodulate(reinterpret_cast<const uint8_t*>(input.data()), static_cast<size_t>(n));
    auto out = env->NewByteArray(static_cast<jsize>(frames.size()));
    if (!out || frames.empty()) return out;
    env->SetByteArrayRegion(out, 0, static_cast<jsize>(frames.size()), reinterpret_cast<const jbyte*>(frames.data()));
    return out;
}
