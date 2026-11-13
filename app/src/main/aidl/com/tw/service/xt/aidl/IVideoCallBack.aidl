package com.tw.service.xt.aidl;

interface IVideoCallBack {
    void videoNext();
    void videoPre();
    void videoPlay();
    void videoPause();
    void extendedInterface(in Bundle b);
}
