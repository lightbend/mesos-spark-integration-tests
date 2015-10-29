import sbt.complete.Parsers._

organization := "com.typesafe.spark"
name := "mesos-spark-integration-tests"
version := "0.1.0"
scalaVersion := "2.10.5"

val sparkVersion = "1.5.1"

libraryDependencies ++= Seq(
 "org.apache.spark"  %% "spark-core"      % sparkVersion  % "provided" withSources(),
 "org.apache.spark"  %% "spark-core"      % sparkVersion  % "provided" withSources(),
 "org.apache.spark"  %% "spark-streaming" % sparkVersion  % "provided" withSources(),
 "org.apache.spark"  %% "spark-sql"       % sparkVersion  % "provided" withSources(),
 "org.scalatest"     %% "scalatest"       % "2.2.4",
 "com.typesafe"      %  "config"          % "1.3.0"
)

val mit = inputKey[Unit]("Runs spark/mesos integration tests.")

mainClass := Some("org.typesafe.spark.mesos.test.framework.MesosIntegrationTestRunner")

//invoking inputtasks is weird in sbt. TODO simplify this
def runMainInCompile(sparkSubmitScript: String,
                     sparkExecutorPath: String,
                     mesosMasterUrl: String,
                     applicationJar: String) = Def.taskDyn {

 val main = s"  ${mainClass.value.get} $sparkSubmitScript $sparkExecutorPath $mesosMasterUrl $applicationJar"
 (runMain in Compile).toTask(main)
}

mit := Def.inputTaskDyn {
 val args: Seq[String] = spaceDelimited("<arg>").parsed

 if(args.size != 3) {
  val log = streams.value.log
  log.error("Please provide all the args: <spark-home> <spark-executor-tgz.file> <mesos-master-url>")
  sys.error("failed")
 }
 args foreach println
 //find the spark-submit shell script
 val sparkSubmitScript = s"${args(0)}/bin/spark-submit"
 val sparkExecutorPath = args(1)
 val mesosMasterUrl = args(2)

 //depends on assembly task to package the current project
 val output = assembly.value

 //run the main with args
 runMainInCompile(sparkSubmitScript, sparkExecutorPath, mesosMasterUrl, output.getAbsolutePath)

}.evaluated
