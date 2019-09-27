package com.yxt.livepusher.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLException;
import android.opengl.Matrix;
import android.util.Log;
import android.view.WindowManager;


import com.yxt.livepusher.egl.YUEGLSurfaceView;
import com.yxt.livepusher.livepusher.R;
import com.yxt.livepusher.utils.ShaderUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import javax.microedition.khronos.opengles.GL10;

public class CameraRender implements YUEGLSurfaceView.YuGLRender, SurfaceTexture.OnFrameAvailableListener {
    private Context context;

    private float[] vertexData = {
            -1f, -1f,
            1f, -1f,
            -1f, 1f,
            1f, 1f,
    };
    private float[] fragmentData = {
            0f, 1f,
            1f, 1f,
            0f, 0f,
            1f, 0f
    };

    private FloatBuffer vertexBuffer;
    private FloatBuffer fragmentBuffer;

    private int program = -1;
    private int vPosition;
    private int fPosition;
    private int vboId = -1;
    private int fboId = -1;
    private int fboTextureId = -1;

    private int cameraTextureId = -1;
    private int umatrixl;
    private float[] matrix = new float[16];
    private float[] zjmatrix = new float[16];

    private SurfaceTexture surfaceTexture;

    private OnSurfaceCreateListener onSurfaceCreateListener;

    private CameraFboRender cameraFboRender;

    private Interactive interactive;

    //屏幕的宽
    private int screenWidth;
    //屏幕的高
    private int screenHeight;
    //实际控件的宽
    private int width;
    private int height;

    private ScreenShotListener screenShotListener;
    private Thread screenShotThread;
    private boolean requestScreenBitmap = false;
    int vertexShader = -1;
    int fragmentShader = -1;

    public CameraRender(Context context, int width, int height) {
        this.context = context;
        restMatrix();
        cameraFboRender = new CameraFboRender(context);
        vertexBuffer = ByteBuffer.allocateDirect(vertexData.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(vertexData);
        vertexBuffer.position(0);
        fragmentBuffer = ByteBuffer.allocateDirect(fragmentData.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(fragmentData);
        fragmentBuffer.position(0);

        this.width = width;
        this.height = height;

    }


    public void restMatrix() {
        //初始化矩阵
        Matrix.setIdentityM(matrix, 0);
    }

    public void setAngle(float angle, float x, float y, float z) {
        Matrix.rotateM(matrix, 0, angle, x, y, z);

    }

    public void setInteractive(Interactive interactive) {
        this.interactive = interactive;
    }

    @Override
    public void onSurfaceCreated() {
        try {
            cameraFboRender.onCreate();
            String vertexSource = ShaderUtils.getRawResource(context, R.raw.vertex_shader);
            String fragmentSource = ShaderUtils.getRawResource(context, R.raw.fragment_shader);
            int[] id = ShaderUtils.createProgram(vertexSource, fragmentSource);
            if (id != null) {
                vertexShader = id[0];
                fragmentShader = id[1];
                program = id[2];
            }
            vPosition = GLES20.glGetAttribLocation(program, "v_Position");
            fPosition = GLES20.glGetAttribLocation(program, "f_Position");
            umatrixl = GLES20.glGetUniformLocation(program, "u_Matrix");
            //创建顶点坐标缓存空间（可以解决每次在Cpu中将顶点坐标送到GPU里）
            int[] vbos = new int[1];
            GLES20.glGenBuffers(1, vbos, 0);
            vboId = vbos[0];
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId);
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vertexData.length * 4 + fragmentData.length * 4, null, GLES20.GL_STATIC_DRAW);
            GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, vertexData.length * 4, vertexBuffer);
            GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, vertexData.length * 4, fragmentData.length * 4, fragmentBuffer);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

            //创建fbo  离屏渲染
            int[] fbos = new int[1];
            GLES20.glGenBuffers(1, fbos, 0);
            fboId = fbos[0];
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId);


            int[] textureIds = new int[1];
            GLES20.glGenTextures(1, textureIds, 0);
            fboTextureId = textureIds[0];
            //绑定纹理ID
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboTextureId);
//            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
//            GLES20.glUniform1i(sampler, 0);

            //环绕设置 超出纹理坐标    S是横坐标  T是纵坐标   GL_REPEAT重复
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);
            //过滤 缩小放大  线性
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

            WindowManager wm = (WindowManager) context
                    .getSystemService(Context.WINDOW_SERVICE);

//            screenWidth = wm.getDefaultDisplay().getWidth();
//            screenHeight = wm.getDefaultDisplay().getHeight();
            screenWidth = width;
            screenHeight = height;
            Log.e("screenWidth", screenWidth + "   " + screenHeight);
            Log.e("宽度", this.width + "  " + this.height);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, screenWidth, screenHeight, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);


            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, fboTextureId, 0);

            //判断 fbo是否绑定正常
            if (GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            }

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

            int[] textureidseos = new int[1];
            GLES20.glGenTextures(1, textureidseos, 0);
            cameraTextureId = textureidseos[0];

            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId);
            //环绕设置 超出纹理坐标    S是横坐标  T是纵坐标   GL_REPEAT重复
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);
            //过滤 缩小放大  线性
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

            surfaceTexture = new SurfaceTexture(cameraTextureId);
            surfaceTexture.setOnFrameAvailableListener(this);
            if (onSurfaceCreateListener != null) {
                onSurfaceCreateListener.onSurfaceCreate(surfaceTexture, fboTextureId);
            }
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onSurfaceChanged(int width, int height) {
//        cameraFboRender.onChange(width,height);
//        GLES20.glViewport(0,0,width,height);
//        this.width = width;
//        this.height = height;
        Matrix.orthoM(zjmatrix, 0, -1f, 1f, -1f, 1f, -1f, 1f);

    }

    @Override
    public void onDrawFrame() {
        surfaceTexture.updateTexImage();
        //清屏
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glClearColor(1.0f, 0.0f, 0.0f, 1.0f);
        //使用program
        GLES20.glUseProgram(program);
//        Matrix.rotateM(matrix,0,angle,x,y,z);
        GLES20.glViewport(0, 0, screenWidth, screenHeight);
        //绑定离屏渲染
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId);
        //使用矩阵并赋值
        GLES20.glUniformMatrix4fv(umatrixl, 1, false, matrix, 0);
        //绑定顶点坐标
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId);


        GLES20.glEnableVertexAttribArray(vPosition);
        GLES20.glVertexAttribPointer(vPosition, 2, GLES20.GL_FLOAT, false, 8, 0);
        GLES20.glEnableVertexAttribArray(fPosition);
        GLES20.glVertexAttribPointer(fPosition, 2, GLES20.GL_FLOAT, false, 8, vertexData.length * 4);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        cameraFboRender.onChange(width, height);
        cameraFboRender.onDrawFrame(fboTextureId);

//        if (requestScreenBitmap) {
//            sendImage(width, height);
//            requestScreenBitmap = false;
//        }

        if (requestScreenBitmap) {
//            Bitmap bitmap = cutBitmap(0, 0, width, height);
            sendImage(width, height);
            requestScreenBitmap = false;
//            if (screenShotListener != null)
//                screenShotListener.onBitmapAvailable(bitmap);
        }
    }

    @Override
    public void onDeleteTextureId() {
        cameraFboRender.onDeleteTextureId();
        if (vboId != -1)
            GLES20.glDeleteBuffers(1, new int[]{vboId}, 0);
        if (fboId != -1)
            GLES20.glDeleteBuffers(1, new int[]{fboId}, 0);
        if (fboTextureId != -1)
            GLES20.glDeleteTextures(1, new int[]{fboTextureId}, 0);
        if (cameraTextureId != -1)
            GLES20.glDeleteBuffers(1, new int[]{cameraTextureId}, 0);
        if (program != -1)
            GLES20.glDeleteProgram(program);
        if (vertexShader != -1)
            GLES20.glDeleteShader(vertexShader);
        if (fragmentShader != -1)
            GLES20.glDeleteShader(fragmentShader);

    }

    public void requestScreenShot(ScreenShotListener screenShotListener) {
        requestScreenShot(true, screenShotListener);
    }


    private void sendImage(final int width, final int height) {
        final ByteBuffer rgbaBuf = ByteBuffer.allocateDirect(width * height * 4);
        rgbaBuf.order(ByteOrder.LITTLE_ENDIAN);
        GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE,
                rgbaBuf);
        rgbaBuf.rewind();
        screenShotThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
//            bitmap.copyPixelsToBuffer(rgbaBuf);
                bitmap.copyPixelsFromBuffer(rgbaBuf);
                android.graphics.Matrix matrixBitmap = new android.graphics.Matrix();
                matrixBitmap.preScale(1.0F, -1.0F);
                Bitmap normalBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrixBitmap, true);
                if (screenShotListener != null)
                    screenShotListener.onBitmapAvailable(normalBitmap);
                bitmap.recycle();
                bitmap = null;
                normalBitmap.recycle();
                normalBitmap = null;
            }
        });
        screenShotThread.setPriority(Thread.MAX_PRIORITY);//设置最大的线程优先级
        screenShotThread.start();
    }

    private Bitmap cutBitmap(int x, int y, int w, int h) {
        int bitmapBuffer[] = new int[w * h];
        int bitmapSource[] = new int[w * h];
        IntBuffer intBuffer = IntBuffer.wrap(bitmapBuffer);
        intBuffer.position(0);
        try {
            GLES20.glReadPixels(x, y, w, h, GL10.GL_RGBA, GL10.GL_UNSIGNED_BYTE,
                    intBuffer);
            int offset1, offset2;
            for (int i = 0; i < h; i++) {
                offset1 = i * w;
                offset2 = (h - i - 1) * w;
                for (int j = 0; j < w; j++) {
                    int texturePixel = bitmapBuffer[offset1 + j];
                    int blue = (texturePixel >> 16) & 0xff;
                    int red = (texturePixel << 16) & 0x00ff0000;
                    int pixel = (texturePixel & 0xff00ff00) | red | blue;
                    bitmapSource[offset2 + j] = pixel;
                }
            }
        } catch (GLException e) {
            return null;
        }
        Bitmap bitmap = Bitmap.createBitmap(bitmapSource, w, h, Bitmap.Config.ARGB_8888);
        intBuffer.clear();
        return bitmap;
    }

    public void requestScreenShot(boolean requestScreenBitmap, ScreenShotListener screenShotListener) {
        this.screenShotListener = screenShotListener;
        this.requestScreenBitmap = requestScreenBitmap;
        if (interactive != null) {
            interactive.refresh();
        }
    }

    public interface Interactive {
        void refresh();
    }

    //是否能截图  true  可以截图   false 不能截图
//    public synchronized boolean isCanScreenShot() {
//        return !requestScreenBitmap && screenShotListener == null && screenShotThread == null;
////        return screenShotListener == null || screenShotThread == null || !(screenShotThread.isAlive() && screenShotListener != null);
//    }


    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
    }

    public void setOnSurfaceCreateListener(OnSurfaceCreateListener onSurfaceCreateListener) {
        this.onSurfaceCreateListener = onSurfaceCreateListener;
    }

    public interface OnSurfaceCreateListener {
        void onSurfaceCreate(SurfaceTexture surfaceTexture, int textureId);
    }

    public interface ScreenShotListener {
        void onBitmapAvailable(Bitmap bitmap);
    }

    public void release() {
        if (screenShotThread != null && screenShotThread.isAlive()) {
            screenShotThread.interrupt();
            screenShotThread = null;
        }
        if (surfaceTexture != null) {
            surfaceTexture.release();
            surfaceTexture = null;
        }
    }
}
