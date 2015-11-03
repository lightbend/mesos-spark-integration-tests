package org.typesafe.spark.mesos.framework.runners

import com.typesafe.config.ConfigFactory
import org.typesafe.spark.mesos.framework.runners.Utils._

object MesosIntegrationTestRunner {


  def main(args: Array[String]): Unit = {

    implicit val config = ConfigFactory.load().getConfig("mit")
    printMsg(config.toString)


    val result = ClientModeRunner.run(args) ++ ClusterModeRunner.run(args)

    //TODO: Generate some JUnit style report for CI
    printMsg("TestResults:")
    result.foreach(printMsg)
  }


}
