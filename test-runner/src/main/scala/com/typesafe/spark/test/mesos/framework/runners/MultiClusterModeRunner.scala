package com.typesafe.spark.test.mesos.framework.runners

import org.apache.zookeeper.{KeeperException, ZKUtil, ZooKeeper}

import com.typesafe.config.{Config, ConfigFactory}

import Utils._

object MultiClusterModeRunner {
  def deleteZNodes(zk: ZooKeeper, path: String): Unit = {
    try {
      ZKUtil.deleteRecursive(zk, path);
    } catch {
      // Ignoring if the node doesn't exist.
      case e: KeeperException.NoNodeException => {}
    }
  }

  def run(args: Array[String])(implicit config: Config): Int = {
    val sparkHome = args(0)
    val mesosMasterUrl = args(1)
    val applicationJarPath = args(2)
    val mesosConsoleUrl = mesosMasterUrl.replaceAll("mesos://", "http://")

    val hostAddress = if (config.hasPath("docker.host.ip")) config.getString("docker.host.ip")
      else "localhost"

    val jarPath = if (config.hasPath("hdfs.uri")) {
      val hdfsUri = config.getString("hdfs.uri")
      // Copying application jar to docker location so mesos slaves can pick it up.
      val hdfsJarLocation = Utils.copyApplicationJar(args(2), hdfsUri)
      printMsg(s"Application jar file is copied to HDFS $hdfsJarLocation")
      hdfsJarLocation
    } else {
      applicationJarPath
    }

    // Make sure we kill any running mesos frameworks. Right now if we run
    // mesos dispatcher it doesn't die automatically.
    killAnyRunningFrameworks(mesosConsoleUrl)

    val zookeeperDir = config.getString("mesos.dispatcher.zookeeper.dir")
    val zkConnection = config.getString("spark.zk.uri").replaceAll("zk://", "")
    val zkClient = new ZooKeeper(zkConnection, 3000, null)
    val zookeeperDir1 = zookeeperDir + "1"
    val zookeeperDir2 = zookeeperDir + "2"

    // Remove completely all zk state for both dispatchers.
    deleteZNodes(zkClient, zookeeperDir1)
    deleteZNodes(zkClient, zookeeperDir2)

    var uiPort = config.getInt("mesos.dispatcher.port")
    var webUiPort = config.getInt("mesos.dispatcher.webui.port")

    // Start two dispatchers.
    val dispatcherUrl1 = startMesosDispatcher(sparkHome,
      config.getString("spark.executor.uri"),
      mesosMasterUrl,
      false,
      Some(zkConnection),
      1,
      Some(uiPort),
      Some(webUiPort),
      Map("spark.deploy.zookeeper.dir" -> zookeeperDir1))

    val dispatcherUrl2 = startMesosDispatcher(sparkHome,
      config.getString("spark.executor.uri"),
      mesosMasterUrl,
      false,
      Some(zkConnection),
      2,
      Some(uiPort + 10),
      Some(webUiPort + 10),
      Map("spark.deploy.zookeeper.dir" -> zookeeperDir2))

    printMsg(s"Mesos dispatchers running at $dispatcherUrl1 and $dispatcherUrl2")
    val result1 = runSparkJobAndCollectResult {
      runDispatcherTests(dispatcherUrl1, sparkHome, jarPath, mesosConsoleUrl, hostAddress)
    }

    val result2 = runSparkJobAndCollectResult {
      runDispatcherTests(dispatcherUrl2, sparkHome, jarPath, mesosConsoleUrl, hostAddress)
    }

    result1 + result2
  }

  def runDispatcherTests(
      dispatcherUrl: String,
      sparkHome: String,
      jarPath: String,
      mesosConsoleUrl: String,
      hostAddress: String)(implicit config: Config): Unit = {
      // Run spark submit in cluster mode.
    val sparkSubmitJobDesc = Seq(s"${sparkHome}/bin/spark-submit",
      "--class com.typesafe.spark.test.mesos.framework.runners.SparkJobRunner",
      s"--master $dispatcherUrl",
      s"--driver-memory 512m",
      s"--deploy-mode cluster")

    submitSparkJob(clientMode = false, sparkSubmitJobDesc.mkString(" "),
      jarPath,
      mesosConsoleUrl,
      "cluster",
      config.getString("spark.role"),
      config.getString("spark.attributes"),
      config.getString("spark.roleCpus"),
      hostAddress,
      config.getString("test.runner.port"))
  }


  def main(args: Array[String]): Unit = {
    implicit val config = ConfigFactory.load()
    printMsg(config.toString)

    if (!config.hasPath("spark.zk.uri")) {
      printMsg("spark.zk.uri is required to run Multiple dispatcher tests")
      System.exit(1)
    }

    val failures: Int = MultiClusterModeRunner.run(args)

    if (failures > 0) {
      printMsg(Console.RED + s"ERROR: $failures test(s) failed" + Console.RESET)
      System.exit(1)
    }
  }
}
