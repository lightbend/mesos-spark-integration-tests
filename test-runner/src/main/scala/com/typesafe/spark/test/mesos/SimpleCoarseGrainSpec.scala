package com.typesafe.spark.test.mesos

import com.typesafe.spark.test.mesos.mesosstate.MesosCluster
import org.scalatest.Assertions._

trait SimpleCoarseGrainSpec { self: MesosIntTestHelper =>

  def mesosConsoleUrl: String

  ignoreSparkTest ("simple count in coarse-grained mode", "spark.mesos.coarse" -> "true") { sc =>
    val rdd = sc.makeRDD(1 to 5)
    val res = rdd.sum()

    assert(15 == res)

    val m = MesosCluster.loadStates(mesosConsoleUrl)
    assert(m.sparkFramework.isDefined, "The driver should be running")

    // TODO: Review this assertion.  We generally don't have any guarantees over which nodes run the spark job.
    // get number of slaves as each slave will be running a long running Spark task
    assert(m.slaves.size == m.sparkFramework.get.tasks.size, "One task per slave should be running, since it's coarse grain mode")
  }
}
