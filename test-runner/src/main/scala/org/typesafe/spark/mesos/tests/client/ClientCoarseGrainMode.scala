package org.typesafe.spark.mesos.tests.client

import java.net.URL

import mesostest.mesosstate.MesosCluster
import org.apache.spark.{Accumulable, SparkContext}
import org.scalatest.FunSuite
import org.scalatest.concurrent.TimeLimitedTests
import org.typesafe.spark.mesos.framework.runners.{TestResult, TestResultCollector}
import org.typesafe.spark.mesos.tests.MesosIntTestHelper

import scala.collection.mutable.{Set => MSet}

object ClientCoarseGrainMode {
  import org.scalatest.time.SpanSugar._

  val TEST_TIMEOUT = 300 seconds
}

class ClientCoarseGrainMode(sc: SparkContext,
                                mesosConsoleUrl: String,
                                accumulable: Accumulable[TestResultCollector, TestResult])
  extends FunSuite with TimeLimitedTests with MesosIntTestHelper {

  import ClientFineGrainMode._

  override val timeLimit = TEST_TIMEOUT

  test("simple count in coarse grain mode") {
    accumulateResult("ClientCoarseGrainMode", accumulable) {
      val rdd = sc.makeRDD(1 to 5)
      val res = rdd.sum()

      assert(15 == res)

      val m = MesosCluster.loadStates(mesosConsoleUrl)
      assert(1 == m.frameworks.size, "only one framework should be running")
      assert(1 == m.frameworks.head.tasks.size, "no task should be running")
    }
  }

}


