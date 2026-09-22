package com.asok.medrecall.data.transcription

/**
 * Toolchain-verification checkpoint (2026-09-24): a bare JNI call into the
 * new native-bridge library, added BEFORE whisper.cpp itself so an NDK/CMake
 * build failure can be isolated from whisper.cpp's own (much bigger) build
 * surface. nativeTestString() should just return "native ok" -- once that's
 * confirmed working on a real build, this file becomes the real bridge to
 * whisper.cpp for on-device transcription (see RecordVisitScreen.kt's
 * transcribeAudioOnDevice(), still a stub pending this).
 */
object NativeTranscriber {
    init {
        System.loadLibrary("native-bridge")
    }

    external fun nativeTestString(): String
}
