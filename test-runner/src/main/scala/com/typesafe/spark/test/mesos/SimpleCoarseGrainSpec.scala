package com.typesafe.spark.test.mesos

import com.typesafe.spark.test.mesos.mesosstate.MesosCluster

trait SimpleCoarseGrainSpec { self: MesosIntTestHelper =>

  def mesosConsoleUrl: String

  runSparkTest("simple count in coarse grain mode", "spark.mesos.coarse" -> "true") { sc =>
    val rdd = sc.makeRDD(1 to 5)
    val res = rdd.sum()

    assert(15 == res)

    val m = MesosCluster.loadStates(mesosConsoleUrl)
    assert(m.sparkFramework.isDefined, "test driver framework should be running")
    //get number of slaves as each slave will be running a long running Spark task
    assert(1 * m.numberOfSlaves == m.sparkFramework.get.tasks.size, "One task per slave should be running, since its coarse grain mode")
  }

}