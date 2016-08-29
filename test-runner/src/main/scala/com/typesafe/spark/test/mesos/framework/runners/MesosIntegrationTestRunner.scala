package com.typesafe.spark.test.mesos.framework.runners

import java.io.File

import com.typesafe.config.ConfigFactory
import com.typesafe.spark.test.mesos.framework.runners.Utils._

object MesosIntegrationTestRunner {
  def main(args: Array[String]): Unit = {
    implicit val config = ConfigFactory.parseFile(new File("mit-application.conf"))
    printMsg(config.toString)

    val failures: Int = if (!config.hasPath("spark.zk.uri")) {
      ClientModeRunner.run(args) + ClusterModeRunner.run(args)
    } else {
      ClientModeRunner.run(args) + ClusterModeRunner.run(args) + MultiClusterModeRunner.run(args)
    }

    if (failures > 0) {
      printMsg(Console.RED + s"ERROR: $failures test(s) failed" + Console.RESET)
      System.exit(1)
    }
  }
}
