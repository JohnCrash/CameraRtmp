package com.example.wesnoth.camerartmp;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;

import org.ffmpeg.device.AndroidDemuxer;

/**
 * Created by john on 2016/8/19.
 */
public class MyGLRenderer implements GLSurfaceView.Renderer
{
    /*
     * (non-Javadoc)
     *
     * @see
     * android.opengl.GLSurfaceView.Renderer#onSurfaceCreated(javax.
     * microedition.khronos.opengles.GL10, javax.microedition.khronos.
     * egl.EGLConfig)
     */
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * android.opengl.GLSurfaceView.Renderer#onDrawFrame(javax.
     * microedition.khronos.opengles.GL10)
     */
    public void onDrawFrame(GL10 gl) {
        AndroidDemuxer.mainGL();
    }
    /*
     * (non-Javadoc)
     *
     * @see
     * android.opengl.GLSurfaceView.Renderer#onSurfaceChanged(javax.
         * microedition.khronos.opengles.GL10, int, int)
     */
    public void onSurfaceChanged(GL10 gl, int w, int h) {
    }
}
