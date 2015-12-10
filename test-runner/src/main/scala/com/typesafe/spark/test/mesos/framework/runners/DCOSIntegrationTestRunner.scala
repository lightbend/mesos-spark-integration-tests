package com.typesafe.spark.test.mesos.framework.runners

import com.typesafe.config.ConfigFactory
import Utils._;

object DCOSIntegrationTestRunner {

  def main(args: Array[String]): Unit = {

    implicit val config = ConfigFactory.load()

    val dcosURL = args(0)
    val applicationJarPath = args(1)
    val result = DCOSClusterModeRunner.run(dcosURL, applicationJarPath)

    printMsg("TestResults:")
    println(result)

    //TODO: Generate some JUnit style report for CI
    if (result.contains("FAILED")) {
      System.err.println("Tests failed")
      System.exit(1)
    }
  }

}
