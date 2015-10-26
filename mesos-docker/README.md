# Guide Spark on Mesos with Docker

The project sets up a cluster of the following components using docker:
- mesos (supported)
- spark (supported)
- hdfs (not supported yet)
- zookeeper (not supported yet)
- marathon (not supported yet, needs zookeeper)

## Setup your host machine

execute:

```sh
sudo su
./host_setup_ubuntu.sh
```

The script installs the latest mesos library on the system also libapparmor which
is needed by docker executable due to a bug, and stops mesos services on your local
host so that the cluster created later can run in net=host mode.

## Build the images or pull them

To build the docker mesos image just run:

```sh
./build.sh from the build directory.
```

Note: You can always build the image with your own repo:tag name and publish it as well.
Otherwise just run the script to build the
image from the default repo skonto/spark_mesos. Alternatively, you can just pull
the image with docker pull skonto/spark_mesos and skip building.

For the available options run: buildsh.sh -h or build.sh --help.

## Create the cluster
To run and configure your cluster execute run.sh and use the appropriate options.

The most simple run is:

```sh
./run.sh
```

which creates a simple cluster with 1 mesos master and 1 mesos slave
with the latest spark version installed.
Another common usage is:

```sh
./run.sh --number-of-slaves 3
```

It creates 3 slaves and one master.

Resource allocation is done according to a threshold for cpus and memory.
The default is 0.5 of all host resources if you do not specify any parameters.
For example if a cluster has 2 slaves on a host with 8 cpus and 8GB of ram
the 2 slaves will share 4 cpus and 4GB of ram approximately.
We take advantage of the static resource allocation strategy for mesos.
To change the allocation use the flags --mem-th and --cpu-th.
For the available options run: run.sh -h or run/sh --help.
The script by default checks if the latest spark binary is available if not tries
to download it. It is straightforward also to use a pre-existing binary like this:
./run.sh --spark-binary-file /home/stavros/workspace/installs/spark-1.5.1-bin-hadoop2.6.tgz

You can access the master ui [here](http://127.0.0.1:5050).

## Using the cluster

Connecting from your host to the cluster is simple. To connect form spark repl
for example you need (assuming you are under the spark installation dir):
```sh
export SPARK_EXECUTOR_URI=/var/spark/spark-1.5.1-bin-hadoop2.6.tgz
./spark-shell --master mesos://127.0.1.1:5050  
```
If you assign one cpu per slave then you need to set:
--conf spark.mesos.mesosExecutor.cores=0

because mesosExecutor reserves by default 1 cpu and otherwise the job will not
have enough resources.
