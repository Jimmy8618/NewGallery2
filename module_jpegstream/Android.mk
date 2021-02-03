LOCAL_PATH := $(call my-dir)

#jpegstream
include $(CLEAR_VARS)
ifeq ($(strip $(TARGET_ARCH)),arm)
    LIB_PATH := libs/armeabi-v7a
else ifeq ($(strip $(TARGET_ARCH)),arm64)
    LIB_PATH := libs/arm64-v8a
endif

LIB_NAME := libsprdjni_jpeg
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
LOCAL_SRC_FILES := \
        jni/inputstream_wrapper.cpp \
        jni/jerr_hook.cpp \
        jni/jpeg_hook.cpp \
        jni/jpeg_reader.cpp \
        jni/jpeg_writer.cpp \
        jni/jpegstream.cpp \
        jni/outputstream_wrapper.cpp \
        jni/stream_wrapper.cpp
LOCAL_MODULE := libsprdjni_jpegstream2
LOCAL_LDLIBS := -llog
LOCAL_SHARED_LIBRARIES := libsprdjni_jpeg
include $(BUILD_SHARED_LIBRARY)