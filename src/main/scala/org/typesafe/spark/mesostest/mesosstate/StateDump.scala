package org.typesafe.spark.mesostest.mesosstate

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import java.net.URL
import java.io.InputStreamReader

object MesosState extends Enumeration {
  type MesosState = Value
  val TASK_RUNNING, TASK_FINISHED = Value
}
import MesosState._

case class MesosCluster(frameworks: List[MesosFramework])

object MesosCluster {
  def apply(c: Config): MesosCluster = {
    import collection.JavaConverters._
    val frameworks: List[MesosFramework] = c.getConfigList("frameworks").asScala.map{ 
      MesosFramework(_)
    }(collection.breakOut)
    MesosCluster(frameworks)
  }
  def apply(url: URL): MesosCluster = {
    apply(ConfigFactory.parseURL(url))
  }
}

case class MesosFramework (tasks: List[MesosTask])

object MesosFramework {
  def apply(c: Config): MesosFramework = {
    import collection.JavaConverters._
    val tasks: List[MesosTask] = c.getConfigList("tasks").asScala.map{ 
      MesosTask(_)
    }(collection.breakOut)
    MesosFramework(tasks)
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