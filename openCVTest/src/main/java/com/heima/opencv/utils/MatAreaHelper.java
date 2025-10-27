package com.heima.opencv.utils;

import com.heima.opencv.entity.MatArea;
import org.opencv.core.Mat;

import java.util.*;
import java.util.stream.Collectors;

public class MatAreaHelper {

    private List<MatArea> matAreaList = new ArrayList<>();


    public void addMat(Mat mat) {
        matAreaList.add(new MatArea(mat));
    }

    public List<Mat> getMatList() {
        double avg = getAvg();
        List<Mat> matList = new ArrayList<>();
        for (MatArea p : matAreaList) {
            //如果小于平均值 忽略
            if (p.getArea() < avg) {
                continue;
                //如果面积大于平均值的二倍忽略
            } else if (p.getArea() > avg * 5) {
                continue;
            }
            matList.add(p.getMat());
        }
        return matList;
    }

    public double getAvg() {
        List<MatArea> sortMatAreaList = matAreaList.stream().sorted(Comparator.comparingDouble(MatArea::getArea)).collect(Collectors.toList());
        double total = 0;
        int num = 0;
        for (int i = 1; i < sortMatAreaList.size() - 1; i++) {
            total += sortMatAreaList.get(i).getArea();
            num++;
        }
        return total / num;
    }

}
