package com.typesafe.spark.test.mesos.framework.runners

import Utils._
import sys.process._
import com.typesafe.config.Config

object DCOSUtils {

  def waitForSparkJobDCOS(submissionId : String)(implicit config: Config) = {
    var completed = false
    while (!completed) {
      val stdout = "dcos task --completed" !!

      if (stdout.contains(submissionId)) {
        completed = true
      } else {
        Thread.sleep(5000)
      }
    }
  }

  def getLogOutputDCOS(taskId : String) = {
    val cmd = s"dcos task log --completed --lines=1000 ${taskId}"
    printMsg(s"Running cmd: ${cmd}")
    cmd !!
  }

  def submitSparkJobDCOS(jarURI : String)(implicit config: Config): String = {
    val sparkJobRunnerArgs = Seq[String]("http://leader.mesos:5050",
      "cluster",
      "\"" ++ config.getString("spark.role") ++ "\"",
      config.getString("spark.attributes"),
      config.getString("spark.roleCpus")).mkString(" ")

    val submitArgs = s"--class com.typesafe.spark.test.mesos.framework.runners.SparkJobRunner " ++
      s"${jarURI} " ++ sparkJobRunnerArgs

    val cmd: Seq[String] = Seq("dcos", "spark", "run", s"--submit-args=${submitArgs}")

    printMsg(s"Running command: ${cmd.mkString(" ")}")
    val proc = Process(cmd, None)
    val output = proc !!

    printMsg(output)

    val idRegex = """Submission id: (\S+)""".r
    val submissionId = idRegex.findFirstMatchIn(output).get.group(1)
    printMsg(s"Command completed.  Submission ID: ${submissionId}")

    submissionId
  }
}
