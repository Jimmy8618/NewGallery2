LOCAL_PATH := $(call my-dir)

###################################################
include $(CLEAR_VARS)
LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES := glide-4.10.0:libraries/glide-4.10.0.jar
LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES += sprd_common_framework:libraries/sprd_common_framework.jar
LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES += eventbus-3.1.1:libraries/eventbus-3.1.1.jar
LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES += libandroid_tensorflow_inference_java:libraries/libandroid_tensorflow_inference_java.jar
LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES += tensorflow-lite-1.10.0:libraries/tensorflow-lite-1.10.0.aar
include $(BUILD_MULTI_PREBUILT)
###################################################

#################### Go/Discover ##################
include $(CLEAR_VARS)

#LOCAL_DEX_PREOPT := false

LOCAL_MODULE_TAGS := optional

APP_ALLOW_MISSING_DEPS := true

LOCAL_PACKAGE_NAME := NewGallery2

LOCAL_OVERRIDES_PACKAGES := Gallery Gallery3D GalleryNew3D Gallery2 DreamGallery2

LOCAL_PRIVATE_PLATFORM_APIS := true

LOCAL_PRIVILEGED_MODULE := true

LOCAL_PROGUARD_FLAG_FILES := proguard.flags

LOCAL_PROGUARD_ENABLED := disabled

LOCAL_JAVA_LIBRARIES := org.apache.http.legacy

LOCAL_STATIC_ANDROID_LIBRARIES := \
     androidx.legacy_legacy-support-v13 \
     androidx.legacy_legacy-support-v4 \
     androidx.recyclerview_recyclerview \
     androidx.appcompat_appcompat \
     com.google.android.material_material \
     androidx.exifinterface_exifinterface

LOCAL_STATIC_JAVA_LIBRARIES := \
     xmp_toolkit \
     mp4parser \
     glide-4.10.0 \
     sprd_common_framework \
     eventbus-3.1.1 \
     libandroid_tensorflow_inference_java

LOCAL_STATIC_JAVA_AAR_LIBRARIES := \
     tensorflow-lite-1.10.0

LOCAL_SRC_FILES := \
     $(call all-java-files-under, src) \
     $(call all-renderscript-files-under, src) \
     $(call all-java-files-under, module_gallerycommon/src/main/java) \
     $(call all-java-files-under, module_bokeh/src/main/java) \
     $(call all-java-files-under, libraries/ext/src)

LOCAL_SRC_FILES += src/com/sprd/gallery3d/aidl/IFloatWindowController.aidl

LOCAL_RESOURCE_DIR := \
    $(LOCAL_PATH)/res

ifeq ($(strip $(PRODUCT_GO_DEVICE)), true)
    LOCAL_MANIFEST_FILE := app_go/src/main/AndroidManifest.xml
    LOCAL_RESOURCE_DIR += $(LOCAL_PATH)/app_go/src/main/res
else
    LOCAL_MANIFEST_FILE := app_discover/src/main/AndroidManifest.xml
    LOCAL_RESOURCE_DIR += $(LOCAL_PATH)/app_discover/src/main/res
endif

LOCAL_JNI_SHARED_LIBRARIES := \
     libsprdjni_eglfence2 \
     libsprdjni_filtershow_filters2 \
     libsprdjni_jpegstream2 \
     libjni_sprd_dehaze \
     libjni_sprd_facedetector \
     libjni_sprd_smarterase

ifneq ($(findstring $(TARGET_BOARD_BOKEH_MODE_SUPPORT), true sbs),)
LOCAL_JNI_SHARED_LIBRARIES += \
     libjni_sprd_real_bokeh \
     libjni_sprd_imageblendings
endif

ifeq ($(strip $(TARGET_BOARD_BLUR_MODE_SUPPORT)),true)
LOCAL_JNI_SHARED_LIBRARIES += \
     libjni_sprd_blur
endif

include $(BUILD_PACKAGE)
#################### Go/Discover ##################

####################### GMS #######################
include $(CLEAR_VARS)

#LOCAL_DEX_PREOPT := false

LOCAL_MODULE_TAGS := optional

APP_ALLOW_MISSING_DEPS := true

LOCAL_PACKAGE_NAME := USCPhotoEdit

LOCAL_OVERRIDES_PACKAGES := Gallery Gallery3D GalleryNew3D Gallery2 DreamGallery2 NewGallery2

LOCAL_PRIVATE_PLATFORM_APIS := true

LOCAL_PRIVILEGED_MODULE := true

LOCAL_PROGUARD_FLAG_FILES := proguard.flags

LOCAL_PROGUARD_ENABLED := disabled

LOCAL_JAVA_LIBRARIES := org.apache.http.legacy

LOCAL_STATIC_ANDROID_LIBRARIES := \
     androidx.legacy_legacy-support-v13 \
     androidx.legacy_legacy-support-v4 \
     androidx.recyclerview_recyclerview \
     androidx.appcompat_appcompat \
     com.google.android.material_material \
     androidx.exifinterface_exifinterface

LOCAL_STATIC_JAVA_LIBRARIES := \
     xmp_toolkit \
     mp4parser \
     glide-4.10.0 \
     sprd_common_framework \
     eventbus-3.1.1 \
     libandroid_tensorflow_inference_java

LOCAL_STATIC_JAVA_AAR_LIBRARIES := \
     tensorflow-lite-1.10.0

LOCAL_SRC_FILES := \
     $(call all-java-files-under, src) \
     $(call all-renderscript-files-under, src) \
     $(call all-java-files-under, module_gallerycommon/src/main/java) \
     $(call all-java-files-under, module_bokeh/src/main/java) \
     $(call all-java-files-under, libraries/ext/src)

LOCAL_SRC_FILES += src/com/sprd/gallery3d/aidl/IFloatWindowController.aidl

LOCAL_RESOURCE_DIR := \
    $(LOCAL_PATH)/res

LOCAL_MANIFEST_FILE := app_gms/src/main/AndroidManifest.xml
LOCAL_RESOURCE_DIR += $(LOCAL_PATH)/app_gms/src/main/res

LOCAL_JNI_SHARED_LIBRARIES := \
     libsprdjni_eglfence2 \
     libsprdjni_filtershow_filters2 \
     libsprdjni_jpegstream2 \
     libjni_sprd_dehaze \
     libjni_sprd_facedetector \
     libjni_sprd_smarterase

ifneq ($(findstring $(TARGET_BOARD_BOKEH_MODE_SUPPORT), true sbs),)
LOCAL_JNI_SHARED_LIBRARIES += \
     libjni_sprd_real_bokeh \
     libjni_sprd_imageblendings
endif

ifeq ($(strip $(TARGET_BOARD_BLUR_MODE_SUPPORT)),true)
LOCAL_JNI_SHARED_LIBRARIES += \
     libjni_sprd_blur
endif

include $(BUILD_PACKAGE)
####################### GMS #######################

include $(call all-makefiles-under, $(LOCAL_PATH))