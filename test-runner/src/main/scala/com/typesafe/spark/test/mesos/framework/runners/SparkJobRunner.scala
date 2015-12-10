package com.typesafe.spark.test.mesos.framework.runners

import java.net.{InetAddress, Socket}

import com.typesafe.spark.test.mesos.{ClientModeSpec, ClusterModeSpec}

case class RoleConfigInfo(role: String, attributes: String, roleCpus: String)

object SparkJobRunner {

  def main(args: Array[String]): Unit = {
    // this makes sure `System.exit` does not terminate the process
    // and ScalaTest gets a chance to collect the failing test result
    System.setSecurityManager(NoExitSecurityManager)

    val mesosConsoleUrl = args(0)
    val deployMode = args(1)
    val cfg = RoleConfigInfo(args(2), args(3), args(4))

    val testToRun = deployMode match {
      case "cluster" => new ClusterModeSpec(mesosConsoleUrl, cfg)
      case "client" => new ClientModeSpec(mesosConsoleUrl, cfg)
    }

    if (args.length > 5) {
      val runnerAddress = InetAddress.getByName(args(5))
      val runnerPort = args(6).toInt

      val socket = new Socket(runnerAddress, runnerPort)
      try {
        Console.withOut(socket.getOutputStream) {
          org.scalatest.run(testToRun)
        }
      } finally {
        socket.close()
      }
    } else {
      Console.withOut(System.out) {
        org.scalatest.run(testToRun)
      }
    }

  }
}
