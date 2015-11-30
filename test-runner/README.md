# Spark Mesos integration tests

The goal of this project to provide an adequate test suite for Spark and Mesos integration. These tests should
run against any Mesos cluster, but for convenience this repository comes with a Docker-based set-up. Check `../mesos-docker` for details.
Ideally, any developer should be able to checkout this repository and run the test suite on his development machine. 

## Prerequisite 

This project assumes that you have a Mesos cluster. You can use the scripts in [../mesos-docker](../mesos-docker) to setup one if you don't
have access to an existing cluster.

## Configure the project

There are number of settings that you need to provide for tests to run. Update the [application.conf](src/main/resources/application.conf) with respective values before running the tests. 

> In case you are using the Docker-based scripts, a valid configuration file has been saved for
> your convenience in `./mit-application.conf`. You can either overwrite the existing example in
> `/src/main/resources`, or pass `-Dconfig.file=mit-application.conf` to Sbt.

##Running the tests

Invoke the following sbt task by specifying the SPARK_HOME folder and mesos master url

```sh
cd mesos-spark-integration-tests
sbt "mit <spark_home> mesos://<mesos-master-ip>:5050"
```

*We use spark_submit.sh from <SPARK_HOME> to submit jobs. So please make sure you have a Spark binary distribution downloaded and unzipped*
