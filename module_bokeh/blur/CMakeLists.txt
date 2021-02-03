cmake_minimum_required(VERSION 3.4.1)

set(blur_sources
        jni/sprdblur.cpp
        )

set(blur_includes
        jni/Bokeh2Frames_interface.h
        jni/commonblur.h
        jni/iSmooth.h
        jni/twoframeblur.h
        jni/Compat.h
        jni/Errors.h
        jni/Mutex.h
        jni/Timers.h
        )

find_library(
        log-lib
        log
)

add_library(libjni_sprd_blur SHARED
        ${blur_sources}
        ${blur_includes}
        )

target_link_libraries(
        libjni_sprd_blur
        ${log-lib}
)

set_target_properties(libjni_sprd_blur PROPERTIES OUTPUT_NAME jni_sprd_blur)