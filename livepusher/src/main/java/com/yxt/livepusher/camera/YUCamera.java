package com.yxt.livepusher.camera;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import java.io.IOException;
import java.util.List;

public class YUCamera {

    private SurfaceTexture surfaceTexture;

    private Camera camera;

    private int width;
    private int height;

    public YUCamera(int width, int height) {
//        Display display = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
//        width = display.getWidth();
//        height = display.getHeight();
        this.width = width;
        this.height = height;
        Log.e("width", width + "  " + height);
    }

    public void setWidthAndHeight(int width, int height) {
        this.width = width;
        this.height = height;
    }
    public void initCamera(SurfaceTexture surfaceTexture, int cameraId) {
        this.surfaceTexture = surfaceTexture;
        setCameraParme(cameraId);
    }

    private void setCameraParme(int cameraId) {
        try {
            camera = Camera.open(cameraId);
            camera.setPreviewTexture(surfaceTexture);
            Camera.Parameters parameters = camera.getParameters();
            parameters.setFlashMode("off");
            parameters.setPreviewFormat(ImageFormat.NV21);

            Camera.Size size = getFitSize(parameters.getSupportedPictureSizes());
            if (width > height)
                parameters.setPictureSize(size.width, size.height);
            else
                parameters.setPictureSize(size.width, size.height);


            size = getFitSize(parameters.getSupportedPreviewSizes());
            if (width > height)
                parameters.setPreviewSize(size.width, size.height);
            else
                parameters.setPreviewSize(size.width, size.height);

            camera.setParameters(parameters);
            camera.startPreview();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void stopPreview() {
        if (camera != null) {
            camera.stopPreview();
            camera.release();
            camera = null;
        }
    }

    public void changeCamera(int cameraId) {
        if (camera != null) {
            stopPreview();
        }
        setCameraParme(cameraId);
    }

    private Camera.Size getFitSize(List<Camera.Size> sizes) {
        int widtha = width;
        int heightt = height;
        if (widtha < heightt) {
            int t = heightt;
            heightt = widtha;
            widtha = t;
        }
        for (Camera.Size size : sizes) {
            if (1.0f * size.width / size.height == 1.0f * widtha / heightt) {
                return size;
            }
        }
        return sizes.get(0);
    }
}
