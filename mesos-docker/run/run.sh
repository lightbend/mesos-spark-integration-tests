################################################################################
#! /bin/bash
# Author: skonto
# date: 21/10/2015
# purpose: Support spark with mesos on docker. Only net mode is supported since
# there is a bug on the mesos side and spakr may need patching.
################################################################################


################################ VARIABLES #####################################

SCRIPT=`basename ${BASH_SOURCE[0]}`
SCRIPTPATH="$( cd "$(dirname "$0")" ; pwd -P )"

#image tag serves as the version
IMAGE_VERSION=latest

MASTER_CONTAINER_NAME="spm_master"
SLAVE_CONTAINER_NAME="spm_slave"
MASTER_IMAGE="spark_mesos:$IMAGE_VERSION"
SLAVE_IMAGE="spark_mesos:$IMAGE_VERSION"
DOCKER_USER="skonto"
START_COMMAND="/usr/sbin/mesos-master"
START_COMMAND_SLAVE="/usr/sbin/mesos-slave"
NUMBER_OF_SLAVES=1
SPARK_BINARY_PATH=
HADOOP_BINARY_PATH=
SPARK_VERSION=1.5.1
HADOOP_VERSION_FOR_SPARK=2.6
INSTALL_HDFS=
IS_QUIET=
SPARK_FILE=spark-$SPARK_VERSION-bin-hadoop$HADOOP_VERSION_FOR_SPARK.tgz
RESOURCE_THRESHOLD=0.5
MEM_TH=$RESOURCE_THRESHOLD
CPU_TH=$RESOURCE_THRESHOLD

################################ FUNCTIONS #####################################
function clean_up_container {
  echo "Stopping container:$1"
  docker stop $1
  echo "Stopping container:$1..."
  docker rm $1

}

#start master
function start_master {

  #clean previous instances if any

  docker run -p 5050:5050 \
  -e "MESOS_EXECUTOR_REGISTRATION_TIMEOUT=5mins" \
  -e "MESOS_ISOLATOR=cgroups/cpu,cgroups/mem" \
  -e "MESOS_PORT=5050" \
  -e "MESOS_LOG_DIR=/var/log" \
  -e "MESOS_REGISTRY=in_memory" \
  -e "MESOS_WORK_DIR=/tmp/mesos" \
  -e "MESOS_CONTAINERIZERS=docker,mesos" \
  --privileged=true \
  --pid=host \
  --expose=5050 \
  --net=host \
  -d \
  -v "$SPARK_BINARY_PATH":/var/spark/$SPARK_FILE  \
  --name $MASTER_CONTAINER_NAME $DOCKER_USER/$MASTER_IMAGE $START_COMMAND
}

function get_binaries {

  if [[ -z "${SPARK_BINARY_PATH}" ]]; then
    SPARK_BINARY_PATH=$SCRIPTPATH/binaries/$SPARK_FILE
    if [ ! -f "$SCRIPTPATH/binaries/$SPARK_FILE" ]; then
      wget -P $SCRIPTPATH/binaries/ http://d3kbcqa49mib13.cloudfront.net/$SPARK_FILE
    fi
  fi
}

function calcf {
  awk "BEGIN { print "$*" }"
}

function get_cpus {
  nproc
}

function get_mem {

  #in Mbs

  m=$(grep MemTotal /proc/meminfo | awk '{print $2}')

  echo "$((m/1000))"
}

#libapparmor is needed
#https://github.com/RayRutjes/simple-gitlab-runner/pull/1

function start_slaves {

  for i in `seq 1 $NUMBER_OF_SLAVES`;
  do
    echo "starting slave ...$i"
    cpus=$(calcf $(($(get_cpus)/$NUMBER_OF_SLAVES))*$CPU_TH)
    mem=$(calcf $(($(get_mem)/$NUMBER_OF_SLAVES))*$MEM_TH)

    docker run -e "MESOS_MASTER=127.0.1.1:5050" \
    -e "MESOS_PORT=505$i" \
    -e  "MESOS_SWITCH_USER=false" \
    -e  "MESOS_RESOURCES=ports(*):[920$i-920$i,930$i-930$i];cpus(*):$cpus;mem(*):$mem" \
    -e "MESOS_ISOLATOR=cgroups/cpu,cgroups/mem" \
    -e "MESOS_EXECUTOR_REGISTRATION_TIMEOUT=5mins" \
    -e "MESOS_CONTAINERIZERS=docker,mesos" \
    -e "MESOS_LOG_DIR=/var/log" \
    -d \
    --privileged=true \
    --pid=host \
    --net=host \
    --name "$SLAVE_CONTAINER_NAME"_"$i" -it -v /var/lib/docker:/var/lib/docker -v /sys/fs/cgroup:/sys/fs/cgroup \
    -v "$SPARK_BINARY_PATH":/var/spark/$SPARK_FILE \
    -v  /usr/bin/docker:/usr/bin/docker \
    -v /var/run/docker.sock:/var/run/docker.sock \
    -v /usr/lib/x86_64-linux-gnu/libapparmor.so.1:/usr/lib/x86_64-linux-gnu/libapparmor.so.1:ro \
    $DOCKER_USER/$SLAVE_IMAGE $START_COMMAND_SLAVE
  done
}


function show_help {

cat<< EOF
This script creates a mini mesos cluster for testing purposes.
Usage: $SCRIPT [OPTIONS]

eg: ./run.sh --number-of-slaves 3 --image-version 0.0.1

Options:

-h|--help prints this message.
-q|quiet no output is shown to the console regarding execution status.
--number-of-slaves number of slave mesos containers to create (optional, defaults to 1).
--hadoop-binary-file  the hadoop binary file to use in docker configuration (optional, if not present tries to download the image).
--spark-binary-file  the hadoop binary file to use in docker configuration (optional, if not present tries to download the image).
--image-version  the image version to use for the containers (optional, defaults to the latest hardcoded value).
--with-hdfs installs hdfs on the mesos master and slaves
--mem-th the percentage of the host cpus to use for slaves. Default: 0.5.
--cpu-th the percentage of the host memory to use for slaves. Default: 0.5.
EOF
}

function parse_args {

  #parse args
  while :; do
    case $1 in
      -h|--help)   # Call a "show_help" function to display a synopsis, then exit.
      show_help
      exit
      ;;
      -q|--quiet)   # Call a "show_help" function to display a synopsis, then exit.
      IS_QUIET=1
      shift 1
      continue
      ;;
      --number-of-slaves)       # Takes an option argument, ensuring it has been specified.
      if [ -n "$2" ]; then
        NUMBER_OF_SLAVES=$2
        shift 2
        continue
      else
        printf 'ERROR: "--number-of-slaves" requires a non-empty option argument.\n' >&2
        show_help
        exit 1
      fi
      ;;
      --spark-binary-file)       # Takes an option argument, ensuring it has been specified.
      if [ -n "$2" ]; then
        SPARK_BINARY_PATH=$2
        shift 2
        continue
      else
        printf 'ERROR: "spark-binary-file" requires a non-empty option argument.\n' >&2
        show_help
        exit 1
      fi
      ;;
      --image-version)       # Takes an option argument, ensuring it has been specified.
      if [ -n "$2" ]; then
        IMAGE_VERSION=$2
        shift 2
        continue
      else
        printf 'ERROR: "--image-version" requires a non-empty option argument.\n' >&2
        show_help
        exit 1
      fi
      ;;
      --hadoop-binary-file)       # Takes an option argument, ensuring it has been specified.
      if [ -n "$2" ]; then
        HADOOP_BINARY_PATH=$2
        shift 2
        continue
      else
        printf 'ERROR: "hadoop-binary-file" requires a non-empty option argument.\n' >&2
        show_help
        exit 1
      fi
      ;;
      --mem-th)       # Takes an option argument, ensuring it has been specified.
      if [ -n "$2" ]; then
        MEM_TH=$2
        shift 2
        continue
      else
        printf 'ERROR: "mem_th" requires a non-empty option argument.\n' >&2
        show_help
        exit 1
      fi
      ;;
      --cpu-th)       # Takes an option argument, ensuring it has been specified.
      if [ -n "$2" ]; then
        CPU_TH=$2
        shift 2
        continue
      else
        printf 'ERROR: "cpu_th" requires a non-empty option argument.\n' >&2
        show_help
        exit 1
      fi
      ;;
      --with-hdfs)       # Takes an option argument, ensuring it has been specified.
      INSTALL_HDFS=1
      shift 1
      continue
      ;;

      --)              # End of all options.
      shift
      break
      ;;
      -?*)
      printf 'The option is not valid...: %s\n' "$1" >&2
      show_help
      exit 1
      ;;
      *)               # Default case: If no more options then break out of the loop.
      break
    esac
    shift
  done
}

function printMsg {
  if [[ ! -n "$IS_QUIET" ]]; then
    echo -e "$1\n"
  fi
}

################################## MAIN ####################################

parse_args $@

cat $SCRIPTPATH/message.txt

echo -e "\n"

type docker >/dev/null 2>&1 || { echo >&2 "docker binary is required but it's not installed.  Aborting."; exit 1; }

printMsg "Setting folders..."
mkdir -p $SCRIPTPATH/binaries

#clean up containers

printMsg "Stopping and removing master container(s)..."
docker ps -a | grep $MASTER_CONTAINER_NAME | awk '{print $1}' | xargs -i --  bash -c 'docker stop {}; docker rm {}'

printMsg "Stopping and removing slave container(s)..."
docker ps -a | grep $SLAVE_CONTAINER_NAME | awk '{print $1}' | xargs -i --  bash -c 'docker stop {}; docker rm {}'

printMsg "Getting binaries..."
get_binaries

printMsg "Starting master(s)..."
start_master

printMsg "Starting slave(s)..."
start_slaves

exit 0
