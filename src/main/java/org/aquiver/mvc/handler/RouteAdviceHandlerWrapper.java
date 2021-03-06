/*
 * MIT License
 *
 * Copyright (c) 2019 1619kHz
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.aquiver.mvc.handler;

import org.apex.ApexContext;
import org.aquiver.mvc.annotation.HandleAdvice;
import org.aquiver.mvc.annotation.RouteAdvice;
import org.aquiver.mvc.argument.MethodArgumentGetter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author WangYi
 * @since 2020/8/29
 */
public class RouteAdviceHandlerWrapper implements RouteAdviceHandler {
  private static final Logger log = LoggerFactory.getLogger(RouteAdviceHandlerWrapper.class);

  private final ApexContext context = ApexContext.of();
  private final Map<Class<? extends Throwable>, Method> handlerMethod = new ConcurrentHashMap<>();
  private final MethodHandles.Lookup lookup = MethodHandles.lookup();
  private Class<?> exceptionHandler;

  public void initialize(Class<?> exceptionHandler) {
    if (!exceptionHandler.isAnnotationPresent(RouteAdvice.class)) {
      throw new IllegalArgumentException(exceptionHandler.getName()
              + "is not annotation" + " present " + RouteAdvice.class.getSimpleName());
    }
    this.exceptionHandler = exceptionHandler;
    final Method[] methods = exceptionHandler.getMethods();
    if (methods.length == 0) {
      return;
    }
    for (final Method method : methods) {
      if (!method.isAnnotationPresent(HandleAdvice.class)) {
        continue;
      }
      final HandleAdvice handleAdvice = method.getAnnotation(HandleAdvice.class);
      this.handlerMethod.put(handleAdvice.value(), method);
    }
  }

  @Override
  public void handle(Throwable throwable) {
    final MethodArgumentGetter methodArgumentGetter = context.getBean(MethodArgumentGetter.class);
    if (Objects.isNull(methodArgumentGetter)) {
      throw new IllegalArgumentException("methodArgumentGetter can't be null");
    }
    final Method method = handlerMethod.get(throwable.getClass());
    if (handlerMethod.isEmpty() || !handlerMethod.containsKey(throwable.getClass())
            || (Objects.isNull(method) || method.getParameterCount() == 0)) {
      return;
    }
    try {
      final List<Object> invokeArguments = methodArgumentGetter.getParams(method.getParameters());
      this.lookup.unreflect(method).bindTo(exceptionHandler.newInstance())
              .invokeWithArguments(invokeArguments);
    } catch (Throwable e) {
      log.error("invoke error handler method exception", e);
    }
  }
}
