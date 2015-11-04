# Guide Spark on Mesos with Docker

The project sets up a cluster of the following components using docker:
- mesos (supported)
- spark (supported)
- hdfs (supported)
- zookeeper (not supported yet)
- marathon (not supported yet, needs zookeeper)

## Setup your host machine

**_Install the latest docker version_**  then execute (only for ubuntu):

```sh
sudo su
./host_setup_ubuntu.sh
```

The script installs the latest mesos library on the system also libapparmor which
is needed by docker executable due to a [bug](https://github.com/RayRutjes/simple-gitlab-runner/pull/1), and stops mesos services on your local
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
```sh
./run.sh --spark-binary-file /home/stavros/workspace/installs/spark-1.5.1-bin-hadoop2.6.tgz
```
At the end of the script run a message is printed with url of the master for example:

Mesos cluster dashboard url http://172.17.42.1:5050

### HDFS

Using the --with-hdfs flag you can setup a full hdfs system:
```sh
./run.sh --with-hdfs
```
To access the hadoop ui for example: http://172.17.42.1:50070.

Run the following command to see how many datanodes are running:

```sh
docker -it spm_master hdfs dfsadmin -report
```
Each mesos slave gets one datanode and the master has a namenode and a datanode.

Note: The result of the above command will not match the datanodes in the hadoop ui due
to a known [bug](https://issues.apache.org/jira/browse/HDFS-7303) for versions <2.7.
In buggy versions you can only see one datanode. This applies when datanodes are all created in localhost.

### Configuring Master and Slave explicitly

You can easily configure master and slaves with the following options,
--mesos-master-config, --mesos-slave-config.

```sh
./run.sh --mesos-master-config "--roles=role" --mesos-slave-config "--resources=cpus(role):8;mem(role):4000 --attributes=spark:only"
```

There are some restrictions though:
- Configurations for slaves are the same so it is not possible for each slave to have different attributes.
To support this would require orchestration capabilities (like in the ones found kubernetes) and it is not
needed as the goal is to test spark on mesos.
- Some properties are used to set the cluster to work correctly in docker net=host mode, so
changing their values may broken the set up. For example --ip is pre-configured to a specific value
so it is known in advance where to bind the master and this will not be discovered from the manual configuration.
Resources can be configured for the slaves but have in mind that they are the same for all slaves, you cannot allocate different ports per slave for example.
We give next which values are pre-configured through env variables:

```sh
#master:
-e "MESOS_EXECUTOR_REGISTRATION_TIMEOUT=5mins" \
-e "MESOS_ISOLATOR=cgroups/cpu,cgroups/mem" \
-e "MESOS_PORT=5050" \
-e "MESOS_LOG_DIR=/var/log" \
-e "MESOS_REGISTRY=in_memory" \
-e "MESOS_WORK_DIR=/tmp/mesos" \
-e "MESOS_CONTAINERIZERS=docker,mesos" \
```

```sh
-e "MESOS_PORT=505$i" \
-e "MESOS_SWITCH_USER=false" \
-e "MESOS_RESOURCES=cpus(*):$cpus;mem(*):$mem" \
-e "MESOS_ISOLATOR=cgroups/cpu,cgroups/mem" \
-e "MESOS_EXECUTOR_REGISTRATION_TIMEOUT=5mins" \
-e "MESOS_CONTAINERIZERS=docker,mesos" \
-e "MESOS_LOG_DIR=/var/log" \
```
As it is clear mesos master/slave bind ports are auto allocated and there is no need to change.
Resources are overriden if specified at the command line by default.

## Using the cluster

Connecting from your host to the cluster is simple. To connect form spark repl
for example you need (assuming you are under the spark installation dir):
```sh
export SPARK_EXECUTOR_URI=/var/spark/spark-1.5.1-bin-hadoop2.6.tgz
./spark-shell --master mesos://172.17.42.1:5050  
```
If you assign one cpu per slave then you need to set:
--conf spark.mesos.mesosExecutor.cores=0

because mesosExecutor reserves by default 1 cpu and otherwise the job will not
have enough resources.

### Mesos Roles And Attributes with Spark

You can configure roles for slaves and attributes as follows:

```sh
./run.sh --mesos-master-config "--roles=role" --mesos-slave-config "--attributes=spark:only"
```

and connect to the master as follows:

```sh
./spark-shell --master mesos://172.17.42.1:5050 --conf spark.mesos.role=role --conf spark.mesos.constraints="spark:only"
```
