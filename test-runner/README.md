# Spark Mesos integration tests

The goal of this project to provide an adequate test suite for Spark
and Mesos integration.  Ideally, any developer should be able to
checkout this repository and run the test suite on his development
machine.

## Prerequisite

This test suite supports Mesos and DCOS clusters.
We have two test runners: one for local development (mesos test runner) and one for running the tests on DCOS (DCOS test runner).

The Mesos test runner requires you to have a Mesos cluster
running locally.  You can use the scripts in
[../mesos-docker](../mesos-docker) to set one up.

**Note**: For local mode we support several Ubuntu versions, see more here
[../mesos-docker/README.md](../mesos-docker/README.md).

We also support the latest OS X (Mac users).
For other OSs a straightforward solution is to use a vm enabled with Ubuntu.

The DCOS test runner requires you to have a DCOS cluster running
(either locally or remotely).  It also requires that the Spark package
is installed via `dcos package install spark`.  See install
instructions [here](https://docs.mesosphere.com/services/spark/).  By
default, `dcos package install spark` will install the stable version
of Spark in the DCOS repo.  To configure DCOS to install the version
of Spark under test, see instructions
[here](https://github.com/mesosphere/universe).

## Configure

The tests are primarily configured via
`src/main/resources/application.conf`.  There are sample configuration
files provided in that directory for both the mesos-docker and DCOS
environments. You can copy them to `application.conf`, or pass them in
via `-Dconfig.file=<file>'

## Run

### Mesos cluster

Invoke the following sbt task by specifying the SPARK_HOME folder and mesos master url

```sh
cd mesos-spark-integration-tests
sbt "mit <spark_home> mesos://<mesos-master-ip>:5050"
```

*We use spark_submit.sh from <SPARK_HOME> to submit jobs. So please
 make sure you have a Spark binary distribution downloaded and
 unzipped*

### DCOS cluster

The DCOS test runner uploads the Spark test JAR to S3 so the cluster
can access it.  So the following config vars are required:

```sh
aws.access_key
aws.secret_key
aws.s3.bucket
aws.s3.prefix
```

Command:

```sh
cd mesos-spark-integration-tests
sbt "dcos <dcos_url>"
```
