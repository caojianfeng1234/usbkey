package com.wjw.usbkey.util;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationCompat;

import com.vondear.rxtool.RxTool;
import com.wjw.usbkey.R;

import static android.app.Notification.VISIBILITY_SECRET;
import static android.content.Context.NOTIFICATION_SERVICE;

/**
 * Created by wjw on 2019/11/20 0020
 */
public class NotificationUtils {
    private static NotificationManager manager;

    private static NotificationManager getManager() {
        if (manager == null) {
            manager = (NotificationManager) RxTool.getContext().getSystemService(NOTIFICATION_SERVICE);
        }
        return manager;
    }

    private static NotificationCompat.Builder getNotificationBuilder(String title, String content, String channelId) {
        //大于8.0

        Intent clickIntent = new Intent(RxTool.getContext(), NotificationClickReceiver.class); //点击通知之后要发送的广播
        int id = (int) (System.currentTimeMillis() / 1000);
        PendingIntent contentIntent = PendingIntent.getBroadcast(RxTool.getContext(), id, clickIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        return new NotificationCompat.Builder(RxTool.getContext()).setAutoCancel(true)
                .setContentTitle(title)
                .setContentIntent(contentIntent)
                .setDeleteIntent(contentIntent)
                .setContentText(content).setSmallIcon(R.mipmap.ic_launcher);
    }

    public static void showNotification(String title, String content, int manageId, String channelId, int progress, int maxProgress) {
        final NotificationCompat.Builder builder = getNotificationBuilder(title, content, channelId);
        builder.setOnlyAlertOnce(true);
        builder.setDefaults(Notification.FLAG_ONLY_ALERT_ONCE);
        builder.setProgress(maxProgress, progress, false);
        builder.setWhen(System.currentTimeMillis());
        getManager().notify(manageId, builder.build());
    }

    public static void showNotification(String title, String content, int manageId, String channelId) {
        final NotificationCompat.Builder builder = getNotificationBuilder(title, content, channelId);
        builder.setOnlyAlertOnce(true);
        builder.setDefaults(Notification.FLAG_ONLY_ALERT_ONCE);
        builder.setWhen(System.currentTimeMillis());
        getManager().notify(manageId, builder.build());
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public static void cancleNotification(int manageId) {
        getManager().cancel(manageId);
    }
}
