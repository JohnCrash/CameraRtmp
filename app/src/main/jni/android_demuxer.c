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

        if(!strcmp(type, "video")) {
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
#define MAXCAPS 32*1024

static int parse_device_id(const char *device_name)
{
    if(device_name) {
        int len = strlen(device_name);
        char c = device_name[len-1];
        if(c >= '0' && c <= '9')
            return c-'0';
    }
    return -1;
}

static int
android_list_device_options(AVFormatContext *avctx,enum androidDeviceType devtype)
{
    struct android_camera_ctx *ctx = avctx->priv_data;
    const char *device_name = ctx->device_name[devtype];
    int deviceId;

    deviceId = parse_device_id(device_name);
    if(deviceId<0){
        av_log(avctx, AV_LOG_ERROR, "android_list_device_options device name:%s\n", deviceId);
        return AVERROR(EIO);
    }
    if(devtype==VideoDevice){
        int n = android_getNumberOfCameras();
        int* pinfo = malloc(MAXCAPS*sizeof(int));
        for(int i=0;i<n;i++) {
            if(i==deviceId) {
                int ncount = android_getCameraCapabilityInteger(deviceId, pinfo, MAXCAPS);
                if (ncount > 0) {
                    av_log(avctx, AV_LOG_INFO, "face : %s\n", pinfo[0]==1?"front":"back");
                    av_log(avctx, AV_LOG_INFO, "orientation : %d\n", pinfo[1]);
                    int off1 = pinfo[2]*2+2+1;
                    int off2 = off1+pinfo[off1]+1;
                    int min_fps = 100;
                    int max_fps = 0;
                    /*
                     * 摄像头支持的帧率
                     */
                    for(int s=0;s<pinfo[off2];s++){
                        int fps =  pinfo[off2+s+1];
                        av_log(avctx, AV_LOG_INFO,"   framerate: %d \n",fps);
                        if( min_fps > fps )
                            min_fps = fps;
                        if( max_fps < fps )
                            max_fps = fps;
                    }
                    /*
                     * 摄像头支持的格式,FLEX_RGBA_8888,FLEX_RGB_888,JPEG,NV16,YUV_420_888,YUV_422_888
                     * YUV_444_888,YUY2,YV12,RGB_565,RAW10,RAW12,NV21
                     */
                    for(int k=0;k<pinfo[off1];k++){
                        const char *formatName = android_ImageFormatName( pinfo[k+off1+1] );
                        if(formatName)
                            av_log(avctx, AV_LOG_INFO,"  pixel_format=%s\n",formatName);
                        else
                            av_log(avctx, AV_LOG_INFO,"    pixel_format=%s\n",pinfo[k+off1+1]);
                        /*
                         * 格式和尺寸的组合
                         */
                        for(int j=0;j<pinfo[2];j++){
                            int w = pinfo[3+2*j];
                            int h = pinfo[3+2*j+1];
                            av_log(avctx, AV_LOG_INFO, "  min s=%ldx%ld fps=%g max s=%ldx%ld fps=%g\n",
                                   w,h,min_fps,w,h,max_fps);
                        }
                    }
                } else {
                    av_log(avctx, AV_LOG_ERROR,
                           "android_getCameraCapabilityInteger(%d) return %d\n", i, ncount);
                    return AVERROR(EIO);
                }
            }
        }
        free(pinfo);
    }
    else if(devtype==AudioDevice){
        /*
         * 固定的参数
         */
        av_log(avctx, AV_LOG_INFO, "  min ch=%lu bits=%lu rate=%6lu max ch=%lu bits=%lu rate=%6lu\n",
               1,8,22050,2,16,44100);
    }
    else{
        return AVERROR(EIO);
    }
    return 0;
}

static int android_grab_buffer(int type,void * bufObj,int size,unsigned char * buf,
                               int fmt,int w,int h,int64_t timestramp)
{
    return 0;
}

static enum AVCodecID waveform_codec_id(enum AVSampleFormat sample_fmt)
{
    switch (sample_fmt) {
        case AV_SAMPLE_FMT_U8:  return AV_CODEC_ID_PCM_U8;
        case AV_SAMPLE_FMT_S16: return AV_CODEC_ID_PCM_S16LE;
        case AV_SAMPLE_FMT_S32: return AV_CODEC_ID_PCM_S32LE;
        default:                return AV_CODEC_ID_NONE; /* Should never happen. */
    }
}
static enum AVSampleFormat waveform_format(int width)
{
    switch(width){
        case 8:return AV_SAMPLE_FMT_U8;
        case 16:return AV_SAMPLE_FMT_S16;
        case 32:return AV_SAMPLE_FMT_S32;
    }
    return AV_SAMPLE_FMT_NONE;
}
/*
 * 加入设备
 */
static int add_device(AVFormatContext *avctx,enum androidDeviceType devtype)
{
    int ret = AVERROR(EIO);
   // AM_MEDIA_TYPE type;
    AVCodecParameters *par;
    AVStream *st;

    struct android_camera_ctx *ctx = avctx->priv_data;
    st = avformat_new_stream(avctx, NULL);
    if (!st) {
        ret = AVERROR(ENOMEM);
        return ret;
    }
    st->id = devtype;

    ctx->stream_index[devtype] = st->index;
    par = st->codecpar;
    if(devtype==VideoDevice){
        AVRational time_base;

        st->avg_frame_rate = av_inv_q(time_base);
        st->r_frame_rate = av_inv_q(time_base);
        par->codec_type = AVMEDIA_TYPE_VIDEO;
     //   par->width      = bih->biWidth;
     //   par->height     = bih->biHeight;
     //   par->codec_tag  = bih->biCompression;
     //   par->format     = dshow_pixfmt(bih->biCompression, bih->biBitCount);
    } else{
        par->codec_type  = AVMEDIA_TYPE_AUDIO;
        par->format      = waveform_format(ctx->sample_format);
        par->codec_id    = waveform_codec_id(par->format);
        par->sample_rate = ctx->sample_rate;
        par->channels    = ctx->channels;
    }

    avpriv_set_pts_info(st, 64, 1, 10000000);

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
    int iDevice = parse_device_id(ctx->device_name[VideoDevice]);
    if(iDevice>=0) {
        android_setDemuxerCallback(android_grab_buffer);
        av_log(avctx,AV_LOG_INFO,"open android camera,oes=%d,dev=%d,w=%d,h=%d,pixfmt=%d,fps=%d,ch=%d,bits=%d,rate=%d",
               ctx->oes_texture, iDevice, ctx->requested_width,
               ctx->requested_height,
               ctx->pixel_format, av_q2d(ctx->requested_framerate),
               ctx->channels, 16, ctx->sample_rate);
        int result = android_openDemuxer(ctx->oes_texture, iDevice, ctx->requested_width,
                                         ctx->requested_height,
                                         ctx->pixel_format, av_q2d(ctx->requested_framerate),
                                         ctx->channels, ctx->sample_format, ctx->sample_rate);
        if(!result){
            av_log(avctx, AV_LOG_ERROR, "android_openDemuxer return %d\n",result);
            return ret;
        }
        ret = add_device(avctx,VideoDevice);
        if(ret<0){
            av_log(avctx, AV_LOG_ERROR, "add_device return %d\n",ret);
            return ret;
        }
        ret = 0;
    }else{
        av_log(avctx, AV_LOG_ERROR, "android_read_header videoDevice = %s\n",ctx->device_name[VideoDevice]);
    }
    return ret;
}

static int android_read_packet(AVFormatContext *s, AVPacket *pkt)
{
    return 0;
}

static int android_read_close(AVFormatContext *s)
{
    struct android_camera_ctx *ctx = s->priv_data;
    android_closeDemuxer();
    return 0;
}

#define OFFSET(x) offsetof(struct android_camera_ctx, x)
#define DEC AV_OPT_FLAG_DECODING_PARAM
static const AVOption options[] = {
        { "video_size", "set video size given a string such as 640x480.", OFFSET(requested_width), AV_OPT_TYPE_IMAGE_SIZE, {.str = NULL}, 0, 0, DEC },
        { "pixel_format", "set video pixel format", OFFSET(pixel_format), AV_OPT_TYPE_PIXEL_FMT, {.i64 = AV_PIX_FMT_NONE}, -1, INT_MAX, DEC },
        { "framerate", "set video frame rate", OFFSET(framerate), AV_OPT_TYPE_STRING, {.str = NULL}, 0, 0, DEC },
        { "oes_texture", "set android preview oes texture", OFFSET(oes_texture), AV_OPT_TYPE_INT, {.i64 = 0}, 0, INT_MAX, DEC },
        { "sample_format", "set audio sample width 8 or 16,32", OFFSET(sample_format), AV_OPT_TYPE_INT, {.i64 = 0}, 0, INT_MAX, DEC },
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
