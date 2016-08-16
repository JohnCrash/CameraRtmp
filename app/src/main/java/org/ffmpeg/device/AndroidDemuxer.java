package org.ffmpeg.device;

import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.os.Build;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import android.media.AudioFormat;
import android.media.AudioRecord;

/**
 * Created by john on 2016/7/15.
 */
public class AndroidDemuxer{
    static String TAG = "AndroidDemuxer";


    public AndroidDemuxer(){
    }

    public static int getNumberOfCameras(){
        return Camera.getNumberOfCameras();
    }

    public static int getCameraCapabilityInteger( int n,int info[] ){
        int count = 0;
        try {
            Camera.CameraInfo cameraInfo;
            cameraInfo = new Camera.CameraInfo();
            Camera.getCameraInfo(n,cameraInfo);
            info[count++] = cameraInfo.facing;
            info[count++] = cameraInfo.orientation;
            Camera cam = Camera.open(n);
            Camera.Parameters param = cam.getParameters();
            List<Camera.Size> sizes = param.getSupportedPreviewSizes();
            info[count++] = sizes.size();
            for( Camera.Size size:sizes ) {
                info[count++] = size.width;
                info[count++] = size.height;
            }
            List<Integer> fmts = param.getSupportedPreviewFormats();
            info[count++] = fmts.size();
            for( Integer fmt:fmts ){
                info[count++] = fmt.intValue();
            }

            List<Integer> fps = param.getSupportedPreviewFrameRates();
            info[count++] = fps.size();
            for (Integer i : fps) {
                info[count++] = i.intValue();
            }
            cam.release();
        }catch(Exception e){
            Log.e(TAG,String.format("Could not open camera device %d \n",n));
            count = 0;
        }
        return count;
    }

    private static Camera _cam = null;
    private static Deque<byte []> _buffers = null;
    private static int nMaxFrame = 3;
    private static int _bufferSize;
    private static int _width,_height,_targetFps;
    private static int _pixFmt;
    private static AudioRecord _audioRecord = null;
    private static Thread _audioRecordThread = null;
    private static boolean _audioRecordLoop = false;
    private static boolean _audioRecordStop = true;
    private static SurfaceTexture _textrue = null;
    private static Thread _preivewThread = null;
    private static int _sampleFmt,_channel;
    private static int _sampleRate = 0;
    private static long _nGrabFrame = 0;
    private static long _timeStrampBegin = 0;
    //public static byte[] _currentBuf = null;

    public static SurfaceTexture getSurfaceTexture(){
        return _textrue;
    }

    public static boolean getDemuxerInfo(int [] data){
        if(_cam !=null && data !=null && data.length>=7){
            int i = 0;
            data[i++] = _width;
            data[i++] = _height;
            data[i++] = _pixFmt;
            data[i++] = _targetFps;
            data[i++] = _channel;
            data[i++] = _sampleFmt;
            data[i++] = _sampleRate;
            return true;
        }
        return false;
    }

    public static void releaseBuffer(byte [] data){
        if(_buffers!=null) {
            synchronized (_buffers) {
                if (_bufferSize == data.length)
                    _buffers.push(data);
            }
        }
    }

    private static native void ratainBuffer(int type,byte [] data,int len,
                                            int fmt,int p0,int p1,
                                            long timestramp);

    public static byte [] newFrame(){
        return new byte[_bufferSize];
    }

    public static boolean autoFocus(boolean b){
        if(_cam==null)return false;
        try {
            if (b){
                _cam.autoFocus(new Camera.AutoFocusCallback() {
                    @Override
                    public void onAutoFocus(boolean success, Camera camera) {
                        Log.w(TAG, "onAutoFocus");
                        ratainBuffer(2, null,0,0,0,0,System.nanoTime()-_timeStrampBegin);
                    }
                });
            }else{
                _cam.cancelAutoFocus();
            }
            return true;
        }catch(Exception e){
            Log.e(TAG,"couldn't (de)activate autofocus", e);
        }
        return false;
    }

    public static void update(float [] videoTextureTransform){
        if(_textrue!=null) {
            try {
                _textrue.updateTexImage();
                _textrue.getTransformMatrix(videoTextureTransform);
            }catch(Exception e){
                Log.e(TAG,e.getMessage());
            }
        }
    }

    public static int openDemuxer(int tex,int nDevice,int w,int h,int fmt,int fps,
                                  int nChannel,int sampleFmt,int sampleRate ){
        Camera.Parameters config;
        if(Build.VERSION.SDK_INT < 11){
            Log.e(TAG,"openDemuxer need API level 11 or later");
            return -11;
        }
        if(_cam!=null || _audioRecord!=null ) {
            Log.e(TAG,"openDemuxer already opened");
            return -10;
        }
        if( nDevice>=0 ) {
            int bitsPrePixel = ImageFormat.getBitsPerPixel(fmt);
            if (bitsPrePixel <= 0 || w <= 0 || h <= 0) {
                Log.e(TAG,"openDemuxer invalid argument");
                return -1;
            }

            try {
                _cam = Camera.open(nDevice);
                _cam.setErrorCallback(new Camera.ErrorCallback(){
                    @Override
                    public void onError(int error,Camera cam){
                        if(error==Camera.CAMERA_ERROR_SERVER_DIED){
                            closeDemuxer();
                        }
                    }
                });
                config = _cam.getParameters();
                config.setPreviewSize(w, h);
                config.setPreviewFormat(fmt);
                try{
                    Method setRecordingHint = config.getClass().getMethod("setRecordingHint",boolean.class);
                    setRecordingHint.invoke(config, true);
                }catch(Exception e){
                    Log.i(TAG,"couldn't set recording hint");
                }
                //config.setPreviewFpsRange(minFps, maxFps);
                _targetFps = fps;
                if(fps<0) {
                    for (Integer i : config.getSupportedPreviewFrameRates()) {
                        if (_targetFps < i) {
                            _targetFps = i;
                        }
                    }
                }
                config.setPreviewFrameRate(_targetFps);
                {
                    _cam.setDisplayOrientation(90);

                // 华为前置摄像头导致setParameters异常
                //    try {
                //        config.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
                //    }catch(Exception e){
                //        Log.w(TAG,"couldn't setFocusMode FOCUS_MODE_CONTINUOUS_VIDEO");
                //    }
                }
                _cam.setParameters(config);

                config = _cam.getParameters();
                _width = config.getPreviewSize().width;
                _height = config.getPreviewSize().height;
                if(_width!=w || _height!=h)  {
                    Log.w(TAG,String.format("camera size different than asked for, resizing (this can slow the app) (%d,%d)->(%d,%d)",w,h,_width,_height));
                }
                _pixFmt = config.getPreviewFormat();
                if(fmt!=_pixFmt){
                    Log.w(TAG,String.format("camera format different than asked for, (%d)->(%d)",fmt,config.getPreviewFormat()));
                }

                bitsPrePixel = ImageFormat.getBitsPerPixel(_pixFmt);
                _bufferSize = _width * _height * bitsPrePixel / 8;
            } catch (RuntimeException e) {
                Log.e(TAG,e.getMessage());
                return -3;
            } catch (Exception e) {
                Log.e(TAG,e.getMessage());
                return -4;
            }

            _buffers = new ArrayDeque<byte[]>();
            for (int i = 0; i < nMaxFrame; i++)
                _cam.addCallbackBuffer(newFrame());
            _cam.setPreviewCallbackWithBuffer(new Camera.PreviewCallback() {
                @Override
                public void onPreviewFrame(byte[] data, Camera camera) {
                    //Log.w(TAG, String.format("onPreviewFrame 0 %d",data.length));
                    //_currentBuf = Arrays.copyOf(data,data.length);
                    ratainBuffer(0, data,data.length,_pixFmt,_width,_height,System.nanoTime()-_timeStrampBegin);
                    synchronized (_buffers) {
                        if (_buffers.isEmpty()) {
                            _cam.addCallbackBuffer(newFrame());
                        } else {
                            _cam.addCallbackBuffer(_buffers.pop());
                        }
                    }
                }
            });

            try {
                _nGrabFrame = 0;
                _textrue = new SurfaceTexture(tex);
                _textrue.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener(){
                    @Override
                    public void onFrameAvailable(SurfaceTexture surfaceTexture){
                        //Log.w(TAG,"openDemuxer onFrameAvailable");
                        ratainBuffer(3, null,0,0,0,0,System.nanoTime()-_timeStrampBegin);
                        _nGrabFrame++;
                    }
                });
                _cam.setPreviewTexture(_textrue);
            }catch(Exception e){
                Log.e(TAG,e.getMessage());
                return -11;
            }

            _timeStrampBegin = System.nanoTime();
            _preivewThread = new Thread(new Runnable(){
                @Override
                public void run(){
                    _cam.startPreview();
                    autoFocus(true);
                }
            });
            ratainBuffer(4, null,0,0,0,0,System.nanoTime()-_timeStrampBegin);
            _preivewThread.start();
        }

        if(nChannel>0) {
        //if(false){
            _sampleFmt = sampleFmt;
            _channel = nChannel;
            _sampleRate = sampleRate;
            int ch = nChannel==1 ? AudioFormat.CHANNEL_CONFIGURATION_MONO : AudioFormat.CHANNEL_IN_STEREO;
            int samplefmt = sampleFmt==8 ? AudioFormat.ENCODING_PCM_8BIT : AudioFormat.ENCODING_PCM_16BIT;
            final int bufferSize = AudioRecord.getMinBufferSize(sampleRate,ch,samplefmt)*2;
            if( bufferSize <= 0 ){
                closeDemuxer();
                Log.e(TAG,"openDemuxer audio parameter is invalid or not supported");
                return -5;
            }
            try {
                _audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, nChannel, samplefmt, bufferSize);
            }catch(IllegalArgumentException e){
                Log.e(TAG,e.getMessage());
                closeDemuxer();
                return -6;
            }
            if(_audioRecord.getState() != AudioRecord.STATE_INITIALIZED){
                closeDemuxer();
                Log.e(TAG,"openDemuxer audio parameter is not supported sampleRate or sampleFmt");
                return -7;
            }
            _audioRecordLoop = true;
            _audioRecord.startRecording();
            _audioRecordThread = new Thread(new Runnable(){
                @Override
                public void run(){
                    byte[] buffer = new byte[bufferSize];
                    _audioRecordStop = false;
                    while(_audioRecordLoop){
                        int result = _audioRecord.read(buffer,0,bufferSize);
                        if(result<0){
                            Log.e(TAG,"openDemuxer audioRecord read error");
                            _audioRecord.stop();
                            _audioRecord.release();
                            _audioRecord = null;
                            break;
                        }
                     //   Log.e(TAG, String.format("onPreviewFrame 1 %d",result));
                        ratainBuffer(1,buffer,result,_sampleFmt,_channel,0,System.nanoTime()-_timeStrampBegin);
                    }
                    _audioRecordStop = true;
                }
            });

            _audioRecordThread.start();
        }
        return 0;
    }

    public static long getGrabFrameCount(){
        return _nGrabFrame;
    }

    public static void closeDemuxer(){
        if(_cam!=null){
            _cam.stopPreview();

            _cam.setPreviewCallback(null);

            _textrue.release();
            _cam.release();
            _textrue = null;
            _cam = null;
            _nGrabFrame = 0;
        }
        if(_audioRecord!=null) {
            _audioRecordLoop = false;
            while(!_audioRecordStop){
                try {
                    Thread.sleep(10);
                }catch(Exception e){
                    break;
                }
            }
            _audioRecord.stop();
            _audioRecord.release();
            _audioRecord = null;
        }

        if(_buffers!=null) {
            _buffers.clear();
            _buffers = null;
        }
        ratainBuffer(5, null,0,0,0,0,System.nanoTime()-_timeStrampBegin);
        Log.e(TAG, "closeDemuxer  end");
    }
}
