[![Build Status](https://travis-ci.org/typesafehub/mesos-spark-integration-tests.svg?branch=master)](https://travis-ci.org/typesafehub/mesos-spark-integration-tests)

# Spark on Mesos Integration Tests Project

The purpose of this project is to provide integration tests for Apache Spark
on Mesos. It consists of two modules:
- mesos-docker: which creates a dockerized cluster with several big data components.
- test-runner: the actual intergation test suite.

Each project contains detail instructions how to use them separately at each Readme
file.

## 1. To run the tests with default configuration

```sh
./run-tests.sh --distro <path to spark.tgz file>  
```
You can also use extra parameters passed directly to run.sh script which creates the cluster (see Create a Cluster section bellow).
eg. use ./run-tests.sh --distro <path to spark.tgz file>  --extra-cluster-config "--with-zk --with-marathon",
--with-zk and--with-marathon flags get zk and marathon up and running.

## 2. To run the tests with custom configuration  

An example of combining these two modules to test spark on mesos (assuming a machine with 8 cpus,8GB and enough disk space) is:

### Create a cluster

*Important*: Please check sub-project [mesos-docker](mesos-docker/README.md) for supported OSs.

 We also support DCOS see [test-runner](test-runner/README.md) sub-project for more.

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

```sh
### start a cluster
mesos-docker/run/run.sh --mesos-master-config "--roles=spark_role" --mesos-slave-config "--resources=disk(spark_role):10000;cpus(spark_role):4;mem(spark_role):3000;cpus(*):4;mem(*):3000;disk(*):10000"
```
Alternatively provide start a cluster with a slaves config file:
```sh
mesos-docker/run/run.sh --mesos-master-config "--roles=spark_role" --slaves-cfg-file slaves-config.json.template
```


Check the output generated (index.html or console output) for config info to use next eg. mesos master url.

The scripts generate a default `application.conf` file for consumption by the test runner, saved in `test-runner/mit-application.conf`.

### Run test suite


```sh
###run the tests

test-runner/sbt -Dconfig.file="test/runner/mit-application.conf" "mit /home/stavros/workspace/installs/spark-1.5.1-bin-hadoop2.6  mesos://172.17.42.1:5050"
```

Note: If you leave out `-Dconfig.file`, the default configuration file under `src/main/resources` will be picked up.


## 3. To define your own tests

The simplest way to add a new test is to include it in one of the existing test suites. All tests are defined in `test-runner/` as [ScalaTest](http://www.scalatest.org/) *Spec* suites.

### Add a test to an existing suite

Locate the right place to add your test. Note that currently there are two *root* suites: `ClientModeSpec` and `ClusterModeSpec`, each one mixing-in other traits with actual tests. The right place would be inside one of the traits that are mixed-in.

For example one could just pick SimpleCoarseGrainSpec and add a test case inside that spec trait:
```scala
trait SimpleCoarseGrainSpec { self: MesosIntTestHelper =>
  ...
  runSparkTest ("my test name, "spark.mesos.coarse" -> "true") { sc =>
   // TODO define test code here
  }
}
```
Then this test case will run along with the other test cases.

### Advanced use-cases

In order to implement more involved testing scenarios you first need to understand the core design pattern of the test-runner project.

The sbt tool manages the initial task run for the two different available running
modes for the suite itself: the local mode and the DCOS mode. We will focus on the local mode, which is the more common scenario and does not require a full blown cluster. In local mode all tests are run inside a Docker-based Mesos cluster (for more info on how to create one, check the [documentation inside mesos-docker](/mesos-docker/README.md)).

For local mode there is an sbt task named `mit` used to run all tests.
When this sbt task runs, it creates a `MesosIntegrationTestRunner` instance
which in turn then calls different runners for different deploy modes for the spark
itself (like cluster or client). Then these runners run individual specs like
`ClientModeSpec` which contain the actual test code (other specific scala test
based specs) and which finally are submitted to a mesos cluster via a `SparkJobRunner` instance.

According to the nature of the tests a user may wish to add, he may need to define his own Spec class, or he could go all the way by defining his own sbt task and his own runners following the design pattern described above.
