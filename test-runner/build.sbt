import sbt.complete.Parsers._

organization := "com.typesafe.spark"
name := "mesos-spark-integration-tests"
version := "0.1.0"
scalaVersion := "2.11.7"

scalacOptions ++= Seq("-feature", "-deprecation", "-language:postfixOps")

// default Spark version
val sparkVersion = "2.0.0"

val sparkHome = SettingKey[Option[String]]("spark-home", "the value of the variable 'spark.home'")

//
// Scala Style setup: run scalastyle after compilation, as part of the `package` task
//
fork in Compile := true

outputStrategy := Some(StdoutOutput)

val compileScalastyle = taskKey[Unit]("compileScalastyle")

compileScalastyle := org.scalastyle.sbt.ScalastylePlugin.scalastyle.in(Compile).toTask("").value

compileScalastyle <<= compileScalastyle dependsOn (compile in Compile)

(packageBin in Compile) <<= (packageBin in Compile) dependsOn compileScalastyle

assembly <<= assembly dependsOn compileScalastyle

// trim the assembly even further (20MB in the Scala library)
assemblyOption in assembly := (assemblyOption in assembly).value.copy(includeScala = false)

assemblyExcludedJars in assembly := {
 if (sparkHome.value.isDefined) {
  (file(sparkHome.value.get + "/jars/") * "*.jar").classpath.toList
 } else {
  List.empty
 }
}

unmanagedJars in Compile ++= {
 if (sparkHome.value.isDefined) {
  (file(sparkHome.value.get + "/jars/") * "*.jar").classpath
 } else {
  (file("") * "").classpath // dummy
 }
}

def addSparkDependencies(sparkHome: Option[String]): Seq[ModuleID] = if (sparkHome.isDefined) {
 Seq()
} else {
 Seq(
  "org.apache.spark" %% "spark-core" % sparkVersion % "provided",
  "org.apache.spark" %% "spark-streaming" % sparkVersion % "provided",
  "org.apache.spark" %% "spark-sql" % sparkVersion % "provided" ).map(_.withSources())
}

sparkHome := sys.props.get("spark.home")

libraryDependencies ++= addSparkDependencies(sparkHome.value) ++ Seq(
 "org.apache.hadoop"     % "hadoop-client"   % "2.6.1" % "provided" excludeAll(
   ExclusionRule(organization = "commons-beanutils"),
   ExclusionRule(organization = "org.apache.hadoop", name = "hadoop-yarn-api")),
 "org.scalatest"        %% "scalatest"       % "2.2.4",
 "com.typesafe"          % "config"          % "1.2.1",
 "com.amazonaws"         % "aws-java-sdk"    % "1.0.002",
 "commons-io"            % "commons-io"      % "2.4",
 "org.apache.zookeeper"  % "zookeeper"       % "3.4.7",
 "com.databricks" % "spark-csv_2.11" % "1.4.0",
 "org.scalaj" %% "scalaj-http" % "2.3.0"
)

// This is a bit of a hack: since Hadoop is a "provided" dependency (scraps 40MB off the assembly jar)
// we need use the compilation classpath when running. The assembly jar (without Hadoop) will run fine
// on the cluster, since the Spark assmebly has it.
runMain in Compile <<= Defaults.runMainTask(fullClasspath in Compile, runner in (Compile, run))

val mit = inputKey[Unit]("Runs spark/mesos integration tests.")

mainClass := Some("com.typesafe.spark.test.mesos.framework.runners.MesosIntegrationTestRunner")

// invoking inputtasks is weird in sbt. TODO simplify this
def runMainInCompile(sparkHome: String,
  mesosMasterUrl: String,
  applicationJar: String): Def.Initialize[Task[Unit]] = Def.taskDyn {

 val main = s"  ${mainClass.value.get} $sparkHome $mesosMasterUrl $applicationJar"
 (runMain in Compile).toTask(main)
}

mit := Def.inputTaskDyn {
 val args: Seq[String] = spaceDelimited("<arg>").parsed

 if(args.size != 2) {
  val log = streams.value.log
  log.error("Please provide all the args: <spark-home> <mesos-master-url>")
  sys.error("failed")
 }
 args foreach println
 // find the spark-submit shell script
 val sparkHome = args.head
 val mesosMasterUrl = args(1)

 // depends on assembly task to package the current project
 val output = assembly.value

 // run the main with args
 runMainInCompile(sparkHome, mesosMasterUrl, output.getAbsolutePath)

}.evaluated
