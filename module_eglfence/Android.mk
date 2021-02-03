LOCAL_PATH := $(call my-dir)

#egl_fence
include $(CLEAR_VARS)
LOCAL_SRC_FILES := jni/jni_egl_fence.cpp
LOCAL_MODULE := libsprdjni_eglfence2
LOCAL_LDLIBS := -llog -lEGL
LOCAL_CFLAGS := -DEGL_EGLEXT_PROTOTYPES
include $(BUILD_SHARED_LIBRARY)