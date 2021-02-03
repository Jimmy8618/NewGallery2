cmake_minimum_required(VERSION 3.4.1)

set(bokeh_sources
        jni/sprdrealbokeh.cpp
        )

set(bokeh_includes
        jni/Compat.h
        jni/Errors.h
        jni/Mutex.h
        jni/SGM_SPRD.h
        jni/sprdbokeh.h
        jni/sprdrealbokeh.h
        jni/sr_interface.h
        jni/Timers.h
        )

find_library(
        log-lib
        log
)

add_definitions("-DSPRD_NEED_SR")

add_library(libjni_sprd_real_bokeh SHARED
        ${bokeh_sources}
        ${bokeh_includes}
        )

add_library(sprdsr-lib SHARED IMPORTED)
set_target_properties(sprdsr-lib PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/libs/${ANDROID_ABI}/libsprdsr.so)

target_link_libraries(
        libjni_sprd_real_bokeh
        sprdsr-lib
        ${log-lib}
)

set_target_properties(libjni_sprd_real_bokeh PROPERTIES OUTPUT_NAME jni_sprd_real_bokeh)