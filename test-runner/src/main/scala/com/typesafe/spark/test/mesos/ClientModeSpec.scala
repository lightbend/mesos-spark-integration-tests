package com.typesafe.spark.test.mesos

import org.scalatest.FunSuite
import org.scalatest.concurrent.TimeLimitedTests

import com.typesafe.spark.test.mesos.framework.runners.RoleConfigInfo

import MesosIntTestHelper.TEST_TIMEOUT

class ClientModeSpec(
  override val mesosConsoleUrl: String,
  override val cfg: RoleConfigInfo)
  extends FunSuite with TimeLimitedTests with MesosIntTestHelper
  with SimpleFineGrainSpec
  with SimpleCoarseGrainSpec
  with SparkPropertiesSpec
  with RolesSpec {

  import MesosIntTestHelper._

  override val timeLimit = TEST_TIMEOUT
}
