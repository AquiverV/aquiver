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
package org.application.controller;

import org.application.bean.User;
import org.aquiver.annotation.*;
import org.aquiver.annotation.bind.*;
import org.aquiver.mvc.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

@RestPath
@Path(value = "/controller")
public class ApplicationController {

  private static final Logger log = LoggerFactory.getLogger(ApplicationController.class);

  @Path(value = "/requestParam", method = PathMethod.GET)
  public String requestParam(@Param String name) {
    log.info("request param:" + name);
    return "requestParam:" + name;
  }

  @JSON
  @Path(value = "/requestParamJson", method = PathMethod.GET)
  public User requestParamJson(@Param String name) {
    log.info("request param:" + name);
    return new User("1", 2, (short) 3);
  }

  @Path(value = "/requestParamAlisa", method = PathMethod.GET)
  public String requestParamAlisa(@Param(value = "username") String name) {
    log.info("request param:" + name);
    return "requestParamAlisa:" + name;
  }

  @Path(value = "/requestCookies", method = PathMethod.GET)
  public String requestCookies(@Cookies String name) {
    log.info("request param:" + name);
    return "requestCookies:" + name;
  }

  @Path(value = "/requestHeaders", method = PathMethod.GET)
  public String requestHeaders(@Header(value = "Accept") String name) {
    log.info("request param:" + name);
    return "requestHeaders:" + name;
  }

  @Path(value = "/pathVariable/{name}/{code}", method = PathMethod.GET)
  public String pathVariable(@PathVar String name, @PathVar String code) {
    log.info("request param:" + name + ":" + "request param:" + code);
    return "pathVariable:" + name + ":" + code;
  }

  @Path(value = "/postBody", method = PathMethod.POST)
  public String postBody(@Body User user) {
    log.info("post body param:" + user);
    return "post body:" + user;
  }

  @GET(value = "/get")
  public String get() {
    return "controller/get";
  }

  @POST(value = "/uploadFile")
  public String uploadFile(@FileUpload MultipartFile file) {
    log.info("fileName:{}", file.getFileName());
    try (InputStream inputStream = file.getInputStream();
         BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream))) {
      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = bufferedReader.readLine()) != null) {
        sb.append(line);
      }
      System.out.println(sb.toString());
    } catch (IOException e) {
      log.error("error:", e);
    }
    return "controller/uploadFile";
  }
}
