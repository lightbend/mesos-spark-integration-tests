package com.typesafe.spark.test.mesos

import com.typesafe.spark.test.mesos.mesosstate.MesosCluster
import org.scalatest.Assertions._

trait SimpleCoarseGrainSpec { self: MesosIntTestHelper =>

  def mesosConsoleUrl: String

  runSparkTest ("simple count in coarse-grained mode", () => List("spark.mesos.coarse" -> "true")) { sc =>
    val rdd = sc.makeRDD(1 to 5)
    val res = rdd.sum()

    assert(15 == res)

    val m = MesosCluster.loadStates(mesosConsoleUrl)
    assert(m.sparkFramework.isDefined, "The driver should be running")

    // In our setup we assume all slaves are utilized for a job.
    // This may not be true in a real setup.
    assert(m.sparkFramework.get.tasks.size > 0, "Tasks should still be running, since it's coarse grain mode")
  }
}
