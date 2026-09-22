// Toolchain-verification checkpoint (2026-09-24) -- see CMakeLists.txt.
// A single trivial JNI function so we can confirm the whole native build
// pipeline works before adding whisper.cpp's much bigger build surface.
#include <jni.h>
#include <string>

extern "C" JNIEXPORT jstring JNICALL
Java_com_asok_medrecall_data_transcription_NativeTranscriber_nativeTestString(
        JNIEnv *env,
        jobject /* this */) {
    std::string result = "native ok";
    return env->NewStringUTF(result.c_str());
}
