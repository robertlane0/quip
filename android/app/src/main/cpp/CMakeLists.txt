cmake_minimum_required(VERSION 3.20)
project("quip_jni" CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

get_filename_component(QUIP_ROOT_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../.." ABSOLUTE)
include_directories(${QUIP_ROOT_DIR}/include)

add_library(quip_jni SHARED quip_jni.cpp)

find_library(log-lib log)

target_link_libraries(quip_jni ${log-lib})
