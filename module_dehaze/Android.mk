LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
ifeq ($(strip $(TARGET_ARCH)),arm)
    LIB_PATH := libs/armeabi-v7a
else ifeq ($(strip $(TARGET_ARCH)),arm64)
    LIB_PATH := libs/arm64-v8a
endif

LIB_NAME := libDehaze
LOCAL_MODULE := $(LIB_NAME)
LOCAL_MODULE_CLASS := SHARED_LIBRARIES
LOCAL_MODULE_TAGS := optional
LOCAL_MULTILIB := both
LOCAL_MODULE_STEM_32 := $(LIB_NAME).so
LOCAL_MODULE_STEM_64 := $(LIB_NAME).so
LOCAL_SRC_FILES_32 := $(LIB_PATH)/$(LIB_NAME).so
LOCAL_SRC_FILES_64 := $(LIB_PATH)/$(LIB_NAME).so
include $(BUILD_PREBUILT)

#dehaze
include $(CLEAR_VARS)
LOCAL_SRC_FILES := jni/jni_sprd_dehaze.cpp
LOCAL_MODULE := libjni_sprd_dehaze
LOCAL_LDLIBS := -llog
LOCAL_SHARED_LIBRARIES := libDehaze
include $(BUILD_SHARED_LIBRARY)