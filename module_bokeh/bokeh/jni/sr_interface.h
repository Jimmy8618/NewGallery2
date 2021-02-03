#ifndef __SPRD_SR_INTERFACE_H__
#define __SPRD_SR_INTERFACE_H__

#ifdef __cplusplus
extern "C" {
#endif
void *sprd_sr_init(int width, int height, int threadnum); // threadnum -> [1,4]
int sprd_sr_deinit(void *handle);
int sprd_sr_process(void *handle, unsigned char *input /*, unsigned char* output*/);


#ifdef __cplusplus
}
#endif

#endif
