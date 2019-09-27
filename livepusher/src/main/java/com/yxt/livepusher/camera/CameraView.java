package com.yxt.livepusher.camera;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;

import com.yxt.livepusher.egl.YUEGLSurfaceView;

import javax.microedition.khronos.egl.EGLContext;

public class CameraView extends YUEGLSurfaceView {
    private CameraRender cameraRender;
    private YUCamera yuCamera;

    private int cameraId = Camera.CameraInfo.CAMERA_FACING_FRONT;

    private int textureId = -1;

    public CameraView(Context context) {
        this(context, null);
    }

    public CameraView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CameraView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public OnSurfaceCreate onSurfaceCreate;

    public CameraView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        start(640, 480);

    }


//    @Override
//    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
//        super.onLayout(changed, left, top, right, bottom);
//        start(this.getWidth(), this.getHeight());
//
//    }

    private void start(int width, int height) {
        if (cameraRender == null) {
            cameraRender = new CameraRender(this.getContext(), width, height);
            cameraRender.setInteractive(new CameraRender.Interactive() {
                @Override
                public void refresh() {
                    CameraView.this.requestRender();
                }
            });
            setRender(cameraRender);
            yuCamera = new YUCamera(width, height);
            prevewAngle(this.getContext());
            cameraRender.setOnSurfaceCreateListener(new CameraRender.OnSurfaceCreateListener() {
                @Override
                public void onSurfaceCreate(SurfaceTexture surfaceTexture, int textureId) {
                    yuCamera.initCamera(surfaceTexture, cameraId);
                    CameraView.this.textureId = textureId;
                    if (onSurfaceCreate != null)
                        onSurfaceCreate.onSurfaceCreate(textureId, getEglContext());
                }
            });
        }
    }

    public void setOnSurfaceCreate(OnSurfaceCreate onSurfaceCreate) {
        this.onSurfaceCreate = onSurfaceCreate;
    }

    public void prevewAngle(Context context) {
        int angle = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
        Log.e("angleTotal", "  " + angle);
//        cameraRender.restMatrix();
        switch (angle) {
            case Surface.ROTATION_0:
                Log.e("angle", "0");
                if (cameraId == Camera.CameraInfo.CAMERA_FACING_BACK) {
//                    cameraRender.setAngle(90, 0, 0, 1);
                    cameraRender.setAngle(180, 0, 0, 1);
                    Log.e("aaaa", "aaaaa");
                    //                    cameraRender.setAngle(180, 1, 0, 0);

                } else {
                    Log.e("aaaa", "bbbbb");
                    cameraRender.setAngle(180, 0, 0, 1);
//                    cameraRender.setAngle(90, 0, 0, 1);
                }
                break;
            case Surface.ROTATION_90:
                Log.e("angle", "90");
                if (cameraId == Camera.CameraInfo.CAMERA_FACING_BACK) {
                    cameraRender.setAngle(180, 0, 0, 1);
                    cameraRender.setAngle(180, 0, 1, 0);
                } else {
                    cameraRender.setAngle(180, 0, 0, 1);

                }
                break;
            case Surface.ROTATION_180:
                Log.e("angle", "180");
                if (cameraId == Camera.CameraInfo.CAMERA_FACING_BACK) {
                    cameraRender.setAngle(90, 0, 0, 1);
                    cameraRender.setAngle(180, 0, 1, 0);
                } else {
                    cameraRender.setAngle(-90, 0, 0, 1);
                }
                break;
            case Surface.ROTATION_270:
                Log.e("angle", "270");
                if (cameraId == Camera.CameraInfo.CAMERA_FACING_BACK) {
                    cameraRender.setAngle(180, 0, 1, 0);
                }
                break;
        }
    }

    public int getTextureId() {
        return textureId;
    }


    public CameraRender getCameraRender() {
        return cameraRender;
    }

    public interface OnSurfaceCreate {
        void onSurfaceCreate(int textureId, EGLContext eglContext);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        cameraRender.release();
        if (yuCamera != null) {
            yuCamera.stopPreview();
        }

    }

    public int getCameraOrient() {
        return cameraId;
    }

    public void setCameraOrient(int cameraId) {
        this.cameraId = cameraId;
    }
}
