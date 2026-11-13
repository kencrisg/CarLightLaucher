package com.tw.service.xt.aidl;

interface IRadioCallBack {
    void radioNext();
    void radioPre();
    void radioSearchNext();
    void radioSearchPre();
    void radioSetChannel(String ch);
    void radioBand(int band);
    void radioBandChange();
    void extendedInterface(in Bundle b);
}
