//
// Created by yangw on 2018-9-14.
//

#ifndef WLLIVEPUSHER_RTMPPUSH_H
#define WLLIVEPUSHER_RTMPPUSH_H

#include <malloc.h>
#include <string.h>
#include "MessageQueue.h"
#include "pthread.h"
#include "CallJava.h"

extern "C"
{
#include "librtmp/rtmp.h"
};

class RtmpPush {

public:
    RTMP *rtmp = NULL;
    char *url = NULL;
    MessageQueue *queue = NULL;
    pthread_t push_thread;
    CallJava *wlCallJava = NULL;
    bool startPushing = false;
    long startTime = 0;
public:
    RtmpPush(const char *url, CallJava *wlCallJava);
    ~RtmpPush();

    void init();

    void pushSPSPPS(char *sps, int sps_len, char *pps, int pps_len);

    void pushVideoData(char *data, int data_len, bool keyframe);

    void pushAudioData(char *data, int data_len);

    void pushStop();



};


#endif //WLLIVEPUSHER_RTMPPUSH_H
