/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.wjw.usbkey;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.FileProvider;
import android.support.v4.widget.ListViewCompat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class USBKEY_A6 extends Activity
        implements View.OnClickListener, Runnable {

    private Button m_Copy, m_USBFileRead, m_getid, m_getversion, m_FindPort_2, m_FindPort_3, m_sWriteEx, m_sWrite_2Ex, m_sWriteEx_New, m_sWrite_2Ex_New, m_YReadString;
    private Button m_YWriteStringByLen, m_YWriteString, m_YReadStringByLen, m_SetCal_2, m_EncString, m_Cal, m_SetCal_New, m_EncString_New, m_Cal_New, m_ReSet;
    private UsbManager mUsbManager;
    private UsbDevice mDevicePath;
    private UsbFile cFolder;
    private CopyPasteUtil mCopyPasteUtil;
    private ASyunew6 yt_j6;
    private LSettingItem keyId, useState, installMode, installApk, copyMap;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.launcher);
        keyId = findViewById(R.id.keyId);
        useState = findViewById(R.id.useState);
        installMode = findViewById(R.id.installMode);
        installMode.setRightText("手动模式");
        installApk = findViewById(R.id.installApk);
        installApk.setRightText(isInstallPackage(this) ? "已安装" : "未安装");
        copyMap = findViewById(R.id.copyMap);
        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        yt_j6 = new com.wjw.usbkey.util.ASyunew6(mUsbManager, this);
        RxPermissionsTool.with(this)
                .addPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                .addPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .addPermission(Manifest.permission.ACCESS_NOTIFICATION_POLICY)
                .addPermission(Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE)
                .addPermission(Manifest.permission.REQUEST_INSTALL_PACKAGES)
                .addPermission(Manifest.permission.READ_PHONE_STATE)
                .initPermission();

        RxTool.init(this);
        installMode.setmOnLSettingItemClick(new LSettingItem.OnLSettingItemClick() {
            @Override
            public void click(boolean isChecked) {
                if (!RxSPTool.getBoolean(USBKEY_A6.this, "keyStatus")) {
                    RxToast.showToast("UKEY无效");
                    return;
                }
            }
        });

        installApk.setmOnLSettingItemClick(new LSettingItem.OnLSettingItemClick() {
            @Override
            public void click(boolean isChecked) {
                if (!RxSPTool.getBoolean(USBKEY_A6.this, "keyStatus")) {
                    RxToast.showToast("UKEY无效");
                    return;
                }
                if (!isInstallPackage(USBKEY_A6.this)) {
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
                if (!RxSPTool.getBoolean(USBKEY_A6.this, "keyStatus")) {
                    RxToast.showToast("UKEY无效");
                    return;
                }
                copyMap.setRightText("复制中...");
                redUDiskDriverList();
                copyFileDir();
            }
        });

        initData();
        // 注册事件通知，以便能检测到硬件拨出消息
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(mUsbReceiver, filter);

        try {
            mDevicePath = yt_j6.FindPort(0);
            keyId.setRightText(getUId());
        } catch (Exception e) {
            RxSPTool.putBoolean(this, "keyStatus", false);
            keyId.setRightText("未识别正确的UKEY");
            return;
        }

        // 获取机器IMEI序列号
        String serialNo = getSerialNumber();

        // 检测USB是否已被占用
        String uid = readUid(Short.valueOf("0"), (short) serialNo.length());
        if (Objects.equals(uid, "")) {
            writeUid(serialNo, (short) serialNo.length());
            useState.setRightText("首次绑定");
            RxSPTool.putBoolean(this, "keyStatus", true);
        }
        // 两组id一致，成功绑定验证成功
        else if (!uid.equals(serialNo)) {
            useState.setRightText("被占用");
            RxSPTool.putBoolean(this, "keyStatus", false);
        } else {
            RxSPTool.putBoolean(this, "keyStatus", true);
            useState.setRightText("匹配成功");
        }

        // 是否自动或手动模式
        boolean isAuto = RxSPTool.getBoolean(this, "isAuto");

        // true为自动安装，检测app是否安装，检测地图文件是否覆盖
        if (isAuto) {
            // 验证是否已安装指定apk软件
            boolean flag = isInstallPackage(this);
            if (!flag) {
                // 执行安装软件
                installBySlient();
            }
            // 打开usb文件
            redUDiskDriverList();
            if (cFolder != null) {
                // 增加提示是否复制文件
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage("您需要同步复制地图文件吗？");
                builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        copyFileDir();
                    }
                });
                builder.setNeutralButton("取消", null);
                AlertDialog dialog = builder.create();
                dialog.show();
            }
        }
    }

    BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                String deviceName = device.getDeviceName();
                if (mDevicePath != null && deviceName.equals(mDevicePath.getDeviceName())) {
                    ShowMessage("收到USB硬件被拨出消息，可以在收到消息后调用对应的检查锁函数来检查是否存在对应的加密锁");
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
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    private void ShowMessage(String info) {
        Toast.makeText(this, info, Toast.LENGTH_LONG).show();
    }

    /**
     * 用于返回加密狗的版本号
     */
    private void getVersion() {
        int version;
        version = yt_j6.GetVersion(mDevicePath);
        if (yt_j6.get_LastError() != 0) {
            ShowMessage("返回版本号错误");
            return;
        }
        ShowMessage("已成功返回锁的版本号:" + Integer.toString(version));
    }

    private String getUId() {
        /**
         *  用于返回加密狗的ID号，加密狗的ID号由两个长整型组成。
         *  '提示1：锁ID可以是开发商唯一或每一把都是唯一的，开发商唯一是指同一开发商相同，不同的开发商不相同，每一把是唯一的，是指每一把锁的ID都不相同、
         *  '提示2、如果是每一把都是唯一的，需要在订货时告知我们)
         *  '提示3: ID唯一是指两个ID转化为16进制字符串后并连接起来后是唯一的
         */
        int ID1, ID2;

        ID1 = yt_j6.GetID_1(mDevicePath);
        if (yt_j6.get_LastError() != 0) {
            ShowMessage("返回ID1错误");
            return "ID_1错误";
        }
        ID2 = yt_j6.GetID_2(mDevicePath);
        if (yt_j6.get_LastError() != 0) {
            ShowMessage("返回ID2错误");
            return "ID_2错误";
        }
        return String.valueOf(Integer.toHexString(ID1) + Integer.toHexString(ID2)).toUpperCase();
    }

    private void f_FindPort_2() {//使用普通算法一来查找指定的加密锁
		/*查找是否存在指定的加密狗,如果找到，则返回0,mDevicePath为锁所在的返回设备所在的路径。
		注意！！！！！！！！！这里的参数“1”及参数“134226688”，随每个软件开发商的不同而不同，因为每个开发商的加密锁的加密算法都不一样，
		1、运行我们的开发工具，
		2、在“算法设置及测试页”-》“加密”-》“请输入要加密的数据”那里随意输入一个数
		3、然后单击“加密数据(使用普通算法一)”
		4、然后就会返回对应的数据(即“加密后的数据”)，
		然后将输入的数和返回的数替换这里的参数“1”及参数“134226688”*/
        mDevicePath = yt_j6.FindPort_2(0, 1, 134226688);
        if (yt_j6.get_LastError() != 0) ShowMessage("未找到指定的加密锁");
        else ShowMessage("找到指定的加密锁");
    }

    private void f_FindPort_3() {//使用普通算法二来查找指定的加密锁
		/*查找是否存在指定的加密狗,如果找到，则返回0,mDevicePath为锁所在的返回设备所在的路径。
		注意！！！！！！！！！这里的参数“1”及参数“134226688”，随每个软件开发商的不同而不同，因为每个开发商的加密锁的加密算法都不一样，
		1、运行我们的开发工具，
		2、在“算法设置及测试页”-》“加密”-》“请输入要加密的数据”那里随意输入一个数
		3、然后单击“加密数据(使用普通算法二)”
		4、然后就会返回对应的数据(即“加密后的数据”)，
		然后将输入的数和返回的数替换这里的参数“1”及参数“134226688”*/
        int version;
        version = yt_j6.GetVersion(mDevicePath);
        if (version < 10) {
            ShowMessage("该锁的版本少于10，不支持该功能。");
            return;
        }
        mDevicePath = yt_j6.FindPort_3(0, 1, 134226688);
        if (yt_j6.get_LastError() != 0) ShowMessage("未找到指定的加密锁");
        else ShowMessage("找到指定的加密锁");
    }

    private void f_sWriteEx() {//对输入的数进行加密运算，然后读出加密运算后的结果（使用普通算法一)
        int result;
        result = yt_j6.sWriteEx(1, mDevicePath);
        if (yt_j6.get_LastError() != 0) {
            ShowMessage("加密错误");
            return;
        }
        ShowMessage("已成功进行加密运算，加密后的数据是：" + Integer.toString(result));
    }

    private void f_sWrite_2Ex() {//对输入的数进行解密运算，然后读出解密运算后的结果（使用普通算法一)
        int result;
        result = yt_j6.sWrite_2Ex(1342266881, mDevicePath);
        if (yt_j6.get_LastError() != 0) {
            ShowMessage("解密错误");
            return;
        }
        ShowMessage("已成功进行解密运算，解密后的数据是：" + Integer.toString(result));
    }

    private void f_sWriteEx_New() {//对输入的数进行加密运算，然后读出加密运算后的结果（使用普通算法二)
        int version;
        version = yt_j6.GetVersion(mDevicePath);
        if (version < 10) {
            ShowMessage("该锁的版本少于10，不支持该功能。");
            return;
        }
        int result;
        result = yt_j6.sWriteEx_New(1, mDevicePath);
        if (yt_j6.get_LastError() != 0) {
            ShowMessage("加密错误");
            return;
        }
        ShowMessage("已成功使用普通算法二进行加密运算，加密后的数据是：" + Integer.toString(result));
    }

    private void f_sWrite_2Ex_New() { //对输入的数进行解密运算，然后读出解密运算后的结果（使用普通算法二)
        int version;
        version = yt_j6.GetVersion(mDevicePath);
        if (version < 10) {
            ShowMessage("该锁的版本少于10，不支持该功能。");
            return;
        }
        int result;
        result = yt_j6.sWrite_2Ex_New(134226688, mDevicePath);
        if (yt_j6.get_LastError() != 0) {
            ShowMessage("解密错误");
            return;
        }
        ShowMessage("已成功使用普通算法二进行解密运算，解密后的数据是：" + Integer.toString(result));
    }

    private int writeUid(String uid, short length) {
        return yt_j6.YWriteString(uid, length, "FFFFFFFF", "FFFFFFFF", mDevicePath);
    }

    private String readUid(short index, short length) {
        return yt_j6.YReadString(index, length, "FFFFFFFF", "FFFFFFFF", mDevicePath);
    }

    private void f_YReadString() {//读取字符串，,使用默认的读密码
        String outString;
        short address = 0;//要读取的字符串在加密锁中储存的起始地址
        short len = 6;//注意这里的6是长度，要与写入的字符串的长度相同
        outString = yt_j6.YReadString(address, len, "FFFFFFFF", "FFFFFFFF", mDevicePath);
        if (yt_j6.get_LastError() != 0) {
            ShowMessage("读字符串失败");
            return;
        }

        ShowMessage("读字符串成功：" + outString);
    }

    private void f_YWriteStringByLen() {////写入字符串带长度，,使用默认的读密码
        int ret;
        int nlen;
        String InString;
        byte[] buf = new byte[1];
        InString = "加密锁";


        //写入字符串到地址1
        nlen = yt_j6.YWriteString(InString, (short) 1, "ffffffff", "ffffffff", mDevicePath);
        if (yt_j6.get_LastError() != 0) {
            ShowMessage("写入字符串(带长度)错误。");
            return;
        }
        //写入字符串的长度到地址0
        buf[0] = (byte) nlen;
        yt_j6.SetBuf(0, buf[0]);
        ret = yt_j6.YWrite((short) 0, (short) 1, "ffffffff", "ffffffff", mDevicePath);
        if (ret != 0)
            ShowMessage("写入字符串长度错误。错误码：");
        else
            ShowMessage("写入字符串(带长度)成功");
    }

    private void f_YWriteString() {//写入字符串到加密锁中,使用默认的写密码
        short address = 0;//要写入的地址为0
        int WriteLen = yt_j6.YWriteString("加密锁", address, "FFFFFFFF", "FFFFFFFF", mDevicePath);//WriteLen返回写入的字符串的长度，以字节来计算
        if (yt_j6.get_LastError() != 0) {
            ShowMessage("写字符串失败");
            return;
        }
        ShowMessage("写入成功。写入的字符串的长度是：" + Integer.toString(WriteLen));
    }

    private void f_YReadStringByLen() {//读取字符串带长度，,使用默认的读密码
        int ret;
        short nlen;
        String outString;
        //先从地址0读到以前写入的字符串的长度
        ret = yt_j6.YRead((short) 0, (short) 1, "ffffffff", "ffffffff", mDevicePath);
        nlen = yt_j6.GetBuf(0);
        if (ret != 0) {
            ShowMessage("读取字符串长度错误。错误码：");
            return;
        }
        //再读取相应长度的字符串
        outString = yt_j6.YReadString((short) 1, nlen, "ffffffff", "ffffffff", mDevicePath);
        if (yt_j6.get_LastError() != 0)
            ShowMessage("读取字符串(带长度)错误。错误码：");
        else
            ShowMessage("已成功读取字符串(带长度)：" + outString);
    }

    private void f_SetCal_2() {//设置增强算法一密钥
        //注意：密钥为不超过32个的0-F字符，例如：1234567890ABCDEF1234567890ABCDEF,不足32个字符的，系统会自动在后面补0
        int version;
        version = yt_j6.GetVersion(mDevicePath);
        if (version < 8) {
            ShowMessage("该锁的版本少于8，不支持该功能。");
            return;
        }

        int ret;
        String Key;
        Key = "1234567890ABCDEF1234567890ABCDEF";
        ret = yt_j6.SetCal_2(Key, mDevicePath);
        if (ret != 0) {
            ShowMessage("设置增强算法密钥错误");
            return;
        }
        ShowMessage("已成功设置了增强算法密钥一");
    }

    private void f_EncString() {//'使用增强算法一对字符串进行加密
        int version;
        version = yt_j6.GetVersion(mDevicePath);
        if (version < 8) {
            ShowMessage("该锁的版本少于8，不支持该功能。");
            return;
        }

        String InString;
        String outString;
        InString = "加密锁";
        outString = yt_j6.EncString(InString, mDevicePath);
        if (yt_j6.get_LastError() != 0) {
            ShowMessage("加密字符串出现错误");
            return;
        }
        ShowMessage("已成功使用增强算法一对字符串进行加密，加密后的字符串为：" + outString);
        //推荐加密方案：生成随机数，让锁做加密运算，同时在程序中端使用代码做同样的加密运算，然后进行比较判断。
        //'以下是对应的加密代码，可以参考使用

		/*String Outstring_2;
		InString="加密锁";
		outString=yt_j6.StrEnc(InString,"1234567890ABCDEF1234567890ABCDEF");
		Outstring_2=yt_j6.StrDec(outString,"1234567890ABCDEF1234567890ABCDEF");
		ShowMessage("软件加密后的结果是："+outString);
		ShowMessage("软件解密后的结果是："+Outstring_2);*/

    }

    private void f_Cal() {//使用增强算法一对二进制数据进行加密
        int version;
        version = yt_j6.GetVersion(mDevicePath);
        if (version < 8) {
            ShowMessage("该锁的版本少于8，不支持该功能。");
            return;
        }

        int ret, n;
        byte[] OutBuf = new byte[8];
        short Data = 0;
        for (n = 0; n < 8; n++) {
            yt_j6.SetEncBuf(n, Data);
            Data++;
        }
        ret = yt_j6.Cal(mDevicePath);
        if (ret != 0) {
            ShowMessage("加密数据时失败");
            return;
        }
        for (n = 0; n < 8; n++) {
            OutBuf[n] = (byte) yt_j6.GetEncBuf(n);
        }
        ShowMessage("已成功使用增强算法一对二进制数据进行了加密:" + Arrays.toString(OutBuf));
        //推荐加密方案：生成随机数，让锁做加密运算，同时在程序中端使用代码做同样的加密运算，然后进行比较判断。
        //以下是对应的加密代码，可以参考使用

		/*byte[] indata = new byte[]{ 0,1,2,3,4,5,6,7};
		byte [] outdata=new byte[8];byte [] outdata_2=new byte[8];
		yt_j6.EnCode(indata,outdata,"1234567890ABCDEF1234567890ABCDEF");
		yt_j6.DeCode(outdata,outdata_2,"1234567890ABCDEF1234567890ABCDEF");
		ShowMessage("软件加密后的结果是："++Arrays.toString(indata));
		ShowMessage("软件解密后的结果是："++Arrays.toString(outdata_2));*/

    }

    private void f_SetCal_New() {//设置增强算法二密钥
        //注意：密钥为不超过32个的0-F字符，例如：1234567890ABCDEF1234567890ABCDEF,不足32个字符的，系统会自动在后面补0
        int version;
        version = yt_j6.GetVersion(mDevicePath);
        if (version < 10) {
            ShowMessage("该锁的版本少于10，不支持该功能。");
            return;
        }

        int ret;
        String Key;
        Key = "ABCDEF1234567890ABCDEF1234567890";
        ret = yt_j6.SetCal_New(Key, mDevicePath);
        if (ret != 0) {
            ShowMessage("设置增强算法密钥错误");
            return;
        }
        ShowMessage("已成功设置了增强算法密钥二");

    }

    private void f_EncString_New() {//'使用增强算法二对字符串进行加密
        int version;
        version = yt_j6.GetVersion(mDevicePath);
        if (version < 10) {
            ShowMessage("该锁的版本少于10，不支持该功能。");
            return;
        }

        String InString;
        String outString;
        InString = "加密锁";
        outString = yt_j6.EncString_New(InString, mDevicePath);
        if (yt_j6.get_LastError() != 0) {
            ShowMessage("加密字符串出现错误");
            return;
        }
        ShowMessage("已成功使用增强算法二对字符串进行加密，加密后的字符串为：" + outString);
    }

    private void f_Cal_New() {//使用增强算法二对二进制数据进行加密
        int version;
        version = yt_j6.GetVersion(mDevicePath);
        if (version < 10) {
            ShowMessage("该锁的版本少于10，不支持该功能。");
            return;
        }

        int ret, n;
        byte[] OutBuf = new byte[8];
        short Data = 0;
        for (n = 0; n < 8; n++) {
            yt_j6.SetEncBuf(n, Data);
            Data++;
        }
        ret = yt_j6.Cal_New(mDevicePath);
        if (ret != 0) {
            ShowMessage("加密数据时失败");
            return;
        }
        for (n = 0; n < 8; n++) {
            OutBuf[n] = (byte) yt_j6.GetEncBuf(n);

        }
        ShowMessage("已成功使用增强算法二对二进制数据进行了加密:" + Arrays.toString(OutBuf));
    }

    private void f_ReSet() {//用于将加密锁数据全部初始化为0，只适用于版本号大于或等于9以上的锁
        int version;
        version = yt_j6.GetVersion(mDevicePath);
        if (version < 9) {
            ShowMessage("该锁的版本少于9，不支持该功能。");
            return;
        }

        int ret;
        ret = yt_j6.ReSet(mDevicePath);
        if (ret != 0) {
            ShowMessage("初始化失败");
            return;
        }

        //初始化成功，所有数据将回复到0的状态，读密码及新密码都全部为0
        //以下代码再将它重新设置为原来出厂时的FFFFFFFF-FFFFFFF
        //先设置写密码
        ret = yt_j6.SetWritePassword("00000000", "00000000", "FFFFFFFF", "FFFFFFFF", mDevicePath);
        if (ret != 0) {
            ShowMessage("设置写密码错误。");
            return;
        }
        //再设置读密码,注意，设置读密码是用原"写"密码进行设置，而不是原“读”密码
        ret = yt_j6.SetReadPassword("FFFFFFFF", "FFFFFFFF", "FFFFFFFF", "FFFFFFFF", mDevicePath);
        if (ret != 0) {
            ShowMessage("设置读密码错误。");
            return;
        }
        ShowMessage("初始化成功。");
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
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, new Intent(Consts.ACTION_USB_PERMISSION), 0);
        //一般手机只有1个OTG插口
        for (UsbMassStorageDevice device : storageDevices) {
            //读取设备是否有权限
            if (mUsbManager.hasPermission(device.getUsbDevice())) {
                readDevice(device);
            } else {
                mUsbManager.requestPermission(device.getUsbDevice(), pendingIntent);
            }
        }
        if (storageDevices.length == 0) {
            RxToast.showToast("请插入可用的U盘");
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
            if ("copy".equals(usbFile.getName())) {
                copyUsbFile = usbFile;
                break;
            }
        }
        String outDir = Environment.getExternalStorageDirectory().getPath() + "/Dowload";
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

    Handler copyHandler = new Handler() {
        @SuppressWarnings("unchecked")
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 101:
                    copyMap.setRightText(String.valueOf(msg.obj));
            }
        }
    };

    Message message = copyHandler.obtainMessage();
    Runnable runnable = new Runnable() {
        @Override
        public void run() {
            if (mCopyPasteUtil.isClose) {
                handler.removeCallbacks(this);
            } else {
                System.out.println("进度-------->" + mCopyPasteUtil.mProgress);
                if (mCopyPasteUtil.mProgress == 100) {
                    message.what = 101;
                    message.obj = "复制完成";
                    copyHandler.sendMessage(message);
                    handler.removeCallbacks(this);
                    NotificationUtils.showNotification("", "复制完成", 99, "89", 100, 100);
                } else {
                    NotificationUtils.showNotification("已复制" + mCopyPasteUtil.mProgress + "%", mCopyPasteUtil.fileVolumeText, 99, "89", mCopyPasteUtil.mProgress, 100);
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
            e.printStackTrace();
        }
    }

    public void onClick(View v) {
        //这个用于判断系统中是否存在着加密锁。不需要是指定的加密锁,
        mDevicePath = yt_j6.FindPort(0);
        if (yt_j6.get_LastError() != 0) {
            ShowMessage("未找到加密锁,请插入加密锁后，再进行操作。");
            return;
        }
        if (v == m_USBFileRead) {
            redUDiskDriverList();
        }
        // 拷贝文件
        else if (v == m_Copy) {
            copyFileDir();
        }
        //用于返回加密狗的ID号
        else if (v == m_getid) {
            getUId();
        }
        //用于返回加密狗的版本号
        else if (v == m_getversion) {
            getVersion();
        }
        //使用普通算法一来查找指定的加密锁
        else if (v == m_FindPort_2) {
            f_FindPort_2();
        }
        //使用普通算法二来查找指定的加密锁
        else if (v == m_FindPort_3) {
            f_FindPort_3();
        }
        //对输入的数进行加密运算，然后读出加密运算后的结果（使用普通算法一)
        else if (v == m_sWriteEx) {
            f_sWriteEx();
        }
        //对输入的数进行解密运算，然后读出解密运算后的结果（使用普通算法一)
        else if (v == m_sWrite_2Ex) {
            f_sWrite_2Ex();
        }
        //对输入的数进行加密运算，然后读出加密运算后的结果（使用普通算法二)
        else if (v == m_sWriteEx_New) {
            f_sWriteEx_New();
        }
        //对输入的数进行解密运算，然后读出解密运算后的结果（使用普通算法二)
        else if (v == m_sWrite_2Ex_New) {
            f_sWrite_2Ex_New();
        }
        //读取字符串
        else if (v == m_YReadString) {
            f_YReadString();
        }
        //写入字符串带长度
        else if (v == m_YWriteStringByLen) {
            f_YWriteStringByLen();
        }
        //写入字符串到加密锁中
        else if (v == m_YWriteString) {
            f_YWriteString();
        }
        //读取字符串带长度
        else if (v == m_YReadStringByLen) {
            f_YReadStringByLen();
        }
        //设置增强算法一密钥
        else if (v == m_SetCal_2) {
            f_SetCal_2();
        }
        //使用增强算法一对字符串进行加密
        else if (v == m_EncString) {
            f_EncString();
        }
        //使用增强算法一对二进制数据进行加密
        else if (v == m_Cal) {
            f_Cal();
        }
        //设置增强算法二密钥
        else if (v == m_SetCal_New) {
            f_SetCal_New();
        }
        //使用增强算法二对字符串进行加密
        else if (v == m_EncString_New) {
            f_EncString_New();
        }
        //使用增强算法二对二进制数据进行加密
        else if (v == m_Cal_New) {
            f_Cal_New();
        }
        //初始化加密锁
        else if (v == m_ReSet) {
            f_ReSet();
        }
    }

    @Override
    public void run() {

    }

    /**
     * 检查手机上是否安装了指定的软件
     *
     * @param context 当前app上下文
     * @return boolean 是否已安装
     */
    private boolean isInstallPackage(Context context) {

        //获取packageManager
        final PackageManager packageManager = context.getPackageManager();

        //获取所有已安装程序的包信息
        List<PackageInfo> packageInfos = packageManager.getInstalledPackages(0);

        //用于存储所有已安装程序的包名
        List<String> packageNames = new ArrayList<>();

        //从pinfo中将包名字逐一取出，压入pName list中
        for (PackageInfo packageInfo : packageInfos) {
            String packName = packageInfo.packageName;
            packageNames.add(packName);
        }

        //判断packageNames中是否有目标程序的包名，有TRUE，没有FALSE
        return packageNames.contains("com.xmh.aidlclient");
    }


    /**
     * 静默安装，1-安装成功，或没有升级文件，2-升级安装出现异常，-1-程序异常
     */
    public void installBySlient() {
        try {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    Uri uri;
                    try {
                        UsbFile apkFile = null;
                        UsbFile[] usbFiles = cFolder.listFiles();
                        for (UsbFile usbFile : usbFiles) {
                            if (usbFile.getName().contains("aidlClient")) {
                                apkFile = usbFile;
                                break;
                            }
                        }

                        if (null == apkFile) {
                            RxToast.showToast("Ukey中未找到指定apk安装文件");
                            return;
                        }

                        UsbFileInputStream fileInputStream = new UsbFileInputStream(apkFile);
                        BufferedInputStream inBuff = new BufferedInputStream(fileInputStream);
                        File file = asFile(inBuff);
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        //7.0 Android N
                        if (android.os.Build.VERSION.SDK_INT >= 24) {
                            uri = FileProvider.getUriForFile(USBKEY_A6.this, "com.wjw.usbkey.fileProvider", file);
                            intent.setAction(Intent.ACTION_INSTALL_PACKAGE);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);//7.0以后，系统要求授予临时uri读取权限，安装完毕以后，系统会自动收回权限，该过程没有用户交互
                        } else {
                            //7.0以下
                            uri = Uri.fromFile(file);
                            intent.setAction(Intent.ACTION_VIEW);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        }
                        intent.setDataAndType(uri, "application/vnd.android.package-archive");
                        startActivity(intent);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        } catch (Exception e) {
            RxToast.showToast("应用安装失败");
            e.printStackTrace();
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