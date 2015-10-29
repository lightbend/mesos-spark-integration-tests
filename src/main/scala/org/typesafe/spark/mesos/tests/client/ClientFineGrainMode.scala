package org.typesafe.spark.mesos.tests.client

import java.net.URL

import mesostest.mesosstate.MesosCluster
import org.apache.spark.{Accumulable, SparkContext}
import org.scalatest.FunSuite
import org.scalatest.concurrent.TimeLimitedTests
import org.typesafe.spark.mesos.framework.runners.{TestResult, TestResultCollector}
import org.typesafe.spark.mesos.tests.MesosIntTestHelper

import scala.collection.mutable.{Set => MSet}

object ClientFineGrainMode {
  import org.scalatest.time.SpanSugar._

  val TEST_TIMEOUT = 300 seconds
}

class ClientFineGrainMode(sc: SparkContext,
                              mesosConsoleUrl: String,
                              accumulable: Accumulable[TestResultCollector, TestResult])
  extends FunSuite with TimeLimitedTests with MesosIntTestHelper {

  import ClientFineGrainMode._

  override val timeLimit = TEST_TIMEOUT

  test("simple sum in fine grain mode") {
    accumulateResult("ClientFineGrainMode - simple sum", accumulable) {
      val rdd = sc.makeRDD(1 to 5)
      val res = rdd.sum()

      assert(15 == res)
      // check no task running (fine grained)
      val m = MesosCluster.loadStates(mesosConsoleUrl)
      assert(1 == m.frameworks.size, "only one framework should be running")
      assert(0 == m.frameworks.head.tasks.size, "no task should be running")
    }
  }

  test("simple collect in fine grain mode") {
    accumulateResult("ClientFineGrainMode - collect example", accumulable) {
      val rdd = sc.makeRDD(1 to 5)
      val res = rdd.collect()

      assert(5 == res.size)
      // check no task running (fine grained)
      val m = MesosCluster.loadStates(mesosConsoleUrl)
      assert(1 == m.frameworks.size, "only one framework should be running")
      assert(0 == m.frameworks.head.tasks.size, "no task should be running")
    }
  }
}

