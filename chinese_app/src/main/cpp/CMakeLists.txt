cmake_minimum_required(VERSION 3.18.1)

project("abkksu")

add_library(
    abkksu
    SHARED
    abk_ksu.cc
    abk_ksu_jni.cc
)

target_include_directories(abkksu PRIVATE .)

find_library(log-lib log)
target_link_libraries(abkksu ${log-lib})
