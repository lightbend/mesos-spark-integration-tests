package org.typesafe.spark.mesos.tests

import org.apache.spark.Accumulable
import org.scalatest.exceptions.TestFailedException
import org.typesafe.spark.mesos.framework.runners.{TestResult, TestResultCollector}

trait MesosIntTestHelper {

  def accumulateResult(name: String, accumulable: Accumulable[TestResultCollector, TestResult])(f: => Unit) = {
    try {
      f
      accumulable += TestResult(name, true)
    } catch {
      case e: TestFailedException =>
        accumulable += TestResult(name, false, Some(e.getMessage()))
        throw e
    }
  }
}
