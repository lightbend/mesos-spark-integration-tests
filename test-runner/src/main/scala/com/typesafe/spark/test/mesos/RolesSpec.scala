package com.typesafe.spark.test.mesos

import org.scalatest.Assertions._

import com.typesafe.spark.test.mesos.mesosstate.MesosCluster
import com.typesafe.spark.test.mesos.framework.runners.RoleConfigInfo


trait RolesSpec {
  self: MesosIntTestHelper =>

  def mesosConsoleUrl: String

  def cfg: RoleConfigInfo

  runSparkTest("simple count in fine-grain mode with role",
    "spark.mesos.coarse" -> "false", "spark.mesos.role" -> cfg.role) { sc =>
    val rdd = sc.makeRDD(1 to 5)
    val res = rdd.sum()

    assert(15 == res)

    val m = MesosCluster.loadStates(mesosConsoleUrl)
    assert(m.sparkFramework.isDefined, "The driver should be running")
    val totalCpus = m.slaves.size * sc.getConf.getOption("spark.mesos.mesosExecutor.cores").getOrElse("1").toInt
    if (cfg.role != "*") {

      assert(m.slaves.flatMap { x => x.roleResources.map { y => y.roleName } }.contains(cfg.role), "spark role should exist")

      // each mesos executor takes one cpu by default, no tasks are running.
      assert(m.sparkFramework.get.resources.cpu == totalCpus, "Total cpus assigned to current spark test framework")
    }
  }

  runSparkTest("simple count in coarse-grained mode with role with max cores",
    "spark.mesos.coarse" -> "true", "spark.mesos.role" -> cfg.role, "spark.cores.max" -> "2") { sc =>
    val rdd = sc.makeRDD(1 to 5)
    val res = rdd.sum()

    assert(15 == res)

    val m = MesosCluster.loadStates(mesosConsoleUrl)
    assert(m.sparkFramework.isDefined, "The driver should be running")

    if (cfg.role != "*") {
      assert(m.slaves.flatMap { x => x.roleResources.map { y => y.roleName } }.contains(cfg.role), "spark role should exist")

      // make sure reserved resources for that role are used
      m.slaves.foreach {
        x =>
          val reserved = x.roleResources.filter(r => r.roleName == cfg.role).head
          assert(reserved.resources.cpu == cfg.roleCpus.toInt, "Reserved resources per slave")
      }

      // should be restricted to spark.cores.max
      assert(m.sparkFramework.get.resources.cpu == 2, "Total cpus assigned to current spark test framework")
    }
  }


  runSparkTest("simple count in coarse-grained mode with role",
    "spark.mesos.coarse" -> "true", "spark.mesos.role" -> cfg.role) { sc =>
    val rdd = sc.makeRDD(1 to 5)
    val res = rdd.sum()

    assert(15 == res)

    val m = MesosCluster.loadStates(mesosConsoleUrl)
    assert(m.sparkFramework.isDefined, "The driver should be running")

    if (cfg.role != "*") {

      assert(m.slaves.flatMap { x => x.roleResources.map { y => y.roleName } }.contains(cfg.role), "spark role should exist")

      // make sure reserved resources for that role are used
      m.slaves.foreach {
        x =>
          val reserved = x.roleResources.filter(r => r.roleName == cfg.role).head
          assert(reserved.resources.cpu == cfg.roleCpus.toInt, "Reserved resources per slave")
      }
      val totalCpus = {
        val tmp = m.slaves.map { x => x.resources.cpu }.sum

        if (this.isInstanceOf[ClusterModeSpec]) {tmp -1} else tmp  // minus the cpus owned by the Spark Cluster
      }

      // greedy, should try to use all available cpus in the cluster
      assert(m.sparkFramework.get.resources.cpu <= totalCpus, "Total cpus assigned to current spark test framework")
    }
  }
}
