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

import java.util.Map;

/**
 * @author WangYi
 * @since 2020/6/17
 */
public class ModelAndView {
  private Map<String, Object> params;
  private String htmlPath;
  private String redirectUrl;

  public ModelAndView() {
  }

  public ModelAndView(Map<String, Object> params, String htmlPath) {
    this.params = params;
    this.htmlPath = htmlPath;
  }

  public Map<String, Object> params() {
    return params;
  }

  public void params(Map<String, Object> params) {
    this.params = params;
  }

  public String htmlPath() {
    return htmlPath;
  }

  public void htmlPath(String htmlPath) {
    this.htmlPath = htmlPath;
  }

  public String redirectUrl() {
    return redirectUrl;
  }

  public void redirect(String redirectUrl) {
    this.redirectUrl = redirectUrl;
  }

  @Override
  public String toString() {
    return "ModelAndView{" +
            "params=" + params +
            ", htmlPath='" + htmlPath + '\'' +
            '}';
  }
}
