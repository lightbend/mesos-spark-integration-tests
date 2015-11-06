################################################################################
#! /bin/bash
# Author: skonto
# date: 21/10/2015
# purpose: Support spark with mesos on docker. Only net mode is supported since
# there is a bug on the mesos side and spark may need patching.
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
NUMBER_OF_SLAVES=1
SPARK_BINARY_PATH=
HADOOP_BINARY_PATH=
SPARK_VERSION=1.5.1
HADOOP_VERSION_FOR_SPARK=2.6
INSTALL_HDFS=
IS_QUIET=
SPARK_FILE=spark-$SPARK_VERSION-bin-hadoop$HADOOP_VERSION_FOR_SPARK.tgz
MESOS_MASTER_CONFIG=
MESOS_SLAVE_CONFIG=
HADOOP_FILE=hadoop-$HADOOP_VERSION_FOR_SPARK.0.tar.gz
RESOURCE_THRESHOLD=1.0

MEM_TH=$RESOURCE_THRESHOLD
CPU_TH=$RESOURCE_THRESHOLD
SHARED_FOLDER="$HOME/temp"

################################ FUNCTIONS #####################################
function clean_up_container {
  echo "Stopping container:$1"
  docker stop $1
  echo "Stopping container:$1..."
  docker rm $1

}

function docker_ip {
  if [[ "$(uname)" == "Darwin" ]]; then
    docker-machine ip default
  else
    /sbin/ifconfig docker0 | awk '/addr:/{ print $2;}' |  sed  's/addr://g'
  fi
}

function print_host_ip {
  if [[ "$(uname)" == "Darwin"  ]]; then
    #Getting the IP address of the host as it seen by docker container
    masterContainerId=$(docker ps -a | grep $MASTER_CONTAINER_NAME | awk '{print $1}')
    hostIpAddr=$(docker exec -it $masterContainerId /bin/sh -c "sudo ip route" | awk '/default/ { print $3 }')

    printMsg "The IP address of the host inside docker $hostIpAddr"
  else
    printMsg "The IP address of the host inside docker $(docker_ip)"
  fi
}

#start master
function start_master {

  dip=$(docker_ip)
  start_master_command="/usr/sbin/mesos-master --ip=$dip $MESOS_MASTER_CONFIG"
  if [[ -n $INSTALL_HDFS ]]; then
    HADOOP_VOLUME="-v $HADOOP_BINARY_PATH:/var/hadoop/$HADOOP_FILE"
  else
    HADOOP_VOLUME=
  fi

  docker run -p 5050:5050 \
  -e "MESOS_EXECUTOR_REGISTRATION_TIMEOUT=5mins" \
  -e "MESOS_ISOLATOR=cgroups/cpu,cgroups/mem" \
  -e "MESOS_PORT=5050" \
  -e "MESOS_LOG_DIR=/var/log" \
  -e "MESOS_REGISTRY=in_memory" \
  -e "MESOS_WORK_DIR=/tmp/mesos" \
  -e "MESOS_CONTAINERIZERS=docker,mesos" \
  -e "DOCKER_IP=$dip" \
  --privileged=true \
  --pid=host \
  --expose=5050 \
  --net=host \
  -d \
  --name $MASTER_CONTAINER_NAME \
  -v "$SCRIPTPATH/hadoop":/var/hadoop \
  -v "$SPARK_BINARY_PATH":/var/spark/$SPARK_FILE  $HADOOP_VOLUME \
  $DOCKER_USER/$MASTER_IMAGE $start_master_command

  if [[ -n $INSTALL_HDFS ]]; then
    docker exec -it $MASTER_CONTAINER_NAME /bin/bash /var/hadoop/hadoop_setup.sh
    docker exec -it $MASTER_CONTAINER_NAME /usr/local/bin/hdfs namenode -format -nonInterActive
    docker exec -it $MASTER_CONTAINER_NAME /usr/local/sbin/hadoop-daemon.sh --script hdfs start namenode
    docker exec -it $MASTER_CONTAINER_NAME /usr/local/sbin/hadoop-daemon.sh --script hdfs start datanode
  fi

}

function get_binaries {

  if [[ -z "${SPARK_BINARY_PATH}" ]]; then
    SPARK_BINARY_PATH=$SCRIPTPATH/binaries/$SPARK_FILE
    if [ ! -f "$SPARK_BINARY_PATH" ]; then
      wget -P $SCRIPTPATH/binaries/ http://d3kbcqa49mib13.cloudfront.net/$SPARK_FILE
    fi
  fi

  if [[ -n $INSTALL_HDFS ]]; then
    if [[ -z "${HADOOP_BINARY_PATH}" ]]; then
      HADOOP_BINARY_PATH=$SCRIPTPATH/binaries/$HADOOP_FILE
      if [ ! -f "$HADOOP_BINARY_PATH" ]; then
        TMP_FILE_PATH="hadoop-$HADOOP_VERSION_FOR_SPARK.0/hadoop-$HADOOP_VERSION_FOR_SPARK.0.tar.gz"
        wget -P $SCRIPTPATH/binaries/ "http://mirror.switch.ch/mirror/apache/dist/hadoop/common/$TMP_FILE_PATH"
      fi
    fi
  fi
}

function calcf {
  awk "BEGIN { print "$*" }"
}

function get_cpus {
  if [[ "$(uname)" == "Darwin" ]]; then
    sysctl -n hw.ncpu
  else
    nproc
  fi
}

function get_mem {

  #in Mbs
  if [[ "$(uname)" == "Darwin"  ]]; then
    m=$(( $(vm_stat | awk '/free/ {gsub(/\./, "", $3); print $3}') * 4096 / 1048576))
    echo "$m"
  else
    m=$(grep MemTotal /proc/meminfo | awk '{print $2}')
    echo "$((m/1000))"
  fi
}

#libapparmor is needed
#https://github.com/RayRutjes/simple-gitlab-runner/pull/1

function start_slaves {

echo $MESOS_SLAVE_CONFIG

  dip=$(docker_ip)
  start_slave_command="/usr/sbin/mesos-slave --master=$dip:5050 $MESOS_SLAVE_CONFIG"
  number_of_ports=3
  for i in `seq 1 $NUMBER_OF_SLAVES`;
  do
    echo "starting slave ...$i"
    cpus=$(calcf $(($(get_cpus)/$NUMBER_OF_SLAVES))*$CPU_TH)
    mem=$(calcf $(($(get_mem)/$NUMBER_OF_SLAVES))*$MEM_TH)

    if [[ -n $INSTALL_HDFS ]]; then
      HADOOP_VOLUME="-v $HADOOP_BINARY_PATH:/var/hadoop/$HADOOP_FILE"
    else
      HADOOP_VOLUME=
    fi

    docker run \
    -e "MESOS_PORT=505$i" \
    -e "MESOS_SWITCH_USER=false" \
    -e "MESOS_RESOURCES=cpus(*):$cpus;mem(*):$mem" \
    -e "MESOS_ISOLATOR=cgroups/cpu,cgroups/mem" \
    -e "MESOS_EXECUTOR_REGISTRATION_TIMEOUT=5mins" \
    -e "MESOS_CONTAINERIZERS=docker,mesos" \
    -e "MESOS_LOG_DIR=/var/log" \
    -e "IT_DFS_DATANODE_ADDRESS_PORT=$((50100 + $(($i -1))*$number_of_ports + 1 ))" \
    -e "IT_DFS_DATANODE_HTTP_ADDRESS_PORT=$((50100 + $(($i -1))*$number_of_ports + 2))" \
    -e "IT_DFS_DATANODE_IPC_ADDRESS_PORT=$((50100 + $(($i -1))*$number_of_ports + 3))" \
    -e "DOCKER_IP=$dip" \
    -d \
    --privileged=true \
    --pid=host \
    --net=host \
    --name "$SLAVE_CONTAINER_NAME"_"$i" -it -v /var/lib/docker:/var/lib/docker -v /sys/fs/cgroup:/sys/fs/cgroup \
    -v "$SPARK_BINARY_PATH":/var/spark/$SPARK_FILE $HADOOP_VOLUME \
    -v  /usr/bin/docker:/usr/bin/docker \
    -v  /usr/local/bin/docker:/usr/local/bin/docker \
    -v /var/run/docker.sock:/var/run/docker.sock \
    -v "$SCRIPTPATH/hadoop":/var/hadoop \
    -v "$SHARED_FOLDER":/app:ro \
    -v /usr/lib/x86_64-linux-gnu/libapparmor.so.1:/usr/lib/x86_64-linux-gnu/libapparmor.so.1:ro \
    $DOCKER_USER/$SLAVE_IMAGE $start_slave_command

    if [[ -n $INSTALL_HDFS ]]; then
      docker exec -it "$SLAVE_CONTAINER_NAME"_"$i" /bin/bash /var/hadoop/hadoop_setup.sh SLAVE
      docker exec -it "$SLAVE_CONTAINER_NAME"_"$i" /usr/local/sbin/hadoop-daemon.sh --script hdfs start datanode
    fi

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
  --mesos-master-config parameters passed to the mesos master (specific only and common with slave).
  --mesos-slave-config parameters passed to the mesos slave(specific only an docmmon with the master).
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
        exitWithMsg '"--number-of-slaves" requires a non-empty option argument.\n'
      fi
      ;;
      --spark-binary-file)       # Takes an option argument, ensuring it has been specified.
      if [ -n "$2" ]; then
        SPARK_BINARY_PATH=$2
        shift 2
        continue
      else
        exitWithMsg '"spark-binary-file" requires a non-empty option argument.\n'
      fi
      ;;
      --image-version)       # Takes an option argument, ensuring it has been specified.
      if [ -n "$2" ]; then
        IMAGE_VERSION=$2
        shift 2
        continue
      else
        exitWithMsg '"--image-version" requires a non-empty option argument.\n'
      fi
      ;;
      --hadoop-binary-file)       # Takes an option argument, ensuring it has been specified.
      if [ -n "$2" ]; then
        HADOOP_BINARY_PATH=$2
        shift 2
        continue
      else
        exitWithMsg '"hadoop-binary-file" requires a non-empty option argument.\n'
      fi
      ;;
      --mem-th)       # Takes an option argument, ensuring it has been specified.
      if [ -n "$2" ]; then
        MEM_TH=$2
        shift 2
        continue
      else
        exitWithMsg '"mem_th" requires a non-empty option argument.\n'
      fi
      ;;
      --cpu-th)       # Takes an option argument, ensuring it has been specified.
      if [ -n "$2" ]; then
        CPU_TH=$2
        shift 2
        continue
      else
        exitWithMsg '"cpu_th" requires a non-empty option argument.\n'
      fi
      ;;
      --with-hdfs)       # Takes an option argument, ensuring it has been specified.
      INSTALL_HDFS=1
      shift 1
      continue
      ;;
      --mesos-master-config)       # Takes an option argument, ensuring it has been specified.
      if [ -n "$2" ]; then
        MESOS_MASTER_CONFIG=$2
        shift 2
        continue
      else
        exitWithMsg '"--mesos-master-config" requires a non-empty option argument.\n'
      fi
      ;;
      --mesos-slave-config)       # Takes an option argument, ensuring it has been specified.
      if [ -n "$2" ]; then
        MESOS_SLAVE_CONFIG=$2
        shift 2
        continue
      else
        exitWithMsg '"--mesos-slave-config" requires a non-empty option argument.\n'
      fi
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

  if [[ -n $HADOOP_BINARY_PATH && -z $INSTALL_HDFS ]]; then
    exitWithMsg "You need to specify the with-hdfs flag, otherwise --hadoop-binary-path is ignored"
  fi
}

function exitWithMsg {
  printf 'ERROR: '"$1"'.\n' >&2
  show_help
  exit 1
}

function printMsg {
  if [[ ! -n "$IS_QUIET" ]]; then
    echo -e "$1\n"
  fi
}

################################## MAIN ####################################

parse_args "$@"

cat $SCRIPTPATH/message.txt

echo -e "\n"

type docker >/dev/null 2>&1 || { echo >&2 "docker binary is required but it's not installed.  Aborting."; exit 1; }

printMsg "Setting folders..."
mkdir -p $SCRIPTPATH/binaries

#clean up containers

printMsg "Stopping and removing master container(s)..."
docker ps -a | grep $MASTER_CONTAINER_NAME | awk '{print $1}' | xargs docker rm -f

printMsg "Stopping and removing slave container(s)..."
docker ps -a | grep $SLAVE_CONTAINER_NAME | awk '{print $1}' | xargs docker rm -f

printMsg "Getting binaries..."
get_binaries

printMsg "Starting master(s)..."
start_master

printMsg "Starting slave(s)..."
start_slaves

printMsg "Mesos cluster started!"

printMsg "Mesos cluster dashboard url http://$(docker_ip):5050"

if [[ -n $INSTALL_HDFS ]]; then
  printMsg "Hdfs cluster started!"
  printMsg "Hdfs cluster dashboard url http://$(docker_ip):50070"
  printMsg "Hdfs usrl http://$(docker_ip):8020"
fi

print_host_ip


exit 0
