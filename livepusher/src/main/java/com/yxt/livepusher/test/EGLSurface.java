package com.yxt.livepusher.test;

import android.graphics.SurfaceTexture;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import com.yxt.livepusher.egl.EglHelper;
import com.yxt.livepusher.egl.YUEGLSurfaceView;

import java.lang.ref.WeakReference;

import javax.microedition.khronos.egl.EGLContext;

public class EGLSurface {

    private EGLContext mEglContext;
    public Surface mSurface;

    private GLThread mYUGLThread;
    public final static int RENDERMODE_WHEN_DIRTY = 0;
    public final static int RENDERMODE_CONTINUOUSLY = 1;
    private int mRenderMode = RENDERMODE_CONTINUOUSLY;
    private YUEGLSurfaceView.YuGLRender mYuGLRender;

    private int fps;

    public EGLSurface() {


    }

    public void setFps(int fps) {
        this.fps = fps;
    }

    public void setRenderMode(int mRenderMode) {
        if (mYuGLRender == null)
            throw new RuntimeException("must set render before");


        this.mRenderMode = mRenderMode;
    }

    public void setSurfaceAndEglContext(Surface surface, EGLContext eglContext) {
        mSurface = surface;
        mEglContext = eglContext;
    }

    public void surfaceChanged(int format, int width, int height) {
        Log.e("YUEGLSurfaceView", width + "   " + height);
        mYUGLThread.width = width;

        mYUGLThread.height = height;
        mYUGLThread.isChange = true;

    }

    public void star() {
        surfaceCreated();
        surfaceChanged(1, 640, 480);
    }

    public void surfaceCreated() {
        if (mSurface == null)
            mSurface = new Surface(new SurfaceTexture(10));
        mYUGLThread = new GLThread(new WeakReference<EGLSurface>(this));
        mYUGLThread.isCreate = true;
        mYUGLThread.start();

    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        if (mYUGLThread != null) {
            mYUGLThread.onDestory();
            mYUGLThread = null;
        }
        mSurface = null;
        mEglContext = null;
    }

    public EGLContext getEglContext() {
        if (mYUGLThread != null) {
            return mYUGLThread.getEglContext();
        }
        return null;
    }

    public void requestRender() {
        if (mYUGLThread != null) {
            mYUGLThread.requestRender();
        }
    }

    public void setRender(YUEGLSurfaceView.YuGLRender yuGLRender) {
        mYuGLRender = yuGLRender;
    }


    static class GLThread extends Thread {
        private WeakReference<EGLSurface> yuEglSurfaceViewWeakReference = null;
        private EglHelper eglHelper = null;
        private boolean isCreate = false;
        private boolean isExit = false;
        private boolean isChange = false;
        private boolean isStart = false;
        private int width;
        private int height;
        private Object object;

        public GLThread(WeakReference<EGLSurface> yuEglSurfaceViewWeakReference) {
            this.yuEglSurfaceViewWeakReference = yuEglSurfaceViewWeakReference;
        }

        @Override
        public void run() {
            super.run();
            isExit = false;
            isStart = false;
            object = new Object();
            eglHelper = new EglHelper();
            eglHelper.initEgl(yuEglSurfaceViewWeakReference.get().mSurface, yuEglSurfaceViewWeakReference.get().mEglContext,width,height);
            while (true) {
                if (isExit) {
                    release();
                    break;
                }
                if (isStart) {
                    if (yuEglSurfaceViewWeakReference.get().mRenderMode == RENDERMODE_WHEN_DIRTY) {
                        synchronized (object) {
                            try {
                                object.wait();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    } else if (yuEglSurfaceViewWeakReference.get().mRenderMode == RENDERMODE_CONTINUOUSLY) {
                        try {
                            Thread.sleep(1000 / yuEglSurfaceViewWeakReference.get().fps);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    } else {
                        throw new RuntimeException("mRenderMode is error");
                    }

                }
                onCreate();
                onChange(width, height);
                onDraw();
                isStart = true;
            }
        }

        private void onCreate() {
            if (isCreate && yuEglSurfaceViewWeakReference.get().mYuGLRender != null) {
                isCreate = false;
                yuEglSurfaceViewWeakReference.get().mYuGLRender.onSurfaceCreated();
            }
        }

        private void onChange(int width, int height) {
            if (isChange && yuEglSurfaceViewWeakReference.get().mYuGLRender != null) {
                isChange = false;
                yuEglSurfaceViewWeakReference.get().mYuGLRender.onSurfaceChanged(width, height);
            }
        }

        private void onDraw() {
            if (yuEglSurfaceViewWeakReference.get().mYuGLRender != null && eglHelper != null) {
                yuEglSurfaceViewWeakReference.get().mYuGLRender.onDrawFrame();
                if (!isStart) {
                    yuEglSurfaceViewWeakReference.get().mYuGLRender.onDrawFrame();

                }

                eglHelper.swapBuffers();
            }
        }

        private void requestRender() {
            if (object != null) {
                synchronized (object) {
                    object.notifyAll();
                }
            }

        }

        public void onDestory() {
            isExit = true;
            requestRender();
        }

        public void release() {
            if (eglHelper != null) {
                eglHelper.onDestoryEgl();
                eglHelper = null;
                object = null;
                if (yuEglSurfaceViewWeakReference != null && yuEglSurfaceViewWeakReference.get() != null && yuEglSurfaceViewWeakReference.get().mYuGLRender != null)
                    yuEglSurfaceViewWeakReference.get().mYuGLRender.onDeleteTextureId();
                yuEglSurfaceViewWeakReference = null;
            }
        }


        public EGLContext getEglContext() {
            if (eglHelper != null) {
                return eglHelper.getEglContext();
            }

            return null;
        }


    }
}
