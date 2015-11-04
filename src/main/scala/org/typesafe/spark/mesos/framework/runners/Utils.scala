package org.typesafe.spark.mesos.framework.runners

import java.io.{PrintStream, File}
import java.net.{ServerSocket, Socket, InetAddress}
import java.nio.file.Files
import java.util.concurrent._

import com.typesafe.config.Config
import mesostest.mesosstate.MesosCluster

import scala.annotation.tailrec
import scala.io.{BufferedSource, Source}
import scala.sys.process.Process

object Utils {


  def runSparkJobAndCollectResult(job: => Unit)(implicit config: Config): List[String] = {
    val pool = Executors.newSingleThreadExecutor()
    val server = new ServerSocket(config.getInt("test.runner.port"))
    val results: Future[List[String]] = startServerForResults(server, pool)
    val timeout = config.getDuration("test.timeout", TimeUnit.MILLISECONDS)
    //run the job
    try {
      job
      //return the result of the test
      results.get(timeout, TimeUnit.MILLISECONDS)
    } finally {
      server.close()
      pool.shutdown()
    }
  }

  def startServerForResults(server: ServerSocket, pool: ExecutorService) = {

    def handleConnection(socket: Socket): List[String] = {
      val source = new BufferedSource(socket.getInputStream)
      val results = source.getLines().toList
      source.close()
      socket.close()
      results
    }

    pool.submit(new Callable[List[String]] {
      override def call(): List[String] = {
        val socket = server.accept()
        val taskResults = handleConnection(socket)
        taskResults
      }
    })
  }


  def killAnyRunningFrameworks(mesosConsoleUrl: String) = {
    val cluster = MesosCluster.loadStates(mesosConsoleUrl)
    //TODO: using curl, is it available in all platform
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

  def submitSparkJob(jobDesc: String, jobArgs: String*)(implicit config: Config) = {
    val cmd: Seq[String] = Seq(jobDesc) ++ jobArgs
    //TODO: read the mesos binary file location from parameter
    val proc = Process(cmd.mkString(" "), None,
      "MESOS_NATIVE_JAVA_LIBRARY" -> config.getString("mesos.native.library.location"),
      "SPARK_EXECUTOR_URI" -> config.getString("spark.executor.tgz.location")
    )
    proc.lines_!.foreach(line => println(line))
  }

  def printMsg(m: String): Unit = {
    println("")
    println(m)
    println("")
  }


  def startMesosDispatcher(sparkHome: String, sparkExecutorPath: String, mesosMasterUrl: String)(implicit config: Config): String = {
    //stop any running mesos dispatcher first
    val result = stopMesosDispatcher(sparkHome)
    printMsg(s"Stopped mesos dispatcher $result")

    //TODO: make the port configurable
    val dispatcherPort = config.getInt("mesos.dispatcher.port")

    val mesosStartDispatcherDesc = Seq(s"${sparkHome}/sbin/start-mesos-dispatcher.sh",
      s"--master ${mesosMasterUrl}",
      s"--host ${InetAddress.getLocalHost().getHostName()}",
      s"--port ${dispatcherPort}"
    )
    //TODO: read the mesos binary file location from parameter
    val proc = Process(mesosStartDispatcherDesc.mkString(" "), None,
      "MESOS_NATIVE_JAVA_LIBRARY" -> "/usr/local/lib/libmesos.dylib",
      "SPARK_EXECUTOR_URI" -> sparkExecutorPath
    )
    proc.lines_!.foreach(line => println(line))

    s"mesos://${InetAddress.getLocalHost().getHostName()}:${dispatcherPort}"
  }

  def copyApplicationJar(jar: String, hostLocation: String, dockerLocation: String) = {
    val fileName = new File(jar).getName
    //remove existing copy
    Process(s"rm ${hostLocation}/${fileName}").!
    //copy the application jar file
    Process(s"cp ${jar} ${hostLocation}").!

    s"$dockerLocation/${fileName}"
  }

  def stopMesosDispatcher(sparkHome: String): Int = {
    Process(s"${sparkHome}/sbin/stop-mesos-dispatcher.sh").!
  }
}
