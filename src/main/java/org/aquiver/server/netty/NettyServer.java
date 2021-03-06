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
package org.aquiver.server.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.util.ResourceLeakDetector;
import org.apache.commons.lang3.JavaVersion;
import org.apache.commons.lang3.SystemUtils;
import org.apex.Environment;
import org.aquiver.Aquiver;
import org.aquiver.WebContextInitializer;
import org.aquiver.common.banner.Banner;
import org.aquiver.common.watcher.GlobalEnvListener;
import org.aquiver.common.watcher.GlobalEnvTask;
import org.aquiver.common.banner.SimpleBanner;
import org.aquiver.server.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.nio.file.Paths;
import java.security.cert.CertificateException;
import java.util.Objects;

import static org.aquiver.ServerSpec.DEFAULT_ACCEPT_THREAD_COUNT;
import static org.aquiver.ServerSpec.DEFAULT_IO_THREAD_COUNT;
import static org.aquiver.ServerSpec.PATH_ENV_WATCHER;
import static org.aquiver.ServerSpec.PATH_SERVER_ADDRESS;
import static org.aquiver.ServerSpec.PATH_SERVER_NETTY_ACCEPT_THREAD_COUNT;
import static org.aquiver.ServerSpec.PATH_SERVER_NETTY_IO_THREAD_COUNT;
import static org.aquiver.ServerSpec.PATH_SERVER_PORT;
import static org.aquiver.ServerSpec.PATH_SERVER_SSL;
import static org.aquiver.ServerSpec.PATH_SERVER_SSL_CERT;
import static org.aquiver.ServerSpec.PATH_SERVER_SSL_PRIVATE_KEY;
import static org.aquiver.ServerSpec.PATH_SERVER_SSL_PRIVATE_KEY_PASS;
import static org.aquiver.ServerSpec.SERVER_ADDRESS;
import static org.aquiver.ServerSpec.SERVER_PORT;
import static org.aquiver.ServerSpec.SERVER_SSL;
import static org.aquiver.ServerSpec.SERVER_WATCHER_PATH;

/**
 * open ssl {@code initSSL}
 * open webSocket {@code initWebSocket}
 * open the Http com.aquiver.service {@code startServer}
 * observe the environment {@code watchEnv}
 * configure the shutdown thread to stop the hook {@code shutdownHook}
 *
 * @author WangYi
 * @since 2019/6/5
 */
public class NettyServer implements Server {
  private static final Logger log = LoggerFactory.getLogger(NettyServer.class);

  /**
   * Banner and bootstrap when service starts.
   */
  private final Banner defaultBanner = new SimpleBanner();
  private final ServerBootstrap serverBootstrap = new ServerBootstrap();
  private final WebContextInitializer initializer = new WebContextInitializer();

  /**
   * Netty builds long connection service.
   */
  private EventLoopGroup bossGroup;
  private EventLoopGroup workerGroup;
  private Environment environment;
  private Aquiver aquiver;
  private Channel channel;
  private SslContext sslContext;

  /**
   * Service startup status, using volatile to ensure threads are visible.
   */
  private volatile boolean stop = false;

  /**
   * start server and init setting
   *
   * @param aquiver Aquiver
   * @throws Exception Common Exception
   */
  @Override
  public void start(Aquiver aquiver) throws Exception {
    long startMs = System.currentTimeMillis();

    this.aquiver = aquiver;
    this.environment = this.aquiver.environment();
    this.initializer.initialize(aquiver);
    this.printBanner();

    final String bootClsName = this.aquiver.bootClsName();
    final String bootConfName = this.aquiver.bootConfName();
    final String pidCode = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
    final String hostName = SystemUtils.getHostName();
    final String javaHome = SystemUtils.getJavaHome().getPath();
    final String ioTmpdir = SystemUtils.getJavaIoTmpDir().getPath();
    final String userName = SystemUtils.getUserName();
    final String javaVersion = JavaVersion.JAVA_1_8.name();

    log.info("Starting {} on {} with PID {} ", bootClsName, hostName + "/" + userName, pidCode);
    log.info("Starting service [Netty]");
    log.info("Starting Http Server: Netty/4.1.36.Final");
    log.info("Java Home: {}", javaHome);
    log.info("Io tmp path: {}", ioTmpdir);
    log.info("Java version: {}", javaVersion);
    log.info("The configuration file loaded by this application startup is {}", bootConfName);

    this.initSsl();
    this.startServer(startMs);
    this.watchEnv();
    this.shutdownHook();
  }

  /**
   * init ssl connection
   *
   * @throws CertificateException This exception indicates one of a variety of certificate problems.
   * @throws SSLException         Indicates some kind of error detected by an SSL subsystem.
   *                              This class is the general class of exceptions produced
   *                              by failed SSL-related operations.
   */
  private void initSsl() throws CertificateException, SSLException {
    log.info("Check if the ssl configuration is enabled.");

    final Boolean ssl = environment.getBoolean(PATH_SERVER_SSL, SERVER_SSL);
    final SelfSignedCertificate ssc = new SelfSignedCertificate();

    if (ssl) {
      log.info("Ssl configuration takes effect :{}", true);

      final String sslCert = this.environment.get(PATH_SERVER_SSL_CERT, null);
      final String sslPrivateKey = this.environment.get(PATH_SERVER_SSL_PRIVATE_KEY, null);
      final String sslPrivateKeyPass = this.environment.get(PATH_SERVER_SSL_PRIVATE_KEY_PASS, null);

      log.info("SSL CertChainFile  Path: {}", sslCert);
      log.info("SSL PrivateKeyFile Path: {}", sslPrivateKey);
      log.info("SSL PrivateKey Pass: {}", sslPrivateKeyPass);

      sslContext = SslContextBuilder.forServer(setKeyCertFileAndPriKey(sslCert, ssc.certificate()),
          setKeyCertFileAndPriKey(sslPrivateKey, ssc.privateKey()), sslPrivateKeyPass).build();
    }

    log.info("Current netty server ssl startup status: {}", ssl);
    log.info("A valid ssl connection configuration is not configured and is rolled back to the default connection state.");
  }

  /**
   * Open an com.aquiver.http server and initialize the Netty configuration.
   * {@link NettyServerInitializer}
   * <p>
   * After initializing, the thread group is initialized according to the configuration parameters, including
   * {@code netty.accept-thread-count} 和 {@code netty.io-thread-count}
   * <p>
   * Then, according to the current system, the choice of communication model for Mac or Windows, NIO mode on Windows.
   * Judgment method in {@link EventLoopKit}, {@code nioGroup} is the judgment method under windows
   * {@code epollGroup} is the judgment method for Mac system or Linux system.
   * <p>
   * <p>
   * IO threads, in order to make full use of the CPU, while considering reducing the overhead of line context
   * switching, usually set to twice the number of CPU coresFor scenes with high response time requirements,
   * disable the nagle algorithm and send immediately without waiting.
   * After these are done, they will get the port and ip address to start. These are all user-configurable.
   * </p>
   * <p>
   * Bootstrap configuration parameter, specify a pooled Allocator, PooledByteBufAllocator pooled multiplexingIn
   * IO operation, direct memory is allocated instead of JVM heap space, to avoid copying from JVM to direct memory
   * when sending data
   * </p>
   *
   * @param startTime Startup time for calculating start time
   * @throws Exception Common exception
   */
  private void startServer(long startTime) throws Exception {
    ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.DISABLED);

    this.serverBootstrap.childHandler(new NettyServerInitializer(sslContext));

    final int acceptThreadCount = environment.getInt(PATH_SERVER_NETTY_ACCEPT_THREAD_COUNT, DEFAULT_ACCEPT_THREAD_COUNT);
    final int ioThreadCount = environment.getInt(PATH_SERVER_NETTY_IO_THREAD_COUNT, DEFAULT_IO_THREAD_COUNT);

    if (aquiver.epollIsAvailable()) {
      this.bossGroup = new EpollEventLoopGroup(acceptThreadCount, withThreadName("epoll-boss@"));
      this.workerGroup = new EpollEventLoopGroup(ioThreadCount, withThreadName("epoll-worker@"));
      this.serverBootstrap.channel(EpollServerSocketChannel.class);
    } else {
      this.bossGroup = new NioEventLoopGroup(acceptThreadCount, withThreadName("nio-boss@"));
      this.workerGroup = new NioEventLoopGroup(ioThreadCount, withThreadName("nio-worker@"));
      this.serverBootstrap.channel(NioServerSocketChannel.class);
    }

    this.serverBootstrap.group(bossGroup, workerGroup).option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
    log.info("The IO mode of the application startup is: {}", aquiver.epollIsAvailable() ? "Epoll" : "Nio");

    this.stop = false;

    final int port = this.environment.getInt(PATH_SERVER_PORT, SERVER_PORT);
    final String address = this.environment.get(PATH_SERVER_ADDRESS, SERVER_ADDRESS);
    this.channel = serverBootstrap.bind(address, port).sync().channel();

    long endTime = System.currentTimeMillis();
    long startUpTime = (endTime - startTime);
    long jvmStartTime = (endTime - ManagementFactory.getRuntimeMXBean().getStartTime());

    log.info("Aquiver started on port(s): {} (com.aquiver.http) with context path ''", port);
    log.info("Started {} in {} ms (JVM running for {} ms)", aquiver.bootClsName(), startUpTime, jvmStartTime);
  }

  private NettyThreadFactory withThreadName(String s) {
    return new NettyThreadFactory(s);
  }

  /**
   * stop http server
   */
  @Override
  public void stop() {
    log.info("Netty Server Shutdown...");
    if (stop) {
      return;
    }
    stop = true;
    try {
      if (Objects.nonNull(bossGroup)) {
        this.bossGroup.shutdownGracefully();
      }
      if (Objects.nonNull(workerGroup)) {
        this.workerGroup.shutdownGracefully();
      }
      log.info("The netty service is gracefully closed");
    } catch (Exception e) {
      log.error("An exception occurred while the Netty Http service was down", e);
    }
  }

  @Override
  public void join() {
    try {
      this.channel.closeFuture().sync();
    } catch (InterruptedException e) {
      log.error("Channel close future fail", e);
    }
  }

  /**
   * Observe the environment class, which can be opened by configuration.
   * After opening, it will perform various operations through the log
   * printing environment.
   */
  private void watchEnv() {
    boolean watch = this.environment.getBoolean(PATH_ENV_WATCHER, false);
    if (watch) {
      log.info("start application watcher");
      final GlobalEnvListener fileListener = new GlobalEnvListener();
      GlobalEnvTask.config().watchPath(SERVER_WATCHER_PATH).listener(fileListener).start();
    }
  }

  /**
   * Add a hook that stops the current service when the system is shut down
   */
  private void shutdownHook() {
    Thread shutdownThread = new Thread(this::stop);
    shutdownThread.setName("shutdown@thread");
    Runtime.getRuntime().addShutdownHook(shutdownThread);
  }

  /**
   * print default banner
   */
  private void printBanner() {
    this.defaultBanner.printBanner(System.out, aquiver.bannerText(), aquiver.bannerFont());
  }

  /**
   * Use the configured path if the certificate and private key are
   * configured, otherwise use the default configuration
   *
   * @param keyPath         private keystore path
   * @param defaultFilePath default private keystore path
   * @return keystore file
   */
  private File setKeyCertFileAndPriKey(String keyPath, File defaultFilePath) {
    return Objects.nonNull(keyPath) ? Paths.get(keyPath).toFile() : defaultFilePath;
  }
}
