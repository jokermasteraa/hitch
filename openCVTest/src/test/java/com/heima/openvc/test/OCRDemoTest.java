package com.heima.openvc.test;


import com.heima.opencv.utils.images.ImgUtils;
import com.heima.opencv.utils.images.PaintUtils;
import org.junit.Test;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.highgui.HighGui;

import java.util.List;

/**
 * OCR 验证码识别demo
 */
public class OCRDemoTest {

    private static final String imagePath = "E:\\tmp\\xxx.png";

    static {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }

    /**
     * 显示原始图像
     */
    @Test
    public void orginImageTest() {
        Mat src = ImgUtils.matFactory(imagePath);
        HighGui.imshow("灰度化测试", src);
        HighGui.waitKey(0);
    }

    /**
     * openCV灰度化测试
     */
    @Test
    public void grayNativeTest() {
        Mat src = ImgUtils.matFactory(imagePath);
        Mat dst = ImgUtils.gray(src);
        HighGui.imshow("原始图像", src);
        HighGui.imshow("灰度化测试", dst);
        HighGui.waitKey(0);
    }


    /**
     * openCV图像二值化
     */
    @Test
    public void binaryzationTest() {
        Mat src = ImgUtils.matFactory(imagePath);
        //首先进行灰度化处理
        Mat gray = ImgUtils.gray(src);
        //true 代表白底黑子
        Mat dst = ImgUtils.binaryzation(gray, false);
        HighGui.imshow("原始图像", src);
        HighGui.imshow("二值化后的图像", dst);
        HighGui.waitKey(0);
    }


    /**
     * 8邻域降噪
     */
    @Test
    public void navieRemoveNoiseTest() {
        Mat src = ImgUtils.matFactory(imagePath);
        //首先进行灰度化处理
        Mat gray = ImgUtils.gray(src);
        //true 代表白底黑子
        Mat binaryzation = ImgUtils.binaryzation(gray, true);
        //九宫格降噪
        Mat dst = ImgUtils.navieRemoveNoise(binaryzation, 1);
        HighGui.imshow("原始图像", src);
        HighGui.imshow("九宫格降噪后的图像", dst);
        HighGui.waitKey(0);
    }


    /**
     * 连通降噪
     */
    @Test
    public void connectedRemoveNoiseTest() {
        Mat src = ImgUtils.matFactory(imagePath);
        //首先进行灰度化处理
        Mat gray = ImgUtils.gray(src);
        //true 代表白底黑子
        Mat binaryzation = ImgUtils.binaryzation(gray, true);
        //九宫格降噪
        Mat navieRemoveNoise = ImgUtils.navieRemoveNoise(binaryzation, 1);
        //连通降噪
        Mat dst = ImgUtils.connectedRemoveNoise(navieRemoveNoise, 10);
        HighGui.imshow("原始图像", src);
        HighGui.imshow("连通降噪后的图像", dst);
        HighGui.waitKey(0);
    }


    /**
     * 图像腐蚀/膨胀处理
     */
    @Test
    public void erodeImgTest() {
        Mat src = ImgUtils.matFactory(imagePath);
        //首先进行灰度化处理
        Mat gray = ImgUtils.gray(src);
        //true 代表白底黑子
        Mat binaryzation = ImgUtils.binaryzation(gray, true);
        //九宫格降噪
        Mat navieRemoveNoise = ImgUtils.navieRemoveNoise(binaryzation, 1);
        //连通降噪
        Mat dst = ImgUtils.connectedRemoveNoise(navieRemoveNoise, 10);

        //图像腐蚀/膨胀处理
        dst = ImgUtils.erodeDilateImg(dst, 2, 1);
        //高斯迷糊
        dst = ImgUtils.gaussianBlurTest(dst, 5, 5);
        HighGui.imshow("原始图像", src);
        HighGui.imshow("图像腐蚀后的图像", dst);
        HighGui.waitKey(0);
    }

    /**
     * 高斯模糊
     */
    @Test
    public void gaussianBlurTest() {
        Mat src = ImgUtils.matFactory(imagePath);
        //首先进行灰度化处理
        Mat gray = ImgUtils.gray(src);
        //true 代表白底黑子
        Mat binaryzation = ImgUtils.binaryzation(gray, true);
        //九宫格降噪
        Mat navieRemoveNoise = ImgUtils.navieRemoveNoise(binaryzation, 1);
        //连同降噪
        Mat dst = ImgUtils.connectedRemoveNoise(navieRemoveNoise, 10);
        //图像腐蚀/膨胀处理
        dst = ImgUtils.erodeDilateImg(dst, 2, 1);
        //高斯迷糊
        dst = ImgUtils.gaussianBlurTest(dst, 5, 5);
        HighGui.imshow("原始图像", src);
        HighGui.imshow("高斯模糊后的图像", dst);
        HighGui.waitKey(0);
    }

    /**
     * 查找轮廓
     */
    @Test
    public void paintConest() {
        Mat src = ImgUtils.matFactory(imagePath);
        //首先进行灰度化处理
        Mat gray = ImgUtils.gray(src);
        //true 代表白底黑子
        Mat binaryzation = ImgUtils.binaryzation(gray, true);
        //九宫格降噪
        Mat navieRemoveNoise = ImgUtils.navieRemoveNoise(binaryzation, 1);
        //连同降噪
        Mat dst = ImgUtils.connectedRemoveNoise(navieRemoveNoise, 10);

        //图像腐蚀/膨胀处理
        dst = ImgUtils.erodeDilateImg(dst, 2, 1);
        //高斯迷糊
        dst = ImgUtils.gaussianBlurTest(dst, 5, 5);
        //查找轮廓
        dst = PaintUtils.paintCon(dst);
        HighGui.imshow("原始图片", src);
        HighGui.imshow("边框矩形", dst);

        HighGui.waitKey(0);
    }


    /**
     * 矩形切割
     */
    @Test
    public void cutRectangle() {
        Mat src = ImgUtils.matFactory(imagePath);
        //首先进行灰度化处理
        Mat gray = ImgUtils.gray(src);
        //true 代表白底黑子
        Mat binaryzation = ImgUtils.binaryzation(gray, true);
        //九宫格降噪
        Mat navieRemoveNoise = ImgUtils.navieRemoveNoise(binaryzation, 1);
        //连同降噪
        Mat dst = ImgUtils.connectedRemoveNoise(navieRemoveNoise, 10);

        //图像腐蚀/膨胀处理
        dst = ImgUtils.erodeDilateImg(dst, 2, 1);
        //高斯模糊
        dst = ImgUtils.gaussianBlurTest(dst, 5, 5);
        //矩形切割
        List<Mat> dstList = PaintUtils.cutpaintCon(dst);
        HighGui.imshow("水平分隔字符", dst);
        for (int i = 0; i < dstList.size(); i++) {
            double area = dstList.get(i).width() * dstList.get(i).height();
            HighGui.imshow("面积" + area, dstList.get(i));
        }
        HighGui.waitKey(0);
    }

    //归一化
    @Test
    public void normalization() {
        Mat src = ImgUtils.matFactory(imagePath);
        //首先进行灰度化处理
        Mat gray = ImgUtils.gray(src);
        //true 代表白底黑子
        Mat binaryzation = ImgUtils.binaryzation(gray, true);
        //九宫格降噪
        Mat navieRemoveNoise = ImgUtils.navieRemoveNoise(binaryzation, 1);
        //连同降噪
        Mat dst = ImgUtils.connectedRemoveNoise(navieRemoveNoise, 10);

        //图像腐蚀/膨胀处理
        dst = ImgUtils.erodeDilateImg(dst, 2, 1);
        //高斯模糊
        dst = ImgUtils.gaussianBlurTest(dst, 5, 5);
        //矩形切割
        List<Mat> dstList = PaintUtils.cutpaintCon(dst);
        HighGui.imshow("水平分隔字符", dst);
        for (int i = 0; i < dstList.size(); i++) {
            Mat tmp = ImgUtils.resize(dstList.get(i));
            double area = dstList.get(i).width() * dstList.get(i).height();
            float[] tzm = PaintUtils.getHog(tmp);
            System.out.println(tzm);
            HighGui.imshow("面积" + area, tmp);
        }
        HighGui.waitKey(0);
    }
}
