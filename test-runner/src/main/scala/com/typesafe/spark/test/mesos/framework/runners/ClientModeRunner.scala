package com.typesafe.spark.test.mesos.framework.runners

import java.net.{Socket, ServerSocket}
import java.util.concurrent._

import Utils._
import com.typesafe.config.Config

import scala.collection.mutable.ArrayBuffer
import scala.io.BufferedSource

object ClientModeRunner {

  def run(args: Array[String])(implicit config: Config): Int = {

    val sparkHome = args(0)
    val mesosMasterUrl = args(1)
    val applicationJarPath = args(2)
    val mesosConsoleUrl = mesosMasterUrl.replaceAll("mesos://", "http://")

    // make sure we kill any running mesos frameworks. Right now if we run
    // mesos dispatcher it doesn't die automatically
    killAnyRunningFrameworks(mesosConsoleUrl)

    // this file is in `src/main/resources`, but ends up in root on the classpath
    val logFileConfig = "-Dlog4j.configuration=mit-log4j.properties"

    runSparkJobAndCollectResult {
      val sparkSubmitJobDesc = ArrayBuffer(
        s"${sparkHome}/bin/spark-submit",
        "--class com.typesafe.spark.test.mesos.framework.runners.SparkJobRunner",
        s"--master $mesosMasterUrl",
        "--deploy-mode client",
        s"""--driver-java-options "$logFileConfig"""",
        s"""--conf spark.executor.extraJavaOptions="$logFileConfig""""
      )

      if (config.hasPath("spark.driver.host")) {
        sparkSubmitJobDesc += s"--conf spark.driver.host=${config.getString("spark.driver.host")}"
      }

      if (config.hasPath("spark.mesos.executor.home")) {
        sparkSubmitJobDesc += s"--conf spark.mesos.executor.home=${config.getString("spark.mesos.executor.home")}"
      }

      submitSparkJob(clientMode = true, sparkSubmitJobDesc.mkString(" "),
        applicationJarPath,
        mesosConsoleUrl,
        "client",
        config.getString("spark.role"),
        config.getString("spark.attributes"),
        config.getString("spark.roleCpus"),
        "localhost",
        config.getString("test.runner.port"))
    }
  }

}
