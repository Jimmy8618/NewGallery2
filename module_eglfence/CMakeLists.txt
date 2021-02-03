cmake_minimum_required(VERSION 3.4.1)

set(CMAKE_LIBRARY_OUTPUT_DIRECTORY ${CMAKE_SOURCE_DIR}/../out/lib/${ANDROID_ABI})

set(CMAKE_C_FLAGS_RELEASE "${CMAKE_C_FLAGS_RELEASE} -s")

set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -s")

set(egl_sources
        jni/jni_egl_fence.cpp
        )

set(egl_includes
        jni/jni_egl_fence.h
        )

find_library(
        EGL-lib
        EGL
)

find_library(
        log-lib
        log
)

add_definitions("-DEGL_EGLEXT_PROTOTYPES")

add_library(libsprdjni_eglfence2 SHARED
        ${egl_sources}
        ${egl_includes}
        )

target_link_libraries(
        libsprdjni_eglfence2
        ${EGL-lib}
        ${log-lib}
)

set_target_properties(libsprdjni_eglfence2 PROPERTIES OUTPUT_NAME sprdjni_eglfence2)