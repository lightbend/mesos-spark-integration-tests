#! /bin/bash
# Author: skonto
# date: 12/11/2015
# purpose: stop and remove a running cluster
################################################################################

MASTER_CONTAINER_NAME="spm_master"
SLAVE_CONTAINER_NAME="spm_slave"


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

type docker >/dev/null 2>&1 || { echo >&2 "docker binary is required but it's not installed.  Aborting."; exit 1; }

echo -e "Containers before cleanup...\n"
docker ps -a
echo -e "\n"
echo  "Stopping and removing master container(s)..."
remove_container_by_name_prefix $MASTER_CONTAINER_NAME
echo -e "\n"
echo  "Stopping and removing slave container(s)..."
remove_container_by_name_prefix $SLAVE_CONTAINER_NAME
echo -e "\n"
echo -e "Containers after cleanup....\n"
docker ps -a

exit 0
