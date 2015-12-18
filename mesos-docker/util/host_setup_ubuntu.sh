###############################################
#! /bin/bash
# Author: skonto
# date: 21/10/2015
# purpose: setup your ubuntu host
###############################################

##### VARIABLES #####
SCRIPT=`basename ${BASH_SOURCE[0]}`
SCRIPTPATH="$(cd "$(dirname "$0")" ; pwd -P )"

##### FUNCTIONS #####

function install_java {

#install java 8 silently
  sudo add-apt-repository -y ppa:webupd8team/java
  sudo apt-get update
  echo oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | sudo /usr/bin/debconf-set-selections
  sudo apt-get -q -y install oracle-java8-installer
  sudo apt-get install oracle-java8-set-default
  export JAVA_HOME=$(readlink -f $(which java) | sed "s:bin/java::")
}

function install_sbt {

  echo "deb https://dl.bintray.com/sbt/debian /" | sudo tee -a /etc/apt/sources.list.d/sbt.list
  sudo apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv 642AC823
  sudo apt-get update
  sudo apt-get -y install sbt

}

function install_git {

  sudo apt-get update
  sudo apt-get -y install git

}


function install_maven {

  sudo rm -rf /usr/bin/mvn
  wget http://mirror.switch.ch/mirror/apache/dist/maven/maven-3/3.3.9/binaries/apache-maven-3.3.9-bin.tar.gz
  tar -xzvf apache-maven-3.3.9-bin.tar.gz
  sudo ln -s $(pwd)/apache-maven-3.3.9/bin/mvn /usr/bin/mvn
}


function install_mesos_latest {

  sudo apt-key adv --keyserver keyserver.ubuntu.com --recv E56151BF
  DISTRO=$(lsb_release -is | tr '[:upper:]' '[:lower:]')
  CODENAME=$(lsb_release -cs)

  # Add the repository
  echo "deb http://repos.mesosphere.com/${DISTRO} ${CODENAME} main" | \
  sudo tee /etc/apt/sources.list.d/mesosphere.list
  sudo apt-get -y update
  sudo apt-get -y install mesos

  #disable mesos related services at host
  echo manual | sudo tee /etc/init/zookeeper.override
  echo manual | sudo tee /etc/init/mesos-master.override
  echo manual | sudo tee /etc/init/mesos-slave.override

  service mesos-master stop
  service mesos-slave stop
  service zookeeper stop

}

##### MAIN #####

install_java
install_sbt
install_git
install_maven
install_mesos_latest 

exit 0
