package org.typesafe.spark.mesos.tests

import java.net.InetAddress

import mesostest.mesosstate.MesosCluster
import org.scalatest.FunSuite
import org.scalatest.concurrent.TimeLimitedTests
import org.typesafe.spark.mesos.framework.runners.{RoleConfigInfo, Utils}

import scala.collection.mutable.{Set => MSet}

class ClusterModeSpec(mesosConsoleUrl: String, cfg:RoleConfigInfo)
  extends FunSuite with TimeLimitedTests with MesosIntTestHelper {

  import MesosIntTestHelper._

  override val timeLimit = TEST_TIMEOUT

  runSparkTest("simple count in coarse grain mode", "spark.mesos.coarse" -> "true") { sc =>
    val rdd = sc.makeRDD(1 to 5)
    val res = rdd.sum()

    assert(15 == res)

    val m = MesosCluster.loadStates(mesosConsoleUrl)
    assert(2 == m.frameworks.size, "should be two. One for dispatcher and another one framework for spark should be running")

    val sparkFramework = m.sparkFramework
    //get number of slaves as each slave will be running a long running Spark task
    assert(1 * m.numberOfSlaves == sparkFramework.get.tasks.size, "One task per slave should be running, since its coarse grain mode")
  }

  runSparkTest("spark.cores.max property should be honored in coarse grain mode",
    "spark.mesos.coarse" -> "true", "spark.cores.max" -> "1") { sc =>
    val rdd = sc.makeRDD(1 to 5)
    val res = rdd.sum()
    val m = MesosCluster.loadStates(mesosConsoleUrl)
    assert(1 >= m.sparkFramework.get.resources.cpu,
      "should honor the spark.cores.max property in coarse grain mode")

  }

//  runSparkTest("Use principal and secret to authenticate framework",
//    "spark.mesos.coarse" -> "false", "spark.mesos.principal" -> "typesafe", "spark.mesos.secret" -> "spark") { sc =>
//    val m = MesosCluster.loadStates(mesosConsoleUrl)
//    assert(m.sparkFramework.isDefined, "framework should be registered with valid principal & secret")
//  }

  runSparkTest("simple count in fine grain mode", "spark.mesos.coarse" -> "false") { sc =>
    val rdd = sc.makeRDD(1 to 5)
    val res = rdd.sum()

    assert(15 == res)
    val m = MesosCluster.loadStates(mesosConsoleUrl)
    assert(2 == m.frameworks.size, "should be two. One for dispatcher and another one framework for spark should be running")
    assert(0 == m.frameworks.head.tasks.size, "no task should be running")
  }

  runSparkTest("simple count in fine fine grain mode with role in cluster mode",  "spark.mesos.coarse" -> "false",
    "spark.mesos.role" -> cfg.role, "spark.cores.max" -> cfg.roleCpus) { sc =>
    val rdd = sc.makeRDD(1 to 5)
    val res = rdd.sum()

    assert(15 == res)

    val m = MesosCluster.loadStates(mesosConsoleUrl)

    assert(2 == m.frameworks.size, "two frameworks should be running")

    assert(m.slaves.flatMap{x=> x.roleResources.map{y=> y.roleName}}.contains(cfg.role))

    assert( m.sparkFramework.get.resources.cpu == cfg.roleCpus.toInt )
  }

  runSparkTest("simple count in fine grained mode with role",  "spark.mesos.coarse" -> "true",
    "spark.mesos.role" -> cfg.role, "spark.cores.max" -> cfg.roleCpus) { sc =>
    val rdd = sc.makeRDD(1 to 5)
    val res = rdd.sum()

    assert(15 == res)

    val m = MesosCluster.loadStates(mesosConsoleUrl)

    assert(2 == m.frameworks.size, "two frameworks should be running")

    assert(m.slaves.flatMap{x=> x.roleResources.map{y=> y.roleName}}.contains(cfg.role))

    //make sure reserved resources for that role are used
    m.slaves.foreach {
      x =>
        val reserved=x.roleResources.filter(r => r.roleName == cfg.role).head
        val used= x.usedResources
        assert(reserved.resources.cpu >= used.cpu)
    }

    assert( m.sparkFramework.get.resources.cpu == cfg.roleCpus.toInt )

  }

}
