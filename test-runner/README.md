# Spark Mesos integration tests

The goal of this project to provide an adequate test suite for Spark
and Mesos integration.  Ideally, any developer should be able to
checkout this repository and run the test suite on his development
machine.

## Prerequisite

The Mesos test runner requires you to have a Mesos cluster
running locally.  You can use the scripts in
[../mesos-docker](../mesos-docker) to set one up.

**Note**: For local mode we support several Ubuntu versions, see more here
[../mesos-docker/README.md](../mesos-docker/README.md).

We also support the latest OS X (Mac users).
For other OSs a straightforward solution is to use a vm enabled with Ubuntu.

## Configure

The tests are primarily configured via
`src/main/resources/application.conf`.  There are sample configuration
files provided in that directory.  You can copy them to
`application.conf`, or pass them in via `-Dconfig.file=<file>'

## Run

Invoke the following sbt task by specifying the SPARK_HOME folder and mesos master url

```sh
cd mesos-spark-integration-tests
sbt "mit <spark_home> mesos://<mesos-master-ip>:5050"
```

*We use spark_submit.sh from <SPARK_HOME> to submit jobs. So please
 make sure you have a Spark binary distribution downloaded and
 unzipped*

To run the Multiple cluster mode tests with zookeeper

```sh
cd mesos-spark-integration-tests
sbt "mcm <spark_home> mesos://<mesos-master-ip>:5050"
```
