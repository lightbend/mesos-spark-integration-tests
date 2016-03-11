package com.typesafe.spark.test.mesos

import com.typesafe.spark.test.mesos.mesosstate.MesosCluster
import org.scalatest.Assertions._
import org.scalatest.Tag
import org.scalatest.concurrent.Eventually
import org.scalatest.time._
import org.scalatest.time.SpanSugar._

trait DynamicAllocationSpec extends Eventually { self: MesosIntTestHelper =>

  def mesosConsoleUrl: String

  implicit override val patienceConfig = PatienceConfig(timeout = scaled(Span(30, Seconds)), interval = scaled(Span(500, Millis)))

  val initialExecutorCount = 2

  runSparkTest("dynamic allocation, in coarse grain mode",
    List("spark.mesos.coarse" -> "true",
      "spark.dynamicAllocation.enabled" -> "true",
      "spark.shuffle.service.enabled" -> "true",
      "spark.dynamicAllocation.executorIdleTimeout" -> "3s",
      "spark.dynamicAllocation.maxExecutors" -> "3",
      "spark.dynamicAllocation.minExecutors" -> "1",
      "spark.dynamicAllocation.initialExecutors" -> initialExecutorCount.toString,
      "spark.dynamicAllocation.schedulerBacklogTimeout" -> "1s"),
    List(Tag("skip-dcos"))) { sc =>

      val startState = MesosCluster.loadStates(mesosConsoleUrl)
      val numberOfSlaves = startState.numberOfSlaves
      val numberOfUnreservedCpus = startState.slaves.map(_.unreservedResources.cpu).sum.toInt

      eventually {
        // start with 1 executor per slave
        // TODO: this is a bug, it should follow spark.dynamicAllocation.initialExecutors
        val m = MesosCluster.loadStates(mesosConsoleUrl)
        assertResult(true, "test driver framework should be running") {
          m.sparkFramework.isDefined
        }
        assertResult(initialExecutorCount, "Few tasks should be running, at startup, as per spark.dynamicAllocation.initialExecutors") {
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

      // for serialization of the condition in the following RDD transformation
      val mesosUrl = mesosConsoleUrl

      val rdd = sc.makeRDD(1 to 25, numberOfUnreservedCpus).mapPartitions { i =>
        // wait for the other executors to be started
        while (MesosCluster.loadStates(mesosUrl).sparkFramework.get.nbRunningTasks != numberOfSlaves)
          Thread.sleep(1000)

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
        assertResult(1 * numberOfSlaves, "One task per slave should be running, after first execution, as per spark.dynamicAllocation.maxExecutors") {
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

      val rdd2 = sc.makeRDD(1 to 25, numberOfUnreservedCpus).mapPartitions { i =>
        // wait for the other executors to be started
        while (MesosCluster.loadStates(mesosUrl).sparkFramework.get.nbRunningTasks != numberOfSlaves)
          Thread.sleep(1000)

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
        assertResult(1 * numberOfSlaves, "One task per slave should be running, after second execution, as per spark.dynamicAllocation.maxExecutors") {
          m.sparkFramework.get.nbRunningTasks
        }
      }
    }

}
