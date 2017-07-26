package com.typesafe.spark.test.mesos

import java.util.concurrent.TimeoutException

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits
import scala.concurrent.Future

import org.scalatest.{ Finders, FunSuite, Tag }
import org.scalatest.time.SpanSugar

import org.apache.spark.{ SparkConf, SparkContext }

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
   * @param properties  key-value pairs of Spark configuration
   * @param tags scalatest tags to apply to the test
   * @param t function that contains the testcase
   * @return ()
   */
  def runSparkTest(name: String, properties: => Seq[(String, String)], tags: Seq[Tag] = Nil)(t: (SparkContext) => Unit) {
    import scala.concurrent.ExecutionContext.Implicits._
    import scala.concurrent.duration._

    test(name, tags: _*) {
      val sparkConf = new SparkConf()
        .setAppName(s"$SPARK_FRAMEWORK_PREFIX-$name")
        .set("spark.executor.memory", "512mb")
        .set("spark.app.id", "mit-spark")
      for (
        (key, value) <- properties
      ) {
        sparkConf.set(key, value)
      }

      // this may block forever if the Mesos driver is killed on another thread
      val futureContext = Future { new SparkContext(sparkConf) }

      // fail if we can't connect to the master sufficiently fast
      // TODO: make the timeout configurable
      val sc = try {
        Await.result(futureContext, 30 seconds)
      } catch {
        case _: TimeoutException =>
          fail("Could not obtain a SparkContext in due time, check logs for Mesos errors")
      }

      try {
        t(sc)
      } finally {
        sc.stop()
      }
    }
  }

  def ignoreSparkTest(name: String, properties: => Seq[(String, String)], tags: Seq[Tag] = Nil)(t: (SparkContext) => Unit) {
    ignore(name) {
    }
  }
}
