package com.wjw.usbkey.base;

import android.app.Application;

import com.vondear.rxtool.RxTool;

/**
 * Created by wjw on 2019/11/24 0024
 */
public class BaseApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        RxTool.init(this);
    }
}
