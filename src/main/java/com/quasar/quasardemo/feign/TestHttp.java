package com.quasar.quasardemo.feign;


import co.paralleluniverse.fibers.Suspendable;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@FeignClient(value="value", url = "http://www.baidu.com")
public interface TestHttp {

    @Suspendable
    @RequestMapping(method = RequestMethod.GET, value = "/")
    String  getBaiduContent();
}
