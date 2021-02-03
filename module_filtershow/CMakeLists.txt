cmake_minimum_required(VERSION 3.4.1)

set(CMAKE_LIBRARY_OUTPUT_DIRECTORY ${CMAKE_SOURCE_DIR}/../out/lib/${ANDROID_ABI})

set(CMAKE_C_FLAGS_RELEASE "${CMAKE_C_FLAGS_RELEASE} -s")

set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -s")

set(filter_sources
        jni/bwfilter.c
        jni/contrast.c
        jni/edge.c
        jni/exposure.c
        jni/fx.c
        jni/geometry.c
        jni/gradient.c
        jni/highlight.c
        jni/hsv.c
        jni/hue.c
        jni/kmeans.cc
        jni/negative.c
        jni/redeye.c
        jni/redEyeMath.c
        jni/saturated.c
        jni/shadows.c
        jni/tinyplanet.cc
        jni/vibrance.c
        jni/wbalance.c
        )

set(filter_includes
        jni/filters.h
        jni/kmeans.h
        )

find_library(
        jnigraphics-lib
        jnigraphics
)

find_library(
        log-lib
        log
)

add_library(libsprdjni_filtershow_filters2 SHARED
        ${filter_sources}
        ${filter_includes}
        )

target_link_libraries(
        libsprdjni_filtershow_filters2
        ${jnigraphics-lib}
        ${log-lib}
)

set_target_properties(libsprdjni_filtershow_filters2 PROPERTIES OUTPUT_NAME sprdjni_filtershow_filters2)