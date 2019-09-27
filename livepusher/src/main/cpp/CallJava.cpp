//
// Created by yangw on 2018-9-14.
//

#include "CallJava.h"

CallJava::CallJava(JavaVM *javaVM, JNIEnv *jniEnv, jobject *jobj) {

    this->javaVM = javaVM;
    this->jniEnv = jniEnv;
    this->jobj = jniEnv->NewGlobalRef(*jobj);

    jclass jlz = jniEnv->GetObjectClass(this->jobj);

    jmid_connecting =jniEnv->GetMethodID(jlz, "onConnecting", "()V");
    jmid_connectsuccess = jniEnv->GetMethodID(jlz, "onConnectSuccess", "()V");
    jmid_connectfail = jniEnv->GetMethodID(jlz, "onConnectFial", "(Ljava/lang/String;)V");
}

CallJava::~CallJava() {
    jniEnv->DeleteGlobalRef(jobj);
    javaVM = NULL;
    jniEnv = NULL;
}

void CallJava::onConnectint(int type) {

    if(type == WL_THREAD_CHILD)
    {
        JNIEnv *jniEnv;
        if(javaVM->AttachCurrentThread(&jniEnv, 0) != JNI_OK)
        {
            return;
        }
        jniEnv->CallVoidMethod(jobj, jmid_connecting);
        javaVM->DetachCurrentThread();
    }
    else
    {
        jniEnv->CallVoidMethod(jobj, jmid_connecting);
    }
}

void CallJava::onConnectsuccess() {
    JNIEnv *jniEnv;
    if(javaVM->AttachCurrentThread(&jniEnv, 0) != JNI_OK)
    {
        return;
    }
    jniEnv->CallVoidMethod(jobj, jmid_connectsuccess);
    javaVM->DetachCurrentThread();
}

void CallJava::onConnectFail(char *msg) {

    JNIEnv *jniEnv;
    if(javaVM->AttachCurrentThread(&jniEnv, 0) != JNI_OK)
    {
        return;
    }

    jstring jmsg = jniEnv->NewStringUTF(msg);

    jniEnv->CallVoidMethod(jobj, jmid_connectfail, jmsg);

    jniEnv->DeleteLocalRef(jmsg);
    javaVM->DetachCurrentThread();
}
