LOCAL_PATH := $(call my-dir)

ifeq ($(strip $(GALLERY_PLATFORM_BUILD_SUPPORT)),true)
    $(warning 'support platform build, use platform mk')
    include $(LOCAL_PATH)/platform.mk
else
    $(warning 'not support platform build, use prebuilt mk')
    include $(LOCAL_PATH)/platform.mk
endif
