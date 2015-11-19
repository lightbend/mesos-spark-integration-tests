package org.typesafe.spark.mesos.framework.runners

import java.net.{InetAddress, Socket}
import org.typesafe.spark.mesos.tests.{ClientModeSpec, ClusterModeSpec}

object SparkJobRunner {

  def main(args: Array[String]): Unit = {
    val mesosConsoleUrl = args(0)
    val deployMode = args(1)
    val runnerAddress = InetAddress.getByName(args(2))
    val runnerPort = args(3).toInt

    val testToRun = deployMode match {
      case "cluster" => new ClusterModeSpec(mesosConsoleUrl)
      case "client" => new ClientModeSpec(mesosConsoleUrl)
    }

    val socket = new Socket(runnerAddress, runnerPort)
    try {
      Console.withOut(socket.getOutputStream) {
        org.scalatest.run(testToRun)
      }
    } finally {
      socket.close()
    }
  }


}
