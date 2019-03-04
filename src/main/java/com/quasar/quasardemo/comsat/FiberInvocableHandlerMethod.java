package com.quasar.quasardemo.comsat;

import co.paralleluniverse.fibers.Fiber;
import co.paralleluniverse.fibers.SuspendExecution;
import co.paralleluniverse.fibers.Suspendable;
import co.paralleluniverse.fibers.instrument.SuspendableHelper;
import co.paralleluniverse.strands.SuspendableRunnable;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.support.InvocableHandlerMethod;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.Callable;

/**
 * @Author huaili
 * @Date 2019/3/4 17:00
 * @Description TODO
 **/
public class FiberInvocableHandlerMethod extends InvocableHandlerMethod {
    private static final String SPRING_BOOT_ERROR_CONTROLLER_CLASS_NAME = "org.springframework.boot.autoconfigure.web.ErrorController";
    private static Class springBootErrorControllerClass;

    public FiberInvocableHandlerMethod(Object bean, Method method) {
        super(bean, method);
    }

    public FiberInvocableHandlerMethod(HandlerMethod handlerMethod) {
        super(handlerMethod);
    }

    public FiberInvocableHandlerMethod(Object bean, String methodName, Class... parameterTypes) throws NoSuchMethodException {
        super(bean, methodName, parameterTypes);
    }

    private boolean isBootErrorController() {
        return springBootErrorControllerClass != null && springBootErrorControllerClass.isAssignableFrom(this.getBean().getClass());
    }

    private boolean isSpringTraditionalThreadBlockingControllerMethod() {
        Method m = this.getMethod();
        return m.getAnnotation(Suspendable.class) == null && !Arrays.asList(m.getExceptionTypes()).contains(SuspendExecution.class) && !SuspendableHelper.isInstrumented(m);
    }

    @Override
    protected Object doInvoke(Object... args) throws Exception {
        return !this.isBootErrorController() && !this.isSpringTraditionalThreadBlockingControllerMethod() ? this.fiberDispatchInvoke(args) : this.threadBlockingInvoke(args);
    }

    protected Object threadBlockingInvoke(Object... args) throws IllegalAccessException, Exception {
        return super.doInvoke(args);
    }

    protected Object fiberDispatchInvoke(final Object... args) {
        final Object b = this.getBean();
        final Method m = this.getBridgedMethod();
        ReflectionUtils.makeAccessible(m);
        final DeferredResult ret = new DeferredResult();
        (new Fiber(new SuspendableRunnable() {
            private Object deAsync(Object o) throws SuspendExecution, Exception {
                return o instanceof Callable ? this.deAsync(((Callable)o).call()) : o;
            }

            @Override
            public void run() throws SuspendExecution, InterruptedException {
                try {
                    Object originalRet = m.invoke(b, args);
                    ret.setResult(this.deAsync(originalRet));
                } catch (IllegalArgumentException var4) {
                   FiberInvocableHandlerMethod.this.assertTargetBean(m, b, args);
                    ret.setErrorResult(new IllegalStateException(FiberInvocableHandlerMethod.this.getInvocationErrorMessage(var4.getMessage(), args), var4));
                } catch (InvocationTargetException var5) {
                    Throwable targetException = var5.getTargetException();
                    if (!(targetException instanceof RuntimeException) && !(targetException instanceof Error) && !(targetException instanceof Exception)) {
                        String msg = FiberInvocableHandlerMethod.this.getInvocationErrorMessage("Failed to invoke controller method", args);
                        ret.setErrorResult(new IllegalStateException(msg, targetException));
                    } else {
                        ret.setErrorResult(targetException);
                    }
                } catch (Exception var6) {
                    ret.setErrorResult(var6);
                }

            }
        })).start();
        return ret;
    }

    @Override
    protected void assertTargetBean(Method method, Object targetBean, Object[] args) {
        Class<?> methodDeclaringClass = method.getDeclaringClass();
        Class<?> targetBeanClass = targetBean.getClass();
        if (!methodDeclaringClass.isAssignableFrom(targetBeanClass)) {
            String msg = "The mapped controller method class '" + methodDeclaringClass.getName() + "' is not an instance of the actual controller bean instance '" + targetBeanClass.getName() + "'. If the controller requires proxying " + "(e.g. due to @Transactional), please use class-based proxying.";
            throw new IllegalStateException(this.getInvocationErrorMessage(msg, args));
        }
    }

    private String getInvocationErrorMessage(String message, Object[] resolvedArgs) {
        StringBuilder sb = new StringBuilder(message);
        sb.append("Resolved arguments: \n");

        for(int i = 0; i < resolvedArgs.length; ++i) {
            sb.append("[").append(i).append("] ");
            if (resolvedArgs[i] == null) {
                sb.append("[null] \n");
            } else {
                sb.append("[type=").append(resolvedArgs[i].getClass().getName()).append("] ");
                sb.append("[value=").append(resolvedArgs[i]).append("]\n");
            }
        }

        return sb.toString();
    }

    static {
        try {
            springBootErrorControllerClass = Class.forName("org.springframework.boot.autoconfigure.web.ErrorController");
        } catch (ClassNotFoundException var1) {
            ;
        }

    }
}

