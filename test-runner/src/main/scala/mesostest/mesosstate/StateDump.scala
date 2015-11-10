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

case class MesosCluster(numberOfSlaves: Int, frameworks: List[MesosFramework]) {
  def sparkFramework: Option[MesosFramework] =
    frameworks.find(f => f.name.startsWith(MesosIntTestHelper.SPARK_FRAMEWORK_PREFIX))
}


object MesosCluster {
  def apply(c: Config): MesosCluster = {
    import collection.JavaConverters._
    val frameworks: List[MesosFramework] =
      c.getConfigList("frameworks").asScala.map(MesosFramework.apply)(collection.breakOut)
    val slaveCount = c.getConfigList("slaves").size()
    MesosCluster(slaveCount, frameworks)
  }

  def apply(url: URL): MesosCluster = {
    apply(ConfigFactory.parseURL(url))
  }

  def loadStates(mesosConsoleUrl: String): MesosCluster = {
    MesosCluster(new URL(s"${mesosConsoleUrl}/state.json"))
  }
}

case class Resources(cpu: Int, disk: Double, mem: Double)
case class MesosFramework (frameworkId: String, name: String, tasks: List[MesosTask], resources: Resources)

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