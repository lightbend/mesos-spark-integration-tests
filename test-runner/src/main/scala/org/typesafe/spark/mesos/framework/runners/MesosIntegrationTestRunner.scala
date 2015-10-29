package org.typesafe.spark.mesos.framework.runners

import Utils._
import com.typesafe.config.ConfigFactory

object MesosIntegrationTestRunner {


  def main(args: Array[String]): Unit = {
    implicit val config = ConfigFactory.load().getConfig("mit")
    printMsg(config.toString)

    val result =
      ClientModeRunner.run(args, mesosMode = "fine-grained") ++
      ClientModeRunner.run(args, mesosMode = "coarse-grained") ++
      ClusterModeRunner.run(args, mesosMode = "fine-grained") ++
      ClusterModeRunner.run(args, mesosMode = "coarse-grained")

    //TODO: Generate some JUnit style report for CI
    printMsg("TestResults:")
    result.foreach(printMsg)
  }


}
