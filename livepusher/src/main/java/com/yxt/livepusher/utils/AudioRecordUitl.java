package com.yxt.livepusher.utils;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

public class AudioRecordUitl {

    private AudioRecord audioRecord;
    private int bufferSizeInBytes;
    private boolean start = false;
    private int readSize = 0;

    private OnRecordLisener onRecordLisener;

    public AudioRecordUitl() {
        bufferSizeInBytes = AudioRecord.getMinBufferSize(
                44100,
                AudioFormat.CHANNEL_IN_STEREO,
                AudioFormat.ENCODING_PCM_16BIT);

        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                44100,
                AudioFormat.CHANNEL_IN_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSizeInBytes
        );
    }

    public void setOnRecordLisener(OnRecordLisener onRecordLisener) {
        this.onRecordLisener = onRecordLisener;
    }

    public void startRecord() {
        new Thread() {
            @Override
            public void run() {
                super.run();
                start = true;
                audioRecord.startRecording();
                byte[] audiodata = new byte[bufferSizeInBytes];
//                short[] audiodata = new byte[bufferSizeInBytes];
                while (start) {
                    readSize = audioRecord.read(audiodata, 0, bufferSizeInBytes);
                    if (onRecordLisener != null) {
                        onRecordLisener.recordByte(audiodata, readSize);
                    }
                }
                if (audioRecord != null) {
                    audioRecord.stop();
                    audioRecord.release();
                    audioRecord = null;
                }
            }
        }.start();
    }

    public void stopRecord() {
        onRecordLisener = null;
        start = false;
    }

    public interface OnRecordLisener {
        void recordByte(byte[] audioData, int readSize);
    }

    public boolean isStart() {
        return start;
    }

}
