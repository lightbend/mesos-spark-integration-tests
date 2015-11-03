package org.typesafe.spark.mesos.framework.runners

import java.io.{FileWriter, PrintWriter}
import java.net.InetAddress

import scala.collection.mutable.{Set => MSet}

import org.apache.spark._
import org.typesafe.spark.mesos.tests.{ClientModeSpec, ClusterModeSpec}

case class TestResult(testName: String, isSuccess: Boolean, message: Option[String] = None)

object SparkJobRunner {

  def main(args: Array[String]): Unit = {
    val mesosConsoleUrl = args(0)
    val deployMode = args(1)
    val runnerAddress = InetAddress.getByName(args(2))

    try {
      val testToRun = deployMode match {
        case "cluster" => new ClusterModeSpec(mesosConsoleUrl, runnerAddress)
        case "client" => new ClientModeSpec(mesosConsoleUrl, runnerAddress)
      }
      org.scalatest.run(testToRun)
    } finally {
      notifyJobFinished(runnerAddress)
    }
  }

  private def notifyJobFinished(runnerAddress: InetAddress): Unit = Utils.sendMessage(runnerAddress, "<DONE/>")

}
