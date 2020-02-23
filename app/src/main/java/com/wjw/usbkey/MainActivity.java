package com.wjw.usbkey;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.github.mjdev.libaums.UsbMassStorageDevice;
import com.github.mjdev.libaums.fs.FileSystem;
import com.github.mjdev.libaums.fs.UsbFile;
import com.github.mjdev.libaums.partition.Partition;
import com.vondear.rxtool.RxLogTool;
import com.vondear.rxtool.RxPermissionsTool;
import com.vondear.rxtool.view.RxToast;
import com.wjw.usbkey.message.UsbStatusChangeEvent;
import com.wjw.usbkey.receiver.UsbStateChangeReceiver;
import com.wjw.usbkey.util.ASyunew6;
import com.wjw.usbkey.util.Consts;
import com.wjw.usbkey.util.CopyPasteUtil;
import com.wjw.usbkey.util.NotificationUtils;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.IOException;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private TextView tvContent;
    private RadioGroup rgSetting;
    private RadioButton rbAuto;
    private RadioButton rbHand;
    private Button btCopy;
    private UsbMassStorageDevice[] storageDevices;
    private UsbFile cFolder;
    private Handler mHandler;
    private final static String U_DISK_FILE_NAME = "u_disk.txt";
    private CopyPasteUtil mCopyPasteUtil;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        RxPermissionsTool.with(this)
                .addPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                .addPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .addPermission(Manifest.permission.ACCESS_NOTIFICATION_POLICY)
                .addPermission(Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE)
                .initPermission();
        initView();
        initClick();
        initData();
    }

    @SuppressLint("HandlerLeak")
    private void initData() {
        mCopyPasteUtil = CopyPasteUtil.getInstance();
        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case 100:
                        RxToast.showToast("保存成功");
                        break;
                    case 101:
                        String txt = msg.obj.toString();
                        if (!TextUtils.isEmpty(txt))
                            tvContent.setText("读取到的数据是：" + txt);
                        break;
                }
            }

        };

        EventBus.getDefault().register(this);
        registerUDiskReceiver();
    }

    private void initClick() {
        btCopy.setOnClickListener(this);
    }

    private void initView() {
        tvContent = findViewById(R.id.tv_content);
        rgSetting = findViewById(R.id.rg_setting);
        rbAuto = findViewById(R.id.rb_auto);
        rbHand = findViewById(R.id.rb_hand);
        btCopy = findViewById(R.id.bt_copy);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.bt_copy:
                copyFileDir();
                break;
            default:
                break;
        }
    }


    Handler handler = new Handler();
    Runnable runnable = new Runnable() {
        @Override
        public void run() {
            if (mCopyPasteUtil.isClose) {
                handler.removeCallbacks(this);
            } else {
                System.out.println("进度-------->" + mCopyPasteUtil.mProgress);
                if (mCopyPasteUtil.mProgress == 100) {
                    handler.removeCallbacks(this);
                    NotificationUtils.showNotification("", "复制完成", 99, "89", 100, 100);
                } else {
                    NotificationUtils.showNotification("已复制" + mCopyPasteUtil.mProgress + "%", mCopyPasteUtil.fileVolumeText, 99, "89", mCopyPasteUtil.mProgress, 100);
                    handler.postDelayed(this, 1000);
                }
            }
        }
    };

    private void copyFileDir() {
        UsbFile[] usbFiles = new UsbFile[0];
        try {
            if (cFolder == null) {
                RxToast.showToast("未识别UKEY，请重新插拔");
                return;
            }
            usbFiles = cFolder.listFiles();
        } catch (IOException e) {
            RxToast.showToast("文件读取错误");
            return;
        }
        UsbFile copyUsbFile = null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < usbFiles.length; i++) {
            UsbFile usbFile = usbFiles[i];
            if ("copy".equals(usbFile.getName())) {
                copyUsbFile = usbFile;
            }
            sb.append(usbFiles[i].getAbsolutePath()).append("--->").append(usbFiles[i].getName());
        }

        tvContent.setText(sb);


        String outDir = Environment.getExternalStorageDirectory().getPath() + "/Download/";
        handler.postDelayed(runnable, 1000);

        try {
            mCopyPasteUtil.copyDir(copyUsbFile, outDir);
        } catch (Exception e) {
            handler.removeCallbacks(runnable);
            e.printStackTrace();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onNetworkChangeEvent(UsbStatusChangeEvent event) {
        if (event.isConnected) {
            //接收到U盘插入广播，尝试读取U盘设备数据
            redUDiskDriverList();
        } else if (event.isGetPermission) {
            UsbDevice usbDevice = event.usbDevice;
            //用户已授权，可以进行读取操作
            RxLogTool.i("onNetworkChangeEvent: ");
            RxToast.showToast("onReceive: 权限已获取");
            readDevice(getUsbMass(usbDevice));
        } else {

        }
    }

    private UsbMassStorageDevice getUsbMass(UsbDevice usbDevice) {
        for (UsbMassStorageDevice device : storageDevices) {
            if (usbDevice.equals(device.getUsbDevice())) {
                return device;
            }
        }
        return null;
    }

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
            e.printStackTrace();
        }
    }

    /**
     * @description U盘设备读取
     * @author ldm
     * @time 2017/9/1 17:20
     */
    private void redUDiskDriverList() {
        //设备管理器
        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        ASyunew6 yt_j6 = new com.wjw.usbkey.util.ASyunew6(usbManager, this);
        UsbDevice devices = yt_j6.FindPort(0);
        yt_j6.SetU(true, false, devices);
        //获取U盘存储设备
        storageDevices = UsbMassStorageDevice.getMassStorageDevices(this);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, new Intent(Consts.ACTION_USB_PERMISSION), 0);
        //一般手机只有1个OTG插口
        for (UsbMassStorageDevice device : storageDevices) {
            //读取设备是否有权限
            if (usbManager.hasPermission(device.getUsbDevice())) {
                RxToast.showToast("有权限");
                readDevice(device);
            } else {
                RxToast.showToast("没有权限，进行申请");
                //没有权限，进行申请
                usbManager.requestPermission(device.getUsbDevice(), pendingIntent);
            }
        }
        if (storageDevices.length == 0) {
            RxToast.showToast("请插入可用的U盘");
        }
    }

    /**
     * usb插拔广播 注册
     */
    private void registerUDiskReceiver() {
        IntentFilter usbDeviceStateFilter = new IntentFilter();
        usbDeviceStateFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        usbDeviceStateFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        usbDeviceStateFilter.addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED);
        usbDeviceStateFilter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
        usbDeviceStateFilter.addAction("android.hardware.usb.action.USB_STATE");
        usbDeviceStateFilter.addAction(Consts.ACTION_USB_PERMISSION); //自定义广播
        registerReceiver(new UsbStateChangeReceiver(), usbDeviceStateFilter);
    }
}
