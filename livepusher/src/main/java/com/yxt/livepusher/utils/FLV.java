package com.yxt.livepusher.utils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * 使用方法 创建 h264对象 haveAudio(); true 为音视频都有 false为纯音频
 * <p>
 * <p>
 * getFLV()为转换h264和AAC数据为flv
 *
 * @author yxt
 */
public class FLV {

    private byte[] sps = new byte[0];
    private byte[] pps = new byte[0];

    public static final byte[] splitElement = new byte[]{0x00, 0x00, 0x00, 0x01};

    private long pts = 0;
    private long sudioPts = 0;

    private boolean spsb = false;
    private boolean ppsb = false;
    private boolean isPuserSpsPPs = false;
    private boolean isH264Head = false;
    static Object o = new Object();

    private boolean isHead = false;

    // 是否有音频，默认是没有音频
    private boolean haveAudio = false;

    /**
     * 设置是否有音频 true为音视频 false为纯视频
     *
     * @param haveAudio
     */
    public void setHaveAudio(boolean haveAudio) {
        this.isHead = false;
        this.isH264Head = false;
        this.haveAudio = haveAudio;
    }

    /**
     * 获取h264
     *
     * @param data           数据源（可能是音频数据，也可能是视频数据）
     * @param isAudioOrVideo true is Audio; false is Video;
     * @return
     */
    public synchronized byte[] getFLV(byte[] data, boolean isAudioOrVideo) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {

            if (isAudioOrVideo) {
                if (!isHead) {
                    baos.write(flvHeader());
                }
                baos.write(aacToFlv(data));
            } else {

                byte[] h264data = h264ToFlv(data);
                if (h264data == null) {
                    baos.flush();
                    baos.close();
                    return null;
                }
                if (!isHead) {
                    baos.write(flvHeader());
                }
                baos.write(h264data);
            }

            baos.flush();
            baos.close();
        } catch (Exception e) {

        }

        return baos.toByteArray();
    }

    /**
     * 音频
     *
     * @param date
     * @return
     */
    public synchronized byte[] aacToFlv(byte[] date) {

        byte[] data = Arrays.copyOfRange(date, 7, date.length);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            int len = 0;

            // if (isHead)
            // baos.write(flvHeader());

            baos.write(0x08);

            // 长度
            baos.write(integerTo3Bytes(data.length + 2));

            if (sudioPts == 0) {
                // 时间戳
                baos.write(0x00);
                baos.write(0x00);
                baos.write(0x00);
                baos.write(0x00);
                sudioPts = System.currentTimeMillis();
            } else {
                byte[] b = integerTo4Bytes((int) (System.currentTimeMillis() - pts));

                baos.write(b[1]);
                baos.write(b[2]);
                baos.write(b[3]);
                baos.write(b[0]);
            }
            // StreamID
            baos.write(0x00);
            baos.write(0x00);
            baos.write(0x00);
            baos.write(0xAF);
            if (data.length < 10) {
                baos.write(0x00);
            } else {
                baos.write(0x01);
            }


            baos.write(data);

            len = data.length + 13;
            byte[] bbDS = integerTo4Bytes(len);
            baos.write(bbDS[0]);
            baos.write(bbDS[1]);
            baos.write(bbDS[2]);
            baos.write(bbDS[3]);
            baos.flush();
            baos.close();

        } catch (Exception e) {

        }

        return baos.toByteArray();
    }

    /**
     * h264ToFlv 纯视频转换
     *
     * @param data 完整帧数据 可以是多帧 但是必须完整
     * @return
     */
    public synchronized byte[] h264ToFlv(byte[] data) {
        synchronized (o) {
            int len = 0;

            // try {
            if (data.length < 4) {
                throw new RuntimeException("data lenth is err");
            }
            if (data[0] != 0x00 && data[1] != 0x00 && data[2] != 0x00) {
                // throw new RuntimeException("data is not h264");
                return null;
            }
            // if (theFirstFew < 1) {
            // throw new RuntimeException("theFirstFew is 1 start");
            // }

            byte[][] result = splitByte(data, splitElement);

            if (sps.length == 0 || pps.length == 0) {
                for (byte[] b : result) {
                    switch (b[4]) {
                        case 103:
                            spsb = true;
                            sps = Arrays.copyOfRange(b, 4, b.length);
                            break;
                        case 104:
                            ppsb = true;
                            pps = Arrays.copyOfRange(b, 4, b.length);
                            break;
                    }
                }
            }
            if (!spsb || !ppsb)
                return null;

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try {
                if (!isH264Head && sps.length != 0 && pps.length != 0) {
                    isH264Head = true;
                    // isPuserSpsPPs = true;
                    // baos.write(flvHeader());

                    baos.write(0x09);

                    // 长度
                    baos.write(integerTo3Bytes(16 + sps.length + pps.length));

                    // 时间戳
                    baos.write(0x00);
                    baos.write(0x00);
                    baos.write(0x00);
                    baos.write(0x00);

                    // 通道固定 StreamID
                    baos.write(0x00);
                    baos.write(0x00);
                    baos.write(0x00);

                    baos.write(0x17);
                    baos.write(0x00);
                    baos.write(0x00);
                    baos.write(0x00);
                    baos.write(0x00);
                    pts = System.currentTimeMillis();
                    // version
                    baos.write(0x01);

                    baos.write(Arrays.copyOfRange(sps, 1, 4));

                    // baos.wait(sps[1]);
                    // baos.wait(sps[2]);
                    // baos.wait(sps[3]);
                    baos.write(0xFF);
                    baos.write(0xE1);
                    baos.write(integerTo2Bytes(sps.length));
                    baos.write(sps);
                    baos.write(01);
                    baos.write(integerTo2Bytes(pps.length));
                    baos.write(pps);
                    len = pps.length + sps.length + 23;
                    byte[] bb = integerTo4Bytes(len);
                    baos.write(bb[0]);
                    baos.write(bb[1]);
                    baos.write(bb[2]);
                    baos.write(bb[3]);
                }

                for (int i = 0; i < result.length; i++) {

                    if (result[i][4] == 101) {
//                        Log.e("jt1078","65   "+result[i][4]);
                        baos.write(0x09);
                        baos.write(integerTo3Bytes(result[i].length + 5));
                        byte[] b = integerTo4Bytes((int) (System.currentTimeMillis() - pts));

                        baos.write(b[1]);
                        baos.write(b[2]);
                        baos.write(b[3]);
                        baos.write(b[0]);

                        baos.write(0x00);
                        baos.write(0x00);
                        baos.write(0x00);

                        baos.write(0x17);
                        baos.write(0x01);

                        baos.write(0x00);
                        baos.write(0x00);
                        baos.write(0x00);

                        byte[] bbb = integerTo4Bytes(result[i].length - 4);
                        baos.write(bbb[0]);
                        baos.write(bbb[1]);
                        baos.write(bbb[2]);
                        baos.write(bbb[3]);
                        baos.write(Arrays.copyOfRange(result[i], 4, result[i].length));

                        len = result[i].length + 16;

                        byte[] bbDS = integerTo4Bytes(len);
                        baos.write(bbDS[0]);
                        baos.write(bbDS[1]);
                        baos.write(bbDS[2]);
                        baos.write(bbDS[3]);
                    } else if (result[i][4] == 65) {
//                        Log.e("jt1078","41   "+result[i][4]);

                        // byte[] bb = integerTo4Bytes(len);
                        // baos.write(bb[0]);
                        // baos.write(bb[1]);
                        // baos.write(bb[2]);
                        // baos.write(bb[3]);
                        baos.write(0x09);
                        baos.write(integerTo3Bytes(result[i].length + 5));
                        byte[] b = integerTo4Bytes((int) (System.currentTimeMillis() - pts));

                        baos.write(b[1]);
                        baos.write(b[2]);
                        baos.write(b[3]);
                        baos.write(b[0]);

                        baos.write(0x00);
                        baos.write(0x00);
                        baos.write(0x00);

                        baos.write(0x27);
                        baos.write(0x01);

                        baos.write(0x00);
                        baos.write(0x00);
                        baos.write(0x00);

                        byte[] bbb = integerTo4Bytes(result[i].length - 4);
                        baos.write(bbb[0]);
                        baos.write(bbb[1]);
                        baos.write(bbb[2]);
                        baos.write(bbb[3]);
                        baos.write(Arrays.copyOfRange(result[i], 4, result[i].length));

                        len = result[i].length + 16;
                        byte[] bbDS = integerTo4Bytes(len);
                        baos.write(bbDS[0]);
                        baos.write(bbDS[1]);
                        baos.write(bbDS[2]);
                        baos.write(bbDS[3]);
                    } else {
//                        打印一下  result[i][4]
                    }
//                    else if (toHexString1(result[i][4]).equals("61")) {
////                        Log.e("jt1078","61   "+result[i][4]);
//                        baos.write(0x09);
//                        baos.write(integerTo3Bytes(result[i].length + 5));
//                        byte[] b = integerTo4Bytes((int) (System.currentTimeMillis() - pts));
//
//                        baos.write(b[1]);
//                        baos.write(b[2]);
//                        baos.write(b[3]);
//                        baos.write(b[0]);
//
//                        baos.write(0x00);
//                        baos.write(0x00);
//                        baos.write(0x00);
//
//                        baos.write(0x27);
//                        baos.write(0x01);
//
//                        baos.write(0x00);
//                        baos.write(0x00);
//                        baos.write(0x00);
//
//                        byte[] bbb = integerTo4Bytes(result[i].length - 4);
//                        baos.write(bbb[0]);
//                        baos.write(bbb[1]);
//                        baos.write(bbb[2]);
//                        baos.write(bbb[3]);
//                        baos.write(Arrays.copyOfRange(result[i], 4, result[i].length));
//
//                        len = result[i].length + 16;
//                        byte[] bbDS = integerTo4Bytes(len);
//                        baos.write(bbDS[0]);
//                        baos.write(bbDS[1]);
//                        baos.write(bbDS[2]);
//                        baos.write(bbDS[3]);
//                    }

                    // baos.write(integerTo4Bytes());

                }

                // } catch (InterruptedException e) {
                // // TODO Auto-generated catch block
                // e.printStackTrace();
                // }

                baos.flush();
                baos.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } finally {
                try {
                    if (baos != null) {
                        baos.flush();
                        baos.close();
                    }
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }

            byte[] a = baos.toByteArray();
            baos = null;
            return a;
        }
    }

    public byte[] getSpsPPs() {
        return concat(sps, pps);
    }

    public boolean isSpsPPs() {
        return sps != null && sps.length != 0 && pps != null && pps.length != 0;
    }

    public static byte[] concat(byte[] first, byte[] second) {

        byte[] result = Arrays.copyOf(first, first.length + second.length);

        System.arraycopy(second, 0, result, first.length, second.length);

        return result;

    }

    // public static void main(String[] args) {
    // byte[] a = new byte[] { 0x01, 0x02, 0x03, 0x04, 0x05, 0x01, 0x02, 0x03,
    // 0x04, 0x05 };
    // byte[] b = new byte[] { 0x02, 0x03 };
    //
    // byte[][] c = splitByte(a, b);
    //
    // for (byte[] k : c) {
    // System.out.println(Arrays.toString(k));
    // }
    //
    // }
    public static byte[][] splitByte(byte[] src, byte[] splitElement) {
        ArrayList<Integer> arr = new ArrayList<>();
        for (int i = 0; i < src.length - splitElement.length; i++) {
            boolean isSplit = false;
            for (int k = 0; k < splitElement.length; k++) {
                if ((src[i + k] == splitElement[k]) && k == splitElement.length - 1) {
                    isSplit = true;
                }
                if (src[i + k] != splitElement[k]) {
                    isSplit = false;
                    break;
                }
            }
            if (isSplit) {
                // 拆分
                arr.add(i);
            }
        }
        byte[][] by = new byte[arr.size()][];
        for (int i = 0; i < arr.size(); i++) {
            if (i + 1 < arr.size()) {
                by[i] = Arrays.copyOfRange(src, arr.get(i), arr.get(i + 1));
            } else {
                by[i] = Arrays.copyOfRange(src, arr.get(i), src.length);
            }
        }
        return by;
    }

    private byte[] flvHeader() {
        isHead = true;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            baos.write(0x46);
            baos.write(0x4C);
            baos.write(0x56);
            baos.write(0x01);
            // 用来控制是否含有音频 音视频都有为0x05 纯视频是0x01 纯音频为 0x04
            if (haveAudio)
                baos.write(0x05);
            else
                baos.write(0x01);
            baos.write(0x00);
            baos.write(0x00);
            baos.write(0x00);
            baos.write(0x09);

            baos.write(0x00);
            baos.write(0x00);
            baos.write(0x00);
            baos.write(0x00);
            baos.flush();
            baos.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return baos.toByteArray();
    }

    public byte[] newStreamHead() {
        if (sps == null || pps == null || sps.length == 0 || pps.length == 0) {
            return null;
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int len = 0;
        try {
            baos.write(flvHeader());
            baos.write(0x09);

            // 长度
            baos.write(integerTo3Bytes(16 + sps.length + pps.length));

            // 时间戳
            baos.write(0x00);
            baos.write(0x00);
            baos.write(0x00);
            baos.write(0x00);

            // 通道固定 StreamID
            baos.write(0x00);
            baos.write(0x00);
            baos.write(0x00);

            baos.write(0x17);
            baos.write(0x00);
            baos.write(0x00);
            baos.write(0x00);
            baos.write(0x00);
            pts = System.currentTimeMillis();
            // version
            baos.write(0x01);

            baos.write(Arrays.copyOfRange(sps, 1, 4));

            // baos.wait(sps[1]);
            // baos.wait(sps[2]);
            // baos.wait(sps[3]);
            baos.write(0xFF);
            baos.write(0xE1);
            baos.write(integerTo2Bytes(sps.length));
            baos.write(sps);
            baos.write(01);
            baos.write(integerTo2Bytes(pps.length));
            baos.write(pps);
            len = pps.length + sps.length + 23;
            byte[] bb = integerTo4Bytes(len);
            baos.write(bb[0]);
            baos.write(bb[1]);
            baos.write(bb[2]);
            baos.write(bb[3]);
            baos.flush();
            baos.close();
        } catch (IOException e) {
        }

        return baos.toByteArray();


    }

    private byte[] flvTagScript(int width, int height, int fps, int bitRate) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(0x00);
        baos.write(0x00);
        baos.write(0x00);
        baos.write(0x00);
        baos.write(0x12);

        // 长度为3字节 暂时没写
        baos.write(0x00);
        baos.write(0x00);
        baos.write(0x00);

        baos.write(0x00);
        baos.write(0x00);
        baos.write(0x00);
        baos.write(0x00);

        baos.write(0x00);
        baos.write(0x00);
        baos.write(0x00);

        baos.write(0x02);
        baos.write(0x00);
        baos.write(0x0A);

        baos.write(0x6F);
        baos.write(0x6E);
        baos.write(0x4D);
        baos.write(0x65);
        baos.write(0x74);
        baos.write(0x61);
        baos.write(0x44);
        baos.write(0x61);
        baos.write(0x74);
        baos.write(0x61);

        // 键值对
        baos.write(0x08);
        // 键值对个数
        baos.write(0x00);
        baos.write(0x00);
        baos.write(0x00);
        baos.write(0x0C);

        baos.write(0x00);
        baos.write(0x08);
        baos.write(0x64);
        baos.write(0x75);
        baos.write(0x72);
        baos.write(0x61);
        baos.write(0x74);
        baos.write(0x69);
        baos.write(0x6F);
        baos.write(0x6E);

        baos.write(0x00);
        baos.write(0x40);

        return null;
    }

    private class Coordinate {
        private int x;
        private int y;

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public void setX(int x) {
            this.x = x;
        }

        public void setY(int y) {
            this.y = y;
        }
    }

    public static String toHexString1(byte[] b) {
        StringBuffer buffer = new StringBuffer();
        for (int i = 0; i < (b.length > 30 ? 30 : b.length); ++i) {
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

    public static int byte3ToInteger(byte[] value) {
        return (value[0] << 16) + (value[1] << 8) + (value[2]);
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

    public static int byte4TimeToInteger(byte[] value) {
        return (value[3] << 24) + (value[0] << 16) + (value[1] << 8) + (value[2]);
    }


    //flv剪切
    public void FlvShear(String url, int startTime, int endTime, String saveUrl) {
        FileInputStream fis = null;
        FileOutputStream fos = null;
        try {

            int timeGen = 0;
            boolean isFirst = true;
            boolean isWriteAudio = false;
            boolean isWriteVadio = false;
            File file = new File(url);
            if (!file.isFile()) {
                return;
            }

            File saveFile = new File(saveUrl);
            if (saveFile.isFile()) {
                saveFile.createNewFile();
            } else {
                saveFile.delete();
                saveFile.createNewFile();
            }

            fis = new FileInputStream(file);
            fos = new FileOutputStream(saveFile, true);
            byte[] headByte = new byte[13];
            int headlen = fis.read(headByte);
            if (headlen == -1) {
                return;
            }

            if (toHexString1(headByte[0]).equals("46") && toHexString1(headByte[0]).equals("4C") && toHexString1(headByte[0]).equals("56")) {
                fos.write(headByte);
                headByte = null;
            } else {
                fis.close();
                fos.flush();
                fos.close();
                return;
            }


            do {
                byte[] tagHead = new byte[4];
                int tagHeadLen = fis.read(tagHead);
                if (tagHeadLen == -1) {
                    fis.close();
                    fos.flush();
                    fos.close();
                    return;
                }
                int bodyLen = byte3ToInteger(Arrays.copyOfRange(tagHead, 1, tagHead.length)) + 4;
                byte[] tagBody = new byte[bodyLen];

                int tagBodylen = fis.read(tagBody);
                if (tagBodylen == -1) {
                    fis.close();
                    fos.flush();
                    fos.close();
                    return;
                }

                int time = byte4TimeToInteger(Arrays.copyOfRange(tagBody, 0, 4));

                if (toHexString1(tagHead[0]).equals("08")) {
                    //音频
                    if (toHexString1(tagBody[8]).equals("00")) {
                        fos.write(tagHead);
                        fos.write(tagBody);
                        isWriteAudio = true;
                    } else if (toHexString1(tagBody[8]).equals("01")) {
                        if (isWriteVadio && isWriteAudio && time >= startTime && time <= endTime) {
                            fos.write(tagHead);
                            byte[] timeByte = integerTo4Bytes(time - timeGen);
                            tagBody[0] = timeByte[1];
                            tagBody[1] = timeByte[2];
                            tagBody[2] = timeByte[3];
                            tagBody[3] = timeByte[0];
                            fos.write(tagBody);
                        }
                    }

                } else if (toHexString1(tagHead[0]).equals("09")) {
                    //视频
                    if (toHexString1(tagBody[8]).equals("00")) {
                        fos.write(tagHead);
                        fos.write(tagBody);
                    } else if (timeGen == 0 && toHexString1(tagBody[8]).equals("01") && toHexString1(tagBody[16]).equals("65")) {
                        timeGen = time;
                        fos.write(tagHead);
                        tagBody[0] = 0;
                        tagBody[1] = 0;
                        tagBody[2] = 0;
                        tagBody[3] = 0;
                        fos.write(tagBody);
                        isWriteVadio = true;
                    } else if (toHexString1(tagBody[8]).equals("01")) {
                        if (isWriteVadio && time >= startTime && time <= endTime) {
                            fos.write(tagHead);
                            byte[] timeByte = integerTo4Bytes(time - timeGen);
                            tagBody[0] = timeByte[1];
                            tagBody[1] = timeByte[2];
                            tagBody[2] = timeByte[3];
                            tagBody[3] = timeByte[0];
                            fos.write(tagBody);
                        }
                    }
                } else if (toHexString1(tagHead[0]).equals("12")) {
                    fos.write(tagHead);
                    fos.write(tagBody);
                }


            } while (true);


        } catch (Exception e) {
            try {
                if (fis != null)
                    fis.close();
                if (fos != null) {
                    fos.flush();
                    fos.close();
                }
            } catch (IOException e1) {
            }

        } finally {
            try {
                if (fis != null) {
                    fis.close();
                }
                if (fos != null) {
                    fos.flush();
                    fos.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }


}

