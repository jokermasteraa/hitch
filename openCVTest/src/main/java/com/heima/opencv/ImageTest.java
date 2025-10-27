package com.heima.opencv;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.highgui.HighGui;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

public class ImageTest {
    private static final String orginImagePath = "E:\\tmp\\alita.jpg";

    static {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }

    public static void main(String args[]) {
        Mat src = Imgcodecs.imread(orginImagePath);
        //亮度提升
        //Mat dst = brightness(src);
        //亮度降低
        // Mat dst = brightness(src);
        //灰度化
        //Mat dst = gray(src);
        //二值化
        Mat dst = binaryzation(src);
        //锐化
        //Mat dst = sharpen(src);
        //高斯模糊
        //  Mat dst = blur(src);
        //梯度
        // Mat dst = gradient(src);
        //反色处理
        //Mat dst = inverse(src);

        Imgproc.resize(dst, dst, new Size(dst.cols() / 2, dst.rows() / 2));
        HighGui.imshow("图像矩(Image Moments)", dst);
        HighGui.waitKey(0);

    }



    /**
     * 灰度
     *
     * @param image
     * @return
     */
    public static Mat gray(Mat image) {
        // 灰度
        Mat gray = new Mat();
        Imgproc.cvtColor(image, gray, Imgproc.COLOR_BGR2GRAY);
        return gray;
    }

    /**
     * 二值化
     *
     * @param image
     * @return
     */
    public static Mat binaryzation(Mat image) {
        // 二值化
        Mat dst = new Mat();
        Mat src = gray(image);
        Imgproc.adaptiveThreshold(src, dst, 255, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY, 13, 5);
        return dst;
    }



    /**
     * 高斯模糊
     *
     * @param image
     * @return
     */
    public static Mat blur(Mat image) {
        // 高斯模糊
        Mat dst = new Mat();
        Imgproc.GaussianBlur(image, dst, new Size(15, 15), 0);
        return dst;
    }


}
