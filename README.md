# Spark on Mesos Integration Tests Project

The purpose of this project is to provide integration tests for Apache Spark
on Mesos. It consists of two modules:
- mesos-docker: which creates a dockerized cluster with several big data components.
- test-runner: the actual intergation test suite.

Each project contains detail instructions how to use them separately at each Readme
file.

An example of combining these two modules to test spark on mesos (assuming a machine with 8 cpus,8GB and enough disk space) is:

##Create a cluster

- To start the default mesos cluster with HDFS and slave nodes you simply run

	```sh
	mesos-docker/run/run.sh
	```

- Start a cluster with all the options

	```sh
	#start a cluster with all the configurations
	mesos-docker/run/run.sh --mesos-master-config "--roles=spark_role" --mesos-slave-config "--resources=disk(spark_role):10000;cpus(spark_role):4;mem(spark_role):3000;cpus(*):4;mem(*):3000;disk(*):10000"
	```

- If you want to start a mesos cluster without HDFS

	```sh
	mesos-docker/run/run.sh --no-hdfs
	```


Check the output generated (index.html or console output) for config info to use next eg. mesos master url.

The scripts generate a default `application.conf` file for consumption by the test runner, saved in `test-runner/mit-application.conf`.

##Run test suite


```sh
#run the tests

test-runner/sbt -Dconfig.file="test/runner/mit-application.conf" "mit /home/stavros/workspace/installs/spark-1.5.1-bin-hadoop2.6  mesos://172.17.42.1:5050"
```

Note: If you leave out `-Dconfig.file`, the default configuration file under `src/main/resources` will be picked up.
