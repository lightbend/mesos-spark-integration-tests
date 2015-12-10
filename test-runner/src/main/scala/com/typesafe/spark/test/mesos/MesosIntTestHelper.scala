package com.typesafe.spark.test.mesos

import com.typesafe.spark.test.mesos.mesosstate.MesosCluster
import org.apache.spark.{SparkConf, SparkContext}
import org.scalatest.FunSuite

object MesosIntTestHelper {
  import org.scalatest.time.SpanSugar._

  val SPARK_FRAMEWORK_PREFIX = "mit-spark"
  val TEST_TIMEOUT = 300 seconds
}

trait MesosIntTestHelper { self: FunSuite =>

  import MesosIntTestHelper._
  /**
   * Creates a SparkContext based on the given properties and reports test result to runner
   * @param name name of the job and mesos framework. The SPARK_FRAMEWORK_PREFIX-$name is used as the Spark
   *             application name.
   * @param ps  key-value pairs of Spark configuration
   * @param t function that contains the testcase
   * @return ()
   */
  def runSparkTest(name: String, ps: (String, String)*)(t: (SparkContext) => Unit) {
    test(name) {
      val sparkConf = new SparkConf()
        .setAppName(s"$SPARK_FRAMEWORK_PREFIX-$name")
        .set("spark.executor.memory", "256mb")
        .set("spark.app.id", "mit-spark")
      for (
        (key, value) <- ps
      ) {
        sparkConf.set(key, value)
      }

      val sc = new SparkContext(sparkConf)
      try {
        t(sc)
      } finally {
        sc.stop()
      }
    }
  }
}
