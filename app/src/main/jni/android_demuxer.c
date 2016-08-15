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

#include "android_camera.h"

static int parse_device_name(AVFormatContext *avctx)
{
    struct android_camera_ctx *ctx = avctx->priv_data;
    char **device_name = ctx->device_name;
    char *name = av_strdup(avctx->filename);
    char *tmp = name;
    int ret = 1;
    char *type;

    while ((type = strtok(tmp, "="))) {
        char *token = strtok(NULL, ":");
        tmp = NULL;

        if        (!strcmp(type, "video")) {
            device_name[0] = token;
        } else if (!strcmp(type, "audio")) {
            device_name[1] = token;
        } else {
            device_name[0] = NULL;
            device_name[1] = NULL;
            break;
        }
    }

    if (!device_name[0] && !device_name[1]) {
        ret = 0;
    } else {
        if (device_name[0])
            device_name[0] = av_strdup(device_name[0]);
        if (device_name[1])
            device_name[1] = av_strdup(device_name[1]);
    }

    av_free(name);
    return ret;
}

/*
 * 枚举android摄像头信息，并通过AV_LOG_INFO传回
 */
static int
android_cycle_devices(AVFormatContext *avctx,enum androidDeviceType devtype)
{
    struct android_camera_ctx *ctx = avctx->priv_data;
    const char *device_name = ctx->device_name[devtype];
    const char *devtypename = (devtype == VideoDevice) ? "video" : "audio only";

    if(devtype==VideoDevice){
        int n = android_getNumberOfCameras();
        for(int i=0;i<n;i++) {
            av_log(avctx, AV_LOG_INFO, " \"camera_%d\"\n", i);
            av_log(avctx, AV_LOG_INFO, "    Alternative name \"camera_%d\"\n", i);
        }
    }
    else if(devtype==AudioDevice){
        av_log(avctx, AV_LOG_INFO, " \"microphone\"\n");
        av_log(avctx, AV_LOG_INFO, "    Alternative name \"microphone\"\n");
    }
    else{
        return AVERROR(EIO);
    }
    return 0;
}

/*
 * 枚举摄像头信息，通过AV_LOG_INFO传回
 */
static int
android_list_device_options(AVFormatContext *avctx,enum androidDeviceType devtype)
{
    return 0;
}

static int android_read_header(AVFormatContext *avctx)
{
    int ret = AVERROR(EIO);
    struct android_camera_ctx * ctx;
    ctx = avctx->priv_data;

    if (!ctx->list_devices && !parse_device_name(avctx)) {
        av_log(avctx, AV_LOG_ERROR, "Malformed android_camera input string.\n");
        return ret;
    }
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
    if (ctx->framerate) {
        ret = av_parse_video_rate(&ctx->requested_framerate, ctx->framerate);
        if (ret < 0) {
            av_log(avctx, AV_LOG_ERROR, "Could not parse framerate '%s'.\n", ctx->framerate);
            return ret;
        }
    }
    /*
     * 枚举设备
     */
    if (ctx->list_devices) {
        av_log(avctx, AV_LOG_INFO, "Android camera devices (some may be both video and audio devices)\n");
        android_cycle_devices(avctx, VideoDevice);
        av_log(avctx, AV_LOG_INFO, "Android audio devices\n");
        android_cycle_devices(avctx, AudioDevice);
        ret = AVERROR_EXIT;
        return ret;
    }
    /*
     * 枚举设备参数
     */
    if (ctx->list_options) {
        if (ctx->device_name[VideoDevice])
        if ((ret = android_list_device_options(avctx, VideoDevice))) {
            return ret;
        }
        if (ctx->device_name[AudioDevice]) {
            if (android_list_device_options(avctx, AudioDevice)) {
                /* show audio options from combined video+audio sources as fallback */
                if ((ret = android_list_device_options(avctx, AudioDevice))) {
                    return ret;
                }
            }
        }
    }
    /*
     * 打开android摄像头设备和录音设备
     */
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
