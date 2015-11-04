package org.typesafe.spark.mesos.tests

import java.net.InetAddress

import mesostest.mesosstate.MesosCluster
import org.scalatest.FunSuite
import org.scalatest.concurrent.TimeLimitedTests

import scala.collection.mutable.{Set => MSet}

object ClientModeSpec {
  import org.scalatest.time.SpanSugar._

  val TEST_TIMEOUT = 300 seconds
}

class ClientModeSpec(mesosConsoleUrl: String)
  extends FunSuite with TimeLimitedTests with MesosIntTestHelper {

  import ClientModeSpec._

  override val timeLimit = TEST_TIMEOUT

  runSparkTest("simple sum in fine grain mode", "spark.mesos.coarse" -> "false") { sc =>
    val rdd = sc.makeRDD(1 to 5)
    val res = rdd.sum()

    assert(15 == res)
    // check no task running (fine grained)
    val m = MesosCluster.loadStates(mesosConsoleUrl)
    assert(m.sparkFramework.isDefined, "only one framework should be running")
    assert(0 == m.sparkFramework.get.tasks.size, "no task should be running")
  }

  runSparkTest("simple collect in fine grain mode", "spark.mesos.coarse" -> "false") { sc =>
    val rdd = sc.makeRDD(1 to 5)
    val res = rdd.collect()

    assert(5 == res.size)
    // check no task running (fine grained)
    val m = MesosCluster.loadStates(mesosConsoleUrl)
    assert(m.sparkFramework.isDefined, "only one framework should be running")
    assert(0 == m.sparkFramework.get.tasks.size, "no task should be running")
  }

  runSparkTest("simple count in coarse grain mode", "spark.mesos.coarse" -> "true") { sc =>
    val rdd = sc.makeRDD(1 to 5)
    val res = rdd.sum()

    assert(15 == res)

    val m = MesosCluster.loadStates(mesosConsoleUrl)
    assert(m.sparkFramework.isDefined, "only one framework should be running")
    assert(1 == m.sparkFramework.get.tasks.size, "no task should be running")
  }

}

