package org.typesafe.spark.mesos.framework.runners

import java.io.{FileWriter, PrintWriter}
import java.net.InetAddress

import org.scalatest.events.Event
import org.scalatest.{Reporter, Args}
import org.typesafe.spark.mesos.framework.reporter.SocketReporter

import scala.collection.mutable.{Set => MSet}

import org.apache.spark._
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

    val reporter = new SocketReporter(runnerAddress, runnerPort)
    testToRun.run(None, Args(reporter))
  }


}
