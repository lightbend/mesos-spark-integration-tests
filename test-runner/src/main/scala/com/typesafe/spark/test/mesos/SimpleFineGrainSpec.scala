package com.typesafe.spark.test.mesos

import com.typesafe.spark.test.mesos.mesosstate.MesosCluster

trait SimpleFineGrainSpec { self: MesosIntTestHelper =>

  def mesosConsoleUrl: String

  runSparkTest("simple sum in fine grain mode", "spark.mesos.coarse" -> "false") { sc =>
    val rdd = sc.makeRDD(1 to 5)
    val res = rdd.sum()

    assert(15 == res)

    val m = MesosCluster.loadStates(mesosConsoleUrl)
    assert(m.sparkFramework.isDefined, "only one framework should be running")
    // check no task running (fine grained)
    assert(0 == m.sparkFramework.get.tasks.size, "no task should be running")
  }

  runSparkTest("simple collect in fine grain mode", "spark.mesos.coarse" -> "false") { sc =>
    val rdd = sc.makeRDD(1 to 5)
    val res = rdd.collect()

    assert(5 == res.size)

    val m = MesosCluster.loadStates(mesosConsoleUrl)
    assert(m.sparkFramework.isDefined, "only one framework should be running")
    // check no task running (fine grained)
    assert(0 == m.sparkFramework.get.tasks.size, "no task should be running")
  }

}