package com.wjw.usbkey.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.util.Log;

import com.vondear.rxtool.view.RxToast;
import com.wjw.usbkey.message.UsbStatusChangeEvent;
import com.wjw.usbkey.util.Consts;

import org.greenrobot.eventbus.EventBus;

/**
 * Created by yuanpk on 2018/8/2  14:22
 * <p>
 * Description:usb插拔广播接受
 */
public class UsbStateChangeReceiver extends BroadcastReceiver {
    private static final String TAG = "UsbStateChangeReceiver";

    private boolean isConnected;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action.equals(UsbManager.ACTION_USB_DEVICE_ATTACHED)) {
            isConnected = true;
            RxToast.showToast("onReceive: USB设备已连接");

            UsbDevice device_add = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            if (device_add != null) {
                EventBus.getDefault().post(new UsbStatusChangeEvent(isConnected));
            } else {
                RxToast.showToast("onReceive: device is null");
            }


        } else if (action.equals(UsbManager.ACTION_USB_DEVICE_DETACHED)) {
            //Log.i(TAG, "onReceive: USB设备已分离");
            isConnected = false;
            RxToast.showToast("onReceive: USB设备已拔出");

            EventBus.getDefault().post(new UsbStatusChangeEvent(isConnected));
        } else if (action.equals(Consts.ACTION_USB_PERMISSION)) {

            UsbDevice usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            //允许权限申请
            if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                if (usbDevice != null) {
                    Log.i(TAG, "onReceive: 权限已获取");
                    EventBus.getDefault().post(new UsbStatusChangeEvent(true, usbDevice));
                } else {
                    RxToast.showToast("没有插入U盘");
                }
            } else {
                RxToast.showToast("未获取到U盘权限");
            }
        } else {
            //Log.i(TAG, "onReceive: action=" + action);
            RxToast.showToast("action= " + action);
        }
    }
}
