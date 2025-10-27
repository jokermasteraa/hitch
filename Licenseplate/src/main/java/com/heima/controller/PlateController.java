package com.heima.controller;

import com.heima.service.PlateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/plate")
public class PlateController {

    @Autowired
    private PlateService plateService;

    @PostMapping("/discern")
    public String discern(String url) throws Exception {
        return plateService.discern(url);
    }

}
