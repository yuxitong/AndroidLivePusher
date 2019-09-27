//
// Created by yangw on 2018-9-14.
//

#ifndef WLLIVEPUSHER_WLQUEUE_H
#define WLLIVEPUSHER_WLQUEUE_H

#include "queue"
#include "pthread.h"
#include "AndroidLog.h"

extern "C"
{
#include "librtmp/rtmp.h"
};


class MessageQueue {

public:
    std::queue<RTMPPacket *> queuePacket;
    pthread_mutex_t mutexPacket;
    pthread_cond_t condPacket;

public:
    MessageQueue();
    ~MessageQueue();

    int putRtmpPacket(RTMPPacket *packet);

    RTMPPacket* getRtmpPacket();

    void clearQueue();

    void notifyQueue();


};


#endif //WLLIVEPUSHER_WLQUEUE_H
