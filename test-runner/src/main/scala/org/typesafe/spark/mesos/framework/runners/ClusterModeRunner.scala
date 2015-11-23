package org.typesafe.spark.mesos.framework.runners

import java.io.File
import java.net.URL

import Utils._
import com.typesafe.config.Config
import mesostest.mesosstate.MesosCluster

import scala.sys.process.Process

object ClusterModeRunner {

  def run(args: Array[String])(implicit config: Config) = {
    val sparkHome = args(0)
    val mesosMasterUrl = args(1)
    val applicationJarPath = args(2)
    val mesosConsoleUrl = mesosMasterUrl.replaceAll("mesos", "http")

    val dockerHostAddress = config.getString("docker.host.ip")

    val hdfsUri = config.getString("hdfs.uri")

    //copying application jar to docker location so mesos slaves can pick it up
    val hdfsJarLocation = Utils.copyApplicationJar(args(2), hdfsUri)

    printMsg(s"Application jar file is copied to HDFS $hdfsJarLocation")

    //make sure we kill any running mesos frameworks. Right now if we run
    //mesos dispatcher it doesn't die automatically
    killAnyRunningFrameworks(mesosConsoleUrl)

    runSparkJobAndCollectResult {
      //start the dispatcher
      val dispatcherUrl = startMesosDispatcher(sparkHome,
        config.getString("spark.executor.uri"),
        mesosMasterUrl)
      printMsg(s"Mesos dispatcher running at $dispatcherUrl")

      //run spark submit in cluster mode
      val sparkSubmitJobDesc = Seq(s"${sparkHome}/bin/spark-submit",
        "--class org.typesafe.spark.mesos.framework.runners.SparkJobRunner",
        s"--master $dispatcherUrl",
        s"--driver-memory 512mb",
        s"--deploy-mode cluster")

      submitSparkJob(sparkSubmitJobDesc.mkString(" "),
        hdfsJarLocation,
        mesosConsoleUrl,
        "cluster",
        dockerHostAddress,
        config.getString("test.runner.port"),
        config.getString("spark.role"),
        config.getString("spark.attributes"),
        config.getString("spark.roleCpus"))
    }
  }

}
