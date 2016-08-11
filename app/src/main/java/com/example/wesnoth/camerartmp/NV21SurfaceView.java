package com.example.wesnoth.camerartmp;

import android.content.Context;
import android.hardware.Camera;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;

/**
 * Created by wesnoth on 2016/8/11.
 */
public class NV21SurfaceView extends SurfaceView implements SurfaceHolder.Callback{
    private SurfaceHolder mHolder;
    private String TAG = "AndroidDemuxer";

    public NV21SurfaceView(Context context){
        super(context);
        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
        mHolder = getHolder();
        mHolder.addCallback(this);
        // deprecated setting, but required on Android versions prior to 3.0
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    public void surfaceCreated(SurfaceHolder holder)
    {
        // The Surface has been created, now tell the camera where to draw the preview.

    }

    public void surfaceDestroyed(SurfaceHolder holder)
    {

    }

    public void surfaceChanged(SurfaceHolder holder,int format, int w, int h)
    {
        // If your preview can change or rotate, take care of those events here.
        // Make sure to stop the preview before resizing or reformatting it.

        if (mHolder.getSurface() == null){
            // preview surface does not exist
            return;
        }

        // stop preview before making changes


        // set preview size and make any resize, rotate or
        // reformatting changes here

        // start preview with new settings

    }
}
