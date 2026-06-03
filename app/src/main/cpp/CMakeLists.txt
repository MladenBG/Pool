# CMake configuration for Magics Pool 8 Engine
cmake_minimum_required(VERSION 3.22.1)

project("magicspool")

add_library(
        magicspool
        SHARED
        native-lib.cpp)

find_library(
        log-lib
        log)

target_link_libraries(
        magicspool
        ${log-lib})
