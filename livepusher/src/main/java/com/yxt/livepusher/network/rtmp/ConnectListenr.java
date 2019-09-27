package com.yxt.livepusher.network.rtmp;

public interface ConnectListenr {

    void onConnecting();

    void onConnectSuccess();

    void onConnectFail(String msg);

}
