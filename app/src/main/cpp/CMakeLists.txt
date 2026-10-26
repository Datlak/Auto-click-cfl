cmake_minimum_required(VERSION 3.22.1)

project("panda_touch_engine")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include_directories(include)

add_library(
    touch_engine
    SHARED
    touch_injector.cpp
    touch_jni.cpp
)

find_library(log-lib log)

target_link_libraries(
    touch_engine
    ${log-lib}
)
