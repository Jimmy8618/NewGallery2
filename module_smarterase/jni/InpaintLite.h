/* Copyright 2017 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/

#ifndef TENSORFLOW_LITE_EXAMPLES_INPAINTLITE_H
#define TENSORFLOW_LITE_EXAMPLES_INPAINTLITE_H

#include <string>

namespace tflite {
    namespace InpaintLite {

//int InpaintLiteInit(void **handle,const std::string model_name,const int number_of_threads, const bool accel);
        int InpaintLiteInit(void **handle, char *model_name, const int number_of_threads,
                            const bool accel);

        int
        InpaintLiteRun(void *handle, const uint8_t *ori, const uint8_t *mask, const int image_width,
                       const int image_height,
                       const int image_channels, uint8_t *pred);

        int InpaintLiteDeInit(void *handle);

        int InpaintVersionInfoGet(void *a_pOutBuf, int a_dInBufMaxSize);
    }  // namespace InpaintLite
}  // namespace tflite

#endif  // TENSORFLOW_LITE_EXAMPLES_INPAINTLITE_H
