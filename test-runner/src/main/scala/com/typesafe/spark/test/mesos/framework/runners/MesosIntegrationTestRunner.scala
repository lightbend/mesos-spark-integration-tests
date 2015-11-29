package com.typesafe.spark.test.mesos.framework.runners

import com.typesafe.config.ConfigFactory
import com.typesafe.spark.test.mesos.framework.runners.Utils._

object MesosIntegrationTestRunner {


  def main(args: Array[String]): Unit = {

    implicit val config = ConfigFactory.load().getConfig("mit")
    printMsg(config.toString)


    val result = ClientModeRunner.run(args) ++ ClusterModeRunner.run(args)

    printMsg("TestResults:")
    result.foreach(printMsg)

    if (result.exists(_.contains("FAILED"))) {
      printMsg(Console.RED + "ERROR: One or more tests failed" + Console.RESET)
      System.exit(1)
    }

  }


}
