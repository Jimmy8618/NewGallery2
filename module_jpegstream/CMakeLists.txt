cmake_minimum_required(VERSION 3.4.1)

set(CMAKE_LIBRARY_OUTPUT_DIRECTORY ${CMAKE_SOURCE_DIR}/../out/lib/${ANDROID_ABI})

set(CMAKE_C_FLAGS_RELEASE "${CMAKE_C_FLAGS_RELEASE} -s")

set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -s")

set(jpegstream_sources
        jni/inputstream_wrapper.cpp
        jni/jerr_hook.cpp
        jni/jpeg_hook.cpp
        jni/jpeg_reader.cpp
        jni/jpeg_writer.cpp
        jni/jpegstream.cpp
        jni/outputstream_wrapper.cpp
        jni/stream_wrapper.cpp
        )

set(jpegstream_includes
        jni/error_codes.h
        jni/inputstream_wrapper.h
        jni/jerr_hook.h
        jni/jni_defines.h
        jni/jpeg_config.h
        jni/jpeg_hook.h
        jni/jpeg_reader.h
        jni/jpeg_writer.h
        jni/outputstream_wrapper.h
        jni/stream_wrapper.h
        )

find_library(
        log-lib
        log
)

add_library(libsprdjni_jpegstream2 SHARED
        ${jpegstream_sources}
        ${jpegstream_includes}
        )

add_library(jpeg-lib SHARED IMPORTED)

set_target_properties(jpeg-lib PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/libs/${ANDROID_ABI}/libsprdjni_jpeg.so)

target_link_libraries(
        libsprdjni_jpegstream2
        jpeg-lib
        ${log-lib}
)

set_target_properties(libsprdjni_jpegstream2 PROPERTIES OUTPUT_NAME sprdjni_jpegstream2)