package com.example.wesnoth.camerartmp;

import android.graphics.SurfaceTexture;
import android.opengl.GLSurfaceView;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.hardware.Camera;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;
import android.content.Context;
import android.content.res.AssetManager;

import java.io.IOException;

public class MainActivity extends AppCompatActivity {
    private Camera mCamera;
    private CameraPreview mPreview;
    private GLSurfaceView mGLPreview;
    private TextureView mTextureView;
    static String TAG = "Main";

    private static native void nativeSetContext(final Context pContext, final AssetManager pAssetManager);

    protected  void init()
    {
        context = this;

        /*
        mGLPreview = new GLSurfaceView(this);
        mGLPreview.setEGLContextClientVersion(2);
        mGLPreview.setRenderer(new OpenGLRenderer());
        mGLPreview.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view){
                Log.w(TAG,"autoFocus");
                AndroidDemuxer.autoFocus(true);
            }
        });
        */
        mGLPreview = new GLSurfaceView(this);
        mGLPreview.setEGLContextClientVersion(2);
        mGLPreview.setRenderer(new MyGLRenderer());
        mGLPreview.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view){
                Log.w(TAG,"autoFocus");
                AndroidDemuxer.autoFocus(true);
            }
        });

        setContentView(mGLPreview);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        onLoadNativeLibraries();
        nativeSetContext(this,getAssets());
        Log.d(TAG,"Test AndroidDemuxer:");

        //使用TextureView工作
/*
        mTextureView = new TextureView(this);
        mTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener(){
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                mCamera = Camera.open();

                try {
                    mCamera.setPreviewTexture(surface);
                    mCamera.startPreview();
                } catch (IOException ioe) {
                    // Something bad happened
                }
            }

            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                // Ignored, Camera does all the work for us
            }

            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                mCamera.stopPreview();
                mCamera.release();
                return true;
            }

            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
                // Invoked every time there's a new Camera preview frame
            }
        });
        setContentView(mTextureView);
*/
    }

    /** A safe way to get an instance of the Camera object. */
    public static Camera getCameraInstance(){
        Camera c = null;
        try {
            c = Camera.open(); // attempt to get a Camera instance
        }
        catch (Exception e){
            // Camera is not available (in use or does not exist)
        }
        return c; // returns null if camera is unavailable
    }

    @Override
    protected void onResume(){
        super.onResume();
        init();
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                /*
                while(OpenGLRenderer._camTextrue==-1){
                    try {
                        Thread.sleep(10);
                    }catch(Exception e)
                    {
                        return;
                    }
                }
                */
                AndroidDemuxer.testLiveRtmp(0);
                //AndroidDemuxer.openDemuxer(OpenGLRenderer._camTextrue,
                //        0,640,480,17,-1,
                //        2,0,44100);
            }
        });
        t.start();
        // Create an instance of Camera
        /*
        mCamera = getCameraInstance();
        if(mCamera!=null) {
            mPreview = new CameraPreview(this, mCamera);
            FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);
            preview.addView(mPreview);
        }
        */
    }

    static Context context = null;
    public static Context getContext(){
        return context;
    }
    @Override
    protected void onPause(){
        AndroidDemuxer.testLiveRtmpEnd();
        OpenGLRenderer._camTextrue = -1;

        super.onPause();
        /*
        if(mCamera!=null){
            mCamera.release();
            FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);
            preview.removeView(mPreview);
            mPreview = null;
            mCamera = null;
        }
        */
    }

    protected void onLoadNativeLibraries(){
        try {
            /*
            System.loadLibrary("avutil");
            System.loadLibrary("swresample");
            System.loadLibrary("swscale");
            System.loadLibrary("avfilter");
            System.loadLibrary("avformat");
            System.loadLibrary("avcodec");
            System.loadLibrary("avdevice");
            */
            System.loadLibrary("CameraRtmp");
        }catch(Exception e){
            e.printStackTrace();
        }
    }
}
