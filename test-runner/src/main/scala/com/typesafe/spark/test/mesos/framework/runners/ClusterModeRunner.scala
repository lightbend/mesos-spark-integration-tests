package com.typesafe.spark.test.mesos.framework.runners

import scala.collection.mutable.ArrayBuffer
import scala.util.Try

import com.typesafe.config.Config

import Utils._

object ClusterModeRunner {

  def run(args: Array[String])(implicit config: Config): Int = {
    val sparkHome = args(0)
    val mesosMasterUrl = args(1)
    val applicationJarPath = args(2)
    val mesosConsoleUrl = mesosMasterUrl.replaceAll("mesos://", "http://")

    val hostAddress = if (config.hasPath("docker.host.ip")) config.getString("docker.host.ip")
    else "localhost"

    val jarPath = if (config.hasPath("hdfs.uri")) {
      val hdfsUri = config.getString("hdfs.uri")

      // copying application jar to docker location so mesos slaves can pick it up
      val hdfsJarLocation = Utils.copyApplicationJar(args(2), hdfsUri)

      printMsg(s"Application jar file is copied to HDFS $hdfsJarLocation")
      hdfsJarLocation
    } else {
      applicationJarPath
    }

    // make sure we kill any running mesos frameworks. Right now if we run
    // mesos dispatcher it doesn't die automatically
    killAnyRunningFrameworks(mesosConsoleUrl)

    runSparkJobAndCollectResult {
      // start the dispatcher
      val dispatcherUrl = startMesosDispatcher(sparkHome,
        config.getString("spark.executor.uri"), mesosMasterUrl)
      printMsg(s"Mesos dispatcher running at $dispatcherUrl")

      // run spark submit in cluster mode
      val sparkSubmitJobDesc = ArrayBuffer(s"${sparkHome}/bin/spark-submit",
        "--class com.typesafe.spark.test.mesos.framework.runners.SparkJobRunner",
        s"--master $dispatcherUrl",
        s"--driver-memory 512mb",
        s"--deploy-mode cluster")

      if (config.hasPath("spark.executor.uri")) {
        sparkSubmitJobDesc += s"--conf spark.executor.uri=${config.getString("spark.executor.uri")}"
      }

      if (config.hasPath("spark.mesos.executor.home")) {
        sparkSubmitJobDesc += s"--conf spark.mesos.executor.home=${config.getString("spark.mesos.executor.home")}"
      }

      submitSparkJob(sparkSubmitJobDesc.mkString(" "),
        jarPath,
        mesosConsoleUrl,
        "cluster",
        config.getString("spark.role"),
        config.getString("spark.attributes"),
        config.getString("spark.roleCpus"),
        hostAddress,
        config.getString("test.runner.port"))
    }
  }

}
