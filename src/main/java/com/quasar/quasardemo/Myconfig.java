//package com.quasar.quasardemo;
//
//import co.paralleluniverse.springframework.web.servlet.config.annotation.FiberWebMvcConfigurationSupport;
//import org.springframework.boot.autoconfigure.AutoConfigureAfter;
//import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
//import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
//import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
//import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.context.annotation.Import;
//import org.springframework.core.annotation.Order;
//import org.springframework.web.servlet.DispatcherServlet;
//import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;
//
//import javax.servlet.Servlet;
//
///**
// * @Author huaili
// * @Date 2019/3/4 16:26
// * @Description TODO
// **/
//@Configuration
//@ConditionalOnWebApplication
//@ConditionalOnClass({Servlet.class, DispatcherServlet.class, WebMvcConfigurerAdapter.class})
//@Import({FiberWebMvcConfigurationSupport.class})
//@AutoConfigureAfter({DispatcherServletAutoConfiguration.class})
//public class Myconfig extends WebMvcAutoConfiguration {
//    public Myconfig() {
//    }
//}
