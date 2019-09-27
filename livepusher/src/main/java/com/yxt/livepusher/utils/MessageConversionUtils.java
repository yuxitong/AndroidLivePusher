package com.yxt.livepusher.utils;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class MessageConversionUtils {
    public static int videoStreamSerial = -1;

    public static String toHexString1(byte[] b) {
        StringBuffer buffer = new StringBuffer();
        for (int i = 0; i < b.length; ++i) {
            buffer.append(toHexString1(b[i]));
        }
        return buffer.toString();
    }

    public static String toHexString1(byte[] b, int len) {
        StringBuffer buffer = new StringBuffer();
        for (int i = 0; i < len; ++i) {
            buffer.append(toHexString1(b[i]));
        }
        return buffer.toString();
    }

    public static String toHexString1(byte b) {
        String s = Integer.toHexString(b & 0xFF);
        if (s.length() == 1) {
            return "0" + s;
        } else {
            return s;
        }
    }

    /**
     * 把一个整形该为byte
     *
     * @param value
     * @return
     * @throws Exception
     */
    public static byte integerTo1Byte(int value) {
        return (byte) (value & 0xFF);
    }

    /**
     * 把一个整形该为1位的byte数组
     *
     * @param value
     * @return
     * @throws Exception
     */
    public static byte[] integerTo1Bytes(int value) {
        byte[] result = new byte[1];
        result[0] = (byte) (value & 0xFF);
        return result;
    }

    /**
     * 把一个整形改为2位的byte数组
     *
     * @param value
     * @return
     * @throws Exception
     */
    public static byte[] integerTo2Bytes(long value) {
        byte[] result = new byte[2];
        result[0] = (byte) ((value >>> 8) & 0xFF);
        result[1] = (byte) (value & 0xFF);
        return result;
    }

    /**
     * 把一个整形改为3位的byte数组
     *
     * @param value
     * @return
     * @throws Exception
     */
    public static byte[] integerTo3Bytes(int value) {
        byte[] result = new byte[3];
        result[0] = (byte) ((value >>> 16) & 0xFF);
        result[1] = (byte) ((value >>> 8) & 0xFF);
        result[2] = (byte) (value & 0xFF);
        return result;
    }

    /**
     * 把一个整形改为4位的byte数组
     *
     * @param value
     * @return
     * @throws Exception
     */
    public static byte[] integerTo4Bytes(int value) {
        byte[] result = new byte[4];
        result[0] = (byte) ((value >>> 24) & 0xFF);
        result[1] = (byte) ((value >>> 16) & 0xFF);
        result[2] = (byte) ((value >>> 8) & 0xFF);
        result[3] = (byte) (value & 0xFF);
        return result;
    }

    /**
     * 把一个整形改为8位的byte数组
     *
     * @param value
     * @return
     * @throws Exception
     */
    public static byte[] integerTo8Bytes(long value) {
        byte[] result = new byte[8];
        result[0] = (byte) ((value >>> 50) & 0xFF);
        result[1] = (byte) ((value >>> 42) & 0xFF);
        result[2] = (byte) ((value >>> 40) & 0xFF);
        result[3] = (byte) ((value >>> 32) & 0xFF);
        result[4] = (byte) ((value >>> 24) & 0xFF);
        result[5] = (byte) ((value >>> 16) & 0xFF);
        result[6] = (byte) ((value >>> 8) & 0xFF);
        result[7] = (byte) (value & 0xFF);
        return result;
    }

    /**
     * 二制度字符串转字节数组，如 101000000100100101110000 -> A0 09 70
     *
     * @param input 输入字符串。
     * @return 转换好的字节数组。
     */
    public static byte[] string2bytes(String input) {
        StringBuilder in = new StringBuilder(input);
        // 注：这里in.length() 不可在for循环内调用，因为长度在变化
        int remainder = in.length() % 8;
        if (remainder > 0)
            for (int i = 0; i < 8 - remainder; i++)
                in.append("0");
        byte[] bts = new byte[in.length() / 8];

        // Step 8 Apply compression
        for (int i = 0; i < bts.length; i++)
            bts[i] = (byte) Integer.parseInt(in.substring(i * 8, i * 8 + 8), 2);

        return bts;
    }


    /**
     * String 转换BCD
     *
     * @param str
     * @return BCD数组
     */
    public static byte[] string2Bcd(String str) {
        // 濂囨暟,鍓嶈ˉ闆?
        if ((str.length() & 0x1) == 1) {
            str = "0" + str;
        }

        byte ret[] = new byte[str.length() / 2];
        byte bs[] = str.getBytes();
        for (int i = 0; i < ret.length; i++) {

            byte high = ascII2Bcd(bs[2 * i]);
            byte low = ascII2Bcd(bs[2 * i + 1]);

            // TODO 鍙伄缃〣CD浣庡洓浣??
            ret[i] = (byte) ((high << 4) | low);
        }
        return ret;
    }

    private static byte ascII2Bcd(byte asc) {
        if ((asc >= '0') && (asc <= '9'))
            return (byte) (asc - '0');
        else if ((asc >= 'A') && (asc <= 'F'))
            return (byte) (asc - 'A' + 10);
        else if ((asc >= 'a') && (asc <= 'f'))
            return (byte) (asc - 'a' + 10);
        else
            return (byte) (asc - 48);
    }

    public static byte[] getVideoStreamSerial() {
        videoStreamSerial++;
        return integerTo2Bytes(videoStreamSerial);
    }

    /**
     * //实时视频流上传
     *
     * @param boundary           是否是帧边界
     * @param videoStreamSerial  包序号
     * @param date               包数据
     * @param state              数据状态：1原子包，不可拆分   2分包处理时第一个包  3分包处理时最后一个包  4分包处理时中间包
     * @param time               时间戳
     * @param dataType           数据类型  0000为I帧   0001为P帧  0010为B帧   0011音频帧   0100透传数据
     * @param dataLen            数据包长度   长度为0的时候没有该数据
     * @param LastIFrameInterval 距离上一次I帧间隔时间单位ms
     * @param LastFrameInterval  距离上一帧间隔时间单位ms
     * @param phone              手机号
     * @return
     * @throws IOException
     */
    public static byte[] RealTimeVideoStreaming(byte cameraId, boolean boundary, byte[] videoStreamSerial, byte[] date, int state, byte[] time, String dataType, int dataLen, byte[] LastIFrameInterval, byte[] LastFrameInterval, String phone) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(0x30);
        baos.write(0x31);
        baos.write(0x63);
        baos.write(0x64);
        baos.write(0x81);
        //01100010
        if (boundary) {
            baos.write(0xE2);
        } else {
            baos.write(0x62);
        }
        baos.write(videoStreamSerial);
        baos.write(string2Bcd(phone));
        baos.write(cameraId);
//        Log.e("serial", RadixTransformationUtils.toHexString1(videoStreamSerial));
        String dataTypeState = "";
        switch (state) {
            case 1:
//                dataType = "0000" + dataType;
                dataTypeState = dataType + "0000";
                break;
            case 2:
//                dataType = "0001"+dataType;
                dataTypeState = dataType + "0001";
                break;
            case 3:
//                dataType = "0010"+dataType;
                dataTypeState = dataType + "0010";
                break;
            case 4:
//                dataType = "0011"+dataType;
                dataTypeState = dataType + "0011";
                break;
        }
//        Log.e("byte in :", MessageConversionUtils.toHexString1(MessageConversionUtils.string2bytes(dataType)) + "  " + dataTypeState);
        baos.write(MessageConversionUtils.string2bytes(dataTypeState));
        baos.write(time);
        if (!dataType.equals("0011")) {
            baos.write(LastIFrameInterval);
            baos.write(LastFrameInterval);
        }
//        if (dataLen != 0) {
        baos.write(MessageConversionUtils.integerTo2Bytes(dataLen));
//        }
        baos.write(date);
        baos.flush();
        baos.close();
        return baos.toByteArray();
    }


    /**
     * //实时音频流上传
     *
     * @param boundary          是否是帧边界
     * @param videoStreamSerial 包序号
     * @param date              包数据
     * @param state             数据状态：1原子包，不可拆分   2分包处理时第一个包  3分包处理时最后一个包  4分包处理时中间包
     * @param time              时间戳
     * @param dataLen           数据包长度   长度为0的时候没有该数据
     * @param phone             手机号
     * @return
     * @throws IOException
     */
    public static byte[] RealTimeAudioStreaming(byte cameraId, boolean boundary, byte[] videoStreamSerial, byte[] date, int state, byte[] time, int dataLen, String phone) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(0x30);
        baos.write(0x31);
        baos.write(0x63);
        baos.write(0x64);
        baos.write(0x81);

        if (boundary) {
            baos.write(0x93);
        } else {
            baos.write(0x13);
        }
        baos.write(videoStreamSerial);
        baos.write(string2Bcd(phone));
        baos.write(cameraId);
//        Log.e("serial", RadixTransformationUtils.toHexString1(videoStreamSerial));
        String dataTypeState = "";
        switch (state) {
            case 1:
//                dataType = "0000" + dataType;
                dataTypeState = "00110000";
                break;
            case 2:
//                dataType = "0001"+dataType;
                dataTypeState = "00110001";
                break;
            case 3:
//                dataType = "0010"+dataType;
                dataTypeState = "00110010";
                break;
            case 4:
//                dataType = "0011"+dataType;
                dataTypeState = "00110011";
                break;
        }
        baos.write(MessageConversionUtils.string2bytes(dataTypeState));
        baos.write(time);
        //        if (dataLen != 0) {
        baos.write(MessageConversionUtils.integerTo2Bytes(dataLen));
//        }
        baos.write(date);
        baos.flush();
        baos.close();
        return baos.toByteArray();
    }
}
