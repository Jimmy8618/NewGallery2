cmake_minimum_required(VERSION 3.4.1)

set(CMAKE_LIBRARY_OUTPUT_DIRECTORY ${CMAKE_SOURCE_DIR}/../out/lib/${ANDROID_ABI})

set(CMAKE_C_FLAGS_RELEASE "${CMAKE_C_FLAGS_RELEASE} -s")

set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -s")

set(face_sources
        jni/com_android_gallery3d_v2_discover_people_FaceDetector.cpp
        )

set(face_includes
        jni/facealignapi.h
        jni/faceverifyapi.h
        jni/sprdfdapi.h
        )

find_library(
        log-lib
        log
)

add_library(libjni_sprd_facedetector SHARED
        ${face_sources}
        ${face_includes}
        )

add_library(align-lib SHARED IMPORTED)
set_target_properties(align-lib PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/libs/align/${ANDROID_ABI}/libjni_sprd_fa.so)

add_library(detect-lib SHARED IMPORTED)
set_target_properties(detect-lib PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/libs/detect/${ANDROID_ABI}/libjni_sprd_fd.so)

add_library(verify-lib SHARED IMPORTED)
set_target_properties(verify-lib PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/libs/verify/${ANDROID_ABI}/libjni_sprd_fv.so)

target_link_libraries(
        libjni_sprd_facedetector
        align-lib
        detect-lib
        verify-lib
        ${log-lib}
)

set_target_properties(libjni_sprd_facedetector PROPERTIES OUTPUT_NAME jni_sprd_facedetector)