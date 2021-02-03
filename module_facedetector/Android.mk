LOCAL_PATH := $(call my-dir)

#facedetect
include $(CLEAR_VARS)
ifeq ($(strip $(TARGET_ARCH)),arm)
    LIB_PATH := libs/align/armeabi-v7a
else ifeq ($(strip $(TARGET_ARCH)),arm64)
    LIB_PATH := libs/align/arm64-v8a
endif

LIB_NAME := libjni_sprd_fa
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
ifeq ($(strip $(TARGET_ARCH)),arm)
    LIB_PATH := libs/detect/armeabi-v7a
else ifeq ($(strip $(TARGET_ARCH)),arm64)
    LIB_PATH := libs/detect/arm64-v8a
endif

LIB_NAME := libjni_sprd_fd
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
ifeq ($(strip $(TARGET_ARCH)),arm)
    LIB_PATH := libs/verify/armeabi-v7a
else ifeq ($(strip $(TARGET_ARCH)),arm64)
    LIB_PATH := libs/verify/arm64-v8a
endif

LIB_NAME := libjni_sprd_fv
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
LOCAL_SRC_FILES := jni/com_android_gallery3d_v2_discover_people_FaceDetector.cpp
LOCAL_MODULE := libjni_sprd_facedetector
LOCAL_LDLIBS := -llog
LOCAL_SHARED_LIBRARIES := libjni_sprd_fa libjni_sprd_fd libjni_sprd_fv
include $(BUILD_SHARED_LIBRARY)