
#include <jni.h>
#include <string>
#include <opencv2/opencv.hpp>
#include "utils.h"
#include "common.h"

#define DEFAULT_CARD_WIDTH 640
#define DEFAULT_CARD_HEIGHT 400
#define FIX_IDCARD_SIZE Size(DEFAULT_CARD_WIDTH, DEFAULT_CARD_HEIGHT)

using namespace std;
using namespace cv;

extern "C" JNIEXPORT jstring JNICALL
Java_com_yuong_idrecognize_MainActivity_stringFromJNI(
        JNIEnv *env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}
extern "C"
JNIEXPORT jobject JNICALL
Java_com_yuong_idrecognize_Jni_findIdNumber(JNIEnv *env, jobject instance, jobject bitmap,
                                                     jobject config) {
    //原图Mat
    Mat src_img;
    Mat dst;
    //C 没有 Bitmap， 要将 Bitmap 转成 Mat数组
    //1、先将我们的Bitmap 转成 Mat
    bitmap2Mat(env, bitmap, &src_img);
    //2、归一化
    resize(src_img, src_img, FIX_IDCARD_SIZE);

    //3、灰度化
    cvtColor(src_img, dst, COLOR_RGB2GRAY);

    //4、二值化
    threshold(dst, dst, 100, 255, THRESH_BINARY);

    //5、膨胀
    Mat erodeElement = getStructuringElement(MORPH_RECT, Size(20, 10));
    erode(dst, dst, erodeElement);

    //6、轮廓检测
    vector<vector<Point>> contours;
    vector<Rect> rects;
    findContours(dst, contours, RETR_TREE, CHAIN_APPROX_SIMPLE, Point(0, 0));
    for (int i = 0; i < contours.size(); ++i) {
        Rect rect = boundingRect(contours.at(i));
        //绘制
//        rectangle(dst, finalRect, Scalar(0, 0, 255));
        //7、逻辑处理， 找到号码所在区域
        //身份证号码有固定宽高比 > 1:8 && <1:16
        if (rect.width > rect.height * 8 && rect.width < rect.height * 16) {
            rects.push_back(rect);
        }
    }
    //8、继续查找坐标最低的矩形区域
    int lowPoint = 0;
    Rect finalRect;
    for (int i = 0; i < rects.size(); ++i) {
        Rect rect = rects.at(i);
        Point point = rect.tl();
        if (point.y > lowPoint) {
            lowPoint = point.y;
            finalRect = rect;
        }
    }
    //10、图像切割
    if (finalRect.x > 0 && finalRect.y > 0){
        dst = src_img(finalRect);
    } else{
        return NULL;
    }

    //绘制
//    rectangle(dst, finalRect, Scalar(0, 0, 255));
    //11、将Mat 转成batmap
    jobject finalBitmap = createBitmap(env, dst, config);
    if (finalBitmap == NULL){
        return NULL;
    } else{
        return finalBitmap;
    }
}

