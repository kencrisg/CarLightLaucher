package com.tw.service.xt.aidl;

interface IMusicCallBack {
    void musicNext();
    void musicPre();
    void musicPlay();
    void musicPause();
    void musicMode(int m);
    void extendedInterface(in Bundle b);
}
