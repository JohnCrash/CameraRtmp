//
// Created by john on 2016/8/11.
//
#include "android_demuxer.h"

    #include "libavutil/parseutils.h"
    #include "libavutil/pixdesc.h"
    #include "libavutil/opt.h"
    #include "libavformat/internal.h"
    #include "libavformat/riff.h"
    #include "libavdevice/avdevice.h"
    #include "libavcodec/raw.h"

    struct android_camera_ctx{

    };

    static int android_read_header(AVFormatContext *avctx)
    {
        return 0;
    }

    static int android_read_packet(AVFormatContext *s, AVPacket *pkt)
    {

    }

    static int android_read_close(AVFormatContext *s)
    {

    }

    static const AVOption options[] = {
            { NULL },
    };

    static const AVClass android_class = {
            .class_name = "android indev",
            .item_name  = av_default_item_name,
            .option     = options,
            .version    = LIBAVUTIL_VERSION_INT,
            .category   = AV_CLASS_CATEGORY_DEVICE_VIDEO_INPUT,
    };

    AVInputFormat ff_dshow_demuxer = {
            .name           = "androidCamera",
            .long_name      = NULL_IF_CONFIG_SMALL("Android camera"),
            .priv_data_size = sizeof(struct android_camera_ctx),
            .read_header    = android_read_header,
            .read_packet    = android_read_packet,
            .read_close     = android_read_close,
            .flags          = AVFMT_NOFILE,
            .priv_class     = &android_class,
    };

