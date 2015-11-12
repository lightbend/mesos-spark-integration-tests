# Spark on Mesos Integration Tests Project

The purpose of this project is to provide integration tests for Apache Spark
on Mesos. It consists of two modules:
- mesos-docker: which creates a dockerized cluster with several big data components.
- test-runner: the actual intergation test suite.

Each project contains detail instructions how to use them separately at each Readme
file.

An example of combining these two modules to test spark on mesos (assuming a machine with 8 cpus,8GB and enough disk space) is:

Create a cluster:

```sh
#start a cluster
mesos-docker/run/run.sh --with-hdfs --mesos-master-config "--roles=spark_role" --mesos-slave-config "--resources=disk(role):10000;cpus(role):4;mem(role):3000;cpus(*):4;mem(*):3000;disk(*):10000"
```

Check the output generated (index.html or console output) for config info to use next eg. mesos master url.

Configure you application.conf file under test-runner/src/main/resources accordingly
(an example for Ubuntu):

```sh
#Provide full path to the mesos native library. For ubuntu its filename is libmesos.so, for mac its libmesos.dylib
mit.mesos.native.library.location = "/usr/lib/libmesos.so"

#Used to copy the application jar file for cluster mode tests
mit.hdfs.uri = "hdfs://172.17.42.1:8020"

# a spark role to test
mit.spark.role = "spark_role"

#some constraint to use
mit.spark.attributes = "spark:only"

#number of cpus to use for spark.core.max in tests using a role. Should be at most the reserved cpus for the role.
mit.spark.roleCpus = "2"

#The IP address of the host as seen from docker instance. This ip address is used to connect to runner
#from docker instance.
mit.docker.host.ip = "172.17.42.1"

#path of mesos slaves to find spark executor. This should be set when you create docker based mesos cluster
mit.spark.executor.tgz.location = "/var/spark/spark-1.5.1-bin-hadoop2.6.tgz"

mit.mesos.dispatcher.port = 7088

mit.test.timeout = 5 minutes

mit.test.runner.port = 8888
```

Run the suite:
```sh
#run the tests

test-runner/sbt "mit /home/stavros/workspace/installs/spark-1.5.1-bin-hadoop2.6  mesos://172.17.42.1:5050"
```

Note: configuration entries in application.conf are mandatory.
