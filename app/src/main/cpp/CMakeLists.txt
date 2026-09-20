cmake_minimum_required(VERSION 3.18.1)

project("abkksu")

get_filename_component(APP_MODULE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../.." ABSOLUTE)
set(LIBSODIUM_JNI_DIR "${APP_MODULE_DIR}/build/generated/abk-jniLibs/main/libsodium/${ANDROID_ABI}")

add_library(
    abkksu
    SHARED
    abk_ksu.cc
    abk_ksu_jni.cc
    fork_signing_jni.cc
)

target_include_directories(abkksu PRIVATE . third_party/libsodium)

add_library(sodium SHARED IMPORTED)
set_target_properties(sodium PROPERTIES
    IMPORTED_LOCATION "${LIBSODIUM_JNI_DIR}/libsodium.so"
)

find_library(log-lib log)
target_link_libraries(abkksu sodium ${log-lib})
