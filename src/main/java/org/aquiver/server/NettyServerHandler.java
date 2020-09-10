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
package org.aquiver.server;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import org.apex.ApexContext;
import org.aquiver.*;
import org.aquiver.handler.ErrorHandlerResolver;
import org.aquiver.mvc.RequestResult;
import org.aquiver.mvc.argument.MethodArgumentGetter;
import org.aquiver.mvc.interceptor.AspectInterceptorChain;
import org.aquiver.mvc.interceptor.Interceptor;
import org.aquiver.mvc.router.RestfulRouter;
import org.aquiver.mvc.router.RouteInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * @author WangYi
 * @since 2020/5/26
 */
@ChannelHandler.Sharable
public class NettyServerHandler extends SimpleChannelInboundHandler<Object> {
  private static final Logger log = LoggerFactory.getLogger(NettyServerHandler.class);

  private FullHttpRequest request;
  private RequestContext requestContext;
  private final RestfulRouter restfulRouter;
  private final ErrorHandlerResolver errorHandlerResolver;
  private final ResultHandlerResolver resultHandlerResolver;
  private final ApexContext context = ApexContext.of();
  private final StaticFileServerHandler fileServerHandler = new StaticFileServerHandler();

  public NettyServerHandler() {
    this.errorHandlerResolver = context.getBean(ErrorHandlerResolver.class);
    this.restfulRouter = context.getBean(RestfulRouter.class);
    this.resultHandlerResolver = context.getBean(ResultHandlerResolver.class);
  }

  @Override
  public boolean acceptInboundMessage(final Object msg) {
    return msg instanceof HttpMessage;
  }

  @Override
  public void channelReadComplete(ChannelHandlerContext ctx) {
    ctx.flush();
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    log.error("An exception occurred when calling the mapping method", cause);
    this.requestContext.throwable(cause);
    this.errorHandlerResolver.handlerException(cause, requestContext);
    ctx.close();
  }

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
    if (msg instanceof HttpRequest) {
      HttpRequest request = (HttpRequest) msg;
      this.request = new DefaultFullHttpRequest(
              request.protocolVersion(), request.method(), request.uri());
    }

    if (msg instanceof FullHttpRequest) {
      this.request = (FullHttpRequest) msg;
    }

    if (Const.FAVICON_PATH.equals(request.uri())) {
      return;
    }
    try {
      this.requestContext = this.buildRequestContext(request, ctx);

      final RouteInfo routeInfo = restfulRouter.lookup(request.uri());
      if (Objects.isNull(routeInfo)) {
        lookupStaticFile(requestContext);
      } else {
        this.requestContext.route(routeInfo);
      }

      final List<Interceptor> interceptors = Aquiver.interceptors();
      final AspectInterceptorChain interceptorChain = new AspectInterceptorChain(interceptors, requestContext);
      interceptorChain.invoke();

      final RequestResult result = interceptorChain.getResult();
      if (Objects.nonNull(result)) {
        ResultHandler handler = resultHandlerResolver.lookup(result);
        if (Objects.isNull(handler)) {
          throw new IllegalStateException("Unsupported result class:"
                  + " " + result.getResultType().getSimpleName());
        } else {
          handler.handle(requestContext, result);
        }
      }
    } catch (Throwable throwable) {
      exceptionCaught(ctx, throwable);
    }
  }

  private RequestContext buildRequestContext(FullHttpRequest request, ChannelHandlerContext ctx) {
    RequestContext requestContext = new RequestContext(request, ctx);
    MethodArgumentGetter methodArgumentGetter = new MethodArgumentGetter(requestContext);
    this.context.addBean(methodArgumentGetter);
    return requestContext;
  }

  private void lookupStaticFile(RequestContext context) throws Exception {
    final boolean result = this.fileServerHandler.handle(context);
    if (!result) {
      final NoRouteFoundException exception = new NoRouteFoundException
              (context.request().httpMethodName(), context.request().uri());
      final FullHttpResponse response = ResultResponseBuilder.forResponse(
              HttpResponseStatus.NOT_FOUND, exception.getMessage()).build();
      context.writeAndFlush(response);
      throw exception;
    }
  }
}
