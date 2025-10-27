package com.heima.opencv.utils.images;

import com.heima.opencv.utils.MatAreaHelper;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.HOGDescriptor;

import java.util.List;

/**
 * 画图工具类
 */
public class PaintUtils {

    /**
     * 画出所有的矩形
     *
     * @param src
     * @return
     */
    public static Mat paintCon(Mat src) {
        Mat cannyMat = GeneralUtils.canny(src);
        List<MatOfPoint> contours = ContoursUtils.findContours(cannyMat);

        Mat rectMat = src.clone();
        Scalar scalar = new Scalar(0, 0, 255);
        for (int i = contours.size() - 1; i >= 0; i--) {
            MatOfPoint matOfPoint = contours.get(i);
            MatOfPoint2f matOfPoint2f = new MatOfPoint2f(matOfPoint.toArray());

            RotatedRect rect = Imgproc.minAreaRect(matOfPoint2f);

            Rect r = rect.boundingRect();

            System.out.println(r.area() + " --- " + i);

            rectMat = paintRect(rectMat, r, scalar);

        }

        return rectMat;
    }


    /**
     * 画出最大的矩形
     *
     * @param src
     * @return
     */
    public static Mat paintMaxCon(Mat src) {
        Mat cannyMat = GeneralUtils.canny(src);

        RotatedRect rect = ContoursUtils.findMaxRect(cannyMat);

        Rect r = rect.boundingRect();

        Mat rectMat = src.clone();
        Scalar scalar = new Scalar(0, 0, 255);

        rectMat = paintRect(rectMat, r, scalar);

        return rectMat;
    }


    /**
     * 画出所有的矩形
     *
     * @param src
     * @return
     */
    public static List<Mat> cutpaintCon(Mat src) {
        Mat cannyMat = GeneralUtils.canny(src);
        List<MatOfPoint> contours = ContoursUtils.findContours(cannyMat);

        MatAreaHelper matAreaHelper = new MatAreaHelper();
        for (int i = contours.size() - 1; i >= 0; i--) {
            MatOfPoint matOfPoint = contours.get(i);
            MatOfPoint2f matOfPoint2f = new MatOfPoint2f(matOfPoint.toArray());
            RotatedRect rect = Imgproc.minAreaRect(matOfPoint2f);
            Rect r = rect.boundingRect();

            Mat temp = new Mat(src, r);
            matAreaHelper.addMat(temp);
        }

        return matAreaHelper.getMatList();
    }


    /**
     * 画矩形
     *
     * @param src
     * @param r
     * @param scalar
     * @return
     */
    public static Mat paintRect(Mat src, Rect r, Scalar scalar) {
        Point pt1 = new Point(r.x, r.y);
        Point pt2 = new Point(r.x + r.width, r.y);
        Point pt3 = new Point(r.x + r.width, r.y + r.height);
        Point pt4 = new Point(r.x, r.y + r.height);

        Imgproc.line(src, pt1, pt2, scalar, 2);
        Imgproc.line(src, pt2, pt3, scalar, 2);
        Imgproc.line(src, pt3, pt4, scalar, 2);
        Imgproc.line(src, pt4, pt1, scalar, 2);

        return src;
    }

    /**
     * 画实心圆
     *
     * @param src
     * @param point  点
     * @param size   点的尺寸
     * @param scalar 颜色
     * @param path   保存路径
     */
    public static boolean paintCircle(Mat src, Point[] point, int size, Scalar scalar, String path) {
        if (src == null || point == null) {
            throw new RuntimeException("Mat 或者 Point 数组不能为NULL");
        }
        for (Point p : point) {
            Imgproc.circle(src, p, size, scalar, -1);
        }

        if (path != null && !"".equals(path)) {
            return GeneralUtils.saveImg(src, path);
        }

        return false;
    }


    /**
     * 特征码提取
     *
     * @param imageMat
     * @return
     */
    public static float[] getHog(Mat imageMat) {
        HOGDescriptor hog = new HOGDescriptor(new Size(32, 32), new Size(16, 16), new Size(8, 8), new Size(8, 8), 9);
        MatOfFloat descriptorsOfMat = new MatOfFloat();
        hog.compute(imageMat, descriptorsOfMat);
        return descriptorsOfMat.toArray();
    }

}
