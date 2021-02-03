#!/bin/sh

#连so一起编译出来
######################### build ###########################

./gradlew clean
./gradlew assembleRelease

####################### copy libs #########################
for arch in arm64-v8a armeabi-v7a
do
    cp module_dehaze/libs/$arch/libDehaze.so                    prebuilt/lib/$arch/
    cp module_imageblendings/libs/$arch/libfbextraction.so      prebuilt/lib/$arch/
    cp module_imageblendings/libs/$arch/libimageblend.so        prebuilt/lib/$arch/
    cp module_facedetector/libs/align/$arch/libjni_sprd_fa.so   prebuilt/lib/$arch/
    cp module_facedetector/libs/detect/$arch/libjni_sprd_fd.so  prebuilt/lib/$arch/
    cp module_facedetector/libs/verify/$arch/libjni_sprd_fv.so  prebuilt/lib/$arch/
    cp module_jpegstream/libs/$arch/libsprdjni_jpeg.so          prebuilt/lib/$arch/
    cp module_bokeh/libs/$arch/libsprdsr.so                     prebuilt/lib/$arch/
    cp app/src/main/libs/$arch/libtensorflowlite_jni.so         prebuilt/lib/$arch/
    cp module_smarterase/libs/$arch/libInpaintLite.so           prebuilt/lib/$arch/

    cp out/lib/$arch/libjni_sprd_blur.so                        prebuilt/lib/$arch/
    cp out/lib/$arch/libjni_sprd_dehaze.so                      prebuilt/lib/$arch/
    cp out/lib/$arch/libjni_sprd_facedetector.so                prebuilt/lib/$arch/
    cp out/lib/$arch/libjni_sprd_imageblendings.so              prebuilt/lib/$arch/
    cp out/lib/$arch/libjni_sprd_real_bokeh.so                  prebuilt/lib/$arch/
    cp out/lib/$arch/libsprdjni_eglfence2.so                    prebuilt/lib/$arch/
    cp out/lib/$arch/libsprdjni_filtershow_filters2.so          prebuilt/lib/$arch/
    cp out/lib/$arch/libsprdjni_jpegstream2.so                  prebuilt/lib/$arch/
    cp out/lib/$arch/libjni_sprd_smarterase.so                  prebuilt/lib/$arch/
done

printf "release so success!!!\n"

#不编译so, so不打包进apk中
######################### build ###########################

./gradlew clean
./gradlew assembleRelease -Pignoreso=true

####################### copy apks #########################

cp app_discover/build/outputs/apk/release/NewGallery2.apk       prebuilt/apk/app_discover/
cp app_gms/build/outputs/apk/release/USCPhotoEdit.apk           prebuilt/apk/app_gms/
cp app_go/build/outputs/apk/release/NewGallery2.apk             prebuilt/apk/app_go/

printf "release apk success!!!\n"