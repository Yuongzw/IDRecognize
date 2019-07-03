package com.yuong.idrecognize;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private TessBaseAPI tessBaseAPI;
    private String language = "eng";
    private AsyncTask<Void, Void, Boolean> asyncTask;
    private ProgressDialog progressDialog;

    private Button btn_pre, btn_next, btn_recognize, btn_scan;
    private ImageView iv_card;
    private TextView tv_idCard;
    private int[] idCards = {
            R.drawable.id_card01,
            R.drawable.id_card02,
            R.drawable.id_card03,
            R.drawable.id_card04,
            R.drawable.id_card05,
            R.drawable.id_card06,
            R.drawable.id_card07,
            R.drawable.id_card08
    };
    private int index;
    private Jni jni;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        jni = new Jni();
        btn_pre = findViewById(R.id.btn_pre);
        btn_next = findViewById(R.id.btn_next);
        btn_recognize = findViewById(R.id.btn_recognize);
        btn_scan = findViewById(R.id.btn_scan);
        iv_card = findViewById(R.id.iv_card);
        tv_idCard = findViewById(R.id.tv_idCard);
        iv_card.setImageResource(idCards[index]);

        btn_pre.setOnClickListener(this);
        btn_next.setOnClickListener(this);
        btn_recognize.setOnClickListener(this);
        btn_scan.setOnClickListener(this);
        //12、初始化 tessApi
        tessBaseAPI = new TessBaseAPI();
        initTest();
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
                    Toast.makeText(MainActivity.this, "初始化OCR成功！", Toast.LENGTH_SHORT).show();
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

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();


//    public native Bitmap findIdNumber(Bitmap bitmap,  Bitmap.Config config);

    @Override
    public void onClick(View view) {

        switch (view.getId()) {
            case R.id.btn_pre:
                index--;
                if (index < 0) {
                    index = idCards.length - 1;
                }
                iv_card.setImageResource(idCards[index]);
                break;
            case R.id.btn_next:
                index++;
                if (index >= idCards.length) {
                    index = 0;
                }
                iv_card.setImageResource(idCards[index]);
                break;
            case R.id.btn_recognize://身份识别
                //身份证图片区域识别
                //从原图 bitmap得到号码的Bitmap
                Bitmap originBitmap = BitmapFactory.decodeResource(getResources(), idCards[index]);

                Bitmap numberBitmap = jni.findIdNumber(originBitmap, Bitmap.Config.ARGB_8888);

                originBitmap.recycle();
                if (numberBitmap != null) {
                    iv_card.setImageBitmap(numberBitmap);
                } else {
                    return;
                }
                //OCR文字识别
                //13
                tessBaseAPI.setImage(numberBitmap);
                tv_idCard.setText(tessBaseAPI.getUTF8Text());
//                String inspection = tessBaseAPI.getHOCRText(0);
//                tv_idCard.setText(inspection);
                break;
            case R.id.btn_scan:
                startActivity(new Intent(MainActivity.this, ScanActivity.class));
                break;
            default:
                break;
        }

    }
}
