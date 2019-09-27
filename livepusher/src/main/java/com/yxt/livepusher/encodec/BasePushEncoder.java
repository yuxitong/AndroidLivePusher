package com.yxt.livepusher.encodec;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;


import com.yxt.livepusher.egl.EglHelper;
import com.yxt.livepusher.egl.YUEGLSurfaceView;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;

import javax.microedition.khronos.egl.EGLContext;

public abstract class BasePushEncoder {

    private Surface surface;
    private EGLContext eglContext;

    private int width;
    private int height;

    private MediaCodec videoEncodec;
    private MediaFormat videoFormat;
    private MediaCodec.BufferInfo videoBufferinfo;

    private MediaCodec audioEncodec;
    private MediaFormat audioFormat;
    private MediaCodec.BufferInfo audioBufferinfo;
    private long audioPts = 0;
    private int sampleRate;

    private EGLMediaThread mEGLMediaThread;
    private VideoEncodecThread videoEncodecThread;
    private AudioEncodecThread audioEncodecThread;
    private YUEGLSurfaceView.YuGLRender gLRender;

    public final static int RENDERMODE_WHEN_DIRTY = 0;
    public final static int RENDERMODE_CONTINUOUSLY = 1;
    private int mRenderMode = RENDERMODE_CONTINUOUSLY;

    private int fps = 15;
    private int bitrate = 250000;
    private OnMediaInfoListener onMediaInfoListener;

    private final Object audioLock = new Object();
    private final Object videoLock = new Object();

    public static final int STREAM_TYPE_JT1078 = 1;
    public static final int STREAM_TYPE_RTMP = 0;
    private int streamType = 0;

    public BasePushEncoder() {
    }

    public void setRender(YUEGLSurfaceView.YuGLRender gLRender) {
        this.gLRender = gLRender;
    }

    public void setmRenderMode(int mRenderMode) {
        if (gLRender == null) {
            throw new RuntimeException("must set render before");
        }
        this.mRenderMode = mRenderMode;
    }

    public void setOnMediaInfoListener(OnMediaInfoListener onMediaInfoListener) {
        this.onMediaInfoListener = onMediaInfoListener;
    }

    public void initEncodec(EGLContext eglContext, int width, int height) {
        this.width = width;
        this.height = height;
        this.eglContext = eglContext;
        initMediaEncodec(width, height, 44100, 2);
    }

    public void startRecord() {
        if (surface != null && eglContext != null) {

            audioPts = 0;

            mEGLMediaThread = new EGLMediaThread(new WeakReference<BasePushEncoder>(this));
            videoEncodecThread = new VideoEncodecThread(new WeakReference<BasePushEncoder>(this));
            audioEncodecThread = new AudioEncodecThread(new WeakReference<BasePushEncoder>(this));
            mEGLMediaThread.isCreate = true;
            mEGLMediaThread.isChange = true;
            mEGLMediaThread.start();
            videoEncodecThread.start();
            audioEncodecThread.start();
        }
    }

    public void setFps(int fps) {
        this.fps = fps;
    }

    public void stopRecord() {
        if (mEGLMediaThread != null && videoEncodecThread != null && audioEncodecThread != null) {
            videoEncodecThread.exit();
            audioEncodecThread.exit();
            mEGLMediaThread.onDestory();
            synchronized (audioLock) {
                audioLock.notifyAll();
            }
            synchronized (videoLock) {
                videoLock.notifyAll();
            }
            videoEncodecThread = null;
            mEGLMediaThread = null;
            audioEncodecThread = null;
        }
    }

    private void initMediaEncodec(int width, int height, int sampleRate, int channelCount) {
        initVideoEncodec(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
        initAudioEncodec(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount);
    }


    private void initVideoEncodec(String mimeType, int width, int height) {
        try {
            videoBufferinfo = new MediaCodec.BufferInfo();
            videoFormat = MediaFormat.createVideoFormat(mimeType, width, height);
            videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
            videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
            videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 3);

            videoEncodec = MediaCodec.createEncoderByType(mimeType);
            videoEncodec.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

            surface = videoEncodec.createInputSurface();

        } catch (IOException e) {
            e.printStackTrace();
            videoEncodec = null;
            videoFormat = null;
            videoBufferinfo = null;
        }

    }

    public void setBitrate(int bitrate) {
        this.bitrate = bitrate;
    }

    public void setStreamType(int streamType) {
        this.streamType = streamType;
    }

    private void initAudioEncodec(String mimeType, int sampleRate, int channelCount) {
        try {
            this.sampleRate = sampleRate;
            audioBufferinfo = new MediaCodec.BufferInfo();
            audioFormat = MediaFormat.createAudioFormat(mimeType, sampleRate, channelCount);
            audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, 96000);
            audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 4096 * 10);

            audioEncodec = MediaCodec.createEncoderByType(mimeType);
            audioEncodec.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

        } catch (IOException e) {
            e.printStackTrace();
            audioBufferinfo = null;
            audioFormat = null;
            audioEncodec = null;
        }
    }

    public void putPCMData(byte[] buffer, int size) {
        try {
            if (audioEncodecThread != null && !audioEncodecThread.isExit && buffer != null && size > 0) {
                int inputBufferindex = audioEncodec.dequeueInputBuffer(0);
                if (inputBufferindex >= 0) {
                    ByteBuffer byteBuffer = audioEncodec.getInputBuffers()[inputBufferindex];
                    byteBuffer.clear();
                    byteBuffer.put(buffer);
                    long pts = getAudioPts(size, sampleRate);
                    audioEncodec.queueInputBuffer(inputBufferindex, 0, size, pts, 0);
                }
                synchronized (audioLock) {
                    audioLock.notifyAll();
                }
            }
        } catch (Exception e) {
            synchronized (audioLock) {
                audioLock.notifyAll();
            }
        }
    }

    static class EGLMediaThread extends Thread {
        private WeakReference<BasePushEncoder> encoder;
        private EglHelper eglHelper;
        private Object object;

        private boolean isExit = false;
        private boolean isCreate = false;
        private boolean isChange = false;
        private boolean isStart = false;
        private int width;
        private int height;

        private int fps;
        private int mRenderMode;
        private YUEGLSurfaceView.YuGLRender gLRender;

        public EGLMediaThread(WeakReference<BasePushEncoder> encoder) {
            this.encoder = encoder;
            width = encoder.get().width;
            height = encoder.get().height;
            mRenderMode = encoder.get().mRenderMode;
            fps = encoder.get().fps;
            gLRender = encoder.get().gLRender;
        }

        @Override
        public void run() {
            super.run();
            isExit = false;
            isStart = false;
            object = new Object();
            eglHelper = new EglHelper();
            eglHelper.initEgl(encoder.get().surface, encoder.get().eglContext);

            while (true) {
                if (isExit) {
                    release();
                    break;
                }

                if (isStart) {
                    if (mRenderMode == RENDERMODE_WHEN_DIRTY) {
                        synchronized (object) {
                            try {
                                object.wait();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    } else if (mRenderMode == RENDERMODE_CONTINUOUSLY) {
                        try {
                            Thread.sleep(1000 / fps);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    } else {
                        throw new RuntimeException("mRenderMode is wrong value");
                    }
                }
                onCreate();
                onChange(width, height);
                onDraw();
                isStart = true;
                if (encoder != null && encoder.get() != null)
                    synchronized (encoder.get().videoLock) {
                        if (encoder != null && encoder.get() != null)
                            encoder.get().videoLock.notifyAll();
                    }
            }

        }

        private void onCreate() {
            if (isCreate && gLRender != null) {
                isCreate = false;
                encoder.get().gLRender.onSurfaceCreated();
            }
        }

        private void onChange(int width, int height) {
            if (isChange && gLRender != null) {
                isChange = false;
                gLRender.onSurfaceChanged(width, height);
            }
        }

        private void onDraw() {
            if (gLRender != null && eglHelper != null) {
                gLRender.onDrawFrame();
                if (!isStart) {
                    gLRender.onDrawFrame();
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
            if (gLRender != null) {
                gLRender.onDeleteTextureId();
            }
            if (eglHelper != null) {
                eglHelper.onDestoryEgl();
                eglHelper = null;
                object = null;
                encoder = null;
            }

        }
    }

    static class VideoEncodecThread extends Thread {
        private WeakReference<BasePushEncoder> encoder;

        private boolean isExit;

        private MediaCodec videoEncodec;
        private MediaCodec.BufferInfo videoBufferinfo;

        private long pts;
        private byte[] sps;
        private byte[] pps;
        private boolean keyFrame = false;

        private boolean isFirst;

        public VideoEncodecThread(WeakReference<BasePushEncoder> encoder) {
            this.encoder = encoder;
            videoEncodec = encoder.get().videoEncodec;
            videoBufferinfo = encoder.get().videoBufferinfo;
            isFirst = false;
        }

        @Override
        public void run() {
            super.run();
            pts = 0;
            isExit = false;
            videoEncodec.start();
            isFirst = false;
            while (true) {
                if (isExit) {
                    isFirst = false;
                    videoEncodec.stop();
                    videoEncodec.release();
                    videoEncodec = null;
                    Log.d("yxt", "录制完成");

                    break;
                }

                int outputBufferIndex = videoEncodec.dequeueOutputBuffer(videoBufferinfo, 0);
                keyFrame = false;
                if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    Log.d("yxt", "INFO_OUTPUT_FORMAT_CHANGED");

                    ByteBuffer spsb = videoEncodec.getOutputFormat().getByteBuffer("csd-0");
                    sps = new byte[spsb.remaining()];
                    spsb.get(sps, 0, sps.length);

                    ByteBuffer ppsb = videoEncodec.getOutputFormat().getByteBuffer("csd-1");
                    pps = new byte[ppsb.remaining()];
                    ppsb.get(pps, 0, pps.length);

                    Log.d("yxt", "sps:" + byteToHex(sps));
                    Log.d("yxt", "pps:" + byteToHex(pps));

                } else {
                    if (outputBufferIndex >= 0) {
                        while (outputBufferIndex >= 0) {
                            ByteBuffer outputBuffer = videoEncodec.getOutputBuffers()[outputBufferIndex];
                            outputBuffer.position(videoBufferinfo.offset);
                            outputBuffer.limit(videoBufferinfo.offset + videoBufferinfo.size);
                            //
                            if (pts == 0) {
                                pts = videoBufferinfo.presentationTimeUs;
                            }
                            videoBufferinfo.presentationTimeUs = videoBufferinfo.presentationTimeUs - pts;
                            byte[] data = new byte[0];
                            if (encoder != null && encoder.get() != null && encoder.get().streamType == BasePushEncoder.STREAM_TYPE_RTMP) {
                                data = new byte[outputBuffer.remaining()];
                                outputBuffer.get(data, 0, data.length);
                                Log.d("yxt", "data:" + byteToHex(data));

                            } else if (encoder != null && encoder.get() != null && encoder.get().streamType == BasePushEncoder.STREAM_TYPE_JT1078) {
                                if (outputBuffer.get(5) == -120) {
                                    data = new byte[outputBuffer.remaining() + sps.length + pps.length];
                                    outputBuffer.get(data, sps.length + pps.length, data.length - sps.length - pps.length);
                                    System.arraycopy(sps, 0, data, 0, sps.length);
                                    System.arraycopy(pps, 0, data, sps.length, pps.length);
                                    outputBuffer.position(videoBufferinfo.offset);
                                } else {
                                    data = new byte[outputBuffer.remaining()];
                                    outputBuffer.get(data, 0, data.length);
                                    outputBuffer.position(videoBufferinfo.offset);
                                }
                            }


//                            if (encoder.get().streamType == BasePushEncoder.STREAM_TYPE_RTMP && !isFirst) {
////                                if (videoBufferinfo.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME) {
//                                Log.e("asdfasdf", "1111111");
//                                isFirst = true;
//                                keyFrame = true;
//                                if (encoder.get().onMediaInfoListener != null) {
//                                    encoder.get().onMediaInfoListener.onSPSPPSInfo(sps, pps);
//                                }
////                                }
//                            } else if (encoder.get().streamType == BasePushEncoder.STREAM_TYPE_JT1078) {
                            if (outputBuffer.get(5) == -120) {
//                            if (videoBufferinfo.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME) {
                                keyFrame = true;
                                if (encoder.get().onMediaInfoListener != null) {
                                    encoder.get().onMediaInfoListener.onSPSPPSInfo(sps, pps);
                                }
                            }
//                            }

                            if (encoder != null && encoder.get() != null && encoder.get().onMediaInfoListener != null) {
                                if (encoder.get().streamType == BasePushEncoder.STREAM_TYPE_RTMP) {
                                    encoder.get().onMediaInfoListener.onVideoInfo(data, keyFrame);
                                } else if (encoder.get().streamType == BasePushEncoder.STREAM_TYPE_JT1078) {
                                    encoder.get().onMediaInfoListener.onVideoSPSPPS(data);
                                }
                                encoder.get().onMediaInfoListener.onMediaTime((int) (videoBufferinfo.presentationTimeUs / 1000000));
                            }
                            videoEncodec.releaseOutputBuffer(outputBufferIndex, false);
                            outputBufferIndex = videoEncodec.dequeueOutputBuffer(videoBufferinfo, 0);
                        }
                    } else {
                        synchronized (encoder.get().videoLock) {
                            try {
                                encoder.get().videoLock.wait();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }
        }

        public void exit() {
            isExit = true;
        }

    }

    static class AudioEncodecThread extends Thread {

        private WeakReference<BasePushEncoder> encoder;
        private boolean isExit;

        private MediaCodec audioEncodec;
        private MediaCodec.BufferInfo bufferInfo;

        long pts;


        public AudioEncodecThread(WeakReference<BasePushEncoder> encoder) {
            this.encoder = encoder;
            audioEncodec = encoder.get().audioEncodec;
            bufferInfo = encoder.get().audioBufferinfo;
        }

        @Override
        public void run() {
            super.run();
            pts = 0;
            isExit = false;
            audioEncodec.start();
            while (true) {
                if (isExit) {
                    //

                    audioEncodec.stop();
                    audioEncodec.release();
                    audioEncodec = null;
                    break;
                }

                int outputBufferIndex = audioEncodec.dequeueOutputBuffer(bufferInfo, 0);
                if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                } else {
                    if (outputBufferIndex >= 0) {
                        while (outputBufferIndex >= 0) {

                            ByteBuffer outputBuffer = audioEncodec.getOutputBuffers()[outputBufferIndex];
                            outputBuffer.position(bufferInfo.offset);
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size);
                            if (pts == 0) {
                                pts = bufferInfo.presentationTimeUs;
                            }
                            bufferInfo.presentationTimeUs = bufferInfo.presentationTimeUs - pts;

                            byte[] data = new byte[outputBuffer.remaining()];
                            outputBuffer.get(data, 0, data.length);
                            if (encoder != null && encoder.get() != null && encoder.get().onMediaInfoListener != null) {
                                encoder.get().onMediaInfoListener.onAudioInfo(data);
                            }

                            audioEncodec.releaseOutputBuffer(outputBufferIndex, false);
                            outputBufferIndex = audioEncodec.dequeueOutputBuffer(bufferInfo, 0);
                        }
                    } else {
                        synchronized (encoder.get().audioLock) {
                            try {
                                encoder.get().audioLock.wait();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }

            }

        }

        public void exit() {
            isExit = true;
        }
    }

    public interface OnMediaInfoListener {
        void onMediaTime(int times);

        void onSPSPPSInfo(byte[] sps, byte[] pps);

        void onVideoInfo(byte[] data, boolean keyframe);

        void onVideoSPSPPS(byte[] spsppsdata);

        void onAudioInfo(byte[] data);

    }

    private long getAudioPts(int size, int sampleRate) {
        audioPts += (long) (1.0 * size / (sampleRate * 2 * 2) * 1000000.0);
        return audioPts;
    }

    public static String byteToHex(byte[] bytes) {
        StringBuffer stringBuffer = new StringBuffer();
        for (int i = 0; i < bytes.length; i++) {
            String hex = Integer.toHexString(bytes[i]);
            if (hex.length() == 1) {
                stringBuffer.append("0" + hex);
            } else {
                stringBuffer.append(hex);
            }
            if (i > 20) {
                break;
            }
        }
        return stringBuffer.toString();
    }

}
