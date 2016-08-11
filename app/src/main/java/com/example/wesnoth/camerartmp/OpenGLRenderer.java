package com.example.wesnoth.camerartmp;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLES10;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLU;
import android.opengl.GLSurfaceView.Renderer;
import android.opengl.GLUtils;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

/**
 * Created by wesnoth on 2016/8/5.
 */
public class OpenGLRenderer implements Renderer,SurfaceTexture.OnFrameAvailableListener
{
    public static int _camTextrue = -1;
    public static int _dataTextrueY = -1;
    /*
   * (non-Javadoc)
   *
   * @see
   * android.opengl.GLSurfaceView.Renderer#onSurfaceCreated(javax.
       * microedition.khronos.opengles.GL10, javax.microedition.khronos.
       * egl.EGLConfig)
   */
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        // Set the background color to black ( rgba ).
        gl.glClearColor(0.0f, 0.0f, 0.0f, 0.5f);
        // Enable Smooth Shading, default not really needed.
      //  gl.glShadeModel(GL10.GL_SMOOTH);
        // Depth buffer setup.
       // gl.glClearDepthf(1.0f);
        // Enables depth testing.
       // gl.glEnable(GL10.GL_DEPTH_TEST);
        // The type of depth testing to do.
       // gl.glDepthFunc(GL10.GL_LEQUAL);
        // Really nice perspective calculations.
      //  gl.glHint(GL10.GL_PERSPECTIVE_CORRECTION_HINT,
      //          GL10.GL_NICEST);

        checkGlError("onSurfaceCreated glHint");
        context = MainActivity.getContext();

        rebuildCamTextrue();
        setupCameraTextrue();

        setupGraphics();
        setupVertexBuffer();
        setupTexture();
        setupGraphics2();
    }

    public static void rebuildCamTextrue(){
        int [] textures = new int[1];
        /*
         * 产生一个摄像头材质
         */
        GLES20.glGenTextures(1,textures,0);
        checkGlError("rebuildCamTextrue glGenTextures");
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0]);
        checkGlError("glBindTexture GL_TEXTURE_EXTERNAL_OES");
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_MIN_FILTER,
                GL10.GL_LINEAR);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
        // Clamp to edge is only option.
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);
        checkGlError("glTexParameteri");
        _camTextrue = textures[0];
    }

    public void setupCameraTextrue(){
        int [] textures = new int[1];
        GLES20.glGenTextures(1,textures,0);
        checkGlError("updateCameraTextrue glGenTextures");

        _dataTextrueY = textures[0];
    }

    private native void updateCameraTextrue(int tex,int w,int h,byte [] nv21);

    /**接着初始化纹理*/
    private void setupTexture() {
        ByteBuffer texturebb = ByteBuffer.allocateDirect(textureCoords.length * 4);
        texturebb.order(ByteOrder.nativeOrder());

        textureBuffer = texturebb.asFloatBuffer();
        textureBuffer.put(textureCoords);
        textureBuffer.position(0);
        videoTexture = null;
    }

    private boolean frameAvailable = false;
    int textureParamHandle;
    int textureCoordinateHandle;
    int positionHandle;
    int textureTranformHandle;
    int textureParamHandle2;
    int textureCoordinateHandle2;
    int positionHandle2;
    int textureTranformHandle2;
    static String TAG = "AndroidDemuxer";
    private static float squareSize = 1.0f;
    private static float squareCoords[] = {
            -squareSize,  squareSize,   // top left
            -squareSize, -squareSize,   // bottom left
            squareSize, -squareSize,    // bottom right
            squareSize,  squareSize     // top right
    };
    /**绘制次序*/
    private static short drawOrder[] = {
            0, 1, 2,
            0, 2, 3
    };
    /**
     * 用来缓存纹理坐标，因为纹理都是要在后台被绘制好，然
     * 后不断的替换最前面显示的纹理图像
     */
    private FloatBuffer textureBuffer;
    /**纹理坐标*/
    private float textureCoords[] = {
            0.0f, 1.0f, 0.0f, 1.0f,
            0.0f, 0.0f, 0.0f, 1.0f,
            1.0f, 0.0f, 0.0f, 1.0f,
            1.0f, 1.0f, 0.0f, 1.0f
    };
    private int[] textures = new int[1];

    private int width, height;

    private int shaderProgram;
    private int shaderProgram2;
    private FloatBuffer vertexBuffer;
    private ShortBuffer drawListBuffer;
    private float[] videoTextureTransform = new float[16];
    private SurfaceTexture videoTexture;
    private Context context;

    /***
     * 设置顶点缓存
     */
    private void setupVertexBuffer()
    {
        ByteBuffer dlb = ByteBuffer.allocateDirect(drawOrder.length * 2);
        dlb.order(ByteOrder.nativeOrder());
        drawListBuffer = dlb.asShortBuffer();
        drawListBuffer.put(drawOrder);
        drawListBuffer.position(0);

        // Initialize the texture holder
        ByteBuffer bb = ByteBuffer.allocateDirect(squareCoords.length * 4);
        bb.order(ByteOrder.nativeOrder());

        vertexBuffer = bb.asFloatBuffer();
        vertexBuffer.put(squareCoords);
        vertexBuffer.position(0);
    }
    public static void checkGlError(String op) {
        int error;
        while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            Log.e(TAG, op + ": glError " + GLUtils.getEGLErrorString(error));
        }
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture)
    {
        synchronized (this)
        {
            frameAvailable = true;
        }
    }
    /**加载顶点与片段着色器*/
    private void setupGraphics() {
        checkGlError("setupGraphics");
        final String vertexShader = RawResourceReader.readTextFileFromRawResource(context, R.raw.vetext_sharder);
        final String fragmentShader = RawResourceReader.readTextFileFromRawResource(context, R.raw.fragment_sharder);

        final int vertexShaderHandle = ShaderHelper.compileShader(GLES20.GL_VERTEX_SHADER, vertexShader);
        final int fragmentShaderHandle = ShaderHelper.compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShader);
        checkGlError("compileShader");
        shaderProgram = ShaderHelper.createAndLinkProgram(vertexShaderHandle, fragmentShaderHandle,
                new String[]{"texture", "vPosition", "vTexCoordinate", "textureTransform"});
        checkGlError("createAndLinkProgram");
        GLES20.glUseProgram(shaderProgram);
        checkGlError("glUseProgram shaderProgram");
        textureParamHandle = GLES20.glGetUniformLocation(shaderProgram, "texture");
        textureCoordinateHandle = GLES20.glGetAttribLocation(shaderProgram, "vTexCoordinate");
        positionHandle = GLES20.glGetAttribLocation(shaderProgram, "vPosition");
        checkGlError("glGetAttribLocation");
        Log.e(TAG,String.format("glGetAttribLocation %d",positionHandle));
        textureTranformHandle = GLES20.glGetUniformLocation(shaderProgram, "textureTransform");


        checkGlError("setupGraphics done");
    }

    private void setupGraphics2() {
        checkGlError("setupGraphics2");
        final String vertexShader = RawResourceReader.readTextFileFromRawResource(context, R.raw.vetext_sharder);
        final String fragmentShader = RawResourceReader.readTextFileFromRawResource(context, R.raw.fragment_2);

        final int vertexShaderHandle = ShaderHelper.compileShader(GLES20.GL_VERTEX_SHADER, vertexShader);
        final int fragmentShaderHandle = ShaderHelper.compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShader);
        checkGlError("compileShader");
        shaderProgram2 = ShaderHelper.createAndLinkProgram(vertexShaderHandle, fragmentShaderHandle,
                new String[]{"texture", "vPosition", "vTexCoordinate", "textureTransform"});
        checkGlError("createAndLinkProgram");
        GLES20.glUseProgram(shaderProgram2);
        checkGlError("glUseProgram shaderProgram");
        textureParamHandle2 = GLES20.glGetUniformLocation(shaderProgram2, "texture");
        textureCoordinateHandle2 = GLES20.glGetAttribLocation(shaderProgram2, "vTexCoordinate");
        positionHandle2 = GLES20.glGetAttribLocation(shaderProgram2, "vPosition");
        checkGlError("glGetAttribLocation");
        Log.e(TAG,String.format("glGetAttribLocation %d",positionHandle));
        textureTranformHandle2 = GLES20.glGetUniformLocation(shaderProgram, "textureTransform");

        checkGlError("setupGraphics2 done");
    }
    private void drawTexture() {
        // Draw texture
        GLES20.glUseProgram(shaderProgram);
        checkGlError("glUseProgram");

        GLES20.glEnableVertexAttribArray(positionHandle);
        checkGlError("drawTexture glEnableVertexAttribArray");
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer);
        checkGlError("drawTexture glVertexAttribPointer");
        GLES20.glUniform1i(textureParamHandle, 0);
        checkGlError("drawTexture glUniform1i");
        GLES20.glEnableVertexAttribArray(textureCoordinateHandle);
        checkGlError("drawTexture glEnableVertexAttribArray");
        GLES20.glVertexAttribPointer(textureCoordinateHandle, 4, GLES20.GL_FLOAT, false, 0, textureBuffer);
        checkGlError("drawTexture glVertexAttribPointer");
        GLES20.glUniformMatrix4fv(textureTranformHandle, 1, false, videoTextureTransform, 0);
        checkGlError("drawTexture glUniformMatrix4fv");
        GLES20.glDrawElements(GLES20.GL_TRIANGLE_STRIP, drawOrder.length, GLES20.GL_UNSIGNED_SHORT, drawListBuffer);
        GLES20.glDisableVertexAttribArray(positionHandle);
        GLES20.glDisableVertexAttribArray(textureCoordinateHandle);
        checkGlError("drawTexture done");
    }
    private void rotateMatrix(float [] tf,double a){
        for(int i=0;i<16;i++) {
            tf[i] = 0.0f;
        }
        float sin = (float)Math.sin(a);
        float cos = (float)Math.cos(a);

        //-----
    //    tf[0] = cos;
    //    tf[1] = -sin;
    //    tf[4] = sin;
    //    tf[5] = cos;

        // |||||
//            tf[0] = 0;
//            tf[4] = 1;
//            tf[1] = 1;
//            tf[5] = 0;

        tf[0] = 1;
        tf[5] = 1;

        tf[10] = 1;
        tf[15] = 1;
    }
    public void drawCameraTextrue(){
        //init texture matrix
        rotateMatrix(videoTextureTransform,Math.PI);
        // Draw texture
        GLES20.glUseProgram(shaderProgram2);
        checkGlError("drawCameraTextrue glUseProgram");
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        checkGlError("drawCameraTextrue glActiveTexture");
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,_dataTextrueY);
        checkGlError("drawCameraTextrue glBindTexture");
        GLES20.glUniform1i(textureParamHandle2, 0);
        checkGlError("drawCameraTextrue glUniform1i");

        GLES20.glEnableVertexAttribArray(positionHandle2);
        checkGlError("drawCameraTextrue glEnableVertexAttribArray");
        GLES20.glVertexAttribPointer(positionHandle2, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer);
        checkGlError("drawCameraTextrue glVertexAttribPointer");
        GLES20.glEnableVertexAttribArray(textureCoordinateHandle2);
        checkGlError("drawCameraTextrue glEnableVertexAttribArray");
        GLES20.glVertexAttribPointer(textureCoordinateHandle2, 4, GLES20.GL_FLOAT, false, 0, textureBuffer);
        checkGlError("drawCameraTextrue glVertexAttribPointer");
        GLES20.glUniformMatrix4fv(textureTranformHandle2, 1, false, videoTextureTransform, 0);
        checkGlError("drawCameraTextrue glUniformMatrix4fv");
        GLES20.glDrawElements(GLES20.GL_TRIANGLE_STRIP, drawOrder.length, GLES20.GL_UNSIGNED_SHORT, drawListBuffer);
        GLES20.glDisableVertexAttribArray(positionHandle2);
        GLES20.glDisableVertexAttribArray(textureCoordinateHandle2);
        checkGlError("drawCameraTextrue done");
    }
    /*
     * (non-Javadoc)
     *
     * @see
     * android.opengl.GLSurfaceView.Renderer#onDrawFrame(javax.
         * microedition.khronos.opengles.GL10)
     */
    private long _nDisplayFrame = -1;
    public void onDrawFrame(GL10 gl) {
 /*       long ng = AndroidDemuxer.getGrabFrameCount();
        if(_nDisplayFrame!=ng) {
            gl.glClearColor(0, 1, 0, 1);
            gl.glClear(GL10.GL_COLOR_BUFFER_BIT);
            GLES20.glViewport(0, 0, width, height);
            this.drawTexture();
            _nDisplayFrame = ng;
            AndroidDemuxer.update(videoTextureTransform);
        }
 */
        gl.glClearColor(0, 1, 0, 1);
        gl.glClear(GL10.GL_COLOR_BUFFER_BIT);
        GLES20.glViewport(0, 0, width, height);
        if(AndroidDemuxer._currentBuf!=null) {
            updateCameraTextrue(_dataTextrueY, AndroidDemuxer._width, AndroidDemuxer._height, AndroidDemuxer._currentBuf);
            drawCameraTextrue();
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * android.opengl.GLSurfaceView.Renderer#onSurfaceChanged(javax.
         * microedition.khronos.opengles.GL10, int, int)
     */
    public void onSurfaceChanged(GL10 gl, int w, int h) {
        width = w;
        height = h;
        // Sets the current view port to the new size.
        gl.glViewport(0, 0, width, height);
        // Select the projection matrix
        gl.glMatrixMode(GL10.GL_PROJECTION);
        // Reset the projection matrix
        gl.glLoadIdentity();
        // Calculate the aspect ratio of the window
        GLU.gluPerspective(gl, 45.0f,
                (float) width / (float) height,
                0.1f, 100.0f);
        // Select the modelview matrix
        gl.glMatrixMode(GL10.GL_MODELVIEW);
        // Reset the modelview matrix
        gl.glLoadIdentity();
    }
}
