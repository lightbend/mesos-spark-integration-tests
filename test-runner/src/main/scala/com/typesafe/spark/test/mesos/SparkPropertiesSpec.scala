package com.typesafe.spark.test.mesos

import com.typesafe.spark.test.mesos.mesosstate.MesosCluster

trait SparkPropertiesSpec { self: MesosIntTestHelper =>

  def mesosConsoleUrl: String

  runSparkTest("spark.cores.max property should be honored in coarse grain mode",
    "spark.mesos.coarse" -> "true", "spark.cores.max" -> "1") { sc =>
      val rdd = sc.makeRDD(1 to 5)
      val res = rdd.sum()

      assert(15 == res)

      val m = MesosCluster.loadStates(mesosConsoleUrl)
      assert(m.sparkFramework.isDefined, "test driver framework should be running")
      // TODO: better message. should be specific to the assert, not reuse the general test message
      assert(1 >= m.sparkFramework.get.resources.cpu, "should honor the spark.cores.max property in coarse grain mode")
    }

}