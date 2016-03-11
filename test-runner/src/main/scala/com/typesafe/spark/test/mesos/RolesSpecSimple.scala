package com.typesafe.spark.test.mesos

import org.apache.spark.SparkContext
import org.scalatest.Assertions._

import com.typesafe.spark.test.mesos.mesosstate.MesosCluster
import com.typesafe.spark.test.mesos.framework.runners.RoleConfigInfo
import org.scalatest.Tag

trait RolesSpecSimple extends RoleSpecSimpleHelper {
  self: MesosIntTestHelper =>

  def mesosConsoleUrl: String

  def cfg: RoleConfigInfo

  def isInClusterMode : Boolean = false

  runSparkTest("simple count in fine-grained mode with role - simple",
    List("spark.mesos.coarse" -> "false", "spark.mesos.role" -> cfg.role)) { sc =>
    testRoleSimple(sc, false)

  }

  runSparkTest("simple count in coarse-grained mode with role - simple",
    List("spark.mesos.coarse" -> "true", "spark.mesos.role" -> cfg.role)) { sc =>
    testRoleSimple(sc, true)
  }
}

trait RoleSpecSimpleHelper {
  self: MesosIntTestHelper with RolesSpecSimple =>

  def testRoleSimple(sc: SparkContext, isCoarse: Boolean ) : Unit = {

    val m = MesosCluster.loadStates(mesosConsoleUrl)

    // pre-conditions
    if (cfg.role != "*") {
      assert(m.slaves.flatMap { x => x.reservedResources.keys }.contains(cfg.role), "Spark role should exist.")
    }

    assert(m.sparkFramework.get.role == cfg.role)

    val rdd = sc.makeRDD(1 to 5)
    val res = rdd.sum()
    assert(res == 15)
  }
}
