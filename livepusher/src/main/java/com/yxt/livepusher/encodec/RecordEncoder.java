package com.yxt.livepusher.encodec;

import android.content.Context;

public class RecordEncoder extends BaseVideoEncoder {
    private EncodecRender encodecRender;
    public RecordEncoder(Context context,int textureId) {
        super();
        encodecRender = new EncodecRender(context,textureId);
        setRender(encodecRender);
    }

    public EncodecRender getRender(){
        return encodecRender;
    }
}
