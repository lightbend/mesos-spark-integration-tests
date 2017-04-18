package com.typesafe.spark.test.mesos

import org.apache.spark.SparkContext
import org.scalatest.Assertions._

import com.typesafe.spark.test.mesos.mesosstate.MesosCluster
import com.typesafe.spark.test.mesos.framework.runners.RoleConfigInfo
import org.scalatest.Tag

trait RolesSpec extends RoleSpecHelper {
  self: MesosIntTestHelper =>

  def mesosConsoleUrl: String
  def cfg: RoleConfigInfo
  def isInClusterMode : Boolean = false
  def authToken: Option[String]

  ignoreSparkTest("simple count in fine-grained mode with role",
    List("spark.mesos.coarse" -> "false", "spark.mesos.role" -> cfg.role),
    List(Tag("skip-dcos"))) { sc =>
    testRole(sc, false)
  }

  runSparkTest("simple count in coarse-grained mode with role",
    List("spark.mesos.coarse" -> "true", "spark.mesos.role" -> cfg.role),
    List(Tag("skip-dcos"))) { sc =>
    testRole(sc, true)
  }
}

trait RoleSpecHelper {
  self: MesosIntTestHelper with RolesSpec =>

  def testRole(sc: SparkContext, isCoarse: Boolean) : Unit = {

    val m = MesosCluster.loadStates(mesosConsoleUrl, authToken)

    // pre-conditions
    if (cfg.role != "*") {
      assert(m.slaves.flatMap { x => x.reservedResources.keys }.contains(cfg.role), "Spark role should exist.")
    }

    val expectedUsedCpus = {
      val m = MesosCluster.loadStates(mesosConsoleUrl, authToken)
      val tmp = m.slaves.map { x => x.resources.cpu }.sum
      if (isInClusterMode) {
        tmp - 1 // minus the cpus owned by the Spark Cluster, which is started before the tests in cluster mode
      }
      else tmp
    }

    val mesosUrl = mesosConsoleUrl
    val auth = authToken
    val accum = sc.accumulator[Double](0L, "cpuCounter")
    val partitions = 40 // give it enough tasks

    // use enough tasks to utilize all expected resources for tested role and *, pick a task in the middle and observe resources
    val rdd = sc.makeRDD(1 to 100, partitions).mapPartitionsWithIndex{ (idx, iterator) =>

      var counter = 1
      val retries = 1000
      val partitionNumber = partitions / 2
      val timeToSleep = 10 // small resolution to catch the event of a fully utilized cluster
      var max = 0

      if (idx == partitionNumber) {
        while (counter <= retries) {
          val currentNumOfCpus = MesosCluster.loadStates(mesosUrl, auth).sparkFramework.get.resources.cpu

          if (max <= currentNumOfCpus){ max = currentNumOfCpus.toInt }

          if (currentNumOfCpus != expectedUsedCpus) {
            Thread.sleep(timeToSleep)
            counter += 1
          } else {
            counter = retries + 1 // exit
          }
        }

        if (idx == partitionNumber) {
          accum += max
        }
      }
      iterator
    }

    val res = rdd.sum()
    if(isCoarse) {
      assert(accum.value == expectedUsedCpus, "All cpu resources should be utilized.")
    } else {
      // Fine grained does not fully utilize the cluster so relax the check here. This is enough for the role tests.
      // Fine grained mode is deprecated.
      assert(accum.value >= expectedUsedCpus - 1, "All cpu resources should be utilized.")
    }
    assert(5050 == res, "Result should be correct.")
  }
}
