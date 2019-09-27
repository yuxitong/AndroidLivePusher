package com.yxt.livepusher.test;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;

import com.yxt.livepusher.camera.YUCamera;
import com.yxt.livepusher.egl.YUEGLSurfaceView;
import com.yxt.livepusher.encodec.BasePushEncoder;
import com.yxt.livepusher.encodec.PushEncoder;
import com.yxt.livepusher.encodec.RecordEncoder;
import com.yxt.livepusher.network.rtmp.ConnectListenr;
import com.yxt.livepusher.network.rtmp.RtmpPush;
import com.yxt.livepusher.network.rtmp.RtmpPushBack;
import com.yxt.livepusher.utils.AudioRecordUitl;


/**
 * 这里调用所有模块
 */
public class YxtStream {
    public Context context;
    private AudioRecordUitl audioRecordUitl = null;
    private YUCamera yuCamera = null;

    private int width = 640;
    private int height = 480;

    private int cameraFacing = 0;

    private int fps = 15;

    public static final int FACING_BACK = 0;
    public static final int FACING_FRONT = 1;
    private SurfaceView surfaceView = null;
    private YUEGLSurfaceView glSurfaceView = null;
    private CameraGLRender render = null;
    private PushEncoder pushRtmpEncoder = null;
    private RecordEncoder recordEncoder = null;


    private EGLSurface eglSurface = null;
    ;

    private boolean isRTMPPusher = false;
    public RtmpPush rtmpPush;
    public RtmpPushBack rtmpPushBack;
    private boolean initFinish = false;
    private int textureId = -1;


    public YxtStream(SurfaceView surfaceView, Context context) {
        this.context = context.getApplicationContext();
        this.surfaceView = surfaceView;
    }

//    public YxtStream(YUEGLSurfaceView glSurfaceView, Context context) {
//        this.context = context.getApplicationContext();
//        this.glSurfaceView = glSurfaceView;
//    }

    public YxtStream(Context context) {
        this.context = context.getApplicationContext();
    }

    private void initModules() {
        //加载音频模块
        this.yuCamera = new YUCamera(width, height);
        this.eglSurface = new EGLSurface();
        this.eglSurface.setFps(fps);
        this.render = new CameraGLRender(context, width, height);
        this.eglSurface.setRender(render);
        render.setOnSurfaceCreateListener(new CameraGLRender.OnSurfaceCreateListener() {
            @Override
            public void onSurfaceCreate(SurfaceTexture surfaceTexture, int textureId) {
                YxtStream.this.textureId = textureId;
                initFinish = true;
                yuCamera.initCamera(surfaceTexture, cameraFacing);
            }
        });
        prevewAngle(context);
        this.audioRecordUitl = new AudioRecordUitl();
        audioRecordUitl.setOnRecordLisener(new AudioRecordUitl.OnRecordLisener() {
            @Override
            public void recordByte(byte[] audioData, int readSize) {
                if (audioRecordUitl != null && audioRecordUitl.isStart()) {
                    if (recordEncoder != null)
                        recordEncoder.putPCMData(audioData, readSize);
                    if (pushRtmpEncoder != null) {
                        pushRtmpEncoder.putPCMData(audioData, readSize);
                    }
                }
            }
        });
        audioRecordUitl.startRecord();
        if (glSurfaceView != null) {
//            glSurfaceView.setFps(fps);
//            glSurfaceView.setRender(render);
        } else if (surfaceView != null) {
            surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(SurfaceHolder holder) {
                    eglSurface.setSurfaceAndEglContext(surfaceView.getHolder().getSurface(), null);
                    eglSurface.surfaceCreated();
                }

                @Override
                public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                    eglSurface.surfaceChanged(format, width, height);
                }

                @Override
                public void surfaceDestroyed(SurfaceHolder holder) {
                    if (eglSurface != null)
                        eglSurface.surfaceDestroyed(holder);
                }
            });
        } else {
            eglSurface.star();
        }


    }


    public void setWidthAndHeight(int width, int height) {
        this.width = width;
        this.height = height;
        if (yuCamera != null)
            yuCamera.setWidthAndHeight(width, height);
    }

    public void setCameraFacing(int cameraFacing) {
        this.cameraFacing = cameraFacing;
    }

    public void setFps(int fps) {
        this.fps = fps;
    }

    public void startRecord(String savePath) {
        if (initFinish) {
            if (recordEncoder == null) {
                recordEncoder = new RecordEncoder(context, textureId);
                recordEncoder.setFps(fps);
                recordEncoder.initEncodec(eglSurface.getEglContext(), savePath, width, height, 44100, 2);
                recordEncoder.startRecord(savePath);
            }
        }

    }

    public void stopRecord() {
        if (initFinish) {
            if (recordEncoder != null) {
                recordEncoder.stopRecord();
                recordEncoder = null;
            }
        }
    }


    public void setWatermark(int textSize, String textColor, String bgColor, int padding, String speed, String vehicleLicence, String address, String time) {

        if (render != null) {
            render.getCameraFboRender().setBitmap(textSize, textColor, bgColor, padding, speed, vehicleLicence, address, time);
        }
        if (pushRtmpEncoder != null && pushRtmpEncoder.getRender() != null) {
            pushRtmpEncoder.getRender().setBitmap(textSize, textColor, bgColor, padding, speed, vehicleLicence, address, time);
        }
        if (recordEncoder != null && recordEncoder.getRender() != null) {
            recordEncoder.getRender().setBitmap(textSize, textColor, bgColor, padding, speed, vehicleLicence, address, time);
        }
    }


    long startRtmpTime;

    /**
     * 开始推送rtmp
     *
     * @param bitstreamType 0是 主码流  1是子码流
     * @param str
     */
    public void startRtmp(final int bitstreamType, String str) {
        if (startRtmpTime == 0) {
            startRtmpTime = System.currentTimeMillis();
        }
        if (System.currentTimeMillis() - startRtmpTime < 1 * 1000) {
            return;
        }
        stopRtmpStream();
        if (initFinish) {
            if (cameraFacing == FACING_FRONT) {
                rtmpPush = new RtmpPush();
                rtmpPush.initLivePush(str);
                rtmpPush.setConnectListenr(new ConnectListenr() {
                    @Override
                    public void onConnecting() {
                        Log.e("yxt", "连接服务器");
                    }

                    @Override
                    public void onConnectSuccess() {
                        isRTMPPusher = true;
                        Log.e("yxt", "连接成功");
                        pushRtmpEncoder = new PushEncoder(context, textureId);
                        pushRtmpEncoder.setStreamType(BasePushEncoder.STREAM_TYPE_RTMP);

                        if (bitstreamType == 0) {
                            //设置帧率
                            pushRtmpEncoder.setFps(fps);
                            //设置码率
                            pushRtmpEncoder.setBitrate(width * height * 4);
                        } else {
                            pushRtmpEncoder.setFps(15);
                            pushRtmpEncoder.setBitrate(250000);
                        }
                        pushRtmpEncoder.initEncodec(eglSurface.getEglContext(), width, height);
                        pushRtmpEncoder.setOnMediaInfoListener(new BasePushEncoder.OnMediaInfoListener() {
                            @Override
                            public void onMediaTime(int times) {

                            }

                            @Override
                            public void onSPSPPSInfo(byte[] sps, byte[] pps) {
                                if (rtmpPush != null) {
                                    rtmpPush.pushSPSPPS(sps, pps);
                                }
                            }

                            @Override
                            public void onVideoInfo(byte[] data, boolean keyframe) {
                                if (rtmpPush != null) {
                                    rtmpPush.pushVideoData(data, keyframe);
                                }
                            }

                            @Override
                            public void onVideoSPSPPS(byte[] spsppsdata) {
                            }

                            @Override
                            public void onAudioInfo(byte[] data) {
                                if (rtmpPush != null)
                                    rtmpPush.pushAudioData(data);
                            }
                        });

                        pushRtmpEncoder.startRecord();
                    }

                    @Override
                    public void onConnectFail(String msg) {
                        isRTMPPusher = false;
                        Log.e("yxt", "连接失败  " + msg);

                    }
                });
            } else {
                rtmpPushBack = new RtmpPushBack();
                rtmpPushBack.initLivePush(str);
                rtmpPushBack.setConnectListenr(new ConnectListenr() {
                    @Override
                    public void onConnecting() {
                        Log.e("yxt", "连接服务器1");
                    }

                    @Override
                    public void onConnectSuccess() {
                        Log.e("yxt", "连接成功1");
                        isRTMPPusher = true;
                        pushRtmpEncoder = new PushEncoder(context, textureId);
                        pushRtmpEncoder.setStreamType(BasePushEncoder.STREAM_TYPE_RTMP);

                        if (bitstreamType == 0) {
                            pushRtmpEncoder.setFps(fps);
                            pushRtmpEncoder.setBitrate(width * height * 4);
                        } else {
                            pushRtmpEncoder.setFps(15);
                            pushRtmpEncoder.setBitrate(250000);
                        }
                        pushRtmpEncoder.initEncodec(eglSurface.getEglContext(), width, height);
                        pushRtmpEncoder.setOnMediaInfoListener(new BasePushEncoder.OnMediaInfoListener() {
                            @Override
                            public void onMediaTime(int times) {

                            }

                            @Override
                            public void onSPSPPSInfo(byte[] sps, byte[] pps) {

                                if (rtmpPushBack != null) {
                                    rtmpPushBack.pushSPSPPS(sps, pps);
                                }
                            }

                            @Override
                            public void onVideoInfo(byte[] data, boolean keyframe) {
                                if (rtmpPushBack != null) {
                                    rtmpPushBack.pushVideoData(data, keyframe);
                                }
                            }

                            @Override
                            public void onVideoSPSPPS(byte[] spsppsdata) {
                            }

                            @Override
                            public void onAudioInfo(byte[] data) {
                                if (rtmpPushBack != null)
                                    rtmpPushBack.pushAudioData(data);
                            }
                        });

                        pushRtmpEncoder.startRecord();
                    }

                    @Override
                    public void onConnectFail(String msg) {
                        Log.e("yxt", "连接失败1  " + msg);
                        isRTMPPusher = false;

                    }
                });
            }


        }
    }

    public void stopRtmpStream() {
        isRTMPPusher = false;
        if (rtmpPush != null) {
            rtmpPush.stopPush();
            rtmpPush = null;
        }
        if (rtmpPushBack != null) {
            rtmpPushBack.stopPush();
            rtmpPushBack = null;
        }
        if (pushRtmpEncoder != null) {
            pushRtmpEncoder.stopRecord();
            pushRtmpEncoder = null;
        }
    }

    public boolean isRTMPPusher() {
        return isRTMPPusher;
    }

    public void star() {
        initModules();
    }

    public void prevewAngle(Context context) {
        int angle = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
        Log.e("angleTotal", "  " + angle);
//        cameraRender.restMatrix();
        switch (angle) {
            case Surface.ROTATION_0:
                Log.e("angle", "0");
                if (cameraFacing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                    render.setAngle(90, 0, 0, 1);
//                    render.setAngle(180, 0, 0, 1);
                    render.setAngle(180, 1, 0, 0);
                } else {
//                    render.setAngle(180, 0, 0, 1);
                    render.setAngle(90, 0, 0, 1);
                }
                break;
            case Surface.ROTATION_90:
                Log.e("angle", "90");
                if (cameraFacing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                    render.setAngle(180, 0, 0, 1);
                    render.setAngle(180, 0, 1, 0);
                } else {
                    render.setAngle(180, 0, 0, 1);

                }
                break;
            case Surface.ROTATION_180:
                Log.e("angle", "180");
                if (cameraFacing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                    render.setAngle(90, 0, 0, 1);
                    render.setAngle(180, 0, 1, 0);
                } else {
                    render.setAngle(-90, 0, 0, 1);
                }
                break;
            case Surface.ROTATION_270:
                Log.e("angle", "270");
                if (cameraFacing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                    render.setAngle(180, 0, 1, 0);
                }
                break;
        }
    }

    public CameraGLRender getCameraRender() {
        if (render != null)
            return render;
        return null;
    }


    public void onDestory() {

        if (audioRecordUitl != null) {
            audioRecordUitl.stopRecord();
            audioRecordUitl = null;
        }

        stopRecord();
        stopRtmpStream();
        if (rtmpPush != null) {
            rtmpPush.stopPush();
            rtmpPush = null;
        }
        if (pushRtmpEncoder != null) {
            pushRtmpEncoder.stopRecord();
            pushRtmpEncoder = null;
        }
        if (render != null) {
            render.release();
            render = null;
        }
        if (eglSurface != null) {
            eglSurface.surfaceDestroyed(null);
            eglSurface = null;
        }
        if (recordEncoder != null) {
            recordEncoder.stopRecord();
            recordEncoder = null;
        }

        if (yuCamera != null) {
            yuCamera.stopPreview();
            yuCamera = null;
        }
        initFinish = false;
        textureId = -1;

    }
}
