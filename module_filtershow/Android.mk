LOCAL_PATH := $(call my-dir)

#filterShow
include $(CLEAR_VARS)
LOCAL_CPP_EXTENSION := .cc
LOCAL_SRC_FILES := \
        jni/bwfilter.c \
        jni/contrast.c \
        jni/edge.c \
        jni/exposure.c \
        jni/fx.c \
        jni/geometry.c \
        jni/gradient.c \
        jni/highlight.c \
        jni/hsv.c \
        jni/hue.c \
        jni/kmeans.cc \
        jni/negative.c \
        jni/redeye.c \
        jni/redEyeMath.c \
        jni/saturated.c \
        jni/shadows.c \
        jni/tinyplanet.cc \
        jni/vibrance.c \
        jni/wbalance.c
LOCAL_MODULE := libsprdjni_filtershow_filters2
LOCAL_LDLIBS := -llog -ljnigraphics
include $(BUILD_SHARED_LIBRARY)