import sbt.complete.Parsers._

organization := "com.typesafe.spark"
name := "mesos-spark-integration-tests"
version := "0.1.0"
scalaVersion := "2.11.7"

scalacOptions ++= Seq("-feature", "-deprecation", "-language:postfixOps")

// default Spark version
val sparkVersion = "1.6.0"

val sparkHome = SettingKey[Option[String]]("spark-home", "the value of the variable 'spark.home'")

//
// Scala Style setup: run scalastyle after compilation, as part of the `package` task
//

val compileScalastyle = taskKey[Unit]("compileScalastyle")

compileScalastyle := org.scalastyle.sbt.ScalastylePlugin.scalastyle.in(Compile).toTask("").value

compileScalastyle <<= compileScalastyle dependsOn (compile in Compile)

(packageBin in Compile) <<= (packageBin in Compile) dependsOn compileScalastyle

assembly <<= assembly dependsOn compileScalastyle

// trim the assembly even further (20MB in the Scala library)
assemblyOption in assembly := (assemblyOption in assembly).value.copy(includeScala = false)

def assemblyJarPath(sparkHome: String): String = {
  val sparkHomeFile = file(sparkHome)
  val assemblyJarFilter = (sparkHomeFile / "lib") * "spark-assembly-*.jar"
  val jarPath = assemblyJarFilter.getPaths.headOption.getOrElse(sys.error(s"No spark assembly jar found under ${sparkHome}/lib"))
  s"file:///${jarPath}"
}

def addSparkDependencies(sparkHome: Option[String]) = if (sparkHome.isDefined) {
    Seq(
      "org.apache.spark" % "spark-assembly" % "spark-home-version" % "provided" from(assemblyJarPath(sparkHome.get)))
  } else {
   Seq(
    "org.apache.spark" %% "spark-core" % sparkVersion % "provided" withSources(),
    "org.apache.spark" %% "spark-core" % sparkVersion % "provided" withSources(),
    "org.apache.spark" %% "spark-streaming" % sparkVersion % "provided" withSources(),
    "org.apache.spark" %% "spark-sql" % sparkVersion % "provided" withSources())
  }


sparkHome := sys.props.get("spark.home")

libraryDependencies ++= addSparkDependencies(sparkHome.value) ++ Seq(
 "org.apache.hadoop" % "hadoop-client"    % "2.6.1" % "provided" excludeAll(
   ExclusionRule(organization = "commons-beanutils"),
   ExclusionRule(organization = "org.apache.hadoop", name ="hadoop-yarn-api")),
 "org.scalatest"     %% "scalatest"       % "2.2.4",
 "com.typesafe"      %  "config"          % "1.2.1",
 "com.amazonaws"     % "aws-java-sdk"     % "1.0.002",
 "commons-io"        % "commons-io"       % "2.4"
)


// This is a bit of a hack: since Hadoop is a "provided" dependency (scraps 40MB off the assembly jar)
// we need use the compilation classpath when running. The assembly jar (without Hadoop) will run fine
// on the cluster, since the Spark assmebly has it.
runMain in Compile <<= Defaults.runMainTask(fullClasspath in Compile, runner in (Compile, run))

val mit = inputKey[Unit]("Runs spark/mesos integration tests.")
val dcos = inputKey[Unit]("Runs spark/DCOS integration tests.")

mainClass := Some("com.typesafe.spark.test.mesos.framework.runners.MesosIntegrationTestRunner")
val mainDCOSClass = "com.typesafe.spark.test.mesos.framework.runners.DCOSIntegrationTestRunner"

//invoking inputtasks is weird in sbt. TODO simplify this
def runMainInCompile(sparkHome: String,
                     mesosMasterUrl: String,
                     applicationJar: String) = Def.taskDyn {

 val main = s"  ${mainClass.value.get} $sparkHome $mesosMasterUrl $applicationJar"
 (runMain in Compile).toTask(main)
}


//invoking inputtasks is weird in sbt. TODO simplify this
def runMainInCompileDCOS(dcosURL: String,
                         applicationJar: String) = Def.taskDyn {

 val main = s" $mainDCOSClass $dcosURL $applicationJar"
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
 //find the spark-submit shell script
 val sparkHome = args(0)
 val mesosMasterUrl = args(1)

 //depends on assembly task to package the current project
 val output = assembly.value

 //run the main with args
 runMainInCompile(sparkHome, mesosMasterUrl, output.getAbsolutePath)

}.evaluated

dcos := Def.inputTaskDyn {
 val args: Seq[String] = spaceDelimited("<arg>").parsed

 if(args.size != 1) {
  val log = streams.value.log
  log.error("Please provide all the args: <dcos-url>")
  sys.error("failed")
 }

 val dcosURL = args(0)

 //depends on assembly task to package the current project
 val output = assembly.value

 //run the main with args
 runMainInCompileDCOS(dcosURL, output.getAbsolutePath)

}.evaluated

