package com.tw.service.xt.aidl;

interface ITWCommandCallbackAidl {
    void onSystemVolume(int v);
    void onVolumeStatus(int s);
    void onBtPhoneStatus(int s);
    void onBtCallStatus(int s, String a, String b);
    void onBtConnectedStatus(int s);
    void onReverseStatus(int s);
    void onSleepStatus(int s);
    void onExtendedInterface(in Bundle b);
}
