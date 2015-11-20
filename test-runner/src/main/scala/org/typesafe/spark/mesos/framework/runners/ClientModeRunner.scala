package org.typesafe.spark.mesos.framework.runners

import java.net.{Socket, ServerSocket}
import java.util.concurrent._

import Utils._
import com.typesafe.config.Config

import scala.io.BufferedSource

object ClientModeRunner {

  def run(args: Array[String])(implicit config: Config) = {

    val sparkHome = args(0)
    val mesosMasterUrl = args(1)
    val applicationJarPath = args(2)
    val mesosConsoleUrl = mesosMasterUrl.replaceAll("mesos", "http")

    //make sure we kill any running mesos frameworks. Right now if we run
    //mesos dispatcher it doesn't die automatically
    killAnyRunningFrameworks(mesosConsoleUrl)

    runSparkJobAndCollectResult {
      val sparkSubmitJobDesc = Seq(s"${sparkHome}/bin/spark-submit",
        "--class org.typesafe.spark.mesos.framework.runners.SparkJobRunner",
        s"--master $mesosMasterUrl",
        s"--deploy-mode client")

      submitSparkJob(sparkSubmitJobDesc.mkString(" "),
        applicationJarPath,
        mesosConsoleUrl,
        "client",
        "localhost",
        config.getString("test.runner.port"),
        config.getString("spark.role"),
        config.getString("spark.attributes"),
        config.getString("spark.roleCpus"))
    }
  }

}
