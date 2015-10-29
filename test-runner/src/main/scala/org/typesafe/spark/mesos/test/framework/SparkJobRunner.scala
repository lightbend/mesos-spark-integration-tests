package org.typesafe.spark.mesos.test.framework

import org.apache.spark._
import org.scalatest.events._
import org.scalatest.{Reporter, Args}
import org.typesafe.spark.mesos.tests.{SimpleTestCoarseGrainMode, SimpleTestFineGrainMode}

import scala.collection.mutable.{Set => MSet}

case class TestResult(testName: String, isSuccess: Boolean, message: String)

case class TestResultCollector(results: List[TestResult] = Nil) {
  def printResult(): Unit = {
    results.foreach(println)
  }

  def addAll(r2: TestResultCollector): TestResultCollector = TestResultCollector(this.results ++ r2.results)

  def add(t: TestResult): TestResultCollector = TestResultCollector(results :+ t)

}

object TestResultCollector {

  implicit object TestResultAccumulatorParam extends AccumulableParam[TestResultCollector, TestResult] {
    override def addAccumulator(r: TestResultCollector, t: TestResult): TestResultCollector = r.add(t)

    override def addInPlace(r1: TestResultCollector, r2: TestResultCollector): TestResultCollector = r1.addAll(r2)

    override def zero(initialValue: TestResultCollector): TestResultCollector = TestResultCollector()
  }
}

object SparkJobRunner {

  def main(args: Array[String]): Unit = {
    val mesosConsoleUrl = args(0)
    val mode = args(1)
    val sparkConf = new SparkConf()
      .setAppName("Mesos integration test")

    if(mode == "coarse-grained") {
      sparkConf.set("spark.mesos.coarse", "true")
    }
    if(mode == "fine-grained") {
      sparkConf.set("spark.mesos.coarse", "false")
    }


    val sc = new SparkContext(sparkConf)
    val accumulable: Accumulable[TestResultCollector, TestResult] = sc.accumulable[TestResultCollector, TestResult](new TestResultCollector())

    try {
      val testToRun = if(mode == "coarse-grained") {
        new SimpleTestCoarseGrainMode(sc, mesosConsoleUrl, accumulable)
      } else new SimpleTestFineGrainMode(sc, mesosConsoleUrl, accumulable)

      val reporter = new MyReporter
      org.scalatest.run(testToRun)
//        test.run(None, Args(reporter))

      accumulable.value.printResult()

    } finally {
      sc.stop()
    }

  }
}


class MyReporter extends Reporter {
  override def apply(event: Event): Unit = {
    event match {
      case t: SuiteStarting =>
        println()
        println(s"Running suite: " + t.suiteName)
        println()
      case t: TestStarting =>
        println()
        println(s"Starting test: " + t.testName)
        println()
      case t: TestSucceeded =>
        println(">>>>>>> SUCCESS " + t)
      case t: TestFailed =>
        println(">>>>>>> FAILED " + t)
      case t: SuiteAborted =>
        sys.error("SuiteAborted " + t)
      case t: TestCanceled =>
        sys.error("TestCancelled " + t)
      case _ =>
    }
  }
}
