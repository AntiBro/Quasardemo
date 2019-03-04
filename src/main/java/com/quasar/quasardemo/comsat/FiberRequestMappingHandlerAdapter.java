package com.quasar.quasardemo.comsat;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.core.*;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.support.AllEncompassingFormHttpMessageConverter;
import org.springframework.http.converter.xml.SourceHttpMessageConverter;
import org.springframework.ui.ModelMap;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.accept.ContentNegotiationManager;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.support.*;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.async.*;
import org.springframework.web.method.ControllerAdviceBean;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.annotation.*;
import org.springframework.web.method.support.*;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.mvc.annotation.ModelAndViewResolver;
import org.springframework.web.servlet.mvc.method.AbstractHandlerMethodAdapter;
import org.springframework.web.servlet.mvc.method.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.RequestContextUtils;
import org.springframework.web.util.WebUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Author huaili
 * @Date 2019/3/4 16:48
 * @Description TODO
 **/
public class FiberRequestMappingHandlerAdapter extends AbstractHandlerMethodAdapter implements BeanFactoryAware, InitializingBean, Ordered {
    private List<HandlerMethodArgumentResolver> customArgumentResolvers;
    private HandlerMethodArgumentResolverComposite argumentResolvers;
    private HandlerMethodArgumentResolverComposite initBinderArgumentResolvers;
    private List<HandlerMethodReturnValueHandler> customReturnValueHandlers;
    private HandlerMethodReturnValueHandlerComposite returnValueHandlers;
    private List<ModelAndViewResolver> modelAndViewResolvers;
    private ContentNegotiationManager contentNegotiationManager = new ContentNegotiationManager();
    private List<HttpMessageConverter<?>> messageConverters;
    private WebBindingInitializer webBindingInitializer;
    private AsyncTaskExecutor taskExecutor = new SimpleAsyncTaskExecutor("MvcAsync");
    private Long asyncRequestTimeout;
    private CallableProcessingInterceptor[] callableInterceptors = new CallableProcessingInterceptor[0];
    private DeferredResultProcessingInterceptor[] deferredResultInterceptors = new DeferredResultProcessingInterceptor[0];
    private boolean ignoreDefaultModelOnRedirect = false;
    private int cacheSecondsForSessionAttributeHandlers = 0;
    private boolean synchronizeOnSession = false;
    private SessionAttributeStore sessionAttributeStore = new DefaultSessionAttributeStore();
    private ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();
    private ConfigurableBeanFactory beanFactory;
    private final Map<Class<?>, SessionAttributesHandler> sessionAttributesHandlerCache = new ConcurrentHashMap(64);
    private final Map<Class<?>, Set<Method>> initBinderCache = new ConcurrentHashMap(64);
    private final Map<ControllerAdviceBean, Set<Method>> initBinderAdviceCache = new LinkedHashMap();
    private final Map<Class<?>, Set<Method>> modelAttributeCache = new ConcurrentHashMap(64);
    private final Map<ControllerAdviceBean, Set<Method>> modelAttributeAdviceCache = new LinkedHashMap();
    public static final ReflectionUtils.MethodFilter INIT_BINDER_METHODS = new ReflectionUtils.MethodFilter() {

        @Override
        public boolean matches(Method method) {
            return AnnotationUtils.findAnnotation(method, InitBinder.class) != null;
        }
    };
    public static final ReflectionUtils.MethodFilter MODEL_ATTRIBUTE_METHODS = new ReflectionUtils.MethodFilter() {
        @Override
        public boolean matches(Method method) {
            return AnnotationUtils.findAnnotation(method, RequestMapping.class) == null && AnnotationUtils.findAnnotation(method, ModelAttribute.class) != null;
        }
    };

    public FiberRequestMappingHandlerAdapter() {
        StringHttpMessageConverter stringHttpMessageConverter = new StringHttpMessageConverter();
        stringHttpMessageConverter.setWriteAcceptCharset(false);
        this.messageConverters = new ArrayList();
        this.messageConverters.add(new ByteArrayHttpMessageConverter());
        this.messageConverters.add(stringHttpMessageConverter);
        this.messageConverters.add(new SourceHttpMessageConverter());
        this.messageConverters.add(new AllEncompassingFormHttpMessageConverter());
    }

    public void setCustomArgumentResolvers(List<HandlerMethodArgumentResolver> argumentResolvers) {
        this.customArgumentResolvers = argumentResolvers;
    }

    public List<HandlerMethodArgumentResolver> getCustomArgumentResolvers() {
        return this.customArgumentResolvers;
    }

    public void setArgumentResolvers(List<HandlerMethodArgumentResolver> argumentResolvers) {
        if (argumentResolvers == null) {
            this.argumentResolvers = null;
        } else {
            this.argumentResolvers = new HandlerMethodArgumentResolverComposite();
            this.argumentResolvers.addResolvers(argumentResolvers);
        }

    }

    public List<HandlerMethodArgumentResolver> getArgumentResolvers() {
        return this.argumentResolvers != null ? this.argumentResolvers.getResolvers() : null;
    }

    public void setInitBinderArgumentResolvers(List<HandlerMethodArgumentResolver> argumentResolvers) {
        if (argumentResolvers == null) {
            this.initBinderArgumentResolvers = null;
        } else {
            this.initBinderArgumentResolvers = new HandlerMethodArgumentResolverComposite();
            this.initBinderArgumentResolvers.addResolvers(argumentResolvers);
        }

    }

    public List<HandlerMethodArgumentResolver> getInitBinderArgumentResolvers() {
        return this.initBinderArgumentResolvers != null ? this.initBinderArgumentResolvers.getResolvers() : null;
    }

    public void setCustomReturnValueHandlers(List<HandlerMethodReturnValueHandler> returnValueHandlers) {
        this.customReturnValueHandlers = returnValueHandlers;
    }

    public List<HandlerMethodReturnValueHandler> getCustomReturnValueHandlers() {
        return this.customReturnValueHandlers;
    }

    public void setReturnValueHandlers(List<HandlerMethodReturnValueHandler> returnValueHandlers) {
        if (returnValueHandlers == null) {
            this.returnValueHandlers = null;
        } else {
            this.returnValueHandlers = new HandlerMethodReturnValueHandlerComposite();
            this.returnValueHandlers.addHandlers(returnValueHandlers);
        }

    }

    public List<HandlerMethodReturnValueHandler> getReturnValueHandlers() {
        return this.returnValueHandlers.getHandlers();
    }

    public void setModelAndViewResolvers(List<ModelAndViewResolver> modelAndViewResolvers) {
        this.modelAndViewResolvers = modelAndViewResolvers;
    }

    public List<ModelAndViewResolver> getModelAndViewResolvers() {
        return this.modelAndViewResolvers;
    }

    public void setMessageConverters(List<HttpMessageConverter<?>> messageConverters) {
        this.messageConverters = messageConverters;
    }

    public void setContentNegotiationManager(ContentNegotiationManager contentNegotiationManager) {
        this.contentNegotiationManager = contentNegotiationManager;
    }

    public List<HttpMessageConverter<?>> getMessageConverters() {
        return this.messageConverters;
    }

    public void setWebBindingInitializer(WebBindingInitializer webBindingInitializer) {
        this.webBindingInitializer = webBindingInitializer;
    }

    public WebBindingInitializer getWebBindingInitializer() {
        return this.webBindingInitializer;
    }

    public void setTaskExecutor(AsyncTaskExecutor taskExecutor) {
        this.taskExecutor = taskExecutor;
    }

    public void setAsyncRequestTimeout(long timeout) {
        this.asyncRequestTimeout = timeout;
    }

    public void setCallableInterceptors(List<CallableProcessingInterceptor> interceptors) {
        Assert.notNull(interceptors);
        this.callableInterceptors = (CallableProcessingInterceptor[])interceptors.toArray(new CallableProcessingInterceptor[interceptors.size()]);
    }

    public void setDeferredResultInterceptors(List<DeferredResultProcessingInterceptor> interceptors) {
        Assert.notNull(interceptors);
        this.deferredResultInterceptors = (DeferredResultProcessingInterceptor[])interceptors.toArray(new DeferredResultProcessingInterceptor[interceptors.size()]);
    }

    public void setIgnoreDefaultModelOnRedirect(boolean ignoreDefaultModelOnRedirect) {
        this.ignoreDefaultModelOnRedirect = ignoreDefaultModelOnRedirect;
    }

    public void setSessionAttributeStore(SessionAttributeStore sessionAttributeStore) {
        this.sessionAttributeStore = sessionAttributeStore;
    }

    public void setCacheSecondsForSessionAttributeHandlers(int cacheSecondsForSessionAttributeHandlers) {
        this.cacheSecondsForSessionAttributeHandlers = cacheSecondsForSessionAttributeHandlers;
    }

    public void setSynchronizeOnSession(boolean synchronizeOnSession) {
        this.synchronizeOnSession = synchronizeOnSession;
    }

    public void setParameterNameDiscoverer(ParameterNameDiscoverer parameterNameDiscoverer) {
        this.parameterNameDiscoverer = parameterNameDiscoverer;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        if (beanFactory instanceof ConfigurableBeanFactory) {
            this.beanFactory = (ConfigurableBeanFactory)beanFactory;
        }

    }

    protected ConfigurableBeanFactory getBeanFactory() {
        return this.beanFactory;
    }

    @Override
    public void afterPropertiesSet() {
        List handlers;
        if (this.argumentResolvers == null) {
            handlers = this.getDefaultArgumentResolvers();
            this.argumentResolvers = (new HandlerMethodArgumentResolverComposite()).addResolvers(handlers);
        }

        if (this.initBinderArgumentResolvers == null) {
            handlers = this.getDefaultInitBinderArgumentResolvers();
            this.initBinderArgumentResolvers = (new HandlerMethodArgumentResolverComposite()).addResolvers(handlers);
        }

        if (this.returnValueHandlers == null) {
            handlers = this.getDefaultReturnValueHandlers();
            this.returnValueHandlers = (new HandlerMethodReturnValueHandlerComposite()).addHandlers(handlers);
        }

        this.initControllerAdviceCache();
    }

    private List<HandlerMethodArgumentResolver> getDefaultArgumentResolvers() {
        List<HandlerMethodArgumentResolver> resolvers = new ArrayList();
        resolvers.add(new RequestParamMethodArgumentResolver(this.getBeanFactory(), false));
        resolvers.add(new RequestParamMapMethodArgumentResolver());
        resolvers.add(new PathVariableMethodArgumentResolver());
        resolvers.add(new PathVariableMapMethodArgumentResolver());
        resolvers.add(new MatrixVariableMethodArgumentResolver());
        resolvers.add(new MatrixVariableMapMethodArgumentResolver());
        resolvers.add(new ServletModelAttributeMethodProcessor(false));
        resolvers.add(new RequestResponseBodyMethodProcessor(this.getMessageConverters()));
        resolvers.add(new RequestPartMethodArgumentResolver(this.getMessageConverters()));
        resolvers.add(new RequestHeaderMethodArgumentResolver(this.getBeanFactory()));
        resolvers.add(new RequestHeaderMapMethodArgumentResolver());
        resolvers.add(new ServletCookieValueMethodArgumentResolver(this.getBeanFactory()));
        resolvers.add(new ExpressionValueMethodArgumentResolver(this.getBeanFactory()));
        resolvers.add(new ServletRequestMethodArgumentResolver());
        resolvers.add(new ServletResponseMethodArgumentResolver());
        resolvers.add(new HttpEntityMethodProcessor(this.getMessageConverters()));
        resolvers.add(new RedirectAttributesMethodArgumentResolver());
        resolvers.add(new ModelMethodProcessor());
        resolvers.add(new MapMethodProcessor());
        resolvers.add(new ErrorsMethodArgumentResolver());
        resolvers.add(new SessionStatusMethodArgumentResolver());
        resolvers.add(new UriComponentsBuilderMethodArgumentResolver());
        if (this.getCustomArgumentResolvers() != null) {
            resolvers.addAll(this.getCustomArgumentResolvers());
        }

        resolvers.add(new RequestParamMethodArgumentResolver(this.getBeanFactory(), true));
        resolvers.add(new ServletModelAttributeMethodProcessor(true));
        return resolvers;
    }

    private List<HandlerMethodArgumentResolver> getDefaultInitBinderArgumentResolvers() {
        List<HandlerMethodArgumentResolver> resolvers = new ArrayList();
        resolvers.add(new RequestParamMethodArgumentResolver(this.getBeanFactory(), false));
        resolvers.add(new RequestParamMapMethodArgumentResolver());
        resolvers.add(new PathVariableMethodArgumentResolver());
        resolvers.add(new PathVariableMapMethodArgumentResolver());
        resolvers.add(new MatrixVariableMethodArgumentResolver());
        resolvers.add(new MatrixVariableMapMethodArgumentResolver());
        resolvers.add(new ExpressionValueMethodArgumentResolver(this.getBeanFactory()));
        resolvers.add(new ServletRequestMethodArgumentResolver());
        resolvers.add(new ServletResponseMethodArgumentResolver());
        if (this.getCustomArgumentResolvers() != null) {
            resolvers.addAll(this.getCustomArgumentResolvers());
        }

        resolvers.add(new RequestParamMethodArgumentResolver(this.getBeanFactory(), true));
        return resolvers;
    }

    private List<HandlerMethodReturnValueHandler> getDefaultReturnValueHandlers() {
        List<HandlerMethodReturnValueHandler> handlers = new ArrayList();
        handlers.add(new ModelAndViewMethodReturnValueHandler());
        handlers.add(new ModelMethodProcessor());
        handlers.add(new ViewMethodReturnValueHandler());
        handlers.add(new HttpEntityMethodProcessor(this.getMessageConverters(), this.contentNegotiationManager));
        handlers.add(new HttpHeadersReturnValueHandler());
        handlers.add(new CallableMethodReturnValueHandler());
        handlers.add(new DeferredResultMethodReturnValueHandler());
        handlers.add(new AsyncTaskMethodReturnValueHandler(this.beanFactory));
        handlers.add(new ModelAttributeMethodProcessor(false));
        handlers.add(new RequestResponseBodyMethodProcessor(this.getMessageConverters(), this.contentNegotiationManager));
        handlers.add(new ViewNameMethodReturnValueHandler());
        handlers.add(new MapMethodProcessor());
        if (this.getCustomReturnValueHandlers() != null) {
            handlers.addAll(this.getCustomReturnValueHandlers());
        }

        if (!CollectionUtils.isEmpty(this.getModelAndViewResolvers())) {
            handlers.add(new ModelAndViewResolverMethodReturnValueHandler(this.getModelAndViewResolvers()));
        } else {
            handlers.add(new ModelAttributeMethodProcessor(true));
        }

        return handlers;
    }

    private void initControllerAdviceCache() {
        if (this.getApplicationContext() != null) {
            if (this.logger.isDebugEnabled()) {
                this.logger.debug("Looking for controller advice: " + this.getApplicationContext());
            }

            List<ControllerAdviceBean> beans = ControllerAdviceBean.findAnnotatedBeans(this.getApplicationContext());
            Collections.sort(beans, new OrderComparator());
            Iterator var2 = beans.iterator();

            while(var2.hasNext()) {
                ControllerAdviceBean bean = (ControllerAdviceBean)var2.next();
                Set<Method> attrMethods = MethodIntrospector.selectMethods(bean.getBeanType(), MODEL_ATTRIBUTE_METHODS);
                if (!attrMethods.isEmpty()) {
                    this.modelAttributeAdviceCache.put(bean, attrMethods);
                    this.logger.info("Detected @ModelAttribute methods in " + bean);
                }

                Set<Method> binderMethods = MethodIntrospector.selectMethods(bean.getBeanType(), INIT_BINDER_METHODS);
                if (!binderMethods.isEmpty()) {
                    this.initBinderAdviceCache.put(bean, binderMethods);
                    this.logger.info("Detected @InitBinder methods in " + bean);
                }
            }

        }
    }

    @Override
    protected boolean supportsInternal(HandlerMethod handlerMethod) {
        return true;
    }
    @Override
    protected long getLastModifiedInternal(HttpServletRequest request, HandlerMethod handlerMethod) {
        return -1L;
    }
    @Override
    protected final ModelAndView handleInternal(HttpServletRequest request, HttpServletResponse response, HandlerMethod handlerMethod) throws Exception {
        if (this.getSessionAttributesHandler(handlerMethod).hasSessionAttributes()) {
            this.checkAndPrepare(request, response, this.cacheSecondsForSessionAttributeHandlers, true);
        } else {
            this.checkAndPrepare(request, response, true);
        }

        if (this.synchronizeOnSession) {
            HttpSession session = request.getSession(false);
            if (session != null) {
                Object mutex = WebUtils.getSessionMutex(session);
                synchronized(mutex) {
                    return this.invokeHandleMethod(request, response, handlerMethod);
                }
            }
        }

        return this.invokeHandleMethod(request, response, handlerMethod);
    }

    private SessionAttributesHandler getSessionAttributesHandler(HandlerMethod handlerMethod) {
        Class<?> handlerType = handlerMethod.getBeanType();
        SessionAttributesHandler sessionAttrHandler = null;
        Map var4 = this.sessionAttributesHandlerCache;
        synchronized(this.sessionAttributesHandlerCache) {
            sessionAttrHandler = (SessionAttributesHandler)this.sessionAttributesHandlerCache.get(handlerType);
            if (sessionAttrHandler == null) {
                sessionAttrHandler = new SessionAttributesHandler(handlerType, this.sessionAttributeStore);
                this.sessionAttributesHandlerCache.put(handlerType, sessionAttrHandler);
            }

            return sessionAttrHandler;
        }
    }

    private ModelAndView invokeHandleMethod(HttpServletRequest request, HttpServletResponse response, HandlerMethod handlerMethod) throws Exception {
        ServletWebRequest webRequest = new ServletWebRequest(request, response);
        WebDataBinderFactory binderFactory = this.getDataBinderFactory(handlerMethod);
        ModelFactory modelFactory = this.getModelFactory(handlerMethod, binderFactory);
        FiberServletInvocableHandlerMethod requestMappingMethod = this.createRequestMappingMethod(handlerMethod, binderFactory);
        ModelAndViewContainer mavContainer = new ModelAndViewContainer();
        mavContainer.addAllAttributes(RequestContextUtils.getInputFlashMap(request));
        modelFactory.initModel(webRequest, mavContainer, requestMappingMethod);
        mavContainer.setIgnoreDefaultModelOnRedirect(this.ignoreDefaultModelOnRedirect);
        AsyncWebRequest asyncWebRequest = WebAsyncUtils.createAsyncWebRequest(request, response);
        asyncWebRequest.setTimeout(this.asyncRequestTimeout);
        WebAsyncManager asyncManager = WebAsyncUtils.getAsyncManager(request);
        asyncManager.setTaskExecutor(this.taskExecutor);
        asyncManager.setAsyncWebRequest(asyncWebRequest);
        asyncManager.registerCallableInterceptors(this.callableInterceptors);
        asyncManager.registerDeferredResultInterceptors(this.deferredResultInterceptors);
        if (asyncManager.hasConcurrentResult()) {
            Object result = asyncManager.getConcurrentResult();
            mavContainer = (ModelAndViewContainer)asyncManager.getConcurrentResultContext()[0];
            asyncManager.clearConcurrentResult();
            if (this.logger.isDebugEnabled()) {
                this.logger.debug("Found concurrent result value [" + result + "]");
            }

            requestMappingMethod = requestMappingMethod.wrapConcurrentResult(result);
        }

        requestMappingMethod.invokeAndHandle(webRequest, mavContainer, new Object[0]);
        return asyncManager.isConcurrentHandlingStarted() ? null : this.getModelAndView(mavContainer, modelFactory, webRequest);
    }

    private FiberServletInvocableHandlerMethod createRequestMappingMethod(HandlerMethod handlerMethod, WebDataBinderFactory binderFactory) {
        FiberServletInvocableHandlerMethod requestMethod = new FiberServletInvocableHandlerMethod(handlerMethod);
        requestMethod.setHandlerMethodArgumentResolvers(this.argumentResolvers);
        requestMethod.setHandlerMethodReturnValueHandlers(this.returnValueHandlers);
        requestMethod.setDataBinderFactory(binderFactory);
        requestMethod.setParameterNameDiscoverer(this.parameterNameDiscoverer);
        return requestMethod;
    }

    private ModelFactory getModelFactory(HandlerMethod handlerMethod, WebDataBinderFactory binderFactory) {
        SessionAttributesHandler sessionAttrHandler = this.getSessionAttributesHandler(handlerMethod);
        Class<?> handlerType = handlerMethod.getBeanType();
        Set<Method> methods = (Set)this.modelAttributeCache.get(handlerType);
        if (methods == null) {
            methods = MethodIntrospector.selectMethods(handlerType, MODEL_ATTRIBUTE_METHODS);
            this.modelAttributeCache.put(handlerType, methods);
        }

        List<InvocableHandlerMethod> attrMethods = new ArrayList();
        Iterator var7 = this.modelAttributeAdviceCache.entrySet().iterator();

        while(true) {
            Map.Entry entry;
            Object bean;
            do {
                if (!var7.hasNext()) {
                    var7 = methods.iterator();

                    while(var7.hasNext()) {
                        Method method = (Method)var7.next();
                        bean = handlerMethod.getBean();
                        attrMethods.add(this.createModelAttributeMethod(binderFactory, bean, method));
                    }

                    return new ModelFactory(attrMethods, binderFactory, sessionAttrHandler);
                }

                entry = (Map.Entry)var7.next();
            } while(!((ControllerAdviceBean)entry.getKey()).isApplicableToBeanType(handlerType));

            bean = ((ControllerAdviceBean)entry.getKey()).resolveBean();
            Iterator var10 = ((Set)entry.getValue()).iterator();

            while(var10.hasNext()) {
                Method method = (Method)var10.next();
                attrMethods.add(this.createModelAttributeMethod(binderFactory, bean, method));
            }
        }
    }

    private InvocableHandlerMethod createModelAttributeMethod(WebDataBinderFactory factory, Object bean, Method method) {
        InvocableHandlerMethod attrMethod = new InvocableHandlerMethod(bean, method);
        attrMethod.setHandlerMethodArgumentResolvers(this.argumentResolvers);
        attrMethod.setParameterNameDiscoverer(this.parameterNameDiscoverer);
        attrMethod.setDataBinderFactory(factory);
        return attrMethod;
    }

    private WebDataBinderFactory getDataBinderFactory(HandlerMethod handlerMethod) throws Exception {
        Class<?> handlerType = handlerMethod.getBeanType();
        Set<Method> methods = (Set)this.initBinderCache.get(handlerType);
        if (methods == null) {
            methods = MethodIntrospector.selectMethods(handlerType, INIT_BINDER_METHODS);
            this.initBinderCache.put(handlerType, methods);
        }

        List<InvocableHandlerMethod> initBinderMethods = new ArrayList();
        Iterator var5 = this.initBinderAdviceCache.entrySet().iterator();

        while(true) {
            Map.Entry entry;
            Object bean;
            do {
                if (!var5.hasNext()) {
                    var5 = methods.iterator();

                    while(var5.hasNext()) {
                        Method method = (Method)var5.next();
                        bean = handlerMethod.getBean();
                        initBinderMethods.add(this.createInitBinderMethod(bean, method));
                    }

                    return this.createDataBinderFactory(initBinderMethods);
                }

                entry = (Map.Entry)var5.next();
            } while(!((ControllerAdviceBean)entry.getKey()).isApplicableToBeanType(handlerType));

            bean = ((ControllerAdviceBean)entry.getKey()).resolveBean();
            Iterator var8 = ((Set)entry.getValue()).iterator();

            while(var8.hasNext()) {
                Method method = (Method)var8.next();
                initBinderMethods.add(this.createInitBinderMethod(bean, method));
            }
        }
    }

    private InvocableHandlerMethod createInitBinderMethod(Object bean, Method method) {
        InvocableHandlerMethod binderMethod = new InvocableHandlerMethod(bean, method);
        binderMethod.setHandlerMethodArgumentResolvers(this.initBinderArgumentResolvers);
        binderMethod.setDataBinderFactory(new DefaultDataBinderFactory(this.webBindingInitializer));
        binderMethod.setParameterNameDiscoverer(this.parameterNameDiscoverer);
        return binderMethod;
    }

    protected InitBinderDataBinderFactory createDataBinderFactory(List<InvocableHandlerMethod> binderMethods) throws Exception {
        return new ServletRequestDataBinderFactory(binderMethods, this.getWebBindingInitializer());
    }

    private ModelAndView getModelAndView(ModelAndViewContainer mavContainer, ModelFactory modelFactory, NativeWebRequest webRequest) throws Exception {
        modelFactory.updateModel(webRequest, mavContainer);
        if (mavContainer.isRequestHandled()) {
            return null;
        } else {
            ModelMap model = mavContainer.getModel();
            ModelAndView mav = new ModelAndView(mavContainer.getViewName(), model);
            if (!mavContainer.isViewReference()) {
                mav.setView((View)mavContainer.getView());
            }

            if (model instanceof RedirectAttributes) {
                Map<String, ?> flashAttributes = ((RedirectAttributes)model).getFlashAttributes();
                HttpServletRequest request = (HttpServletRequest)webRequest.getNativeRequest(HttpServletRequest.class);
                RequestContextUtils.getOutputFlashMap(request).putAll(flashAttributes);
            }

            return mav;
        }
    }
}

