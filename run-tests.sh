################################################################################
#! /bin/bash
# Author: nraychaudhuri
# date: 12/15/2015
# purpose: Run integration tests for Apache Spark on Mesos
################################################################################

set -e

################################ VARIABLES #####################################

SCRIPTPATH="$(cd "$(dirname "$0")" ; pwd -P)"
isDockerStarted=false
SKIP_CLUSTER=
EXTRA_CLUSTER_CONFIG=
SparkBinaryFile=

################################ FUNCTIONS #####################################

#
# Starts docker in OS X
#
function startDocker {
  echo "Starting up docker..."
  docker-machine start default
  isDockerStarted=true
}

#
# Start the docker-machine if not running in OS X
#
function startDockerMaybe {
  isStopped=$(docker-machine status default)
  if [[ $isStopped == "Stopped" ]]; then
    startDocker
  fi
}

#
# Stop the docker if started by the script in OS X
#
function stopDockerMaybe {
  if $isDockerStarted; then
    echo "Stopping docker..."
    docker-machine stop default
  fi
}

#
# Retrieves the docker ip
#
function docker_ip {
  if [[ "$(uname)" == "Darwin" ]]; then
    docker-machine ip default
  else
    /sbin/ifconfig docker0 | awk '/addr:/{ print $2;}' |  sed  's/addr://g'
  fi
}

#
# Get spark home from the binary
#
function extractHomeFromSparkFile {
  bname=$(basename $SparkBinaryFile)
  echo ${bname%.*} #remove the file extension
}

function exitWithMsg {
  printf 'ERROR: '"$1"'.\n' >&2
  show_help
  exit 1
}

function show_help {
  cat<< EOF
  This script runs spark on mesos integration tests with a default cluster.
  Usage: $SCRIPT -d|--distro path/spark-x.x.x-xxxxx.tgz [OPTIONS]

  eg: ./run-tests.sh -d /home/user/spark/spark-2.0.0-SNAPSHOT-bin-test.tgz

  Options:

  -h|--help prints this message.
  -d|--distro eg. /home/user/spark/spark-2.0.0-SNAPSHOT-bin-test.tgz
  --skip-cluster does not create the cluster only run tests eg. a cluster already exists
   Useful when you are doing developement for the test suite.
  --extra-cluster-config pass arguments directly to run.sh eg. "--with-zk --with-history-server"
EOF
}

function parse_args {
  #parse args - fail fast
  if [[ $# -eq 0 ]]; then
    exitWithMsg  "Spark distribution binary file is missing..."
  fi

  while :; do
    case "$1" in
      -d|--distro) # Takes an option argument, ensuring it has been specified.
      if [ -n "$2" ]; then
        SparkBinaryFile="$2"
        shift 2
        continue
      else
        exitWithMsg '"distro" requires a non-empty option argument.\n'
      fi
      ;;
      --extra-cluster-config) # Takes an option argument, ensuring it has been specified.
      if [ -n "$2" ]; then
        EXTRA_CLUSTER_CONFIG="$2"
        shift 2
        continue
      else
        exitWithMsg '"extra-cluster-config" requires a non-empty option argument.\n'
      fi
      ;;
      -h|--help) # Call a "show_help" function to display a synopsis, then exit.
      show_help
      exit
      ;;
      --skip-cluster)
      SKIP_CLUSTER="TRUE"
      shift 1
      continue
      ;;
      --)              # End of all options.
      shift
      break
      ;;
      -*)
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

#
# Main for running the tests
#
function runTests {
  parse_args "$@"
  if [[ ! -n $SparkBinaryFile ]]; then
    exitWithMsg "Distro spark binary file is missing..."
  fi
  #extract the spark binary file. The scripts will be used by the test runner
  #creating a temporary home for the spark files. Will be removed at the end
  tempSparkFolder=$(mktemp -d "$HOME/mit.XXX")
  tar -xf $SparkBinaryFile -C $tempSparkFolder
  sparkHome=$tempSparkFolder/$(extractHomeFromSparkFile)

  if [[ ! -n $SKIP_CLUSTER ]]; then
    #only start the docker machine for mac
    if [[ "$(uname)" == "Darwin" ]]; then
      startDockerMaybe
      eval "$(docker-machine env default)"
    fi
  fi

  #stop any zombie dispatcher
  $(ps -ax | awk '/grep/ {next} /MesosClusterDispatcher/ {print $1}') \
  | xargs -r kill

  if [[ ! -n $SKIP_CLUSTER ]]; then
    #shutdown any running cluster
    $SCRIPTPATH/mesos-docker/run/cluster_remove.sh
    SP_ROLE_RS="disk(spark_role):10000;cpus(spark_role):1;mem(spark_role):1000"
    DEF_ROLE_RS="cpus(*):2;mem(*):2000;disk(*):10000"
    #start the cluster - pass all arguments but the first to the script
    $SCRIPTPATH/mesos-docker/run/run.sh --spark-binary-file $SparkBinaryFile \
    --mesos-master-config "--roles=spark_role,*" \
    --mesos-slave-config "--resources=$SP_ROLE_RS;$DEF_ROLE_RS" $EXTRA_CLUSTER_CONFIG
  fi

  echo "spark home = $sparkHome"
  echo "Running tests with following properties:"
  echo "Mesos url = mesos://$(docker_ip):5050"

  #run the tests
  cd $SCRIPTPATH/test-runner
  sbt -Dspark.home="$sparkHome" -Dconfig.file="./mit-application.conf" \
  "mit $sparkHome mesos://$(docker_ip):5050"

  if [[ ! -n $SKIP_CLUSTER ]]; then
    stopDockerMaybe
  fi
  # cleanup the temp spark folder
  rm -rf $tempSparkFolder
}

runTests "$@"
