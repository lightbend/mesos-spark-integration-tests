package org.typesafe.spark.mesos.tests

import java.net.InetAddress

import mesostest.mesosstate.MesosCluster
import org.scalatest.FunSuite
import org.scalatest.concurrent.TimeLimitedTests

import scala.collection.mutable.{Set => MSet}

object ClusterModeSpec {

  import org.scalatest.time.SpanSugar._

  val TEST_TIMEOUT = 300 seconds
}

class ClusterModeSpec(mesosConsoleUrl: String,
                      runnerAddress: InetAddress)
  extends FunSuite with TimeLimitedTests with MesosIntTestHelper {

  import ClusterModeSpec._

  override val timeLimit = TEST_TIMEOUT

  test("simple count in coarse grain mode") {
    runSparkTest("ClusterCoarseGrainMode", runnerAddress, "spark.mesos.coarse" -> "true") { sc =>
      val rdd = sc.makeRDD(1 to 5)
      val res = rdd.sum()

      assert(15 == res)

      val m = MesosCluster.loadStates(mesosConsoleUrl)
      assert(2 == m.frameworks.size, "should be two. One for dispatcher and another one framework for spark should be running")
      assert(1 == m.frameworks.head.tasks.size, "no task should be running")
    }
  }


  test("simple count in fine grain mode") {
    runSparkTest("ClusterFineGrainMode", runnerAddress, "spark.mesos.coarse" -> "false") { sc =>
      val rdd = sc.makeRDD(1 to 5)
      val res = rdd.sum()

      assert(15 == res)
      val m = MesosCluster.loadStates(mesosConsoleUrl)
      assert(2 == m.frameworks.size, "should be two. One for dispatcher and another one framework for spark should be running")
      assert(0 == m.frameworks.head.tasks.size, "no task should be running")
    }
  }

}


