package com.wjw.usbkey;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.support.v4.content.FileProvider;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import com.github.mjdev.libaums.UsbMassStorageDevice;
import com.github.mjdev.libaums.fs.FileSystem;
import com.github.mjdev.libaums.fs.UsbFile;
import com.github.mjdev.libaums.fs.UsbFileInputStream;
import com.github.mjdev.libaums.partition.Partition;
import com.leon.lib.settingview.LSettingItem;
import com.vondear.rxtool.RxAppTool;
import com.vondear.rxtool.RxPermissionsTool;
import com.vondear.rxtool.RxSPTool;
import com.vondear.rxtool.RxTool;
import com.vondear.rxtool.view.RxToast;
import com.wjw.usbkey.util.ASyunew6;
import com.wjw.usbkey.util.Consts;
import com.wjw.usbkey.util.CopyPasteUtil;
import com.wjw.usbkey.util.NotificationUtils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.Objects;

public class UsbKeyA6 extends Activity {

    private UsbManager mUsbManager;
    private UsbDevice mDevicePath;
    private UsbFile cFolder;
    private CopyPasteUtil mCopyPasteUtil;
    private UsbMassStorageDevice storageDevice;
    private ASyunew6 yt_j6;
    private boolean isOpen;
    private LSettingItem openKey, keyId, useState, installApk, copyMap;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.launcher);
        keyId = findViewById(R.id.keyId);
        openKey = findViewById(R.id.openKey);
        useState = findViewById(R.id.useState);
        installApk = findViewById(R.id.installApk);
        installApk.setRightText(RxAppTool.isInstallApp(UsbKeyA6.this, "com.cetc32.map") ? "已安装" : "未安装");
        copyMap = findViewById(R.id.copyMap);
        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        yt_j6 = new com.wjw.usbkey.util.ASyunew6(mUsbManager, this);
        mDevicePath = yt_j6.FindPort(0);
        if (null != mDevicePath) {
            yt_j6.SetU(false, true, mDevicePath);
        }
        RxPermissionsTool.with(this)
                .addPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                .addPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                .addPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .addPermission(Manifest.permission.ACCESS_NOTIFICATION_POLICY)
                .addPermission(Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE)
                .addPermission(Manifest.permission.REQUEST_INSTALL_PACKAGES)
                .addPermission(Manifest.permission.READ_PHONE_STATE)
                .initPermission();

        RxTool.init(this);

        openKey.setmOnLSettingItemClick(new LSettingItem.OnLSettingItemClick() {
            @Override
            public void click(boolean isChecked) {
                if (mCopyPasteUtil.isClose) {
                    ShowMessage("正在复制文件中");
                    return;
                }
                if (null == mDevicePath) {
                    openKey.setRightText("未识别有效密钥");
                    return;
                }
                validKey();
            }
        });

        installApk.setmOnLSettingItemClick(new LSettingItem.OnLSettingItemClick() {
            @Override
            public void click(boolean isChecked) {

                if (!RxSPTool.getBoolean(UsbKeyA6.this, "keyStatus")) {
                    RxToast.showToast("非法密钥，无法安装应用!");
                    return;
                }

                if (!isOpen) {
                    RxToast.showToast("请点击License授权密钥!");
                    return;
                }
                boolean isInstall = RxAppTool.isInstallApp(UsbKeyA6.this, "com.cetc32.map");
                if (!isInstall) {
                    redUDiskDriverList();
                    installBySlient();
                } else {
                    RxToast.showToast("已安装应用");
                }
            }
        });

        copyMap.setmOnLSettingItemClick(new LSettingItem.OnLSettingItemClick() {
            @Override
            public void click(boolean isChecked) {

                if (!RxSPTool.getBoolean(UsbKeyA6.this, "keyStatus")) {
                    RxToast.showToast("非法密钥, 无法复制地图!");
                    return;
                }

                if (!isOpen) {
                    RxToast.showToast("请点击License授权密钥!");
                    return;
                }

                boolean isInstall = RxAppTool.isInstallApp(UsbKeyA6.this, "com.cetc32.map");
                if (!isInstall) {
                    RxToast.showToast("未安装指定应用，请先安装");
                    return;
                }

                AlertDialog.Builder builder = new AlertDialog.Builder(UsbKeyA6.this);
                builder.setMessage("您需要同步复制地图文件吗？");
                builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        redUDiskDriverList();
                        copyFileDir();
                    }
                });
                builder.setNeutralButton("取消", null);
                AlertDialog dialog = builder.create();
                dialog.show();
            }
        });

        initData();
        // 注册事件通知，以便能检测到硬件拨出消息
        IntentFilter filter = new IntentFilter(Consts.ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(mUsbReceiver, filter);
        try {
            if (!RxSPTool.getBoolean(UsbKeyA6.this, "isFirst")) {
                keyId.setRightText("首装，请重新插拔密钥");
                RxSPTool.putBoolean(UsbKeyA6.this, "isFirst", true);
                yt_j6.SetU(false, true, mDevicePath);
                return;
            }
            keyId.setRightText(getUId());
        } catch (Exception e) {
            RxSPTool.putBoolean(this, "keyStatus", false);
            keyId.setRightText("未识别有效密钥");
            return;
        }
    }

    BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                String deviceName = device.getDeviceName();
                isOpen = false;
            }
            if (Consts.ACTION_USB_PERMISSION.equals(action)) {
                UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    if (device != null) {
                        openKey.setRightText("授权成功");
                        readDevice(storageDevice);
                        isOpen = true;
                    }
                } else {
                    ShowMessage("用户操作无效!");
                    openKey.setRightText("用户取消授权");
                }
            }

        }
    };

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        installApk.setRightText(RxAppTool.isInstallApp(UsbKeyA6.this, "com.cetc32.map") ? "已安装" : "未安装");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mCopyPasteUtil.isClose) {
            mCopyPasteUtil.isClose = false;
            ShowMessage("非法处理,复制终止!");
        }
        yt_j6.SetU(false, false, mDevicePath);
    }


    @Override
    public void onStop() {
        super.onStop();
        if (mCopyPasteUtil.isClose) {
            mCopyPasteUtil.isClose = false;
            copyMap.setRightText("非法处理,复制终止");
        }
        openKey.setRightText("授权终止,点击授权");
        yt_j6.SetU(false, false, mDevicePath);
    }

    private void ShowMessage(String info) {
        Toast.makeText(this, info, Toast.LENGTH_LONG).show();
    }

    private String getUId() {
        /*
         *  用于返回加密狗的ID号，加密狗的ID号由两个长整型组成。
         *  '提示1：锁ID可以是开发商唯一或每一把都是唯一的，开发商唯一是指同一开发商相同，不同的开发商不相同，每一把是唯一的，是指每一把锁的ID都不相同、
         *  '提示2、如果是每一把都是唯一的，需要在订货时告知我们)
         *  '提示3: ID唯一是指两个ID转化为16进制字符串后并连接起来后是唯一的
         */
        int ID1, ID2;

        ID1 = yt_j6.GetID_1(mDevicePath);
        if (yt_j6.get_LastError() != 0) {
            ShowMessage("返回ID1错误" + yt_j6.get_LastError());
            return "密钥1错误" + yt_j6.get_LastError();
        }
        ID2 = yt_j6.GetID_2(mDevicePath);
        if (yt_j6.get_LastError() != 0) {
            ShowMessage("返回ID2错误" + yt_j6.get_LastError());
            return "密钥2错误" + yt_j6.get_LastError();
        }
        return String.valueOf(Integer.toHexString(ID1) + Integer.toHexString(ID2)).toUpperCase();
    }

    private int writeUid(String uid, short length) {
        return yt_j6.YWriteString(uid, length, "FFFFFFFF", "FFFFFFFF", mDevicePath);
    }

    private String readUid(short index, short length) {
        return yt_j6.YReadString(index, length, "FFFFFFFF", "FFFFFFFF", mDevicePath);
    }

    @SuppressLint("HandlerLeak")
    private void initData() {
        mCopyPasteUtil = CopyPasteUtil.getInstance();
    }

    /**
     * redUDiskDriverList
     */
    private void redUDiskDriverList() {
        //设备管理器
        UsbMassStorageDevice[] storageDevices = UsbMassStorageDevice.getMassStorageDevices(this);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, new Intent(Consts.ACTION_USB_PERMISSION), PendingIntent.FLAG_UPDATE_CURRENT);
        //一般手机只有1个OTG插口
        for (UsbMassStorageDevice device : storageDevices) {
            readDevice(device);
        }
    }

    public static File asFile(InputStream inputStream) throws IOException {
        File tmp = File.createTempFile("ukey", ".apk");
        OutputStream os = new FileOutputStream(tmp);
        int bytesRead;
        byte[] buffer = new byte[8192];
        while ((bytesRead = inputStream.read(buffer, 0, 8192)) != -1) {
            os.write(buffer, 0, bytesRead);
        }
        inputStream.close();
        return tmp;
    }

    private void copyFileDir() {
        UsbFile[] usbFiles = new UsbFile[0];
        try {
            usbFiles = cFolder.listFiles();
        } catch (IOException e) {
            RxToast.showToast("文件读取错误");
        }
        UsbFile copyUsbFile = null;
        for (UsbFile usbFile : usbFiles) {
            if ("mapdata".equals(usbFile.getName().toLowerCase())) {
                copyUsbFile = usbFile;
                break;
            }
        }
        if (copyUsbFile == null) {
            RxToast.showToast("无地图数据，目录名称为/mapdata");
            copyMap.setRightText("复制失败");
            return;
        }
        String outDir = Environment.getExternalStorageDirectory().getPath() + "/mapdata";
        handler.postDelayed(runnable, 1000);
        try {
            mCopyPasteUtil.copyDir(copyUsbFile, outDir);
        } catch (Exception e) {
            handler.removeCallbacks(runnable);
            e.printStackTrace();
        }
    }

    Handler handler = new Handler() {
        @SuppressWarnings("unchecked")
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 101:
                    copyMap.setRightText(String.valueOf(msg.obj));
                    break;
            }
        }
    };

    private void authKey() {
        try {
            yt_j6.SetU(true, false, mDevicePath);
            if (yt_j6.get_LastError() != 0) {
                openKey.setRightText("激活密钥失败!");
                return;
            }
            //设备管理器
            UsbMassStorageDevice[] storageDevices = UsbMassStorageDevice.getMassStorageDevices(UsbKeyA6.this);
            while (true) {
                storageDevices = UsbMassStorageDevice.getMassStorageDevices(UsbKeyA6.this);
                if (storageDevices.length > 0) {
                    break;
                }
            }
            PendingIntent pendingIntent = PendingIntent.getBroadcast(UsbKeyA6.this, 0, new Intent(Consts.ACTION_USB_PERMISSION), PendingIntent.FLAG_UPDATE_CURRENT);
            //一般手机只有1个OTG插口
            for (UsbMassStorageDevice device : storageDevices) {
                //读取设备是否有权限
                storageDevice = device;
                if (mUsbManager.hasPermission(device.getUsbDevice())) {
                    this.readDevice(device);
                } else {
                    mUsbManager.requestPermission(device.getUsbDevice(), pendingIntent);
                }
            }
            if (storageDevices.length == 0) {
                yt_j6.SetU(false, false, mDevicePath);
                RxToast.showToast("请重新拔插密钥");
            }
        } catch (Exception e) {
            yt_j6.SetU(false, false, mDevicePath);
            RxToast.showToast("应用处理异常：" + e.getMessage());
        }
    }

    private void validKey() {
        try {
            String serialNo = getSerialNumber();

            if (null == serialNo || serialNo.equals("")) {
                ShowMessage("获取本机序列号失败!");
                return;
            }
            // 检测USB是否已被占用
            String uid = readUid(Short.valueOf("10"), (short) 10);
            if (Objects.equals(uid, "0000000000")) {
                writeUid(serialNo.substring(serialNo.length() - 10), (short) 10);
                useState.setRightText("合法密钥");
                isOpen = true;
                RxSPTool.putBoolean(UsbKeyA6.this, "keyStatus", true);
            }
            // 两组id一致，成功绑定验证成功
            else if (!uid.equals(serialNo.substring(serialNo.length() - 10))) {
                useState.setRightText("非法密钥");
                openKey.setRightText("授权失败");
                RxSPTool.putBoolean(UsbKeyA6.this, "keyStatus", false);
            } else {
                RxSPTool.putBoolean(UsbKeyA6.this, "keyStatus", true);
                useState.setRightText("合法密钥");
                isOpen = true;
            }

            // 授权USB操作权限
            if (isOpen) {
                authKey();
            }
        } catch (Exception e) {
            RxToast.showToast("请重新拔插密钥");
        }
    }

    Runnable runnable = new Runnable() {
        @Override
        public void run() {
            if (mCopyPasteUtil.isClose) {
                handler.removeCallbacks(this);
            } else {
                if (mCopyPasteUtil.mProgress == 100) {
                    Message message = handler.obtainMessage();
                    message.what = 101;
                    message.obj = "复制完成";
                    handler.sendMessage(message);
                    handler.removeCallbacks(this);
                    NotificationUtils.showNotification("", "复制完成", 99, "89", 100, 100);
                } else {
                    NotificationUtils.showNotification("已复制" + mCopyPasteUtil.mProgress + "%", mCopyPasteUtil.fileVolumeText, 99, "89", mCopyPasteUtil.mProgress, 100);
                    Message message = handler.obtainMessage();
                    message.what = 101;
                    message.obj = "已复制：" + mCopyPasteUtil.mProgress + "%";
                    handler.sendMessage(message);
                    handler.removeCallbacks(this);
                    handler.postDelayed(this, 1000);
                }
            }
        }
    };

    private void readDevice(UsbMassStorageDevice device) {
        try {
            device.init();//初始化
            //设备分区
            Partition partition = device.getPartitions().get(0);
            //文件系统
            FileSystem currentFs = partition.getFileSystem();
            currentFs.getVolumeLabel();//可以获取到设备的标识
            //通过FileSystem可以获取当前U盘的一些存储信息，包括剩余空间大小，容量等等
            Log.e("Capacity: ", currentFs.getCapacity() + "");
            Log.e("Occupied Space: ", currentFs.getOccupiedSpace() + "");
            Log.e("Free Space: ", currentFs.getFreeSpace() + "");
            Log.e("Chunk size: ", currentFs.getChunkSize() + "");
            cFolder = currentFs.getRootDirectory();//设置当前文件对象为根目录
        } catch (Exception e) {
            yt_j6.SetU(false, false, mDevicePath);
        }
    }

    /**
     * 静默安装，1-安装成功，或没有升级文件，2-升级安装出现异常，-1-程序异常
     */
    public void installBySlient() {
        try {
            Uri uri;
            try {
                UsbFile apkFile = null;
                if (cFolder == null) {
                    RxToast.showToast("未识别有效密钥，请重新插拔");
                    return;
                }
                UsbFile[] usbFiles = cFolder.listFiles();
                for (UsbFile usbFile : usbFiles) {
                    if (usbFile.getName().toUpperCase().equals("CETC32.APK")) {
                        apkFile = usbFile;
                        break;
                    }
                }

                if (null == apkFile) {
                    RxToast.showToast("密钥中未找到指定map.apk安装文件");
                    return;
                }

                UsbFileInputStream fileInputStream = new UsbFileInputStream(apkFile);
                BufferedInputStream inBuff = new BufferedInputStream(fileInputStream);
                File file = asFile(inBuff);
                Intent intent = new Intent(Intent.ACTION_VIEW);
                // 7.0 Android N
                if (android.os.Build.VERSION.SDK_INT >= 24) {
                    uri = FileProvider.getUriForFile(UsbKeyA6.this, "com.wjw.usbkey.fileProvider", file);
                    intent.setAction(Intent.ACTION_INSTALL_PACKAGE);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    //7.0以后，系统要求授予临时uri读取权限，安装完毕以后，系统会自动收回权限，该过程没有用户交互
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }
                // 7.0 以下
                else {

                    uri = Uri.fromFile(file);
                    intent.setAction(Intent.ACTION_VIEW);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                }
                intent.setDataAndType(uri, "application/vnd.android.package-archive");
                startActivity(intent);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            RxToast.showToast("应用安装失败");
            yt_j6.SetU(false, false, mDevicePath);
        }
    }

    /**
     * 获取手机序列号
     *
     * @return 手机序列号
     */
    @SuppressLint({"NewApi", "MissingPermission", "HardwareIds", "PrivateApi"})
    public String getSerialNumber() {
        String serial = "";
        try {
            //10.0+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                TelephonyManager tm = (TelephonyManager) UsbKeyA6.this.getSystemService(Service.TELEPHONY_SERVICE);
                return Settings.System.getString(getContentResolver(), Settings.Secure.ANDROID_ID).toUpperCase();
            }

            //8.0+
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N) {
                serial = Build.SERIAL;
            } else {
                //8.0-
                Class<?> c = Class.forName("android.os.SystemProperties");
                Method get = c.getMethod("get", String.class);
                serial = (String) get.invoke(c, "ro.serialno");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return serial;
    }
}