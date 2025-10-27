package com.heima.openvc.test;

import com.heima.opencv.utils.images.ImgUtils;
import org.junit.Test;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.highgui.HighGui;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

public class OpenCVTest {
    private static final String orginImagePath = "E:\\tmp\\alita.jpg";

    static {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }

    /**
     * 亮度提升
     */
    @Test
    public void brightnessTest() {
        Mat src = Imgcodecs.imread(orginImagePath);
        Mat dst = ImgUtils.brightness(src);
        Imgproc.resize(src, src, new Size(dst.cols() / 2, dst.rows() / 2));
        Imgproc.resize(dst, dst, new Size(dst.cols() / 2, dst.rows() / 2));
        HighGui.imshow("原图", src);
        HighGui.imshow("亮度提升后的图", dst);
        HighGui.waitKey(0);
    }

    /**
     * 亮度降低
     */
    @Test
    public void darknessTest() {
        Mat src = Imgcodecs.imread(orginImagePath);
        Mat dst = ImgUtils.darkness(src);
        Imgproc.resize(src, src, new Size(dst.cols() / 2, dst.rows() / 2));
        Imgproc.resize(dst, dst, new Size(dst.cols() / 2, dst.rows() / 2));
        HighGui.imshow("原图", src);
        HighGui.imshow("亮度降低后的图", dst);
        HighGui.waitKey(0);
    }

    /**
     * 图像锐化
     */
    @Test
    public void sharpenTest() {
        Mat src = Imgcodecs.imread(orginImagePath);
        Mat dst = ImgUtils.sharpen(src);
        Imgproc.resize(src, src, new Size(dst.cols() / 2, dst.rows() / 2));
        Imgproc.resize(dst, dst, new Size(dst.cols() / 2, dst.rows() / 2));
        HighGui.imshow("原图", src);
        HighGui.imshow("锐化后的图", dst);
        HighGui.waitKey(0);
    }

    /**
     * 图像灰度化
     */
    @Test
    public void grayTest() {
        Mat src = Imgcodecs.imread(orginImagePath);
        Mat dst = ImgUtils.gray(src);
        Imgproc.resize(src, src, new Size(dst.cols() / 2, dst.rows() / 2));
        Imgproc.resize(dst, dst, new Size(dst.cols() / 2, dst.rows() / 2));
        HighGui.imshow("原图", src);
        HighGui.imshow("灰度化后的图", dst);
        HighGui.waitKey(0);
    }

    /**
     * 梯度处理
     */
    @Test
    public void gradientTest() {
        Mat src = Imgcodecs.imread(orginImagePath);
        Mat dst = ImgUtils.gradient(src);
        Imgproc.resize(src, src, new Size(dst.cols() / 2, dst.rows() / 2));
        Imgproc.resize(dst, dst, new Size(dst.cols() / 2, dst.rows() / 2));
        HighGui.imshow("原图", src);
        HighGui.imshow("梯度后的图", dst);
        HighGui.waitKey(0);
    }


    /**
     * 图像二值化
     */
    @Test
    public void binaryzationTest() {
        Mat src = Imgcodecs.imread(orginImagePath);
        Mat gray = ImgUtils.gray(src);
        Mat dst = ImgUtils.binaryzation(gray);
        Imgproc.resize(src, src, new Size(dst.cols() / 2, dst.rows() / 2));
        Imgproc.resize(dst, dst, new Size(dst.cols() / 2, dst.rows() / 2));
        HighGui.imshow("原图", src);
        HighGui.imshow("二值化后的图", dst);
        HighGui.waitKey(0);
    }

    /**
     * 高斯模糊
     */
    @Test
    public void gaussianBlurTest() {
        Mat src = Imgcodecs.imread(orginImagePath);
        Mat dst = ImgUtils.gaussianBlurTest(src);
        Imgproc.resize(src, src, new Size(dst.cols() / 2, dst.rows() / 2));
        Imgproc.resize(dst, dst, new Size(dst.cols() / 2, dst.rows() / 2));
        HighGui.imshow("原图", src);
        HighGui.imshow("高斯模糊后的图", dst);
        HighGui.waitKey(0);
    }

    /**
     * 反色处理
     */
    @Test
    public void inverseTest() {
        Mat src = Imgcodecs.imread(orginImagePath);
        Mat dst = ImgUtils.inverse(src);
        Imgproc.resize(src, src, new Size(dst.cols() / 2, dst.rows() / 2));
        Imgproc.resize(dst, dst, new Size(dst.cols() / 2, dst.rows() / 2));
        HighGui.imshow("原图", src);
        HighGui.imshow("反色后的图", dst);
        HighGui.waitKey(0);
    }

}
