package org.typesafe.spark.mesos.framework.runners

import java.io.File
import java.net.InetAddress
import java.nio.file.Files

import com.typesafe.config.Config
import mesostest.mesosstate.MesosCluster

import scala.annotation.tailrec
import scala.io.Source
import scala.sys.process.Process

object Utils {


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

  //looking for a file called <testresult log>.done file which will be produced
  //by the spark job runner when its finished
  def blockTillJobIsOverAndReturnResult(baseDir: String, logFile: String): List[String] = {
    //TODO: Use Java 8 watch service
    val logFileName = new File(logFile).getName
    val doneFileName = logFileName + ".done"

    //poor man's way to check whether file is created.
    val file = new File(baseDir, doneFileName)
    @tailrec
    def checkFileExists: Boolean = {
      if(file.exists()) {
        true
      } else {
        Thread.sleep(10000) //in milliseconds
        printMsg(s"Checking for ${file.getAbsoluteFile}")
        checkFileExists
      }
    }
    checkFileExists
    Source.fromFile(baseDir + "/" + logFileName).getLines().toList
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


  def logFileForThisRun(base: String): String = {
    s"${base}/test-results-${System.nanoTime()}.log"
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
