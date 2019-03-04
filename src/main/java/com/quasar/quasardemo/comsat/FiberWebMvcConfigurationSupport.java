package com.quasar.quasardemo.comsat;


import org.springframework.context.annotation.Bean;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.web.context.request.async.CallableProcessingInterceptor;
import org.springframework.web.context.request.async.DeferredResultProcessingInterceptor;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.DelegatingWebMvcConfiguration;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author huaili
 * @Date 2019/3/4 16:43
 * @Description TODO
 **/
public class FiberWebMvcConfigurationSupport extends DelegatingWebMvcConfiguration {
    public FiberWebMvcConfigurationSupport() {
    }

    @Bean
    public FiberRequestMappingHandlerAdapter fiberRequestMappingHandlerAdapter(AsyncTaskExecutor asyncTaskExecutor, List<CallableProcessingInterceptor> callableProcessingInterceptorList, List<DeferredResultProcessingInterceptor> deferredResultProcessingInterceptorList) {
        List<HandlerMethodArgumentResolver> argumentResolvers = new ArrayList();
        this.addArgumentResolvers(argumentResolvers);
        List<HandlerMethodReturnValueHandler> returnValueHandlers = new ArrayList();
        this.addReturnValueHandlers(returnValueHandlers);
        FiberRequestMappingHandlerAdapter adapter = new FiberRequestMappingHandlerAdapter();
        adapter.setContentNegotiationManager(this.mvcContentNegotiationManager());
        adapter.setMessageConverters(this.getMessageConverters());
        adapter.setWebBindingInitializer(this.getConfigurableWebBindingInitializer());
        adapter.setCustomArgumentResolvers(argumentResolvers);
        adapter.setCustomReturnValueHandlers(returnValueHandlers);
        AsyncSupportConfigurer configurer = new AsyncSupportConfigurer();
        this.configureAsyncSupport(configurer);
//        if (ServletConfigAnnotationProtectedProxy.getTaskExecutor(configurer) != null) {
//            adapter.setTaskExecutor(asyncTaskExecutor);
//        }
//
//        if (ServletConfigAnnotationProtectedProxy.getTimeout(configurer) != null) {
//            adapter.setAsyncRequestTimeout(6000);
//        }

        adapter.setCallableInterceptors(callableProcessingInterceptorList);
        adapter.setDeferredResultInterceptors(deferredResultProcessingInterceptorList);
        return adapter;
    }
}
