package com.quasar.quasardemo.comsat;

import org.springframework.http.HttpStatus;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.support.HandlerMethodReturnValueHandlerComposite;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.View;
import org.springframework.web.util.NestedServletException;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;

/**
 * @Author huaili
 * @Date 2019/3/4 16:48
 * @Description TODO
 **/
public class FiberServletInvocableHandlerMethod extends FiberInvocableHandlerMethod {
    private HttpStatus responseStatus;
    private String responseReason;
    private HandlerMethodReturnValueHandlerComposite returnValueHandlers;

    public FiberServletInvocableHandlerMethod(Object handler, Method method) {
        super(handler, method);
        this.initResponseStatus();
    }

    public FiberServletInvocableHandlerMethod(HandlerMethod handlerMethod) {
        super(handlerMethod);
        this.initResponseStatus();
    }

    private void initResponseStatus() {
        ResponseStatus annot = (ResponseStatus)this.getMethodAnnotation(ResponseStatus.class);
        if (annot != null) {
            this.responseStatus = annot.value();
            this.responseReason = annot.reason();
        }

    }

    public void setHandlerMethodReturnValueHandlers(HandlerMethodReturnValueHandlerComposite returnValueHandlers) {
        this.returnValueHandlers = returnValueHandlers;
    }

    public final void invokeAndHandle(ServletWebRequest webRequest, ModelAndViewContainer mavContainer, Object... providedArgs) throws Exception {
        Object returnValue = this.invokeForRequest(webRequest, mavContainer, providedArgs);
        this.setResponseStatus(webRequest);
        if (returnValue == null) {
            if (this.isRequestNotModified(webRequest) || this.hasResponseStatus() || mavContainer.isRequestHandled()) {
                mavContainer.setRequestHandled(true);
                return;
            }
        } else if (StringUtils.hasText(this.responseReason)) {
            mavContainer.setRequestHandled(true);
            return;
        }

        mavContainer.setRequestHandled(false);

        try {
            this.returnValueHandlers.handleReturnValue(returnValue, this.getReturnValueType(returnValue), mavContainer, webRequest);
        } catch (Exception var6) {
            if (this.logger.isTraceEnabled()) {
                this.logger.trace(this.getReturnValueHandlingErrorMessage("Error handling return value", returnValue), var6);
            }

            throw var6;
        }
    }

    private void setResponseStatus(ServletWebRequest webRequest) throws IOException {
        if (this.responseStatus != null) {
            if (StringUtils.hasText(this.responseReason)) {
                webRequest.getResponse().sendError(this.responseStatus.value(), this.responseReason);
            } else {
                webRequest.getResponse().setStatus(this.responseStatus.value());
            }

            webRequest.getRequest().setAttribute(View.RESPONSE_STATUS_ATTRIBUTE, this.responseStatus);
        }
    }

    private boolean isRequestNotModified(ServletWebRequest webRequest) {
        return webRequest.isNotModified();
    }

    private boolean hasResponseStatus() {
        return this.responseStatus != null;
    }

    private String getReturnValueHandlingErrorMessage(String message, Object returnValue) {
        StringBuilder sb = new StringBuilder(message);
        if (returnValue != null) {
            sb.append(" [type=").append(returnValue.getClass().getName()).append("] ");
        }

        sb.append("[value=").append(returnValue).append("]");
        return sb.toString();
    }

    FiberServletInvocableHandlerMethod wrapConcurrentResult(final Object result) {
        return new FiberServletInvocableHandlerMethod.CallableHandlerMethod(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                if (result instanceof Exception) {
                    throw (Exception)result;
                } else if (result instanceof Throwable) {
                    throw new NestedServletException("Async processing failed", (Throwable)result);
                } else {
                    return result;
                }
            }
        });
    }

    private class CallableHandlerMethod extends FiberServletInvocableHandlerMethod {
        public CallableHandlerMethod(Callable<?> callable) {
            super(callable, ClassUtils.getMethod(callable.getClass(), "call", new Class[0]));
            this.setHandlerMethodReturnValueHandlers(FiberServletInvocableHandlerMethod.this.returnValueHandlers);
        }

        @Override
        protected Object doInvoke(Object... args) throws Exception {
            return this.threadBlockingInvoke(args);
        }
        @Override
        public Class<?> getBeanType() {
            return FiberServletInvocableHandlerMethod.this.getBeanType();
        }
        @Override
        public <A extends Annotation> A getMethodAnnotation(Class<A> annotationType) {
            return FiberServletInvocableHandlerMethod.this.getMethodAnnotation(annotationType);
        }
    }
}
