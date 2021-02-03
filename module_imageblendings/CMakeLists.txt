cmake_minimum_required(VERSION 3.4.1)

set(CMAKE_LIBRARY_OUTPUT_DIRECTORY ${CMAKE_SOURCE_DIR}/../out/lib/${ANDROID_ABI})

set(CMAKE_C_FLAGS_RELEASE "${CMAKE_C_FLAGS_RELEASE} -s")

set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -s")

set(imageblend_sources
        jni/jni_sprd_imageblending.cpp
        )

set(imageblend_includes
        jni/fb_extraction.h
        jni/ImageBlending.h
        jni/jni_sprd_imageblending.h
        )

find_library(
        log-lib
        log
)

add_library(libjni_sprd_imageblendings SHARED
        ${imageblend_sources}
        ${imageblend_includes}
        )

add_library(fbextraction-lib SHARED IMPORTED)
set_target_properties(fbextraction-lib PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/libs/${ANDROID_ABI}/libfbextraction.so)

add_library(imageblend-lib SHARED IMPORTED)
set_target_properties(imageblend-lib PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/libs/${ANDROID_ABI}/libimageblend.so)

target_link_libraries(
        libjni_sprd_imageblendings
        fbextraction-lib
        imageblend-lib
        ${log-lib}
)

set_target_properties(libjni_sprd_imageblendings PROPERTIES OUTPUT_NAME jni_sprd_imageblendings)