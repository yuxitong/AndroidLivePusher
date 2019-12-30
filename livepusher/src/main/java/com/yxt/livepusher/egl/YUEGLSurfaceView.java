package com.yxt.livepusher.egl;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.lang.ref.WeakReference;

import javax.microedition.khronos.egl.EGLContext;

public abstract class YUEGLSurfaceView extends SurfaceView implements SurfaceHolder.Callback {
    private EGLContext mEglContext;
    public Surface mSurface;

    private YUGLThread mYUGLThread;

    private YuGLRender mYuGLRender;
    public final static int RENDERMODE_WHEN_DIRTY = 0;
    public final static int RENDERMODE_CONTINUOUSLY = 1;
    private int mRenderMode = RENDERMODE_CONTINUOUSLY;

    private int fps = 15;

    public YUEGLSurfaceView(Context context) {
        this(context, null);
    }

    public YUEGLSurfaceView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public YUEGLSurfaceView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public YUEGLSurfaceView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        getHolder().addCallback(this);

//        this.setRender();
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

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.e("YUEGLSurfaceView", width + "   " + height);
        mYUGLThread.width = width;

        mYUGLThread.height = height;
        mYUGLThread.isChange = true;

    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (mSurface == null)
            mSurface = holder.getSurface();
        mYUGLThread = new YUGLThread(new WeakReference<YUEGLSurfaceView>(this));
        mYUGLThread.isCreate = true;
        mYUGLThread.start();

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        mYUGLThread.onDestory();
        mYUGLThread = null;
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

    public interface YuGLRender {
        void onSurfaceCreated();

        void onSurfaceChanged(int width, int height);

        void onDrawFrame();

        void onDeleteTextureId();
    }

    public void setRender(YuGLRender yuGLRender) {
        mYuGLRender = yuGLRender;
    }

    static class YUGLThread extends Thread {
        private WeakReference<YUEGLSurfaceView> yuEglSurfaceViewWeakReference = null;
        private EglHelper eglHelper = null;
        private boolean isCreate = false;
        private boolean isExit = false;
        private boolean isChange = false;
        private boolean isStart = false;
        private int width;
        private int height;
        private Object object;

        public YUGLThread(WeakReference<YUEGLSurfaceView> yuEglSurfaceViewWeakReference) {
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
