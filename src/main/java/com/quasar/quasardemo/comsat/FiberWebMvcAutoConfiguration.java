package com.quasar.quasardemo.comsat;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

import javax.servlet.Servlet;

/**
 * @Author huaili
 * @Date 2019/3/4 17:45
 * @Description TODO
 **/
@ConditionalOnWebApplication
@ConditionalOnClass({Servlet.class, DispatcherServlet.class, WebMvcConfigurerAdapter.class})
@Import({FiberWebMvcConfigurationSupport.class})
@Order(-2147483628)
@AutoConfigureAfter({DispatcherServletAutoConfiguration.class})
public class FiberWebMvcAutoConfiguration extends WebMvcAutoConfiguration {
    public FiberWebMvcAutoConfiguration() {
        System.out.println("------loading FiberWebMvcAutoConfiguration");
    }
}
