################################################################################
#! /bin/bash
# Author: nraychaudhuri
# date: 12/15/2015
# purpose: Run integration tests for Apache Spark on Mesos
################################################################################
################################ VARIABLES #####################################
SCRIPTPATH="$( cd "$(dirname "$0")" ; pwd -P )"
isDockerStarted=false

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


#only start the docker machine for mac
#TODO: do the same for ubuntu
if [[ "$(uname)" == "Darwin" ]]; then
  startDockerMaybe	
  eval "$(docker-machine env default)"
fi	

#shutdown any running cluster
$SCRIPTPATH/mesos-docker/run/cluster_remove.sh

#start the cluster 
$SCRIPTPATH/mesos-docker/run/run.sh --mesos-master-config "--roles=spark_role" --mesos-slave-config "--resources=disk(spark_role):10000;cpus(spark_role):1;mem(spark_role):1000;cpus(*):2;mem(*):2000;disk(*):10000"

#run the tests
cd $SCRIPTPATH/test-runner
sbt -Dconfig.file="./mit-application.conf" "mit $1 $2"

stopDockerMaybe

