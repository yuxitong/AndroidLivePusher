package com.yxt.livepusher.encodec;

import android.content.Context;

import com.yxt.livepusher.egl.BaseRendeer;
import com.yxt.livepusher.egl.YUEGLSurfaceView;


public class EncodecRender extends BaseRendeer implements YUEGLSurfaceView.YuGLRender {

    private int textureId;

    public EncodecRender(Context context, int textureId) {
        super(context);
        this.textureId = textureId;
    }

    @Override
    public void onSurfaceCreated() {
        super.onCreate();
    }

    @Override
    public void onSurfaceChanged(int width, int height) {
        super.onChange(width, height);
    }

    @Override
    public void onDrawFrame() {
        super.onDrawFrame(textureId);
    }

}
