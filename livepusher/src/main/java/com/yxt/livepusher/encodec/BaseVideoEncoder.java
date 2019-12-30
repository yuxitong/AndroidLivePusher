package com.yxt.livepusher.encodec;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;
import android.util.TimeUtils;
import android.view.Surface;

import com.yxt.livepusher.egl.EglHelper;
import com.yxt.livepusher.egl.YUEGLSurfaceView;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;

import javax.microedition.khronos.egl.EGLContext;

/**
 * 录制视频编码器
 */
public abstract class BaseVideoEncoder {

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

    private MediaMuxer mediaMuxer;
    private boolean encodecStart;
    private boolean audioExit;
    private boolean videoExit;

    private EGLMediaThread eGLMediaThread;
    private VideoEncodecThread videoEncodecThread;
    private AudioEncodecThread audioEncodecThread;


    private final Object audioLock = new Object();
    private final Object videoLock = new Object();


    private YUEGLSurfaceView.YuGLRender gLRender;

    public final static int RENDERMODE_WHEN_DIRTY = 0;
    public final static int RENDERMODE_CONTINUOUSLY = 1;
    private int mRenderMode = RENDERMODE_CONTINUOUSLY;

    private OnMediaInfoListener onMediaInfoListener;

    private int fps = 15;

    public BaseVideoEncoder() {
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


    public void initEncodec(EGLContext eglContext, String savePath, int width, int height, int sampleRate, int channelCount) {
        this.width = width;
        this.height = height;
        this.eglContext = eglContext;
        initMediaEncodec(savePath, width, height, sampleRate, channelCount);
    }

    public void setFps(int fps) {
        this.fps = fps;
    }

    public void startRecord(String savePath) {
        if (surface != null && eglContext != null) {
            audioPts = 0;
            audioExit = false;
            videoExit = false;
            encodecStart = false;

            eGLMediaThread = new EGLMediaThread(new WeakReference<BaseVideoEncoder>(this));
            videoEncodecThread = new VideoEncodecThread(new WeakReference<BaseVideoEncoder>(this));
            audioEncodecThread = new AudioEncodecThread(new WeakReference<BaseVideoEncoder>(this));
            eGLMediaThread.isCreate = true;
            eGLMediaThread.isChange = true;
            eGLMediaThread.start();
            videoEncodecThread.start();
            audioEncodecThread.start();
        }
    }

    public void stopRecord() {
        if (eGLMediaThread != null && videoEncodecThread != null && audioEncodecThread != null) {
            videoEncodecThread.exit();
            audioEncodecThread.exit();
            eGLMediaThread.onDestory();
            synchronized (audioLock) {
                audioLock.notifyAll();
            }
            synchronized (videoLock) {
                videoLock.notifyAll();
            }
            videoEncodecThread = null;
            eGLMediaThread = null;
            audioEncodecThread = null;
        }
    }

    private void initMediaEncodec(String savePath, int width, int height, int sampleRate, int channelCount) {
        try {
            mediaMuxer = new MediaMuxer(savePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            initVideoEncodec(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
            initAudioEncodec(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private void initVideoEncodec(String mimeType, int width, int height) {
        try {
            videoBufferinfo = new MediaCodec.BufferInfo();
            videoFormat = MediaFormat.createVideoFormat(mimeType, width, height);
            videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, width * height * 4);
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

    private void initAudioEncodec(String mimeType, int sampleRate, int channelCount) {
        try {
            this.sampleRate = sampleRate;
            audioBufferinfo = new MediaCodec.BufferInfo();
            audioFormat = MediaFormat.createAudioFormat(mimeType, sampleRate, channelCount);
            audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, 96000);
            audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 4096);

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
        private WeakReference<BaseVideoEncoder> encoder;
        private EglHelper eglHelper;
        private Object object;

        private boolean isExit = false;
        private boolean isCreate = false;
        private boolean isChange = false;
        private boolean isStart = false;

        private int width;
        private int height;
        private int mRenderMode;
        private YUEGLSurfaceView.YuGLRender gLRender;

        public EGLMediaThread(WeakReference<BaseVideoEncoder> encoder) {
            this.encoder = encoder;
            setName("RecordMediaThread");
            mRenderMode = encoder.get().mRenderMode;
            width = encoder.get().width;
            height = encoder.get().height;
            gLRender = encoder.get().gLRender;
        }

        @Override
        public void run() {
            super.run();
            isExit = false;
            isStart = false;
            object = new Object();
            eglHelper = new EglHelper();
            eglHelper.initEgl(encoder.get().surface, encoder.get().eglContext,width,height);

            while (true) {
                if (isExit) {
                    release();
                    break;
                }
                if (encoder.get() == null) {
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
                            Thread.sleep(1000 / encoder.get().fps);
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
                if (encoder != null && encoder.get() != null && encoder.get().videoLock != null)
                    synchronized (encoder.get().videoLock) {
                        if (encoder != null && encoder.get() != null && encoder.get().videoLock != null)
                            encoder.get().videoLock.notifyAll();
                    }
            }

        }

        private void onCreate() {
            if (isCreate && gLRender != null) {
                isCreate = false;
                gLRender.onSurfaceCreated();
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
        private WeakReference<BaseVideoEncoder> encoder;

        private boolean isExit;

        private MediaCodec videoEncodec;
        private MediaCodec.BufferInfo videoBufferinfo;
        private MediaMuxer mediaMuxer;

        private int videoTrackIndex = -1;
        private long pts;
        private OnMediaInfoListener onMediaInfoListener;


        public VideoEncodecThread(WeakReference<BaseVideoEncoder> encoder) {
            setName("RecordVideoThread");
            this.encoder = encoder;
            videoEncodec = encoder.get().videoEncodec;
            videoBufferinfo = encoder.get().videoBufferinfo;
            mediaMuxer = encoder.get().mediaMuxer;
            onMediaInfoListener = encoder.get().onMediaInfoListener;
            videoTrackIndex = -1;
        }

        @Override
        public void run() {
            super.run();
            pts = 0;
            videoTrackIndex = -1;
            isExit = false;
            videoEncodec.start();
            while (true) {
                if (isExit) {

                    videoEncodec.stop();
//                    videoEncodec.release();
                    videoEncodec = null;
                    if (encoder.get() != null)
                        encoder.get().videoExit = true;
                    else {
                        if (mediaMuxer != null) {
                            try {
                                mediaMuxer.stop();
                                mediaMuxer.release();
                                mediaMuxer = null;
                            } catch (Exception e) {
                                if (mediaMuxer != null) {
                                    mediaMuxer.release();
                                    mediaMuxer = null;
                                }
                            }
                        }
                    }
                    if (encoder.get() != null)
                        if (encoder.get().audioExit) {
                            try {
                                mediaMuxer.stop();
                                mediaMuxer.release();
                                mediaMuxer = null;
                            } catch (Exception e) {
                                if (mediaMuxer != null) {
                                    mediaMuxer.release();
                                    mediaMuxer = null;
                                }
                            }
                        }
                    Log.e("yxt", "录制完成");
                    break;
                }

                int outputBufferIndex = videoEncodec.dequeueOutputBuffer(videoBufferinfo, 0);

                if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    videoTrackIndex = mediaMuxer.addTrack(videoEncodec.getOutputFormat());
                    if (encoder != null && encoder.get() != null && encoder.get().audioEncodecThread != null && encoder.get().audioEncodecThread.audioTrackIndex != -1) {
                        mediaMuxer.start();
                        encoder.get().encodecStart = true;
                    }
                } else {
                    if (outputBufferIndex >= 0) {
                        while (outputBufferIndex >= 0) {
                            if (encoder.get().encodecStart) {
                                ByteBuffer outputBuffer = videoEncodec.getOutputBuffers()[outputBufferIndex];
                                outputBuffer.position(videoBufferinfo.offset);
                                outputBuffer.limit(videoBufferinfo.offset + videoBufferinfo.size);
                                //
                                if (pts == 0) {
                                    pts = videoBufferinfo.presentationTimeUs;
                                }
                                videoBufferinfo.presentationTimeUs = videoBufferinfo.presentationTimeUs - pts;

                                mediaMuxer.writeSampleData(videoTrackIndex, outputBuffer, videoBufferinfo);
                                if (onMediaInfoListener != null) {
                                    onMediaInfoListener.onMediaTime((int) (videoBufferinfo.presentationTimeUs / 1000000));
                                }
                            }
                            videoEncodec.releaseOutputBuffer(outputBufferIndex, false);
                            outputBufferIndex = videoEncodec.dequeueOutputBuffer(videoBufferinfo, 0);
                        }
                    } else {
                        if (encoder != null && encoder.get() != null && encoder.get().videoLock != null)
                            synchronized (encoder.get().videoLock) {
                                try {
                                    if (encoder != null && encoder.get() != null && encoder.get().videoLock != null)
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

        private WeakReference<BaseVideoEncoder> encoder;
        private boolean isExit;

        private MediaCodec audioEncodec;
        private MediaCodec.BufferInfo bufferInfo;
        private MediaMuxer mediaMuxer;

        private int audioTrackIndex = -1;
        long pts;


        public AudioEncodecThread(WeakReference<BaseVideoEncoder> encoder) {
            setName("RecordAudioThread");
            this.encoder = encoder;
            audioEncodec = encoder.get().audioEncodec;
            bufferInfo = encoder.get().audioBufferinfo;
            mediaMuxer = encoder.get().mediaMuxer;
            audioTrackIndex = -1;

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
//                    audioEncodec.release();
                    audioEncodec = null;
                    if (encoder != null && encoder.get() != null)
                        encoder.get().audioExit = true;
                    if (encoder.get().videoExit) {
                        try {
                            mediaMuxer.stop();
                            mediaMuxer.release();
                            mediaMuxer = null;
                        } catch (Exception e) {
                            if (mediaMuxer != null) {
                                mediaMuxer.release();
                                mediaMuxer = null;
                            }
                        }
                    }
                    break;
                }

                int outputBufferIndex = audioEncodec.dequeueOutputBuffer(bufferInfo, 0);
                if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (mediaMuxer != null) {
                        audioTrackIndex = mediaMuxer.addTrack(audioEncodec.getOutputFormat());
                        if (encoder != null && encoder.get() != null && encoder.get().videoEncodecThread != null && encoder.get().videoEncodecThread.videoTrackIndex != -1) {
                            mediaMuxer.start();
                            encoder.get().encodecStart = true;
                        }
                    }
                } else {
                    if (outputBufferIndex >= 0) {
                        while (outputBufferIndex >= 0) {
                            if (encoder != null && encoder.get() != null && encoder.get().encodecStart) {

                                ByteBuffer outputBuffer = audioEncodec.getOutputBuffers()[outputBufferIndex];
                                outputBuffer.position(bufferInfo.offset);
                                outputBuffer.limit(bufferInfo.offset + bufferInfo.size);
                                if (pts == 0) {
                                    pts = bufferInfo.presentationTimeUs;
//                                    pts = System.currentTimeMillis();
                                }
                                bufferInfo.presentationTimeUs = bufferInfo.presentationTimeUs - pts;
//                                bufferInfo.presentationTimeUs = System.nanoTime();
                                mediaMuxer.writeSampleData(audioTrackIndex, outputBuffer, bufferInfo);
                            }
                            audioEncodec.releaseOutputBuffer(outputBufferIndex, false);
                            outputBufferIndex = audioEncodec.dequeueOutputBuffer(bufferInfo, 0);
                        }
                    } else {
                        if (encoder != null & encoder.get() != null && encoder.get().audioLock != null)
                            synchronized (encoder.get().audioLock) {
                                try {
                                    if (encoder != null && encoder.get() != null && encoder.get().audioLock != null)
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
    }

    private long getAudioPts(int size, int sampleRate) {
        audioPts += (long) (1.0 * size / (sampleRate * 2 * 2) * 1000000.0);
        return audioPts;
    }

}
