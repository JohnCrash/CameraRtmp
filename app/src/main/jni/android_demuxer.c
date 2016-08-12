//
// Created by john on 2016/8/11.
//
#include "android_demuxer.h"
#if 1
    #include "libavutil/parseutils.h"
    #include "libavutil/pixdesc.h"
    #include "libavutil/opt.h"
    #include "libavformat/internal.h"
    #include "libavformat/riff.h"
    #include "libavdevice/avdevice.h"
    #include "libavcodec/raw.h"

    static int android_read_header(AVFormatContext *avctx)
    {
        int ret;
        struct android_camera_ctx * ctx;
        ctx = avctx->priv_data;

        ctx->video_codec_id = avctx->video_codec_id ? avctx->video_codec_id
                                                    : AV_CODEC_ID_RAWVIDEO;
        /*
         *  如果有点格式但是不等于AV_CODEC_ID_RAWVIDEO返回错误
         */
        if (ctx->pixel_format != AV_PIX_FMT_NONE) {
            if (ctx->video_codec_id != AV_CODEC_ID_RAWVIDEO) {
                av_log(avctx, AV_LOG_ERROR, "Pixel format may only be set when "
                        "video codec is not set or set to rawvideo\n");
                ret = AVERROR(EINVAL);
                return ret;
            }
        }
        return 0;
    }

    static int android_read_packet(AVFormatContext *s, AVPacket *pkt)
    {

    }

    static int android_read_close(AVFormatContext *s)
    {

    }

    #define OFFSET(x) offsetof(struct android_camera_ctx, x)
    #define DEC AV_OPT_FLAG_DECODING_PARAM
    static const AVOption options[] = {
            { "video_size", "set video size given a string such as 640x480.", OFFSET(requested_width), AV_OPT_TYPE_IMAGE_SIZE, {.str = NULL}, 0, 0, DEC },
            { "pixel_format", "set video pixel format", OFFSET(pixel_format), AV_OPT_TYPE_PIXEL_FMT, {.i64 = AV_PIX_FMT_NONE}, -1, INT_MAX, DEC },
            { "framerate", "set video frame rate", OFFSET(framerate), AV_OPT_TYPE_STRING, {.str = NULL}, 0, 0, DEC },
            { "sample_rate", "set audio sample rate", OFFSET(sample_rate), AV_OPT_TYPE_INT, {.i64 = 0}, 0, INT_MAX, DEC },
            { "sample_size", "set audio sample size", OFFSET(sample_size), AV_OPT_TYPE_INT, {.i64 = 0}, 0, 16, DEC },
            { "channels", "set number of audio channels, such as 1 or 2", OFFSET(channels), AV_OPT_TYPE_INT, {.i64 = 0}, 0, INT_MAX, DEC },
            { "list_devices", "list available devices",                      OFFSET(list_devices), AV_OPT_TYPE_BOOL, {.i64=0}, 0, 1, DEC },
            { "list_options", "list available options for specified device", OFFSET(list_options), AV_OPT_TYPE_BOOL, {.i64=0}, 0, 1, DEC },
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

#endif