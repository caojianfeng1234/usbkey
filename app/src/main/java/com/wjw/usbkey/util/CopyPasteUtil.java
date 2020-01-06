package com.wjw.usbkey.util;

import android.util.Log;

import com.github.mjdev.libaums.fs.UsbFile;
import com.github.mjdev.libaums.fs.UsbFileInputStream;
import com.github.mjdev.libaums.fs.UsbFileStreamFactory;
import com.vondear.rxtool.RxLogTool;
import com.vondear.rxtool.view.RxToast;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Created by wjw on 2019/11/20 0020
 */
public class CopyPasteUtil {

    private static CopyPasteUtil mCopyPasteUtil;

    /**
     * 文件夹总体积
     */
    private long dirSize = 0;
    /**
     * 已复制的部分，体积
     */
    private long hasReadSize = 0;
    /**
     * 复制文件线程
     */
    private Thread copyFileThread;
    /**
     * r
     */
    private Runnable run = null;
    /**
     * 文件输入流
     */
    private UsbFileInputStream fileInputStream = null;
    /**
     * 文件输出流
     */
    private FileOutputStream fileOutputStream = null;

    /**
     * 复制文件夹线程
     */
    private Thread copyDirThread;
    /**
     * 缓存输入流
     */
    private BufferedInputStream inbuff = null;
    /**
     * 缓存输出流
     */
    private BufferedOutputStream outbuff = null;

    /**
     * 进度
     */
    public int mProgress;
    /**
     * 大小进度 利如  2772 MB/2801 MB
     */
    public String fileVolumeText;

    /**
     * 是否关闭
     */
    public boolean isClose = false;

    private CopyPasteUtil() {
    }

//    /**
//     * 复制单个文件
//     */
//    public boolean copyFile(File oldFile, File newFile){
//        return this.copyFile(oldFile.getAbsolutePath(),newFile.getAbsolutePath());
//    }
//    /**
//     * 复制单个文件
//     */
//    public boolean copyFile(final UsbFile oldFile, final String newPathName) {
//        isClose = false;
//        run = new Runnable() {
//            @Override
//            public void run() {
//                try {
//                    File targetFile = new File(newPathName);
//                    fileInputStream = new UsbFileInputStream(oldFile);
//                    fileOutputStream = new FileOutputStream(targetFile);
//
//
//                    //创建一个4M的缓冲区
//                    ByteBuffer buffer = ByteBuffer.allocate(4096);
//                    //已复制大小
//                    long transferSize = 0;
//                    //单个文件大小
//                    long size = oldFile.getLength();
//                    //文件大小单位M
//                    int fileVolume = (int) (size / 1024 / 1024);
//                    int tempP = 0;
//                    int progress = 0;
//                    while (fileChannelInput.read(buffer) != -1) {
//                        buffer.flip();
//                        transferSize += fileChannelOutput.write(buffer);
//                        //单个文件复制进度百分比
//                        progress = (int) (transferSize * 100 / size);
//                        //加一个保险不会让文件进度出现回退现象
//                        if (progress > tempP) {
//                            tempP = progress;
//                            mProgress = progress;
//                            fileVolumeText = fileVolume * progress / 100 + " MB/" + fileVolume + " MB";
//                            System.out.println("mprogress ----->" + mProgress);
//                            System.out.println("fileVolume ----->" + fileVolume);
//                            System.out.println("fileVolumeText ----->" + fileVolumeText);
//                        }
//                        buffer.clear();
//                    }
//                    fileOutputStream.flush();
//                    fileOutputStream.close();
//                    fileInputStream.close();
//                    fileChannelOutput.close();
//                    fileChannelInput.close();
//                    System.out.println("复制完成");
//                    mProgress = 100;
//                } catch (Exception e) {
//                    Log.e("CopyPasteUtil", "CopyPasteUtil copyFile error:" + e.getMessage());
//                }
//            }
//        };
//        copyFileThread = new Thread(run);
//        copyFileThread.start();
//        return true;
//    }

    /**
     * 复制文件夹
     */
    public void copyDir(UsbFile sourceFile, String targetDir) {
        initValueAndGetDirSize(sourceFile);
        isClose = false;
        copyDirectory(sourceFile,targetDir);

    }

    private void copyDirectory(final UsbFile sourceFile, final String targetDir) {
        run = new Runnable() {
            @Override
            public void run() {
                new File(targetDir).mkdirs();
                // 获取源文件夹当下的文件或目录
                try {
                    UsbFile[] usbFiles = sourceFile.listFiles();
                    for (int i = 0; i < usbFiles.length; i++) {
                        if (!usbFiles[i].isDirectory()) {
                            UsbFile sourceFile = usbFiles[i];
                            // 目标文件
                            File targetFile = new File(new File(targetDir).getAbsolutePath() + File.separator + usbFiles[i].getName());
                            copyFileForDir(sourceFile, targetFile);
                        }
                        if (usbFiles[i].isDirectory()) {
                            String dir2 = targetDir + File.separator + usbFiles[i].getName();
                            copyDirectory(usbFiles[i], dir2);
                        }
                    }
                } catch (IOException e) {
                    RxLogTool.i(e);
                }


            }
        };
        copyDirThread = new Thread(run);
        copyDirThread.start();
    }

    /**
     * 复制单个文件，用于上面的复制文件夹方法
     *
     * @param sourcefile 源文件路径
     * @param targetFile 目标路径
     */
    private synchronized void copyFileForDir(final UsbFile sourcefile, final File targetFile) {
        try {
            //文件缓冲输入流
            fileInputStream = new UsbFileInputStream(sourcefile);
            inbuff = new BufferedInputStream(fileInputStream);
            // 新建文件输出流并对它进行缓冲
            fileOutputStream = new FileOutputStream(targetFile);
            outbuff = new BufferedOutputStream(fileOutputStream);

            int fileVolume = (int) (dirSize / (1024 * 1024));
            byte[] bytes = new byte[4096];
            long transferSize = 0;
            int tempP = 0;
            int progress = 0;
            int a = 0;
            while ( (a = inbuff.read(bytes)) != -1) {
                transferSize += a;
                progress = (int) (((transferSize + hasReadSize) * 100) / dirSize);
                if (progress > tempP) {
                    mProgress = progress;
                    fileVolumeText = fileVolume * progress / 100 + " MB/" + fileVolume + " MB";
                    System.out.println("mprogress ----->" + mProgress);
                    System.out.println("fileVolume ----->" + fileVolume);
                    System.out.println("fileVolumeText ----->" + fileVolumeText);
                }
            }
            hasReadSize += sourcefile.getLength();
            outbuff.flush();
            inbuff.close();
            outbuff.close();
            fileOutputStream.close();
            fileInputStream.close();
        } catch (FileNotFoundException e) {
            Log.e("CopyPasteUtil", "CopyPasteUtil copyFile error:" + e.getMessage());
        } catch (IOException e) {
            Log.e("CopyPasteUtil", "CopyPasteUtil copyFile error:" + e.getMessage());
        }
    }

    /**
     * 获取文件夹大小
     *
     * @param file
     */
    public void getDirSize(UsbFile file) {
        if (!file.isDirectory()) {
            // 如果是文件，获取文件大小累加
            dirSize += file.getLength();
        } else if (file.isDirectory()) {
            UsbFile[] f1 = new UsbFile[0];
            try {
                f1 = file.listFiles();
            } catch (IOException e) {
                RxLogTool.i(e);
            }
            for (int i = 0; i < f1.length; i++) {
                // 调用递归遍历f1数组中的每一个对象
                getDirSize(f1[i]);
            }
        }
    }

    /**
     * 初始化全局变量
     */
    private void initDirSize() {
        dirSize = 0;
        hasReadSize = 0;
    }

    /**
     * 复制文件夹前，初始化两个变量
     */
    public void initValueAndGetDirSize(UsbFile file) {
        close();
        initDirSize();
        getDirSize(file);
    }



    public void close() {
        run = null;
        if (copyDirThread != null)copyDirThread.interrupt();
        copyDirThread = null;
        try {
            if (fileInputStream != null) fileInputStream.close();
            if (fileOutputStream != null) fileOutputStream.close();
            if (inbuff != null) inbuff.close();
            if (outbuff != null) outbuff.close();
        } catch (IOException e) {
            Log.e("CopyPasteUtil", "CopyPasteUtil copyDirectory error:" + e.getMessage());
        }
    }
    private static class SingletonClassInstance {

        private static final CopyPasteUtil instance = new CopyPasteUtil();
    }


    public static CopyPasteUtil getInstance() {
        return SingletonClassInstance.instance;
    }

}
