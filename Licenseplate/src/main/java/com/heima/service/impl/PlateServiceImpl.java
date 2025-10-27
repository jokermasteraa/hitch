package com.heima.service.impl;

import com.alibaba.fastjson.JSON;
import com.heima.enumtype.PlateColor;
import com.heima.service.PlateService;
import com.heima.util.FileUtil;
import com.heima.util.PlateUtil;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;


@Service
public class PlateServiceImpl implements PlateService {


    static {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }

    @Override
    public String discern(String url) {
        String imagePath = FileUtil.downloadImage(url);
        if(StringUtils.isEmpty(imagePath)){
            return "图片下载失败";
        }
        Vector<Mat> dst = new Vector<Mat>();
        PlateUtil.getPlateMat(imagePath, dst);

        Set<String> plates = new HashSet<>();
        dst.stream().forEach(inMat -> {
            PlateColor color = PlateUtil.getPlateColor(inMat, true);
            String plate = PlateUtil.charsSegment(inMat, color);
            plates.add("<" + plate + "," + color.desc + ">");
        });

        return JSON.toJSONString(plates);
    }

}
