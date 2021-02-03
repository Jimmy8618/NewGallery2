LOCAL_PATH := $(call my-dir)

#blur
include $(CLEAR_VARS)
LOCAL_SRC_FILES := blur/jni/sprdblur.cpp
LOCAL_MODULE := libjni_sprd_blur
LOCAL_LDLIBS := -llog
include $(BUILD_SHARED_LIBRARY)


#bokeh
include $(CLEAR_VARS)
ifeq ($(strip $(TARGET_ARCH)),arm)
    LIB_PATH := libs/armeabi-v7a
else ifeq ($(strip $(TARGET_ARCH)),arm64)
    LIB_PATH := libs/arm64-v8a
endif

LIB_NAME := libsprdsr
LOCAL_MODULE := $(LIB_NAME)
LOCAL_MODULE_CLASS := SHARED_LIBRARIES
LOCAL_MODULE_TAGS := optional
LOCAL_MULTILIB := both
LOCAL_MODULE_STEM_32 := $(LIB_NAME).so
LOCAL_MODULE_STEM_64 := $(LIB_NAME).so
LOCAL_SRC_FILES_32 := $(LIB_PATH)/$(LIB_NAME).so
LOCAL_SRC_FILES_64 := $(LIB_PATH)/$(LIB_NAME).so
include $(BUILD_PREBUILT)

include $(CLEAR_VARS)
LOCAL_SRC_FILES := bokeh/jni/sprdrealbokeh.cpp
LOCAL_MODULE := libjni_sprd_real_bokeh
LOCAL_LDLIBS := -llog
LOCAL_CFLAGS := -DSPRD_NEED_SR
LOCAL_SHARED_LIBRARIES := libsprdsr
include $(BUILD_SHARED_LIBRARY)