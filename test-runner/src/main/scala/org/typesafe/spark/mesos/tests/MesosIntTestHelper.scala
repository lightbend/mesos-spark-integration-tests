package org.typesafe.spark.mesos.tests

import java.net.InetAddress

import org.apache.spark.{Accumulable, SparkConf, SparkContext}
import org.scalatest.exceptions.TestFailedException
import org.typesafe.spark.mesos.framework.runners.{TestResult, Utils}

trait MesosIntTestHelper {

  def runSparkTest(name: String, runnerAddress: InetAddress, ps: (String, String)*)(t: (SparkContext) => Unit) {

    val sparkConf = new SparkConf()
      .setAppName("Mesos integration test")
      .set("spark.executor.memory", "512mb")

    for (
      (key, value) <- ps
    ) {
      sparkConf.set(key, value)
    }

    val sc = new SparkContext(sparkConf)
    try {
      t(sc)
      Utils.sendMessage(runnerAddress, TestResult(name, true))
    } catch {
      case e: TestFailedException =>
        Utils.sendMessage(runnerAddress, TestResult(name, false, Some(e.getMessage)))
        throw e
    } finally {
      sc.stop()
    }
  }
}
