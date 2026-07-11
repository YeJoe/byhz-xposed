cmake_minimum_required(VERSION 3.18.1)
project("byhz_hook")

add_library(byhz_hook SHARED native_hook.c)

target_compile_options(byhz_hook PRIVATE -Wall -O2 -fvisibility=hidden)

target_link_libraries(byhz_hook
    android
    log
    dl
)
