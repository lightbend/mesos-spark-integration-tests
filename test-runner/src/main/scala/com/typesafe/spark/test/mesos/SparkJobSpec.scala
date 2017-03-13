package com.typesafe.spark.test.mesos

import org.scalatest.{Args, FunSuite, Status}
import org.scalatest.concurrent.TimeLimitedTests

import com.typesafe.spark.test.mesos.framework.runners.RoleConfigInfo

class SparkJobSpec
  extends FunSuite with TimeLimitedTests with MesosIntTestHelper
  with SimpleCoarseGrainSpec
  with SparkPropertiesSpec
  with RolesSpec
  with RolesSpecSimple
  with DynamicAllocationSpec {

  var _mesosConsoleUrl: String = _
  var _cfg: RoleConfigInfo = _
  var _isInClusterMode: Boolean = _
  var _authToken: Option[String] = Option.empty

  override def isInClusterMode: Boolean = _isInClusterMode
  override def cfg: RoleConfigInfo = _cfg
  override def mesosConsoleUrl: String = _mesosConsoleUrl
  override def authToken: Option[String] = _authToken

  import MesosIntTestHelper._
  override val timeLimit = TEST_TIMEOUT

  override def run(testName: Option[String], args: Args): Status = {
    val mesosURL = args.configMap.getRequired[String]("mesosUrl")
    val deployMode = args.configMap.getRequired[String]("deployMode")
    val role = args.configMap.getRequired[String]("role")
    val attributes = args.configMap.getRequired[String]("attributes")
    val roleCpus = args.configMap.getRequired[String]("roleCpus")
    val authToken = args.configMap.getOptional[String]("authToken")

    _cfg = RoleConfigInfo(role, attributes, roleCpus)
    _mesosConsoleUrl = mesosURL
    _isInClusterMode = deployMode == "cluster"
    _authToken = authToken

    super.run(testName, args)
  }
}
