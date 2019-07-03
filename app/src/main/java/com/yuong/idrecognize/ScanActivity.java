package com.yuong.idrecognize;

import android.app.ProgressDialog;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class ScanActivity extends AppCompatActivity implements SurfaceHolder.Callback {
    private SurfaceView mSvCamera;
    private PreviewBorderView mBorderView;

    private int mCameraPosition = 0; // 0表示后置，1表示前置
    private SurfaceHolder mSvHolder;
    private Camera mCamera;
    private Camera.Parameters mParameters;
    private int mPreviewWidth, mPreviewHeight;
    private float mPreviewScale = mPreviewHeight * 1f / mPreviewWidth;

    private TessBaseAPI tessBaseAPI;
    private String language = "num";
    private AsyncTask<Void, Void, Boolean> asyncTask;
    private ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);
        tessBaseAPI = new TessBaseAPI();
        initTest();
        mSvCamera = findViewById(R.id.svCamera);
        mBorderView = findViewById(R.id.borderView);
        CameraUtil.init(this);
        // 获得句柄
        mSvHolder = mSvCamera.getHolder(); // 获得句柄
        mSvHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        // 添加回调
        mSvHolder.addCallback(this);

    }

    private void initTest() {
        asyncTask = new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... voids) {
                InputStream is = null;
                FileOutputStream fos = null;
                try{
                    is = getAssets().open(language + ".traineddata");
                    File file = new File("/sdcard/tess/tessdata/" + language + ".traineddata");
                    if (!file.exists()){
                        file.getParentFile().mkdir();
                        fos = new FileOutputStream(file);
                        byte[] buffer = new byte[2048];
                        int len;
                        while ((len = is.read(buffer)) != -1){
                            fos.write(buffer, 0, len);
                        }
                        fos.close();
                    }
                    is.close();
                    tessBaseAPI.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789");
                    tessBaseAPI.setVariable(TessBaseAPI.VAR_CHAR_BLACKLIST, "!@#$%^&*()_+=-[]}{;:'\"\\|~`,./<>?");
                    tessBaseAPI.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO_OSD);
                    return tessBaseAPI.init("/sdcard/tess", language);
                }catch (IOException e){
                    e.printStackTrace();
                } finally {
                    try{
                        if (null != is){
                            is.close();
                        }
                        if (null != fos) {
                            fos.close();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                return false;
            }

            @Override
            protected void onPostExecute(Boolean aBoolean) {
                dismissProgress();
                if (aBoolean) {
                    Toast.makeText(ScanActivity.this, "初始化OCR成功！", Toast.LENGTH_SHORT).show();
                } else {
                    finish();
                }
            }

            @Override
            protected void onPreExecute() {
                showProgress();

            }
        };

        asyncTask.execute();

    }

    private void showProgress() {
        if (progressDialog != null) {
            progressDialog.show();
        } else {
            progressDialog = new ProgressDialog(this);
            progressDialog.setMessage("正在初始化，请稍等...");
            progressDialog.setIndeterminate(true);
            progressDialog.setCancelable(false);
            progressDialog.show();
        }
    }

    private void dismissProgress() {
        if (progressDialog != null) {
            progressDialog.dismiss();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mCamera == null) {
            mCamera = getCamera(mCameraPosition);
            mCamera.setPreviewCallback(previewCallback);
            startPreview(mCamera, mSvHolder);
        }
    }

    /**
     * 预览相机
     */
    private void startPreview(Camera camera, SurfaceHolder holder) {
        try {
            setupCamera(camera);
            camera.setPreviewDisplay(holder);
            CameraUtil.getInstance().setCameraDisplayOrientation(this, mCameraPosition, camera);
            camera.startPreview();
        } catch (IOException e) {
            Log.d(this.getClass().getSimpleName(), e.toString());
        }
    }

    private void setupCamera(Camera camera) {
        Camera.Parameters parameters = camera.getParameters();
        parameters.setJpegQuality(100);
        List<String> focusModes = parameters.getSupportedFocusModes();
        if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
            // Autofocus mMode is supported 自动对焦
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        }

        int rootViewW = CameraUtil.screenWidth;
        int rootViewH = CameraUtil.screenHeight;
        if (this.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            // 横屏
            rootViewW = CameraUtil.screenHeight;
            rootViewH = CameraUtil.screenWidth;
        }

        int picHeight = rootViewH - ScreenUtil.getStatusBarHeight(this);
        Camera.Size previewSize = CameraUtil.findBestPreviewResolution(camera);
        parameters.setPreviewSize(previewSize.width, previewSize.height);

        Camera.Size pictrueSize = CameraUtil.getInstance().getPropPictureSize(parameters.getSupportedPictureSizes(), 1000);
        parameters.setPictureSize(pictrueSize.width, pictrueSize.height);

        camera.setParameters(parameters);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(rootViewW, picHeight);
        mBorderView.setLayoutParams(params);
        mSvCamera.setLayoutParams(params);

    }


    /**
     * 获取Camera实例
     */
    private Camera getCamera(int id) {
        Camera camera = null;
        try {
            camera = Camera.open(id);
            mParameters = camera.getParameters();
            // 如果摄像头不支持这些参数都会出错的，所以设置的时候一定要判断是否支持
            List<String> supportedFlashModes = mParameters.getSupportedFlashModes();
            if (supportedFlashModes != null && supportedFlashModes.contains(Camera.Parameters.FLASH_MODE_OFF)) {
                mParameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF); // 设置闪光模式（关闭）
            }
            List<String> supportedFocusModes = mParameters.getSupportedFocusModes();
            if (supportedFocusModes != null && supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                mParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO); // 设置聚焦模式（自动）
            }
            mParameters.setPreviewFormat(ImageFormat.NV21); // 设置预览图片格式
            mParameters.setPictureFormat(ImageFormat.JPEG); // 设置拍照图片格式
            mParameters.setExposureCompensation(0); // 设置曝光强度
            Camera.Size previewSize = getSuitableSize(mParameters.getSupportedPreviewSizes());
            mPreviewWidth = previewSize.width;
            mPreviewHeight = previewSize.height;
            mParameters.setPreviewSize(mPreviewWidth, mPreviewHeight); // 设置预览图片大小
            Camera.Size pictureSize = getSuitableSize(mParameters.getSupportedPictureSizes());
            mParameters.setPictureSize(pictureSize.width, pictureSize.height);
            camera.setParameters(mParameters); // 将设置好的parameters添加到相机里

        } catch (Exception e) {
            Log.d(this.getClass().getSimpleName(), e.toString());
        }
        return camera;
    }

    private Camera.Size getSuitableSize(List<Camera.Size> sizes) {
        int minDelta = Integer.MAX_VALUE; // 最小的差值，初始值应该设置大点保证之后的计算中会被重置
        int index = 0; // 最小的差值对应的索引坐标
        for (int i = 0; i < sizes.size(); i++) {
            Camera.Size previewSize = sizes.get(i);
            // 找到一个与设置的分辨率差值最小的相机支持的分辨率大小
            if (previewSize.width * mPreviewScale == previewSize.height) {
                int delta = Math.abs(mPreviewWidth - previewSize.width);
                if (delta == 0) {
                    return previewSize;
                }
                if (minDelta > delta) {
                    minDelta = delta;
                    index = i;
                }
            }
        }
        return sizes.get(index); // 默认返回与设置的分辨率最接近的预览尺寸
    }


    @Override
    public void onPause() {
        super.onPause();
        /**
         * 记得释放camera，方便其他应用调用
         */
        releaseCamera();
    }


    /**
     * 释放相机资源
     */
    private void releaseCamera() {
        if (mCamera != null) {
            mCamera.setPreviewCallback(null);
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
    }

    //实现自动对焦
    private void autoFocus() {
        new Thread() {
            @Override
            public void run() {
                try {
                    sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (mCamera == null) {
                    return;
                }
                mCamera.autoFocus(new Camera.AutoFocusCallback() {
                    @Override
                    public void onAutoFocus(boolean success, Camera camera) {
                        if (success) {
                            setupCamera(camera);//实现相机的参数初始化
                            camera.cancelAutoFocus();//只有加上了这一句，才会自动对焦。
                        }
                    }
                });
            }
        }.start();
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        releaseCamera();
        mCamera = getCamera(mCameraPosition);
        mCamera.setPreviewCallback(previewCallback);
        startPreview(mCamera, surfaceHolder);
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
        mCamera.stopPreview();
        startPreview(mCamera, surfaceHolder);
        autoFocus();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        releaseCamera();
    }

    Camera.PreviewCallback previewCallback = new Camera.PreviewCallback() {
        @Override
        public void onPreviewFrame(byte[] bytes, Camera camera) {
            Bitmap bitmap = Bytes2Bimap(bytes);
            if (bitmap != null) {
                Jni jni = new Jni();
                Bitmap idNumber = jni.findIdNumber(bitmap, Bitmap.Config.ARGB_8888);
                bitmap.recycle();
                if (idNumber != null) {
                    //OCR文字识别
                    //13
                    tessBaseAPI.setImage(idNumber);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(ScanActivity.this, "身份证号码是：" + tessBaseAPI.getUTF8Text(), Toast.LENGTH_SHORT).show();
                        }
                    });

                } else {
                    return;
                }

            }
        }
    };


    //byte转Bitmap
    public Bitmap Bytes2Bimap(byte[] b) {
        int left;
        int top;
        int right;
        int bottom;
        int reactHeight;
        int reactWidth;
        float rate = 2 / 3f;

        reactWidth = (int) (this.mPreviewWidth * rate);
        reactHeight = (int) (reactWidth / 1.6);
        left = this.mPreviewWidth / 2 - reactWidth / 2;
        top = this.mPreviewHeight / 2 - reactHeight / 2;
        right = left + reactWidth;
        bottom = top + reactHeight;
        ByteArrayOutputStream bytesStream = new ByteArrayOutputStream();
        YuvImage yuvImage = new YuvImage(b, ImageFormat.NV21, mPreviewWidth, mPreviewHeight, null);
        yuvImage.compressToJpeg(new Rect(left, top, right, bottom), 100, bytesStream);
        byte[] jpegBytes = bytesStream.toByteArray();
        Bitmap bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.length);
        if (bitmap != null) {
            bitmap = createRotateBitmap(bitmap);
        }
        bitmap = cropBitmap(bitmap, true);
        return bitmap;
    }

    /**
     * bitmap旋转90度
     *
     * @param bitmap
     * @return
     */
    public static Bitmap createRotateBitmap(Bitmap bitmap) {
        if (bitmap != null) {
            Matrix m = new Matrix();
            try {
                m.postRotate(90);
                Bitmap bmp2 = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, false);
                bitmap.recycle();
                bitmap = bmp2;
            } catch (Exception ex) {
                System.out.print("创建图片失败！" + ex);
            }
        }

        return bitmap;
    }

    /**
     * 裁剪一定高度保留下面
     *
     * @param srcBitmap
     * @param recycleSrc 是否回收原图
     * @return
     */
    private Bitmap cropBitmap(Bitmap srcBitmap, boolean recycleSrc) {

        Log.d("danxx", "cropBitmapBottom before h : " + srcBitmap.getHeight());

        /**裁剪保留下部分的第一个像素的Y坐标*/
        int needY = srcBitmap.getHeight() / 4;

        /**裁剪关键步骤*/
        Bitmap cropBitmap = Bitmap.createBitmap(srcBitmap, 0, needY, srcBitmap.getWidth(), srcBitmap.getHeight() / 2);

        Log.d("danxx", "cropBitmapBottom after h : " + cropBitmap.getHeight());

        /**回收之前的Bitmap*/
        if (recycleSrc && srcBitmap != null && !srcBitmap.equals(cropBitmap) && !srcBitmap.isRecycled()) {
            srcBitmap.recycle();
        }

        return cropBitmap;
    }
}
