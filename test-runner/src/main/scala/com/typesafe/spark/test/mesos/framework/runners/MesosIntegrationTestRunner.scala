package com.typesafe.spark.test.mesos.framework.runners

import com.typesafe.config.ConfigFactory
import com.typesafe.spark.test.mesos.framework.runners.Utils._

object MesosIntegrationTestRunner {


  def main(args: Array[String]): Unit = {

    implicit val config = ConfigFactory.load()
    printMsg(config.toString)

    val failures: Int = ClientModeRunner.run(args) + ClusterModeRunner.run(args)

    if (failures > 0) {
      val suffix = if (failures > 1) "s" else ""
      printMsg(Console.RED + s"ERROR: $failures test$suffix failed" + Console.RESET)
      System.exit(1)
    }
  }
}
