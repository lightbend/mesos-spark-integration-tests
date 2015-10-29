package org.typesafe.spark.mesos.framework.runners

import Utils._
import com.typesafe.config.Config

object ClientModeRunner {

  def run(args: Array[String], mesosMode: String)(implicit config: Config) = {

    val sparkHome = args(0)
    val mesosMasterUrl = args(1)
    val applicationJarPath = args(2)
    val mesosConsoleUrl = mesosMasterUrl.replaceAll("mesos", "http")

    //host location mounted to docker
    val sharedHostLocation = config.getString("mounted.host.location")
    //TODO: make this configurable
    val testResultsLog = logFileForThisRun(sharedHostLocation)

    //make sure we kill any running mesos frameworks. Right now if we run
    //mesos dispatcher it doesn't die automatically
    killAnyRunningFrameworks(mesosConsoleUrl)

    val sparkSubmitJobDesc = Seq(s"${sparkHome}/bin/spark-submit",
      "--class org.typesafe.spark.mesos.framework.runners.SparkJobRunner",
      s"--master $mesosMasterUrl",
      s"--deploy-mode client")

    submitSparkJob(sparkSubmitJobDesc.mkString(" "),
      applicationJarPath,
      mesosConsoleUrl,
      mesosMode,
      "client",
      testResultsLog)

    blockTillJobIsOverAndReturnResult(sharedHostLocation, testResultsLog)
  }


}
