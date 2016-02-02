################################################################################
#! /bin/bash
# Author: nraychaudhuri
# date: 12/15/2015
# purpose: Run integration tests for Apache Spark on Mesos
################################################################################
################################ VARIABLES #####################################
SCRIPTPATH="$( cd "$(dirname "$0")" ; pwd -P )"
isDockerStarted=false

#specified spark binary file
sparkBinaryFile=$1
ZK_FLAG=$2
MARATHON_FLAG=$3

function startDocker {
  echo "Starting up docker..."
  docker-machine start default
  isDockerStarted=true
}

#start the docker-machine if not running
function startDockerMaybe {
  isStopped=$(docker-machine status default)
  if [[ $isStopped == "Stopped" ]]; then
    startDocker
  fi
}

#stop the docker if started by the script
function stopDockerMaybe {
  if $isDockerStarted; then
    echo "Stopping docker..."
    docker-machine stop default
  fi
}

function docker_ip {
  if [[ "$(uname)" == "Darwin" ]]; then
    docker-machine ip default
  else
    /sbin/ifconfig docker0 | awk '/addr:/{ print $2;}' |  sed  's/addr://g'
  fi
}

function extractHomeFromSparkFile {
  bname=$(basename $sparkBinaryFile)
  echo ${bname%.*} #remove the file extension
}


function runTests {
  #extract the spark binary file. The scripts will be used by the test runner
  #creating a temporary home for the spark files. Will be removed at the end
  tempSparkFolder=$(mktemp -d "$HOME/mit.XXX")
  tar -xvf $sparkBinaryFile -C $tempSparkFolder
  sparkHome=$tempSparkFolder/$(extractHomeFromSparkFile)

  #only start the docker machine for mac
  #TODO: do the same for ubuntu
  if [[ "$(uname)" == "Darwin" ]]; then
    startDockerMaybe
    eval "$(docker-machine env default)"
  fi

  #shutdown any running cluster
  $SCRIPTPATH/mesos-docker/run/cluster_remove.sh

  #stop any zombie dispatcher ?
  kill $(ps -ax | awk '/grep/ {next} /MesosClusterDispatcher/ {print $1}')

  #start the cluster
  $SCRIPTPATH/mesos-docker/run/run.sh --spark-binary-file $sparkBinaryFile --mesos-master-config "--roles=spark_role,*" --mesos-slave-config "--resources=disk(spark_role):10000;cpus(spark_role):1;mem(spark_role):1000;cpus(*):2;mem(*):2000;disk(*):10000" "$ZK_FLAG" "$MARATHON_FLAG"

  echo "Running tests with following properties:"
  echo "spark home = $sparkHome"
  echo "Mesos url = mesos://$(docker_ip):5050"

  #run the tests
  cd $SCRIPTPATH/test-runner
  sbt  -Dspark.home="$sparkHome" -Dconfig.file="./mit-application.conf" "mit $sparkHome mesos://$(docker_ip):5050"

  stopDockerMaybe

  # cleanup the temp spark folder
  rm -rf $tempSparkFolder
}

runTests
