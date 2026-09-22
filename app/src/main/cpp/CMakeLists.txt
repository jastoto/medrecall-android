# Toolchain-verification checkpoint (2026-09-24), before whisper.cpp is added.
# Purpose: confirm Gradle -> CMake -> NDK compile -> JNI -> Kotlin actually
# works end-to-end on Asok's machine with a trivial native library, so any
# later whisper.cpp build failure can be isolated from basic toolchain
# problems (SDK/NDK paths, ABI config, JNI naming, etc.).
cmake_minimum_required(VERSION 3.22.1)
project("medrecall_native")

add_library(
    native-bridge
    SHARED
    native-bridge.cpp
)

find_library(log-lib log)

target_link_libraries(
    native-bridge
    ${log-lib}
)
