cmake_minimum_required(VERSION 3.4.1)

set(CMAKE_LIBRARY_OUTPUT_DIRECTORY ${CMAKE_SOURCE_DIR}/../out/lib/${ANDROID_ABI})

set(CMAKE_C_FLAGS_RELEASE "${CMAKE_C_FLAGS_RELEASE} -s")

set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -s")

set(smarterase_sources
        jni/jni_sprd_smarterase.cpp
        )

set(smarterase_includes
        jni/InpaintLite.h
        jni/jni_sprd_smarterase.h
        )

find_library(
        log-lib
        log
)

add_library(libjni_sprd_smarterase SHARED
        ${smarterase_sources}
        ${smarterase_includes}
        )

add_library(inpaintLite-lib SHARED IMPORTED)

set_target_properties(inpaintLite-lib PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/libs/${ANDROID_ABI}/libInpaintLite.so)

target_link_libraries(
        libjni_sprd_smarterase
        inpaintLite-lib
        ${log-lib}
)

set_target_properties(libjni_sprd_smarterase PROPERTIES OUTPUT_NAME jni_sprd_smarterase)