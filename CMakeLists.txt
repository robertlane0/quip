cmake_minimum_required(VERSION 3.20)
project(quip VERSION 1.0.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# libquip Header-only C++ Protocol Core Library
add_library(libquip INTERFACE)
target_include_directories(libquip INTERFACE
    $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/include>
    $<INSTALL_INTERFACE:include>
)

# Unit Tests
add_executable(quip_tests tests/test_protocol.cpp)
target_link_libraries(quip_tests PRIVATE libquip)

# Linux Host Driver (Linux only)
if(CMAKE_SYSTEM_NAME STREQUAL "Linux")
    add_executable(quip_host hosts/linux/quip_linux.cpp)
    target_link_libraries(quip_host PRIVATE libquip)
endif()
