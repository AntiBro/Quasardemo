package com.quasar.quasardemo.controller;

import co.paralleluniverse.fibers.SuspendExecution;
import com.quasar.quasardemo.service.TestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

/**
 * @Author huaili
 * @Date 2019/3/1 17:06
 * @Description TODO
 **/
@RestController
public class TestController {

    @Autowired
    TestService testService;

    @RequestMapping("/test")
    public String getContent() throws ExecutionException, InterruptedException, IOException, SuspendExecution {
        Long t0 = System.currentTimeMillis();
        String ret = testService.getContent();


        System.out.println("结果"+ret+"耗时:"+(System.currentTimeMillis()-t0));
        return ret;
    }
}
