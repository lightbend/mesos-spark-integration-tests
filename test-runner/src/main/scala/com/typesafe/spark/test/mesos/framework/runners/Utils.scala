package com.typesafe.spark.test.mesos.framework.runners

import java.io.{FileInputStream, File}
import java.net.{InetAddress, ServerSocket, Socket}
import java.util.concurrent._

import com.typesafe.config.Config
import com.typesafe.spark.test.mesos.mesosstate.MesosCluster
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs._

import scala.collection.mutable.ArrayBuffer
import scala.io.BufferedSource
import scala.sys.process.Process
import sys.process._

object Utils {

  def runSparkJobAndCollectResult(job: => Unit)(implicit config: Config): Int = {
    val pool = Executors.newSingleThreadExecutor()
    val server = new ServerSocket(config.getInt("test.runner.port"))
    val failures: Future[Int] = startServerForResults(server, pool)
    val timeout = config.getDuration("test.timeout", TimeUnit.MILLISECONDS)
    // run the job
    try {
      job
      // return the result of the test
      failures.get(timeout, TimeUnit.MILLISECONDS)
    } finally {
      server.close()
      pool.shutdown()
    }
  }

  def startServerForResults(server: ServerSocket, pool: ExecutorService): Future[Int] = {

    def handleConnection(socket: Socket): Int = {
      val source = new BufferedSource(socket.getInputStream)
      var failures = 0
      for (line <- source.getLines()) {
        println(line)
        if (line.contains("FAILED")) {
          failures += 1
        }
      }
      source.close()
      socket.close()
      failures
    }

    pool.submit(new Callable[Int] {
      override def call(): Int = {
        val socket = server.accept()
        val taskResults = handleConnection(socket)
        taskResults
      }
    })
  }

  def killAnyRunningFrameworks(mesosConsoleUrl: String): Unit = {
    val cluster = MesosCluster.loadStates(mesosConsoleUrl)
    // TODO: using curl, is it available in all platform
    cluster.frameworks.foreach { framework =>
      printMsg(s"Killing framework ${framework.frameworkId}")
      val p = Process("curl",
        Seq("-XPOST",
          s"${mesosConsoleUrl}/master/teardown",
          "-d",
          s"frameworkId=${framework.frameworkId}"))
      p.!
    }
  }

  def submitSparkJob(clientMode: Boolean, jobDesc: String, jobArgs: String*)(implicit config: Config): Unit = {
    val cmd: Seq[String] = Seq(jobDesc) ++ jobArgs

    val env: ArrayBuffer[(String, String)] = if (clientMode) {
      ArrayBuffer("MESOS_NATIVE_JAVA_LIBRARY" -> mesosNativeLibraryLocation())
    } else {
      ArrayBuffer()
    }

    if (config.hasPath("spark.executor.uri")) {
      env += ("SPARK_EXECUTOR_URI" -> config.getString("spark.executor.uri"))
    }

    if (config.hasPath("spark_env.spark_local_ip")) {
      env += ("SPARK_LOCAL_IP" -> config.getString("spark_env.spark_local_ip"))
    }

    if (config.hasPath("submit_env.libprocess_ip")) {
      env += ("LIBPROCESS_IP" -> config.getString("submit_env.libprocess_ip"))
    }

    val cmdStr = cmd.mkString(" ")
    printMsg(s"Running command: ${cmdStr} with env vars: ${env.mkString(" ")}")
    val proc = Process(cmdStr, None, env: _*)
    proc.lines_!.foreach(line => println(line))
    printMsg(s"Command completed.")
  }

  def printMsg(m: String): Unit = {
    println("")
    println(m)
    println("")
  }

  def startMesosDispatcher(sparkHome: String, sparkExecutorPath: String, mesosMasterUrl: String)(implicit config: Config): String = {
    // stop any running mesos dispatcher first
    val result = stopMesosDispatcher(sparkHome)
    printMsg(s"Stopped mesos dispatcher $result")

    // TODO: make the port configurable
    val dispatcherPort = config.getInt("mesos.dispatcher.port")
    val dispatcherHost = InetAddress.getLocalHost().getHostName()

    val mesosStartDispatcherDesc = Seq(s"${sparkHome}/sbin/start-mesos-dispatcher.sh",
      s"--master ${mesosMasterUrl}",
      s"--host ${dispatcherHost}",
      s"--port ${dispatcherPort}"
    )
    val env = Seq(
      "MESOS_NATIVE_JAVA_LIBRARY" -> mesosNativeLibraryLocation(),
      "SPARK_EXECUTOR_URI" -> sparkExecutorPath
    )

    val cmdStr = mesosStartDispatcherDesc.mkString(" ")
    printMsg(s"Running command: ${cmdStr} with env vars: ${env.mkString(" ")}")
    val proc = Process(cmdStr, None, env: _*)
    proc.lines_!.foreach(line => println(line))

    val numOfTries = 30

    if (!ServiceUtil.checkIfUpWithDelay("MesosDispatcher", dispatcherHost, dispatcherPort, numOfTries)) {
      println(s"MesosDispatcher is not up at host ${dispatcherHost} and port ${dispatcherPort}... going to exit!")
      System.exit(1)
    }
    s"mesos://${dispatcherHost}:${dispatcherPort}"
  }

  // looks up the env variable MESOS_NATIVE_JAVA_LIBRARY first and falls back to
  // "mesos.native.library.location" config property.
  def mesosNativeLibraryLocation()(implicit config: Config): String = {
    scala.sys.env.getOrElse("MESOS_NATIVE_JAVA_LIBRARY",
      config.getString("mesos.native.library.location"))
  }

  def copyApplicationJar(jar: String, uri: String): String = {
    System.setProperty("HADOOP_USER_NAME", "root")
    val fileName = new File(jar).getName
    val path = new Path("/app/" + fileName)
    val conf = new Configuration()
    conf.set("fs.defaultFS", uri)
    val fs = FileSystem.get(conf)
    val os: FSDataOutputStream = fs.create(path, 1.toShort)
    import org.apache.commons.io.IOUtils
    val input = new FileInputStream(jar)
    IOUtils.copy(input, os)

    input.close()
    os.close()
    fs.close()

    s"$uri/app/$fileName"
  }

  def stopMesosDispatcher(sparkHome: String): Int = {
    Process(s"${sparkHome}/sbin/stop-mesos-dispatcher.sh").!
  }
}

object ServiceUtil {

  private def checkAvailablePort(host: String, port: Int): Boolean = {

    var res = false
    var p: Option[Socket] = None

    try {
      p = Some(new Socket(host, port))
      res = true
    } catch {
      case e: Exception => // do nothing we will retry
    } finally {
      if (p.isDefined) {
        try {
          p.get.close()
        } catch {
          case e: Exception => e.printStackTrace() // closing stream failed, print stack
        }
      }
    }

    res
  }

  private def waitOnCheck[T](service: String, delay: Int, iteration: Int, max: Int)(body: => Boolean): Boolean = {

    val rs = body
    println(s"Checking for service $service - try $iteration...")

    if (!rs) {
      if (iteration != max) {
        println(s"Waiting for service $service for ${delay / 1000} secs...")
        Thread.sleep(delay)
      }
    }

    rs
  }

  /** It checks if a service is up though its port.
    *
    * @param service the name of the service we check
    * @param host the hostname to use
    * @param port the port to check
    * @param numOfTries number of times to check the port of the service. There is 1 sec of delay between tries.
    * @return true if service is up, false otherwise
    */
  def checkIfUpWithDelay(service: String, host: String, port: Int, numOfTries: Int): Boolean = {
    (1 to numOfTries).takeWhile { x => !waitOnCheck(service, 1000, x, numOfTries) {
      checkAvailablePort(host, port)
    }
    }.size != numOfTries
  }
}
