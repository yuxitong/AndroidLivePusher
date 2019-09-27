//
// Created by yangw on 2018-9-14.
//



#include "RtmpPush.h"

RtmpPush::RtmpPush(const char *url, CallJava *wlCallJava) {
    this->url = static_cast<char *>(malloc(512));
    strcpy(this->url, url);
    this->queue = new MessageQueue();
    this->wlCallJava = wlCallJava;
}

RtmpPush::~RtmpPush() {
    queue->notifyQueue();
    queue->clearQueue();
    free(url);
}

void *callBackPush(void *data) {

    RtmpPush *rtmpPush = static_cast<RtmpPush *>(data);
    rtmpPush->startPushing = false;
    rtmpPush->rtmp = RTMP_Alloc();
    RTMP_Init(rtmpPush->rtmp);
    rtmpPush->rtmp->Link.timeout = 10;
    rtmpPush->rtmp->Link.lFlags |= RTMP_LF_LIVE;
    RTMP_SetupURL(rtmpPush->rtmp, rtmpPush->url);
    RTMP_EnableWrite(rtmpPush->rtmp);

    if (!RTMP_Connect(rtmpPush->rtmp, NULL)) {
//        LOGE("can not connect the url");
        rtmpPush->wlCallJava->onConnectFail("can not connect the url");
        goto end;
    }
    if (!RTMP_ConnectStream(rtmpPush->rtmp, 0)) {

        rtmpPush->wlCallJava->onConnectFail("can not connect the stream of service");
        goto end;
    }
    rtmpPush->wlCallJava->onConnectsuccess();
    rtmpPush->startPushing = true;
    rtmpPush->startTime = RTMP_GetTime();
    while (true) {
        if (!rtmpPush->startPushing) {
            break;
        }

        RTMPPacket *packet = NULL;
        packet = rtmpPush->queue->getRtmpPacket();
        if (packet != NULL) {
            int result = RTMP_SendPacket(rtmpPush->rtmp, packet, 1);
            LOGD("RTMP_SendPacket result is %d", result);
            RTMPPacket_Free(packet);
            free(packet);
            packet = NULL;
        } else {
            LOGD("RTMP_SendPacket else %d", 123);

        }
    }
    end:
    RTMP_Close(rtmpPush->rtmp);
    RTMP_Free(rtmpPush->rtmp);
    rtmpPush->rtmp = NULL;
    pthread_exit(&rtmpPush->push_thread);
}

void RtmpPush::init() {
    wlCallJava->onConnectint(WL_THREAD_MAIN);
    pthread_create(&push_thread, NULL, callBackPush, this);

}

void RtmpPush::pushSPSPPS(char *sps, int sps_len, char *pps, int pps_len) {

    int bodysize = sps_len + pps_len + 16;
    RTMPPacket *packet = static_cast<RTMPPacket *>(malloc(sizeof(RTMPPacket)));
    RTMPPacket_Alloc(packet, bodysize);
    RTMPPacket_Reset(packet);

    char *body = packet->m_body;

    int i = 0;

    body[i++] = 0x17;

    body[i++] = 0x00;
    body[i++] = 0x00;
    body[i++] = 0x00;
    body[i++] = 0x00;

    body[i++] = 0x01;
    body[i++] = sps[1];
    body[i++] = sps[2];
    body[i++] = sps[3];

    body[i++] = 0xFF;

    body[i++] = 0xE1;
    body[i++] = (sps_len >> 8) & 0xff;
    body[i++] = sps_len & 0xff;
    memcpy(&body[i], sps, sps_len);
    i += sps_len;

    body[i++] = 0x01;
    body[i++] = (pps_len >> 8) & 0xff;
    body[i++] = pps_len & 0xff;
    memcpy(&body[i], pps, pps_len);

    packet->m_packetType = RTMP_PACKET_TYPE_VIDEO;
    packet->m_nBodySize = bodysize;
    packet->m_nTimeStamp = 0;
    packet->m_hasAbsTimestamp = 0;
    packet->m_nChannel = 0x04;
    packet->m_headerType = RTMP_PACKET_SIZE_MEDIUM;
    packet->m_nInfoField2 = rtmp->m_stream_id;

    queue->putRtmpPacket(packet);
}

void RtmpPush::pushVideoData(char *data, int data_len, bool keyframe) {

    int bodysize = data_len + 9;
    RTMPPacket *packet = static_cast<RTMPPacket *>(malloc(sizeof(RTMPPacket)));
    RTMPPacket_Alloc(packet, bodysize);
    RTMPPacket_Reset(packet);

    char *body = packet->m_body;
    int i = 0;

    if (keyframe) {
        body[i++] = 0x17;
    } else {
        body[i++] = 0x27;
    }

    body[i++] = 0x01;
    body[i++] = 0x00;
    body[i++] = 0x00;
    body[i++] = 0x00;

    body[i++] = (data_len >> 24) & 0xff;
    body[i++] = (data_len >> 16) & 0xff;
    body[i++] = (data_len >> 8) & 0xff;
    body[i++] = data_len & 0xff;
    memcpy(&body[i], data, data_len);

    packet->m_packetType = RTMP_PACKET_TYPE_VIDEO;
    packet->m_nBodySize = bodysize;
    packet->m_nTimeStamp = RTMP_GetTime() - startTime;
    packet->m_hasAbsTimestamp = 0;
    packet->m_nChannel = 0x04;
    packet->m_headerType = RTMP_PACKET_SIZE_LARGE;
    packet->m_nInfoField2 = rtmp->m_stream_id;

    queue->putRtmpPacket(packet);
}

void RtmpPush::pushAudioData(char *data, int data_len) {

    int bodysize = data_len + 2;
    RTMPPacket *packet = static_cast<RTMPPacket *>(malloc(sizeof(RTMPPacket)));
    RTMPPacket_Alloc(packet, bodysize);
    RTMPPacket_Reset(packet);
    char *body = packet->m_body;
    body[0] = 0xAF;
    body[1] = 0x01;
    memcpy(&body[2], data, data_len);

    packet->m_packetType = RTMP_PACKET_TYPE_AUDIO;
    packet->m_nBodySize = bodysize;
    packet->m_nTimeStamp = RTMP_GetTime() - startTime;
    packet->m_hasAbsTimestamp = 0;
    packet->m_nChannel = 0x04;
    packet->m_headerType = RTMP_PACKET_SIZE_LARGE;
    packet->m_nInfoField2 = rtmp->m_stream_id;
    queue->putRtmpPacket(packet);
}

void RtmpPush::pushStop() {
    startPushing = false;
    queue->notifyQueue();
    pthread_join(push_thread, NULL);
}
