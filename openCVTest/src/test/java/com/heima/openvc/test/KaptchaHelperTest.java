package com.heima.openvc.test;

import com.heima.opencv.utils.CaptchaHelper;
import org.junit.Test;

import java.io.FileOutputStream;
import java.io.IOException;

public class KaptchaHelperTest {
    private static final String fileName = "E:\\tmp\\xxx1.png";

    @Test
    public void outImage() throws IOException {
        CaptchaHelper captchaHelper = new CaptchaHelper(350,100);
        captchaHelper.write(new FileOutputStream(fileName));
    }
}
