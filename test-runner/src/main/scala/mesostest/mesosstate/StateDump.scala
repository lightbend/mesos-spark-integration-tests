package mesostest.mesosstate

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import java.net.URL
import java.io.InputStreamReader

import org.typesafe.spark.mesos.tests.MesosIntTestHelper

object MesosState extends Enumeration {
  type MesosState = Value
  val TASK_RUNNING, TASK_FINISHED = Value
}
import MesosState._

case class MesosCluster(numberOfSlaves: Int, frameworks: List[MesosFramework], slaves: List[MesosSlave]) {
  def sparkFramework: Option[MesosFramework] =
    frameworks.find(f => f.name.startsWith(MesosIntTestHelper.SPARK_FRAMEWORK_PREFIX))
}


object MesosCluster {
  def apply(c: Config): MesosCluster = {
    import collection.JavaConverters._
    val frameworks: List[MesosFramework] =
      c.getConfigList("frameworks").asScala.map(MesosFramework.apply)(collection.breakOut)
    val slaveCount = c.getConfigList("slaves").size()
    val slaves = c.getConfigList("slaves").asScala.map(MesosSlave.apply).toList

    MesosCluster(slaveCount, frameworks, slaves)
  }

  def apply(url: URL): MesosCluster = {
    apply(ConfigFactory.parseURL(url))
  }

  def loadStates(mesosConsoleUrl: String): MesosCluster = {
    MesosCluster(new URL(s"${mesosConsoleUrl}/state.json"))
  }
}

case class Resources(cpu: Int, disk: Double, mem: Double)
case class ReservedResourcesPerRole(roleName:String, resources: Resources)

case class MesosFramework (frameworkId: String, name: String, tasks: List[MesosTask], resources: Resources)
case class MesosSlave(slaveId:String, resources: Resources, unreservedResources: Resources, usedResources:
Resources, roleResources:List[ReservedResourcesPerRole])

object MesosSlave {

  def apply(c: Config): MesosSlave = {
    val slaveId = c.getString("id")
    import collection.JavaConverters._
    val reserved= c.getObject("reserved_resources").unwrapped().asScala.
      map{x=>
        val res=Resources(c.getInt(s"reserved_resources.${x._1}.cpus"),
          c.getInt(s"reserved_resources.${x._1}.mem"),
          c.getInt(s"reserved_resources.${x._1}.disk"))
        ReservedResourcesPerRole(x._1,res)
      } .toList

    val resources = Resources(
      c.getInt("resources.cpus"),
      c.getInt("resources.disk"),
      c.getInt("resources.mem"))

    val used = Resources(
      c.getInt("used_resources.cpus"),
      c.getInt("resources.disk"),
      c.getInt("resources.mem"))

    val unreserved = Resources(
      c.getInt("unreserved_resources.cpus"),
      c.getInt("resources.disk"),
      c.getInt("resources.mem"))

    MesosSlave(slaveId, resources, unreserved, used, reserved)
  }
}

object MesosFramework {
  def apply(c: Config): MesosFramework = {
    import collection.JavaConverters._
    val tasks: List[MesosTask] = c.getConfigList("tasks").asScala.map{ 
      MesosTask(_)
    }(collection.breakOut)
    val frameworkId = c.getString("id")
    val frameworkName = c.getString("name")
    val resources = Resources(
      c.getInt("resources.cpus"),
      c.getInt("resources.disk"),
      c.getInt("resources.mem"))

    MesosFramework(frameworkId, frameworkName, tasks, resources)
  }
}

case class MesosTask (state: MesosState)

object MesosTask{
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