//
// Created by john on 2016/8/11.
//

#ifndef CAMERARTMP_ANDROID_DEMUXER_H
#define CAMERARTMP_ANDROID_DEMUXER_H

#include "libavdevice/avdevice.h"
#include "libavcodec/internal.h"

enum androidDeviceType {
    VideoDevice = 0,
    AudioDevice = 1,
};

struct android_camera_ctx{
    const AVClass * class;

    char *device_name[2];

    int   list_options;
    int   list_devices;

    int eof;

    enum AVPixelFormat pixel_format;
    enum AVCodecID video_codec_id;
    char *framerate;

    int requested_width;
    int requested_height;
    AVRational requested_framerate;

    int sample_rate;
    int sample_size;
    int channels;
};

#endif //CAMERARTMP_ANDROID_DEMUXER_H
