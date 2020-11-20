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
package org.aquiver;

import org.aquiver.hook.RouteAdviceHook;
import org.aquiver.hook.RestfulRouterHook;
import org.aquiver.hook.WebHook;
import org.aquiver.hook.WebSocketHook;

import java.util.HashMap;
import java.util.Map;

/**
 * @author WangYi
 * @since 2020/8/23
 */
public class WebInitializer {
  private final Map<String, WebHook> webHookMap = new HashMap<>();

  public WebInitializer() {
    this.registerWebHook(new RestfulRouterHook());
    this.registerWebHook(new WebSocketHook());
    this.registerWebHook(new RouteAdviceHook());
  }

  public void registerWebHook(WebHook webHook) {
    this.webHookMap.put(webHook.getClass().getName(), webHook);
  }

  public void lookupWebHook(String className) {
    this.webHookMap.getOrDefault(className, null);
  }

  public void initialize(Map<String, Object> instances, Aquiver aquiver) throws Exception {
    if (instances.isEmpty()) {
      return;
    }
    for (Map.Entry<String, WebHook> entry : webHookMap.entrySet()) {
      entry.getValue().load(instances, aquiver);
    }
  }
}
