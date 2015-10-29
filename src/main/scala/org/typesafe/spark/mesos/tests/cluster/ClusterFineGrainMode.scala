package org.typesafe.spark.mesos.tests.cluster

import java.net.URL

import mesostest.mesosstate.MesosCluster
import org.apache.spark.{Accumulable, SparkContext}
import org.scalatest.FunSuite
import org.scalatest.concurrent.TimeLimitedTests
import org.typesafe.spark.mesos.framework.runners.{TestResult, TestResultCollector}
import org.typesafe.spark.mesos.tests.MesosIntTestHelper

import scala.collection.mutable.{Set => MSet}

object ClusterFineGrainMode {
   import org.scalatest.time.SpanSugar._
 
   val TEST_TIMEOUT = 300 seconds
 }

class ClusterFineGrainMode(sc: SparkContext,
                               mesosConsoleUrl: String,
                               accumulable: Accumulable[TestResultCollector, TestResult])
  extends FunSuite with TimeLimitedTests with MesosIntTestHelper {
   
     import ClusterFineGrainMode._
   
     override val timeLimit = TEST_TIMEOUT
   
     test("simple count in fine grain mode") {
       accumulateResult("ClusterFineGrainMode", accumulable) {
         val rdd = sc.makeRDD(1 to 5)
         val res = rdd.sum()
   
         assert(15 == res)
         // check no task running (fine grained)
         val m = MesosCluster.loadStates(mesosConsoleUrl)
         assert(2 == m.frameworks.size, "should be two. One for dispatcher and another one framework for spark should be running")
         assert(0 == m.frameworks.head.tasks.size, "no task should be running")
       }
     }
   
   }

