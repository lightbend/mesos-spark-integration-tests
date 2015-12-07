################################################################################
#! /bin/bash
# Author: skonto
# date: 21/10/2015
# purpose: Support spark with mesos on docker. Only net mode is supported since
# there is a bug on the mesos side and spark may need patching.
################################################################################

set -e

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
NUMBER_OF_SLAVES=2
SPARK_BINARY_PATH=
HADOOP_BINARY_PATH=
SPARK_VERSION=1.5.1
HADOOP_VERSION_FOR_SPARK=2.6
INSTALL_HDFS=1
IS_QUIET=
SPARK_FILE=spark-$SPARK_VERSION-bin-hadoop$HADOOP_VERSION_FOR_SPARK.tgz
MESOS_MASTER_CONFIG=
MESOS_SLAVE_CONFIG=
HADOOP_FILE=hadoop-$HADOOP_VERSION_FOR_SPARK.0.tar.gz
RESOURCE_THRESHOLD=1.0
SLAVES_CONFIG_FILE=

MEM_TH=$RESOURCE_THRESHOLD
CPU_TH=$RESOURCE_THRESHOLD

# Make sure we know the name of the docker machine. Fail fast if we don't
if [[ ("$(uname)" == "Darwin") && (-z $DOCKER_MACHINE_NAME) ]]; then
  echo "Undefined DOCKER_MACHINE_NAME. This variable is usually set for you when running 'docker env <machinename>'."
  exit 1
fi


################################ FUNCTIONS #####################################
function docker_ip {
  if [[ "$(uname)" == "Darwin" ]]; then
    docker-machine ip default
  else
    /sbin/ifconfig docker0 | awk '/addr:/{ print $2;}' |  sed  's/addr://g'
  fi
}

function print_host_ip {
  printMsg "IP address of the docker host machine is $(get_host_ip)"
}

function get_host_ip {
  if [[ "$(uname)" == "Darwin"  ]]; then
    #Getting the IP address of the host as it seen by docker container
    masterContainerId=$(docker ps -a | grep $MASTER_CONTAINER_NAME | awk '{print $1}')
    docker exec -it $masterContainerId /bin/sh -c "sudo ip route" | awk '/default/ { print $3 }'
  else
    docker_ip
  fi
}

function default_mesos_lib {
  if [[ "$(uname)" == "Darwin"  ]]; then
    echo "/usr/local/lib/libmesos.dylib"
  else
    echo "/usr/local/lib/libmesos.so"
  fi
}

function generate_application_conf_file {
  hdfs_url="hdfs://$(docker_ip):8020"
  host_ip="$(get_host_ip)"
  spark_tgz_file="/var/spark/$SPARK_FILE"
  mesos_native_lib="$(default_mesos_lib)"

  source_location="$SCRIPTPATH/../../test-runner/src/main/resources"
  target_location="$SCRIPTPATH/../../test-runner"

  \cp "$source_location/application.conf" "$target_location/mit-application.conf"
  sed -i -- "s@replace_with_mesos_lib@$mesos_native_lib@g" "$target_location/mit-application.conf"
  sed -i -- "s@replace_with_hdfs_uri@$hdfs_url@g" "$target_location/mit-application.conf"
  sed -i -- "s@replace_with_docker_host_ip@$host_ip@g" "$target_location/mit-application.conf"
  sed -i -- "s@replace_with_spark_executor_ui@$spark_tgz_file@g" "$target_location/mit-application.conf"

  #remove any temp file generated (on OS X)
  rm -f "$target_location/mit-application.conf--"

  printMsg "---------------------------"
  printMsg "Generated application.conf file can be found here: $target_location/mit-application.conf"
  printMsg "---------------------------"
}

function check_if_service_is_running {

  COUNTER=0
  while ! nc -z $dip $2; do
    echo -ne "waiting for $1 at port $2...$COUNTER\r"
    sleep 1
    let COUNTER=COUNTER+1
  done
}

function check_if_container_is_up {

  printMsg "Checking if container $1 is up..."
  #wait to avoid temporary running window...
  sleep 1
  if [[ "$(docker inspect -f {{.State.Running}} $1)" = "false" ]]; then
    echo >&2 "$1 container failed to start...  Aborting."; exit 1;
  else
    printMsg "Container $1 is up..."
  fi
}

#start master
function start_master {

  dip=$(docker_ip)
  start_master_command="/usr/sbin/mesos-master --ip=$dip $MESOS_MASTER_CONFIG"
  if [[ -n $INSTALL_HDFS ]]; then
    HADOOP_VOLUME="-v $HADOOP_BINARY_PATH:/var/tmp/$HADOOP_FILE"
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
  -e "IT_DFS_DATANODE_ADDRESS_PORT=50010" \
  -e "USER=root" \
  --privileged=true \
  --pid=host \
  --expose=5050 \
  --net=host \
  -d \
  --name $MASTER_CONTAINER_NAME \
  -v "$SCRIPTPATH/hadoop":/var/hadoop \
  -v "$SPARK_BINARY_PATH":/var/spark/$SPARK_FILE  $HADOOP_VOLUME \
  $DOCKER_USER/$MASTER_IMAGE $start_master_command

  check_if_container_is_up $MASTER_CONTAINER_NAME

  check_if_service_is_running mesos-master 5050

  if [[ -n $INSTALL_HDFS ]]; then
    docker exec $MASTER_CONTAINER_NAME /bin/bash /var/hadoop/hadoop_setup.sh
    docker exec $MASTER_CONTAINER_NAME /usr/local/bin/hdfs namenode -format -nonInterActive
    docker exec $MASTER_CONTAINER_NAME /usr/local/sbin/hadoop-daemon.sh --script hdfs start namenode
    docker exec $MASTER_CONTAINER_NAME /usr/local/sbin/hadoop-daemon.sh --script hdfs start datanode
  fi

}

function get_binaries {

  if [[ -z "${SPARK_BINARY_PATH}" ]]; then
    SPARK_BINARY_PATH=$SCRIPTPATH/binaries/$SPARK_FILE
    if [ ! -f "$SPARK_BINARY_PATH" ]; then
      wget -P $SCRIPTPATH/binaries/ "http://mirror.switch.ch/mirror/apache/dist/spark/spark-$SPARK_VERSION/$SPARK_FILE"
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
    m=`docker-machine inspect $DOCKER_MACHINE_NAME | awk  '/Memory/ {print substr($2, 0, length($2) - 1); exit}'`
    echo "$m"
  else
    m=$(grep MemTotal /proc/meminfo | awk '{print $2}')
    echo "$((m/1000))"
  fi
}


function remove_quotes {
  echo "$1" | tr -d '"'
}

#libapparmor is needed
#https://github.com/RayRutjes/simple-gitlab-runner/pull/1

function start_slaves {

  dip=$(docker_ip)

  number_of_ports=3
  for i in `seq 1 $NUMBER_OF_SLAVES`;
  do
    start_slave_command="/usr/sbin/mesos-slave --master=$dip:5050 --ip=$dip $MESOS_SLAVE_CONFIG"

    echo "starting slave ...$i"
    cpus=$(calcf $(($(get_cpus)/$NUMBER_OF_SLAVES))*$CPU_TH)
    mem=$(calcf $(($(get_mem)/$NUMBER_OF_SLAVES))*$MEM_TH)

    echo "Using $cpus cpus and ${mem}M memory for slaves."

    if [[ -n $INSTALL_HDFS ]]; then
      HADOOP_VOLUME="-v $HADOOP_BINARY_PATH:/var/tmp/$HADOOP_FILE"
    else
      HADOOP_VOLUME=
    fi

    if [[ -n $SLAVES_CONFIG_FILE ]]; then
      resources_cfg=$(get_field_value_from_slave_cfg_at_index resources $SLAVES_CONFIG_FILE $(($i-1)))
      attributes_cfg=$(get_field_value_from_slave_cfg_at_index attributes $SLAVES_CONFIG_FILE $(($i-1)))
    fi

    if [[ -n $resources_cfg ]]; then
      resources_cfg=$(remove_quotes "--resources=$resources_cfg")
    else
      resources_cfg=""
    fi

    if [[ -n $attributes_cfg ]]; then
      attributes_cfg=$(remove_quotes "--attributes=$attributes_cfg")
    else
      attributes_cfg=""
    fi

    start_slave_command="$start_slave_command  $resources_cfg $attributes_cfg"

    docker run \
    -e "MESOS_PORT=$((5050 + $i))" \
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
    -e "USER=root" \
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
    -v /usr/lib/x86_64-linux-gnu/libapparmor.so.1:/usr/lib/x86_64-linux-gnu/libapparmor.so.1:ro \
    $DOCKER_USER/$SLAVE_IMAGE $start_slave_command

    check_if_container_is_up "$SLAVE_CONTAINER_NAME"_"$i"
    check_if_service_is_running mesos-slave $((5050 + $i))

    if [[ -n $INSTALL_HDFS ]]; then
      docker exec "$SLAVE_CONTAINER_NAME"_"$i" /bin/bash /var/hadoop/hadoop_setup.sh SLAVE
      docker exec "$SLAVE_CONTAINER_NAME"_"$i" /usr/local/sbin/hadoop-daemon.sh --script hdfs start datanode
    fi

  done
}

# $1 variable, $2 pattern, $3 file_in $4 file_out
function replace_in_htmlfile_multi {
  if [[ "$(uname)" == "Darwin" ]]; then
    l_var="$1"
  awk -v r="${l_var//$'\n'/\\n}" "{sub(/$2/,r)}1" $3 >  $SCRIPTPATH/tmp_file && mv $SCRIPTPATH/tmp_file $4
  else
    awk -v r="$1" "{gsub(/$2/,r)}1" $3 >  $SCRIPTPATH/tmp_file && mv $SCRIPTPATH/tmp_file $4
  fi
}


function create_html {

TOTAL_NODES=$(($NUMBER_OF_SLAVES + 1 ))
HTML_SNIPPET=
for i in `seq 1 $NUMBER_OF_SLAVES` ; do
HTML_SNIPPET=$HTML_SNIPPET"<div>Slave $i: $(docker_ip):$((5050 + $i))</div>"
done

node_info=$(cat <<EOF

<div class="my_item">Total Number of Nodes: $TOTAL_NODES (1 Master, $NUMBER_OF_SLAVES Slave(s))</div>
<div>Mesos Master: $(docker_ip):5050 </div>
$HTML_SNIPPET
<div style="margin-top:1em">The IP of the docker interface on host: $(docker_ip)</div>
<div class="my_item">$(print_host_ip)</div>

<div class="alert alert-success" role="alert">Your cluster is up and running!</div>
EOF
)

replace_in_htmlfile_multi "$node_info" "REPLACE_NODES" "$SCRIPTPATH/template.html" "$SCRIPTPATH/index.html"

HDFS_SNIPPET_1=
HDFS_SNIPPET_OUT=
if [[ -n $INSTALL_HDFS ]]; then
HDFS_SNIPPET_1="<div class=\"my_item\"><a data-toggle=\"tooltip\" data-placement=\"top\" data-original-title=\"$(docker_ip):50070\" href=\"http://$(docker_ip):50070\">Hadoop UI</a></div>\
<div>HDFS url: hdfs://$(docker_ip):8020</div>"
HDFS_SNIPPET_OUT="<div> <a href=\"#\" id=\"hho_link\"> Hadoop Healthcheck output </a></div> \
<div id=\"hho\" class=\"my_item\"><pre>$(docker exec spm_master hdfs dfsadmin -report)</pre></div>"
fi

MESOS_OUTPUT="$(curl -s http://$(docker_ip):5050/master/state.json | python -m json.tool)"

dash_info=$(cat <<EOF
<div> <a data-toggle="tooltip" data-placement="top" data-original-title="$(docker_ip):5050" href="http://$(docker_ip):5050">Mesos UI</a> </div>
$HDFS_SNIPPET_1
<div>Spark Uri: /var/spark/${SPARK_FILE} </div>
<div class="my_item">Spark master: mesos://$(docker_ip):5050</div>

$HDFS_SNIPPET_OUT
<div> <a href="#" id="mho_link"> Mesos Healthcheck output </a></div>
<div id="mho"><pre>$MESOS_OUTPUT</pre></div>
<div style="margin-top:10px;" class="alert alert-success" role="alert">Your cluster is up and running!</div>
EOF
)
replace_in_htmlfile_multi "$dash_info" "REPLACE_DASHBOARDS" "$SCRIPTPATH/index.html" "$SCRIPTPATH/index.html"

}

function remove_container_by_name_prefix {

  if [[ "$(uname)" == "Darwin" ]]; then
    input="$(docker ps -a | grep $1 | awk '{print $1}')"

    if [[ -n "$input"  ]]; then
      echo "$input" | xargs docker rm -f
    fi
  else
    docker ps -a | grep $1 | awk '{print $1}' | xargs -r docker rm -f
  fi

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
  --no-hdfs to ignore hdfs installation step
  --mem-th the percentage of the host cpus to use for slaves. Default: 0.5.
  --cpu-th the percentage of the host memory to use for slaves. Default: 0.5.
  --mesos-master-config parameters passed to the mesos master (specific only and common with slave).
  --mesos-slave-config parameters passed to the mesos slave(specific only an docmmon with the master).
  --slaves-cfg-file provide a slave configuration file to pass specific attributes per slave
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
      --no-hdfs)       # Takes an option argument, ensuring it has been specified.
      INSTALL_HDFS=
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
      --slaves-cfg-file)       # Takes an option argument, ensuring it has been specified.
      if [ -n "$2" ]; then
        SLAVES_CONFIG_FILE=$2
        shift 2
        continue
      else
        exitWithMsg '"--slaves-cfg-file" requires a non-empty option argument.\n'
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
    exitWithMsg "Don't specify no-hdfs flag, --hadoop-binary-path is only used when hdfs is used which is default"
  fi

  if [[ -n $SLAVES_CONFIG_FILE ]]; then
    if [[ -f $SLAVES_CONFIG_FILE ]]; then
      . cfg.sh
      #update the number of slaves to start here
      NUMBER_OF_SLAVES=$(get_number_of_slaves $SLAVES_CONFIG_FILE)
    else
      exitWithMsg "File $SLAVES_CONFIG_FILE does not exist..."
    fi
  fi

  # Get the filename only, remove full path.Variable substitution is used.
  # Removes the longest prefix that matches the regular expression */.
  # It returns the same value as basename.
  if [[ -n $SPARK_BINARY_PATH ]]; then
   SPARK_FILE=${SPARK_BINARY_PATH##*/}
  fi

  if [[ -n $HADOOP_BINARY_PATH ]]; then
   HADOOP_FILE=${HADOOP_BINARY_PATH##*/}
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
remove_container_by_name_prefix $MASTER_CONTAINER_NAME

printMsg "Stopping and removing slave container(s)..."
remove_container_by_name_prefix $SLAVE_CONTAINER_NAME

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
  printMsg "Hdfs url hdfs://$(docker_ip):8020"
fi

generate_application_conf_file

create_html

exit 0
