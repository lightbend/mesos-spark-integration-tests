package org.typesafe.spark.mesos.test.framework

import org.apache.spark._

import scala.sys.process.Process

object MesosIntegrationTestRunner {

  def main(args: Array[String]): Unit = {
    //two available mesos modes: fine-grained & coarse-grained
    runInClientMode(args, mesosMode = "fine-grained")
    runInClientMode(args, mesosMode = "coarse-grained")

    runInClusterMode(args, mesosMode = "fine-grained")
  }

  def runInClusterMode(args: Array[String], mesosMode: String) = {

  }

  def runInClientMode(args: Array[String], mesosMode: String) = {

    val sparkSubmitScript = args(0)
    val sparkExecutorPath = args(1)
    val mesosMasterUrl = args(2)
    val applicationJarPath = args(3)
    val mesosConsoleUrl = mesosMasterUrl.replaceAll("mesos", "http")

    val sparkSubmitJobDesc = Seq(s"$sparkSubmitScript",
      "--class org.typesafe.spark.mesos.test.framework.SparkJobRunner",
      s"--master $mesosMasterUrl",
      s"--deploy-mode client",
      s"$applicationJarPath $mesosConsoleUrl $mesosMode"
    )

    //TODO: read the mesos binary file location from parameter
    val proc = Process(sparkSubmitJobDesc.mkString(" "), None,
      "MESOS_NATIVE_JAVA_LIBRARY" -> "/usr/local/lib/libmesos.dylib",
      "SPARK_EXECUTOR_URI" -> sparkExecutorPath
    )
    proc.lines_!.foreach(line => println(line))

  }

}
