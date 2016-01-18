package com.typesafe.spark.test.mesos

import com.typesafe.spark.test.mesos.mesosstate.MesosCluster
import org.scalatest.Assertions._
import org.scalatest.concurrent.Eventually
import org.scalatest.time._
import org.scalatest.time.SpanSugar._

trait DynamicAllocationSpec extends Eventually { self: MesosIntTestHelper =>

  def mesosConsoleUrl: String

  implicit override val patienceConfig = PatienceConfig(timeout = scaled(Span(30, Seconds)), interval = scaled(Span(500, Millis)))

  runSparkTest("dynamic allocation, in coarse grain mode",
    "spark.mesos.coarse" -> "true",
    "spark.dynamicAllocation.enabled" -> "true",
    "spark.shuffle.service.enabled" -> "true",
    "spark.dynamicAllocation.executorIdleTimeout" -> "3s",
    "spark.dynamicAllocation.maxExecutors" -> "3",
    "spark.dynamicAllocation.minExecutors" -> "1",
    "spark.dynamicAllocation.initialExecutors" -> "1",
    "spark.dynamicAllocation.schedulerBacklogTimeout" -> "1s") { sc =>

      eventually {
        // start with 1 executor per slave
        // TODO: this is a bug, it should follow spark.dynamicAllocation.initialExecutors
        val m = MesosCluster.loadStates(mesosConsoleUrl)
        assertResult(true, "test driver framework should be running") {
          m.sparkFramework.isDefined
        }
        assertResult(1 * m.numberOfSlaves, "One task per slave should be running, at startup, as (BUG) per spark.dynamicAllocation.minExecutors") {
          m.sparkFramework.get.nbRunningTasks
        }
      }

      eventually {
        // check state before running, should be done to 1 executor
        val m = MesosCluster.loadStates(mesosConsoleUrl)
        assertResult(true, "test driver framework should be running") {
          m.sparkFramework.isDefined
        }
        assertResult(1, "One task should be running, at startup, as per spark.dynamicAllocation.minExecutors") {
          m.sparkFramework.get.nbRunningTasks
        }
      }

      val numberOfSlaves = MesosCluster.loadStates(mesosConsoleUrl).numberOfSlaves

      val rdd = sc.makeRDD(1 to 25, numberOfSlaves).mapPartitions { i =>
        Thread.sleep(10000)
        i
      }
      val res = rdd.sum()

      assert(325 == res)

      {
        // check state after running, should have 1 executor per slave
        val m = MesosCluster.loadStates(mesosConsoleUrl)
        assertResult(true, "test driver framework should be running") {
          m.sparkFramework.isDefined
        }
        assertResult(1 * m.numberOfSlaves, "One task per slave should be running, after first execution, as per spark.dynamicAllocation.maxExecutors") {
          m.sparkFramework.get.nbRunningTasks
        }
      }

      eventually {
        // check state after waiting, should be back down to 1 executor
        val m = MesosCluster.loadStates(mesosConsoleUrl)
        assertResult(true, "test driver framework should be running") {
          m.sparkFramework.isDefined
        }
        assertResult(1, "One task should be running, after wait, as per spark.dynamicAllocation.minExecutors") {
          m.sparkFramework.get.nbRunningTasks
        }
      }

      val rdd2 = sc.makeRDD(1 to 25, numberOfSlaves).mapPartitions { i =>
        Thread.sleep(20000)
        i
      }
      val res2 = rdd2.sum()

      assert(325 == res2)

      {
        // check state after 2nd run, should be back up to 1 executor per slave
        val m = MesosCluster.loadStates(mesosConsoleUrl)
        assertResult(true, "test driver framework should be running") {
          m.sparkFramework.isDefined
        }
        assertResult(1 * m.numberOfSlaves, "One task per slave should be running, after second execution, as per spark.dynamicAllocation.maxExecutors") {
          m.sparkFramework.get.nbRunningTasks
        }
      }
    }

}
