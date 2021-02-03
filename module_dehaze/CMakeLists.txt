cmake_minimum_required(VERSION 3.4.1)

set(CMAKE_LIBRARY_OUTPUT_DIRECTORY ${CMAKE_SOURCE_DIR}/../out/lib/${ANDROID_ABI})

set(CMAKE_C_FLAGS_RELEASE "${CMAKE_C_FLAGS_RELEASE} -s")

set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -s")

set(dehaze_sources
        jni/jni_sprd_dehaze.cpp
        )

set(dehaze_includes
        jni/data_type.h
        jni/dehaze_interface.h
        jni/jni_sprd_dehaze.h
        )

find_library(
        log-lib
        log
)

add_library(libjni_sprd_dehaze SHARED
        ${dehaze_sources}
        ${dehaze_includes}
        )

add_library(dehaze-lib SHARED IMPORTED)

set_target_properties(dehaze-lib PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/libs/${ANDROID_ABI}/libDehaze.so)

target_link_libraries(
        libjni_sprd_dehaze
        dehaze-lib
        ${log-lib}
)

set_target_properties(libjni_sprd_dehaze PROPERTIES OUTPUT_NAME jni_sprd_dehaze)