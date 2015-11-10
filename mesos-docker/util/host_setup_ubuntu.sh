###############################################
#! /bin/bash
# Author: skonto
# date: 21/10/2015
# purpose: setup your host
###############################################

function setup_host {
  apt-key adv --keyserver keyserver.ubuntu.com --recv E56151BF && \
  DISTRO=$(lsb_release -is | tr '[:upper:]' '[:lower:]') && \
  CODENAME=$(lsb_release -cs) && \
  echo "deb http://repos.mesosphere.io/${DISTRO} ${CODENAME} main" | tee /etc/apt/sources.list.d/mesosphere.list && \
  apt-get -y update && \
  VERSION=$(apt-cache madison mesos | head -1 | awk '{ print $3 }')  && \
  apt-get -y install mesos=${VERSION} && \
  rm -rf /var/lib/apt/lists/*

  service mesos-master stop
  service mesos-slave stop
  service zookeeper stop

  apt-get install libapparmor1  #needed for docker binary

}

setup_host $@

exit 0
