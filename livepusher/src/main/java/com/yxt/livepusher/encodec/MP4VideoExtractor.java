package com.yxt.livepusher.encodec;

import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

import com.yxt.livepusher.utils.MessageConversionUtils;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

public class MP4VideoExtractor {

    private static final String TAG = "MP4VideoExtractor";

    private MediaInfoStream mediaInfoStream;
    private int streamType;

    public static final int STREAM_TYPE_JT1078 = 1;
    public static final int STREAM_TYPE_RTMP = 0;

    private boolean isExit = false;
    private MediaExtractor mediaExtractor;

    private int count = 0;

    private long videoPts = 0;

    private int dubledSpeed = 1;


    private long seekTime = -1;

    //test3.mp4  h264,aac
    public void exactorMedia(final File[] sdcard_path, long startTime) {
//        FileOutputStream videoOutputStream = null;
//        FileOutputStream audioOutputStream = null;
        mediaExtractor = new MediaExtractor();

        isExit = false;
        try {
            //分离的视频文件
//            File videoFile = new File(sdcard_path, "output_video.h264");
            //分离的音频文件
//            File audioFile = new File(sdcard_path, "output_audio.aac");
//            videoOutputStream = new FileOutputStream(videoFile);
//            audioOutputStream = new FileOutputStream(audioFile);
            //输入文件,也可以是网络文件
            //oxford.mp4 视频 h264/baseline  音频 aac/lc 44.1k  2 channel 128kb/s
            mediaExtractor.setDataSource(sdcard_path[0].getAbsolutePath());
            //test3.mp4  视频h264 high   音频aac
            //        mediaExtractor.setDataSource(sdcard_path + "/test3.mp4");
            //test2.mp4 视频mpeg4  音频MP3
            //  mediaExtractor.setDataSource(sdcard_path + "/test2.mp4");
            //信道总数
            int trackCount = mediaExtractor.getTrackCount();
            Log.d(TAG, "trackCount:" + trackCount);
            int audioTrackIndex = -1;
            int videoTrackIndex = -1;


            for (int i = 0; i < trackCount; i++) {
                MediaFormat trackFormat = mediaExtractor.getTrackFormat(i);
                String mineType = trackFormat.getString(MediaFormat.KEY_MIME);

                //视频信道
                if (mineType.startsWith("video/")) {
                    videoTrackIndex = i;
                }
                //音频信道
                if (mineType.startsWith("audio/")) {
                    audioTrackIndex = i;
                }
            }

            if (videoTrackIndex == -1) {
                isExit = true;
                return;
            }
            if (audioTrackIndex == -1) {
                isExit = true;
                return;
            }

            MediaFormat trackFormat1 = mediaExtractor.getTrackFormat(videoTrackIndex);
            ByteBuffer spsb = trackFormat1.getByteBuffer("csd-0");
            final byte[] sps = new byte[spsb.remaining()];
            spsb.get(sps, 0, sps.length);
            Log.e("asdffdsa sps", MessageConversionUtils.toHexString1(sps));
            ByteBuffer ppsb = trackFormat1.getByteBuffer("csd-1");
            final byte[] pps = new byte[ppsb.remaining()];
            ppsb.get(pps, 0, pps.length);
            Log.e("asdffdsa pps", MessageConversionUtils.toHexString1(pps));


            final ByteBuffer byteBuffer = ByteBuffer.allocate(500 * 1024);
            //切换到视频信道
            mediaExtractor.selectTrack(videoTrackIndex);
//            boolean is = true;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        while (true) {
                            if (isExit) {
                                if (mediaExtractor != null) {
                                    mediaExtractor.release();
                                    mediaExtractor = null;
                                }
                                break;
                            }
                            int readSampleCount = mediaExtractor.readSampleData(byteBuffer, 0);
                            if (readSampleCount < 0) {
                                break;
                            }
                            //保存视频信道信息
//                byte[] buffer = new byte[readSampleCount];
//                byteBuffer.get(buffer);
//                videoOutputStream.write(buffer);//buffer 写入到 videooutputstream中
                            if (videoPts == 0) {
                                videoPts = mediaExtractor.getSampleTime();
                            }
                            if (mediaExtractor.getSampleTime() - videoPts > 0) {
                                Thread.sleep((((mediaExtractor.getSampleTime() - videoPts)) / 1000) / dubledSpeed);
                            }
                            videoPts = mediaExtractor.getSampleTime();


                            byte[] data = new byte[0];
                            if (streamType == BasePushEncoder.STREAM_TYPE_RTMP) {
                                data = new byte[byteBuffer.remaining()];
                                byteBuffer.get(data, 0, data.length);
                            } else if (streamType == BasePushEncoder.STREAM_TYPE_JT1078) {
                                if (byteBuffer.get(5) == -120) {
                                    data = new byte[byteBuffer.remaining() + sps.length + pps.length];
                                    byteBuffer.get(data, sps.length + pps.length, data.length - sps.length - pps.length);
                                    System.arraycopy(sps, 0, data, 0, sps.length);
                                    System.arraycopy(pps, 0, data, sps.length, pps.length);
                                } else {
                                    data = new byte[byteBuffer.remaining()];
                                    byteBuffer.get(data, 0, data.length);
                                }
                            }

                            if (mediaInfoStream != null) {
                                mediaInfoStream.spsppsData(data);
                            }


                            byteBuffer.clear();
                            if (seekTime == -1) {
                                mediaExtractor.advance();
                            } else {

                                long time = Long.parseLong(sdcard_path[0].getName().replace("A", "").replace("B", "").replace(".mp4", ""));
                                if (seekTime > time) {
                                    mediaExtractor.seekTo(seekTime - time, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                                    while (mediaExtractor.getSampleTime() < seekTime - time) {
                                        mediaExtractor.advance();

                                    }
                                    seekTime = -1;
                                }
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        stopStream();
                    }
                }
            }).

                    start();

            final int finalAudioTrackIndex = audioTrackIndex;
//            new Thread(new Runnable() {
//                @Override
//                public void run() {
//                    //切换到音频信道
//                    mediaExtractor.selectTrack(finalAudioTrackIndex);
//                    while (true) {
//                        if (isExit) {
//                            if (mediaExtractor != null) {
//                                mediaExtractor.release();
//                                mediaExtractor = null;
//                            }
//                            break;
//                        }
//                        int readSampleCount = mediaExtractor.readSampleData(byteBuffer, 0);
//                        Log.d(TAG, "audio:readSampleCount:" + readSampleCount);
//                        if (readSampleCount < 0) {
//                            break;
//                        }
//                        //保存音频信息
//                        byte[] buffer = new byte[readSampleCount];
//                        byteBuffer.get(buffer);
//                        /************************* 用来为aac添加adts头**************************/
//                        byte[] aacaudiobuffer = new byte[readSampleCount + 7];
//                        addADTStoPacket(aacaudiobuffer, readSampleCount + 7);
//                        System.arraycopy(buffer, 0, aacaudiobuffer, 7, readSampleCount);
////                audioOutputStream.write(aacaudiobuffer);
//                        /***************************************close**************************/
//                        //  audioOutputStream.write(buffer);
//                        byteBuffer.clear();
//                        mediaExtractor.advance();
//                    }
//                }
//            });


        } catch (IOException e) {
            stopStream();
        }
    }

    public void setSeekTime(long seekTime) {
        this.seekTime = seekTime;
    }

    /**
     * 这里之前遇到一个坑，以为这个packetLen是adts头的长度，也就是7，仔细看了下代码，发现这个不是adts头的长度，而是一帧音频的长度
     *
     * @param packet    一帧数据（包含adts头长度）
     * @param packetLen 一帧数据（包含adts头）的长度
     */
    public static void addADTStoPacket(byte[] packet, int packetLen,int sampleRate) {
        int profile = 2; // AAC LC
        int freqIdx = getFreqIdx(sampleRate);
        int chanCfg = 2; // CPE

        // fill in ADTS data
        packet[0] = (byte) 0xFF;
        packet[1] = (byte) 0xF1;
        packet[2] = (byte) (((profile - 1) << 6) + (freqIdx << 2) + (chanCfg >> 2));
        packet[3] = (byte) (((chanCfg & 3) << 6) + (packetLen >> 11));
        packet[4] = (byte) ((packetLen & 0x7FF) >> 3);
        packet[5] = (byte) (((packetLen & 7) << 5) + 0x1F);
        packet[6] = (byte) 0xFC;
    }

    public void stopStream() {
        isExit = true;

        if (mediaExtractor != null) {
            mediaExtractor.release();
            mediaExtractor = null;
        }
    }

    private static int getFreqIdx(int sampleRate) {
        int freqIdx;

        switch (sampleRate) {
            case 96000:
                freqIdx = 0;
                break;
            case 88200:
                freqIdx = 1;
                break;
            case 64000:
                freqIdx = 2;
                break;
            case 48000:
                freqIdx = 3;
                break;
            case 44100:
                freqIdx = 4;
                break;
            case 32000:
                freqIdx = 5;
                break;
            case 24000:
                freqIdx = 6;
                break;
            case 22050:
                freqIdx = 7;
                break;
            case 16000:
                freqIdx = 8;
                break;
            case 12000:
                freqIdx = 9;
                break;
            case 11025:
                freqIdx = 10;
                break;
            case 8000:
                freqIdx = 11;
                break;
            case 7350:
                freqIdx = 12;
                break;
            default:
                freqIdx = 8;
                break;
        }

        return freqIdx;
    }

    public void setMediaInfoStream(MediaInfoStream mediaInfoStream) {
        this.mediaInfoStream = mediaInfoStream;
    }

    public void setStreamType(int streamType) {
        this.streamType = streamType;
    }

    public interface MediaInfoStream {
        void spsppsData(byte[] data);
    }
}
