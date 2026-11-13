package com.tw.service.xt.aidl;

interface IBTCallBack {
    void btCall(String number);
    void btAccept();
    void btHangUP();
    void btOpenMusic();
    void btPlayMusic();
    void btPre();
    void btNext();
    void btGetPhoneStatus();
    void btGetConnectedStatus();
    void extendedInterface(in Bundle b);
}
