package org.typesafe.spark.mesostest

import org.scalatest.FunSuite
import org.apache.spark.SparkContext
import org.apache.spark.SparkConf
import mesosstate._
import java.net.URL
import org.scalatest.concurrent.TimeLimitedTests
import java.util.concurrent.CountDownLatch

class SimpleTest extends FunSuite with TimeLimitedTests {

  import SimpleTest._

  override val timeLimit = TEST_TIMEOUT

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

  test("simple count, coarse grained mode, attributes constraints, all nodes") {
    runSparkTest(("spark.mesos.coarse", "true"),
      ("spark.mesos.constraints", "testAttrA:yes")) { sc =>

        val rdd = sc.makeRDD(1 to 5)
        val res = rdd.sum()

        assert(15 == res)

        // check 2 tasks are running (coarse grained, with testAttr:yes)
        val m = mesosCluster
        assert(1 == m.frameworks.size, "only one framework should be running")
        assert(3 == m.frameworks.head.tasks.size, "3 tasks should be running")
      }
  }

  test("simple count, coarse grained mode, attributes constraints, 2 nodes") {
    runSparkTest(("spark.mesos.coarse", "true"),
      ("spark.mesos.constraints", "testAttrB:yes")) { sc =>

        val rdd = sc.makeRDD(1 to 5)
        val res = rdd.sum()

        assert(15 == res)

        // check 2 tasks are running (coarse grained, with testAttr:yes)
        val m = mesosCluster
        assert(1 == m.frameworks.size, "only one framework should be running")
        assert(2 == m.frameworks.head.tasks.size, "no task should be running")
      }
  }

  test("simple count, coarse grained mode, attributes constraints, no nodes") {
    runSparkTest(("spark.mesos.coarse", "true"),
      ("spark.mesos.constraints", "testAttrA:no")) { sc =>

        val latch = new CountDownLatch(1)

        new Thread {
          override def run() {
            val rdd = sc.makeRDD(1 to 5)
            val res = rdd.sum()

            latch.countDown()
          }
        }.start()

        println(s"start sleep ${CANCEL_TIMEOUT.millisPart}")
        Thread.sleep(CANCEL_TIMEOUT.millisPart)
        println("end sleep")

        assert(1 == latch.getCount, "computation should not have succeeded, no node should be available due to constraints")

      }
  }

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

object SimpleTest {

  import org.scalatest.time.SpanSugar._

  val CANCEL_TIMEOUT = 25 seconds
  val TEST_TIMEOUT = 30 seconds

}