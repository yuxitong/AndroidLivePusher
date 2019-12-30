package com.yxt.livepusher.egl;

import android.opengl.EGL14;
import android.view.Surface;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

public class EglHelper {

    private EGL10 mEgl;
    private EGLDisplay mEglDisplay;
    private EGLContext mEglContext;
    private EGLSurface mEglSurface;

    private OnEGLContext onEGLContext;
    public void initEgl(Surface surface, EGLContext eglContext,int width,int height) {
        mEgl = (EGL10) EGLContext.getEGL();
        mEglDisplay = mEgl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
        if (mEglDisplay == EGL10.EGL_NO_DISPLAY)
            throw new RuntimeException("eglGetDisplay failed");

        int[] version = new int[2];
        if (!mEgl.eglInitialize(mEglDisplay, version))
            throw new RuntimeException("eglInitialize failed");

        int[] attrbutes = new int[]{
                EGL10.EGL_RED_SIZE, 8,
                EGL10.EGL_GREEN_SIZE, 8,
                EGL10.EGL_BLUE_SIZE, 8,
                EGL10.EGL_ALPHA_SIZE, 8,
                EGL10.EGL_DEPTH_SIZE, 8,
                EGL10.EGL_STENCIL_SIZE, 8,
                EGL10.EGL_RENDERABLE_TYPE, 4,
                EGL10.EGL_NONE};

        int[] num_config = new int[1];
        if (!mEgl.eglChooseConfig(mEglDisplay, attrbutes, null, 1, num_config))
            throw new IllegalArgumentException("eglChooseConfig failed");
        int numConfigs = num_config[0];
        if (numConfigs <= 0)
            throw new IllegalArgumentException("No configs match configSpec");


        EGLConfig[] configs = new EGLConfig[numConfigs];
        if (!mEgl.eglChooseConfig(mEglDisplay, attrbutes, configs, numConfigs, num_config))
            throw new IllegalArgumentException("eglChooseConfig#2failed");

        int[] attrib_list = {
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL10.EGL_NONE
        };

        if (eglContext != null)
            mEglContext = mEgl.eglCreateContext(mEglDisplay, configs[0], eglContext, attrib_list);
        else
            mEglContext = mEgl.eglCreateContext(mEglDisplay, configs[0], EGL10.EGL_NO_CONTEXT, attrib_list);

        if(onEGLContext!= null){
            onEGLContext.EGLContext(mEglContext);
        }

        if(surface==null){
            int[] attrib_list1 = {
                    EGL10.EGL_WIDTH,width,
                    EGL10.EGL_HEIGHT,height,
                    EGL10.EGL_NONE
            };
            mEglSurface =  mEgl.eglCreatePbufferSurface(mEglDisplay,configs[0],attrib_list1);
        }else{
            mEglSurface = mEgl.eglCreateWindowSurface(mEglDisplay, configs[0], surface, null);
        }

        if (!mEgl.eglMakeCurrent(mEglDisplay, mEglSurface, mEglSurface, mEglContext))
            throw new RuntimeException("eglMakeCurrent failed");
    }


    public boolean swapBuffers() {
        if (mEgl != null)
            return mEgl.eglSwapBuffers(mEglDisplay, mEglSurface);
        else
            throw new RuntimeException("egl is null");
    }

    public EGLContext getEglContext() {
        return mEglContext;
    }

    public void onDestoryEgl() {
        if (mEgl != null) {
            mEgl.eglMakeCurrent(mEglDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
            mEgl.eglDestroySurface(mEglDisplay, mEglSurface);
            mEglSurface = null;
            mEgl.eglDestroyContext(mEglDisplay, mEglContext);
            mEglContext = null;
            mEgl.eglTerminate(mEglDisplay);
            mEglDisplay = null;
            mEgl = null;
        }
    }
    public interface OnEGLContext{
        void EGLContext(EGLContext eglContext);
    }
}
