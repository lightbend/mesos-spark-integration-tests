# Guide Spark Mesos integration tests

The goal of this project to provide adequate test suite to test Spark Mesos integration and verify supported
mesos features are working properly for Spark. Ideally these tests should be run again very Spark pull request before they are merged to master.


##Running tests using Docker

* Start the docker mesos cluster with HDFS
```sh
cd mesos-docker
./run/run.sh --with-hdfs
```

Note: At the end this will generate an **mit-application.conf** file that we can use to 
run the tests.

* Invoke the following sbt task by specifying the SPARK_HOME folder and mesos master url

```sh
cd test-runner
sbt -Dconfig.file=./mit-application.conf "mit <spark_home> mesos://<mesos-master-ip>:5050"
```

*We use spark_submit.sh from <SPARK_HOME> to submit jobs. So please make sure you have spark binary distribution download and unzip*
