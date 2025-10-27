package com.heima.opencv.entity;

import org.opencv.core.Mat;

import java.util.Comparator;

public class MatArea implements Comparator<MatArea> {
    private Mat mat;
    //面积
    private double area;

    public MatArea(Mat mat) {
        this.mat = mat;
        this.area = mat.width() * mat.height();
    }

    public Mat getMat() {
        return mat;
    }

    public void setMat(Mat mat) {
        this.mat = mat;
    }

    public double getArea() {
        return area;
    }

    public void setArea(double area) {
        this.area = area;
    }

    @Override
    public int compare(MatArea o1, MatArea o2) {
        if (o1.getArea() > o2.getArea())
            return -1;
        else if (o1.getArea() < o2.getArea())
            return 1;
        else {
            if (o1.getArea() > o2.getArea())
                return 1;
            else if (o1.getArea() < o2.getArea())
                return -1;
            else
                return 0;
        }
    }
}
