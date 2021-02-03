LOCAL_PATH := $(call my-dir)

###################### 编译 NewGallery2 #####################
include $(CLEAR_VARS)

#模块名
LOCAL_MODULE := NewGallery2

#覆盖掉的应用
LOCAL_OVERRIDES_PACKAGES := Gallery Gallery3D GalleryNew3D Gallery2 DreamGallery2

#应用
LOCAL_MODULE_CLASS := APPS

#允许使用系统隐藏接口
LOCAL_PRIVATE_PLATFORM_APIS := true

#签名
LOCAL_CERTIFICATE := platform

#编译后的输出目录
LOCAL_MODULE_PATH := $(TARGET_OUT)/priv-app

#预置的apk
ifeq ($(strip $(PRODUCT_GO_DEVICE)), true)
#go版本
    LOCAL_SRC_FILES := apk/app_go/NewGallery2.apk
else
#discover版本
    LOCAL_SRC_FILES := apk/app_discover/NewGallery2.apk
endif

#预置的so
ifeq ($(TARGET_ARCH), $(filter $(TARGET_ARCH), arm64))
    LIB_PATH := lib/arm64-v8a
else ifeq ($(TARGET_ARCH), $(filter $(TARGET_ARCH), arm))
    LIB_PATH := lib/armeabi-v7a
else ifeq ($(TARGET_ARCH), $(filter $(TARGET_ARCH), x86_64))
    LIB_PATH := lib/x86_64
else ifeq ($(TARGET_ARCH), $(filter $(TARGET_ARCH), x86))
    LIB_PATH := lib/x86
endif

LOCAL_PREBUILT_JNI_LIBS := \
    $(LIB_PATH)/libDehaze.so \
    $(LIB_PATH)/libfbextraction.so \
    $(LIB_PATH)/libimageblend.so \
    $(LIB_PATH)/libjni_sprd_dehaze.so \
    $(LIB_PATH)/libjni_sprd_facedetector.so \
    $(LIB_PATH)/libjni_sprd_fa.so \
    $(LIB_PATH)/libjni_sprd_fd.so \
    $(LIB_PATH)/libjni_sprd_fv.so \
    $(LIB_PATH)/libsprdjni_eglfence2.so \
    $(LIB_PATH)/libsprdjni_filtershow_filters2.so \
    $(LIB_PATH)/libsprdjni_jpeg.so \
    $(LIB_PATH)/libsprdjni_jpegstream2.so \
    $(LIB_PATH)/libsprdsr.so \
    $(LIB_PATH)/libtensorflowlite_jni.so \
    $(LIB_PATH)/libInpaintLite.so \
    $(LIB_PATH)/libjni_sprd_smarterase.so

ifneq ($(findstring $(TARGET_BOARD_BOKEH_MODE_SUPPORT), true sbs),)
LOCAL_PREBUILT_JNI_LIBS += \
    $(LIB_PATH)/libjni_sprd_real_bokeh.so \
    $(LIB_PATH)/libjni_sprd_imageblendings.so
endif

ifeq ($(strip $(TARGET_BOARD_BLUR_MODE_SUPPORT)),true)
LOCAL_PREBUILT_JNI_LIBS += \
    $(LIB_PATH)/libjni_sprd_blur.so
endif

include $(BUILD_PREBUILT)

###################### 编译 gms #############################
include $(CLEAR_VARS)

#模块名
LOCAL_MODULE := USCPhotoEdit

#覆盖掉的应用
LOCAL_OVERRIDES_PACKAGES := Gallery Gallery3D GalleryNew3D Gallery2 DreamGallery2 NewGallery2

#应用
LOCAL_MODULE_CLASS := APPS

#允许使用系统隐藏接口
LOCAL_PRIVATE_PLATFORM_APIS := true

#签名
LOCAL_CERTIFICATE := platform

#编译后的输出目录
LOCAL_MODULE_PATH := $(TARGET_OUT)/priv-app

#预置的apk
#gms版本
LOCAL_SRC_FILES := apk/app_gms/USCPhotoEdit.apk

#预置的so
ifeq ($(TARGET_ARCH), $(filter $(TARGET_ARCH), arm64))
    LIB_PATH := lib/arm64-v8a
else ifeq ($(TARGET_ARCH), $(filter $(TARGET_ARCH), arm))
    LIB_PATH := lib/armeabi-v7a
else ifeq ($(TARGET_ARCH), $(filter $(TARGET_ARCH), x86_64))
    LIB_PATH := lib/x86_64
else ifeq ($(TARGET_ARCH), $(filter $(TARGET_ARCH), x86))
    LIB_PATH := lib/x86
endif

LOCAL_PREBUILT_JNI_LIBS := \
    $(LIB_PATH)/libDehaze.so \
    $(LIB_PATH)/libfbextraction.so \
    $(LIB_PATH)/libimageblend.so \
    $(LIB_PATH)/libjni_sprd_dehaze.so \
    $(LIB_PATH)/libjni_sprd_facedetector.so \
    $(LIB_PATH)/libjni_sprd_fa.so \
    $(LIB_PATH)/libjni_sprd_fd.so \
    $(LIB_PATH)/libjni_sprd_fv.so \
    $(LIB_PATH)/libsprdjni_eglfence2.so \
    $(LIB_PATH)/libsprdjni_filtershow_filters2.so \
    $(LIB_PATH)/libsprdjni_jpeg.so \
    $(LIB_PATH)/libsprdjni_jpegstream2.so \
    $(LIB_PATH)/libsprdsr.so \
    $(LIB_PATH)/libtensorflowlite_jni.so \
    $(LIB_PATH)/libInpaintLite.so \
    $(LIB_PATH)/libjni_sprd_smarterase.so

ifneq ($(findstring $(TARGET_BOARD_BOKEH_MODE_SUPPORT), true sbs),)
LOCAL_PREBUILT_JNI_LIBS += \
    $(LIB_PATH)/libjni_sprd_real_bokeh.so \
    $(LIB_PATH)/libjni_sprd_imageblendings.so
endif

ifeq ($(strip $(TARGET_BOARD_BLUR_MODE_SUPPORT)),true)
LOCAL_PREBUILT_JNI_LIBS += \
    $(LIB_PATH)/libjni_sprd_blur.so
endif

include $(BUILD_PREBUILT)