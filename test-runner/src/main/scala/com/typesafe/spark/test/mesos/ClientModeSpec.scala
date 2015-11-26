package com.typesafe.spark.test.mesos

import java.net.InetAddress

import com.typesafe.spark.test.mesos.mesosstate.MesosCluster
import org.scalatest.FunSuite
import org.scalatest.concurrent.TimeLimitedTests
import com.typesafe.spark.test.mesos.framework.runners.RoleConfigInfo

import scala.collection.mutable.{Set => MSet}

object ClientModeSpec {

  import org.scalatest.time.SpanSugar._

  val TEST_TIMEOUT = 300 seconds
}

class ClientModeSpec(mesosConsoleUrl: String, cfg: RoleConfigInfo)
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
    assert(1 * m.numberOfSlaves == m.sparkFramework.get.tasks.size, "One task per slave should be running, since its coarse grain mode")
  }

  runSparkTest("simple count in fine grained mode with role", "spark.mesos.coarse" -> "true",
    "spark.mesos.role" -> cfg.role, "spark.cores.max" -> cfg.roleCpus) { sc =>
    val rdd = sc.makeRDD(1 to 5)
    val res = rdd.sum()

    assert(15 == res)

    val m = MesosCluster.loadStates(mesosConsoleUrl)
    assert(m.sparkFramework.isDefined, "spark framework should be running")

    assert(1 == m.sparkFramework.get.tasks.size, "one framework should be running")

    assert(m.slaves.flatMap { x => x.roleResources.map { y => y.roleName } }.contains(cfg.role))

    //make sure reserved resources for that role are used
    m.slaves.foreach {
      x =>
        val reserved = x.roleResources.filter(r => r.roleName == cfg.role).head
        val used = x.usedResources
        assert(reserved.resources.cpu >= used.cpu)
    }

    assert(m.sparkFramework.get.resources.cpu == cfg.roleCpus.toInt)

  }


  runSparkTest("simple count in fine fine grain mode with role", "spark.mesos.coarse" -> "false",
    "spark.mesos.role" -> cfg.role, "spark.cores.max" -> cfg.roleCpus) { sc =>
    val rdd = sc.makeRDD(1 to 5)
    val res = rdd.sum()

    assert(15 == res)

    val m = MesosCluster.loadStates(mesosConsoleUrl)
    assert(m.sparkFramework.isDefined, "spark framework should be running")

    assert(0 == m.sparkFramework.get.tasks.size, "one framework should be running")

    assert(m.slaves.flatMap { x => x.roleResources.map { y => y.roleName } }.contains(cfg.role))

    assert(m.sparkFramework.get.resources.cpu == cfg.roleCpus.toInt)

  }

}
