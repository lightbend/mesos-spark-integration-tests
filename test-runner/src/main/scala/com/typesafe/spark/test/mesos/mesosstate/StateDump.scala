package com.typesafe.spark.test.mesos.mesosstate

import com.typesafe.config.Config
import com.typesafe.config.ConfigObject
import com.typesafe.config.ConfigFactory
import java.net.URL
import java.io.InputStreamReader

import com.typesafe.spark.test.mesos.MesosIntTestHelper

object MesosState extends Enumeration {
  type MesosState = Value
  val TASK_STAGING, TASK_STARTING, TASK_RUNNING, TASK_FINISHED, TASK_FAILED, TASK_KILLED, TASK_LOST, TASK_ERROR = Value
}

import MesosState._

case class MesosCluster(frameworks: List[MesosFramework], slaves: List[MesosSlave]) {

  val numberOfSlaves: Int = slaves.size

  def sparkFramework: Option[MesosFramework] =
    frameworks.find(f => f.active && f.name.startsWith(MesosIntTestHelper.SPARK_FRAMEWORK_PREFIX))
}

object MesosCluster {
  def apply(c: Config): MesosCluster = {
    import collection.JavaConverters._
    val frameworks: List[MesosFramework] =
      c.getConfigList("frameworks").asScala.map(MesosFramework.apply)(collection.breakOut)
    val slaves = c.getConfigList("slaves").asScala.map(MesosSlave.apply).toList

    MesosCluster(frameworks, slaves)
  }

  def apply(url: URL): MesosCluster = {
    apply(ConfigFactory.parseURL(url))
  }

  def loadStates(mesosConsoleUrl: String): MesosCluster = {
    MesosCluster(new URL(s"${mesosConsoleUrl}/state.json"))
  }
}

case class Resources(cpu: Int, disk: Double, mem: Double)
case class ReservedResourcesPerRole(roleName: String, resources: Resources)

case class MesosFramework(frameworkId: String, name: String, tasks: List[MesosTask], resources: Resources, active: Boolean) {
  lazy val nbRunningTasks: Int =
    tasks.filter { _.state == MesosState.TASK_RUNNING }.size
}

object MesosFramework {
  def apply(c: Config): MesosFramework = {
    import collection.JavaConverters._
    val tasks: List[MesosTask] = c.getConfigList("tasks").asScala.map {
      MesosTask(_)
    }(collection.breakOut)
    val active = c.getBoolean("active")
    val frameworkId = c.getString("id")
    val frameworkName = c.getString("name")
    val resources = Resources(
      c.getInt("resources.cpus"),
      c.getInt("resources.disk"),
      c.getInt("resources.mem"))

    MesosFramework(frameworkId, frameworkName, tasks, resources, active)
  }
}

case class MesosSlave(
  slaveId: String,
  resources: Resources,
  unreservedResources: Resources,
  usedResources: Resources,
  roleResources: List[ReservedResourcesPerRole])

object MesosSlave {

  def apply(c: Config): MesosSlave = {
    val slaveId = c.getString("id")
    import collection.JavaConverters._
    val reserved = c.getObject("reserved_resources").asScala.
      map {
        case (role, configObject: ConfigObject) =>
          val config = configObject.toConfig()
          val res = Resources(config.getInt("cpus"),
            config.getInt("mem"),
            config.getInt("disk"))
          ReservedResourcesPerRole(role, res)
      }.toList

    val resources = Resources(
      c.getInt("resources.cpus"),
      c.getInt("resources.disk"),
      c.getInt("resources.mem"))

    val used = Resources(
      c.getInt("used_resources.cpus"),
      c.getInt("used_resources.disk"),
      c.getInt("used_resources.mem"))

    val unreserved = Resources(
      c.getInt("unreserved_resources.cpus"),
      c.getInt("unreserved_resources.disk"),
      c.getInt("unreserved_resources.mem"))

    MesosSlave(slaveId, resources, unreserved, used, reserved)
  }
}

case class MesosTask(state: MesosState)

object MesosTask {
  def apply(c: Config): MesosTask = {
    val state = c.getString("state")
    MesosTask(MesosState.withName(state))
  }
}

object StateDump {

  def main(args: Array[String]): Unit = {
    val url = new URL(s"http://${args(0)}:5050/state.json")

    val cluster = MesosCluster(url)

    println(cluster)
  }

}
