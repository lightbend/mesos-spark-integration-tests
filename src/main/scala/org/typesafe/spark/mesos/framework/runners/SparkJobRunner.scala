package org.typesafe.spark.mesos.framework.runners

import java.io.{PrintWriter, FileWriter}

import org.apache.spark._
import org.scalatest.events._
import org.scalatest.{FunSuite, Reporter, Args}
import org.typesafe.spark.mesos.tests.client.{ClientFineGrainMode, ClientCoarseGrainMode}
import org.typesafe.spark.mesos.tests.cluster.{ClusterCoarseGrainMode, ClusterFineGrainMode}

import scala.collection.mutable.{Set => MSet}

case class TestResult(testName: String, isSuccess: Boolean, message: Option[String] = None)

case class TestResultCollector(results: List[TestResult] = Nil) {
  def printResult(logFile: String): Unit = {
    //TODO: Change to new Java 8 classes.
    val writer = new PrintWriter(new FileWriter(logFile))
    results.foreach(line => writer.println(line))
    writer.close()

    val doneFile = new PrintWriter(new FileWriter(logFile + ".done"))
    doneFile.close()

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
    val deployMode = args(2)
    val logFile = args(3)

    val sparkConf = new SparkConf()
      .setAppName("Mesos integration test")
      .set("spark.executor.memory", "512mb")

    if(mode == "coarse-grained") {
      sparkConf.set("spark.mesos.coarse", "true")
    }
    if(mode == "fine-grained") {
      sparkConf.set("spark.mesos.coarse", "false")
    }


    val sc = new SparkContext(sparkConf)
    val accumulable: Accumulable[TestResultCollector, TestResult] =
      sc.accumulable[TestResultCollector, TestResult](new TestResultCollector())

    try {
      val testToRun = (deployMode, mode) match {
        case ("cluster", "fine-grained") => new ClusterFineGrainMode(sc, mesosConsoleUrl, accumulable)
        case ("cluster", "coarse-grained") => new ClusterCoarseGrainMode(sc, mesosConsoleUrl, accumulable)
        case ("client", "fine-grained") => new ClientFineGrainMode(sc, mesosConsoleUrl, accumulable)
        case ("client", "coarse-grained") => new ClientCoarseGrainMode(sc, mesosConsoleUrl, accumulable)
      }
      org.scalatest.run(testToRun)
      accumulable.value.printResult(logFile)
    } finally {
      sc.stop()
    }
  }
}
