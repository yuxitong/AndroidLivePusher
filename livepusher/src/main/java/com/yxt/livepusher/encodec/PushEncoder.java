package com.yxt.livepusher.encodec;

import android.content.Context;

public class PushEncoder extends BasePushEncoder {
    private EncodecRender encodecRender;

    public PushEncoder(Context context, int textureId) {
        super();
        encodecRender = new EncodecRender(context, textureId);
        setRender(encodecRender);
    }
    public EncodecRender getRender(){
        return encodecRender;
    }
}
