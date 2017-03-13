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

    val jobArgs = SparkJobRunnerArgs(args)

    val runFunc = () => {
      var runnerArgs = Array(
        "-s", "com.typesafe.spark.test.mesos.SparkJobSpec",
        "-o",
        s"-DmesosUrl=${jobArgs.mesosConsoleUrl}",
        s"-DdeployMode=${jobArgs.deployMode}",
        s"-Drole=${jobArgs.role}",
        s"-Dattributes=${jobArgs.attributes}",
        s"-DroleCpus=${jobArgs.roleCpus}")
      if (jobArgs.authToken.isDefined) {
        runnerArgs = runnerArgs :+ s"-DauthToken=${jobArgs.authToken.get}"
      }
      if (jobArgs.deployMode == "dcos") {
        runnerArgs = runnerArgs :+ "-l" :+ "skip-dcos"
      }
      org.scalatest.tools.Runner.run(runnerArgs)
    }

    if (jobArgs.runnerAddress.isDefined) {
      val socket = new Socket(jobArgs.runnerAddress.get, jobArgs.runnerPort.get)
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

case class SparkJobRunnerArgs(
  mesosConsoleUrl: String,
  deployMode: String,
  role: String,
  attributes: String,
  roleCpus: String,
  runnerAddress: Option[InetAddress],
  runnerPort: Option[Int],
  authToken: Option[String])

object SparkJobRunnerArgs {
  def apply(args: Array[String]): SparkJobRunnerArgs = {

    val (runnerAddress, runnerPort, authToken) =
      if (args.length > 5 && args(5).charAt(0) == '-') {
        val authToken = args(5).split("=")(1)
        (Option.empty, Option.empty, Some(authToken))
      } else if (args.length > 5) {
        val runnerAddress = Some(InetAddress.getByName(args(5)))
        val runnerPort = Some(args(6).toInt)
        val authToken = if (args.length > 7) Some(args(7).split("=")(1)) else Option.empty
        (runnerAddress, runnerPort, authToken)
      } else {
        (Option.empty, Option.empty, Option.empty)
      }

    SparkJobRunnerArgs(
      args(0),
      args(1),
      args(2),
      args(3),
      args(4),
      runnerAddress,
      runnerPort,
      authToken)
  }
}
