package com.yxt.livepusher.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.GLES20;

import com.yxt.livepusher.egl.BaseRendeer;
import com.yxt.livepusher.test.CameraGLRender;
import com.yxt.livepusher.utils.ShaderUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class CameraFboRender extends BaseRendeer {



    public CameraFboRender(Context context) {
        super(context);
    }

    public void onCreate() {
        super.onCreate();
    }

    public void onChange(int width, int height) {
        super.onChange(width, height);
    }

    public void onDrawFrame(int textureId) {
        super.onDrawFrame(textureId);

    }

//    public boolean isRequestScreenShot() {
//        return this.screenShotListener == null;
//    }
//
//    private void sendImage(final int width, final int height) {
//        final ByteBuffer rgbaBuf = ByteBuffer.allocateDirect(width * height * 4);
//        rgbaBuf.order(ByteOrder.LITTLE_ENDIAN);
//        GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE,
//                rgbaBuf);
//        rgbaBuf.rewind();
//        screenShotThread = new Thread(new Runnable() {
//            @Override
//            public void run() {
//                Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
////            bitmap.copyPixelsToBuffer(rgbaBuf);
//                bitmap.copyPixelsFromBuffer(rgbaBuf);
//                android.graphics.Matrix matrixBitmap = new android.graphics.Matrix();
//                matrixBitmap.preScale(1.0F, -1.0F);
//                Bitmap normalBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrixBitmap, true);
//                if (screenShotListener != null)
//                    screenShotListener.onBitmapAvailable(normalBitmap);
//                bitmap.recycle();
//                bitmap = null;
//                normalBitmap.recycle();
//                normalBitmap = null;
//            }
//        });
//        screenShotThread.setPriority(Thread.MAX_PRIORITY);//设置最大的线程优先级
//        screenShotThread.start();
//    }
//
//    public void requestScreenShot(boolean requestScreenBitmap, ScreenShotListener screenShotListener) {
//        this.screenShotListener = screenShotListener;
//        this.requestScreenBitmap = requestScreenBitmap;
//    }
//
//
//    public interface ScreenShotListener {
//        void onBitmapAvailable(Bitmap bitmap);
//    }
//
//    public void requestScreenShot(ScreenShotListener screenShotListener) {
//        requestScreenShot(true, screenShotListener);
//    }
//
//    public void setRequestScreenBitmap(boolean requestScreenBitmap) {
//        this.requestScreenBitmap = requestScreenBitmap;
//    }
//
//    void release(){
//        if(screenShotThread !=null&&screenShotThread.isAlive())
//
//        {
//            screenShotThread.interrupt();
//            screenShotThread = null;
//        }
//    }
}