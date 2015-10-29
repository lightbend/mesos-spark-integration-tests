package org.typesafe.spark.mesos.tests

import java.net.URL

import org.typesafe.spark.mesos.test.framework.{TestResultCollector, TestResult}

import scala.collection.mutable.{Set => MSet}

import mesostest.mesosstate.MesosCluster
import org.apache.spark.{Accumulable, SparkContext}
import org.scalatest.FunSuite
import org.scalatest.concurrent.TimeLimitedTests

object SimpleTestCoarseGrainMode {
  import org.scalatest.time.SpanSugar._

  val TEST_TIMEOUT = 300 seconds
}

class SimpleTestCoarseGrainMode(sc: SparkContext,
                                mesosConsoleUrl: String,
                                accumulable: Accumulable[TestResultCollector, TestResult]) extends FunSuite with TimeLimitedTests {

  import SimpleTestFineGrainMode._

  override val timeLimit = TEST_TIMEOUT

  test("simple count in coarse grain mode") {
    val rdd = sc.makeRDD(1 to 5)
    val res = rdd.sum()

    assert(15 == res)

    val m = mesosCluster
    assert(1 == m.frameworks.size, "only one framework should be running")
    assert(1 == m.frameworks.head.tasks.size, "no task should be running")

    accumulable += TestResult("SimpleTestCoarseGrainMode.simpleTest", true, "this test passed")
  }

  def mesosCluster(): MesosCluster = {
    MesosCluster(new URL(s"${mesosConsoleUrl}state.json"))
  }
}


