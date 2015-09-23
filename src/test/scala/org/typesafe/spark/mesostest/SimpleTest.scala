package org.typesafe.spark.mesostest

import org.scalatest.FunSuite
import org.apache.spark.SparkContext
import org.apache.spark.SparkConf
import mesosstate._
import java.net.URL

class SimpleTest extends FunSuite {

  import SimpleTest._

  test("simple count, fine grained mode") {
    runSparkTest() { sc =>

      val rdd = sc.makeRDD(1 to 5)
      val res = rdd.sum()

      assert(15 == res)

      // check no task running (fine grained)
      val m = mesosCluster
      assert(1 == m.frameworks.size, "only one framework should be running")
      assert(0 == m.frameworks.head.tasks.size, "no task should be running")
    }
  }

  test("simple count, coarse grained mode") {
    runSparkTest(("spark.mesos.coarse", "true")) { sc =>

      val rdd = sc.makeRDD(1 to 5)
      val res = rdd.sum()

      assert(15 == res)

      // check 3 tasks are running (coarse grained)
      val m = mesosCluster
      assert(1 == m.frameworks.size, "only one framework should be running")
      assert(3 == m.frameworks.head.tasks.size, "no task should be running")
    }
  }

  test("simple count, coarse grained mode, attributes constraints") {
    runSparkTest(("spark.mesos.coarse", "true"),
      ("spark.mesos.constraints", "testAttr:yes")) { sc =>

        val rdd = sc.makeRDD(1 to 5)
        val res = rdd.sum()

        assert(15 == res)

        // check 2 tasks are running (coarse grained, with testAttr:yes)
        val m = mesosCluster
        assert(1 == m.frameworks.size, "only one framework should be running")
        assert(2 == m.frameworks.head.tasks.size, "no task should be running")
      }
  }

}

object SimpleTest {

  def mesosCluster(): MesosCluster = {
    MesosCluster(new URL(s"http://$mesosMasterHost:5050/state.json"))
  }

  val mesosMasterHost = System.getProperty("spark.mesos.master")

  def runSparkTest(ps: (String, String)*)(t: (SparkContext) => Unit) {

    val sparkConf = new SparkConf()
      .setAppName("Mesos integration test")
      .setMaster(s"mesos://$mesosMasterHost:5050")
      .set("spark.executor.uri", System.getProperty("spark.executor.uri"))

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