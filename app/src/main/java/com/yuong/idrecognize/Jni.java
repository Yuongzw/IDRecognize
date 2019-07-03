package com.yuong.idrecognize;

import android.graphics.Bitmap;

public class Jni {
    static {
        System.loadLibrary("OpenCV");
    }
    public native Bitmap findIdNumber(Bitmap bitmap, Bitmap.Config config);
}
