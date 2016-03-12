package com.typesafe.spark.test.mesos.framework.runners

import java.net.{InetAddress, Socket}

import com.typesafe.spark.test.mesos.SparkJobSpec
import org.scalatest.{Filter, Args}

case class RoleConfigInfo(role: String, attributes: String, roleCpus: String)

class MyStdOut

object SparkJobRunner {

  def main(args: Array[String]): Unit = {
    // this makes sure `System.exit` does not terminate the process
    // and ScalaTest gets a chance to collect the failing test result
    System.setSecurityManager(NoExitSecurityManager)

    val mesosConsoleUrl = args(0)
    val deployMode = args(1)
    val role = args(2)
    val attributes = args(3)
    val roleCpus = args(4)

    val runFunc = () => {
      var args = Array(
        "-s", "com.typesafe.spark.test.mesos.SparkJobSpec",
        "-o",
        s"-DmesosUrl=${mesosConsoleUrl}",
        s"-DdeployMode=${deployMode}",
        s"-Drole=${role}",
        s"-Dattributes=${attributes}",
        s"-DroleCpus=${roleCpus}")
      if (deployMode == "dcos") {
        args = args :+ "-l" :+ "skip-dcos"
      }
      org.scalatest.tools.Runner.run(args)
    }

    if (args.length > 5) {
      val runnerAddress = InetAddress.getByName(args(5))
      val runnerPort = args(6).toInt

      val socket = new Socket(runnerAddress, runnerPort)
      try {
        Console.withOut(socket.getOutputStream) {runFunc()}
      } finally {
        socket.close()
      }
    } else {
      Console.withOut(System.out) {runFunc()}
    }
  }
}
