package com.wjw.usbkey.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Created by wjw on 2019/11/23 0023
 */
public class NotificationClickReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        CopyPasteUtil copyPasteUtil = CopyPasteUtil.getInstance();
        copyPasteUtil.close();
        copyPasteUtil.isClose =true;
    }
}
