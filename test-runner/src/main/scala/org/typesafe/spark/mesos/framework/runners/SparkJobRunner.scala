package org.typesafe.spark.mesos.framework.runners

import java.net.{InetAddress, Socket}

import org.typesafe.spark.mesos.tests.{ClientModeSpec, ClusterModeSpec}

case class RoleConfigInfo(role: String, attributes: String, roleCpus: String)

object SparkJobRunner {

  def main(args: Array[String]): Unit = {
    val mesosConsoleUrl = args(0)
    val deployMode = args(1)
    val runnerAddress = InetAddress.getByName(args(2))
    val runnerPort = args(3).toInt

    val cfg = RoleConfigInfo(args(4), args(5), args(6))

    val testToRun = deployMode match {
      case "cluster" => new ClusterModeSpec(mesosConsoleUrl, cfg)
      case "client" => new ClientModeSpec(mesosConsoleUrl, cfg)
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
