package com.yxt.livepusher.network;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Arrays;

public class NetSocket {
    private String url;
    private int port;
    private Socket client;
    private PullData pd;
    private ErrorCallBack ecb;

    // 正常停止运行报错
    public static final int ERROR_STOP = 0;
    // 网络报错比如网线没插
    public static final int ERROR_NET = 1;
    // 写入错误
    public static final int ERROR_UPDATA = 2;

    private boolean isStop;
    private boolean isConnect = false;

    public NetSocket connect(String url, int port) {
        isConnect = false;
        isStop = true;
        this.url = url;
        this.port = port;
        getBody();
        return this;
    }

    public NetSocket getBody() {

        new Thread(new Runnable() {
            public void run() {

                try {
                    isConnect = false;
                    client = new Socket(url, port);
                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    if (ecb != null)
                        ecb.error("net error" + e.toString(), ERROR_NET);
                    isStop = false;
                    isConnect = false;
                    return;
                }
                if (ecb != null)
                    ecb.successfulConnection();
                isConnect = true;
                try {
                    byte[] b = new byte[1024];
                    int len = 0;
                    InputStream bodyInput = client.getInputStream();
                    while ((len = bodyInput.read(b)) != -1) {
                        if (!isStop) {
                            break;
                        }
                        if (pd != null) {
                            splitPullDate(b);
                        }

                    }

                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    isConnect = false;
                    if (ecb != null)
                        ecb.error("net error", ERROR_UPDATA);
                }
            }
        }).start();

        return this;
    }

    public void splitPullDate(byte[] data) {
        int len = data.length;
        int startLocation = 0;
        for (int i = 0; i < data.length; i++)
            if (data[i] == 0x7e) {
                startLocation = i;
                break;
            }
        if (startLocation < len - 1)
            if (data[startLocation + 1] == 0x7e) {
                splitPullDate(Arrays.copyOfRange(data, startLocation + 1, data.length));
            } else {
                for (int i = startLocation + 1; i < data.length; i++) {
                    if (data[i] == 0x7e) {
                        if (i < data.length - 1 && data[i + 1] == 0x7e) {
                            splitPullDate(Arrays.copyOfRange(data, i + 1, data.length));
                        }
                        // System.out.println(RadixTransformationUtils.toHexString1(Arrays.copyOfRange(data,
                        // 0, i)));
                        if (pd != null) {
                            pd.getPullData(Arrays.copyOfRange(data, 0, i + 1));
                        }
                        break;
                    }
                }
            }
    }

    /**
     * 是否需要编码
     *
     * @param b 数据
     */
    public void upBody(byte[] b) {
        try {
            if (isConnect && !client.isInputShutdown()) {
                OutputStream op = client.getOutputStream();


//                    Log.e("JT1078", RadixTransformationUtils.toHexString1(b));
                op.write(b);
                op.flush();
            }
        } catch (Exception e) {
            // TODO Auto-generated catch block
            isConnect = false;
            if (ecb != null) {
                ecb.error("stop run  " + e.toString(), ERROR_UPDATA);
            }
            // if (ecb != null) {
            // ecb.error("upDataError", ERROR_UPDATA);
            // }
        }

    }

    public NetSocket setPullDataListener(PullData pd) {
        this.pd = pd;
        return this;
    }

    public NetSocket setErrorCallBack(ErrorCallBack ecb) {
        this.ecb = ecb;
        return this;
    }

    public interface PullData {
        void getPullData(byte[] data);
    }

    public interface ErrorCallBack {
        void error(String err, int code);

        void successfulConnection();
    }

    public void onDestroy() {
        ecb = null;
        isConnect = false;
        isStop = false;
        try {
            if (client != null)
                client.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
