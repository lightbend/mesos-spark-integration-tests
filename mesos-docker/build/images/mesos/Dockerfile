FROM ubuntu:14.04
MAINTAINER Typesafe <skonto>

#Install ansible

RUN apt-get update && \
    apt-get install --no-install-recommends -y software-properties-common && \
    apt-add-repository ppa:ansible/ansible && \
    apt-get update && \
    apt-get install -y ansible

RUN echo '[local]\nlocalhost\n' > /etc/ansible/hosts

#Install java properly
ENV DEBIAN_FRONTEND noninteractive

RUN rm -r /var/lib/apt/lists/*

RUN echo oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | debconf-set-selections && \
  add-apt-repository -y ppa:webupd8team/java && \
  apt-get update && \
  apt-get install -y oracle-java8-installer && \
  rm -rf /var/lib/apt/lists/* && \
  rm -rf /var/cache/oracle-jdk8-installer

ENV JAVA_HOME /usr/lib/jvm/java-8-oracle

RUN java -version

#install mesos - latest
RUN apt-key adv --keyserver keyserver.ubuntu.com --recv E56151BF && \
  DISTRO=$(lsb_release -is | tr '[:upper:]' '[:lower:]') && \
    CODENAME=$(lsb_release -cs) && \
    echo $CODENAME && \
    echo $DISTRO && \
    echo "deb http://repos.mesosphere.io/${DISTRO} ${CODENAME} main" | tee /etc/apt/sources.list.d/mesosphere.list && \
    apt-get -y update && \
    VERSION=$(apt-cache madison mesos | head -1 | awk '{ print $3 }')  && \
    MARATHON_VERSION=$(apt-cache madison marathon | head -1 | awk '{ print $3 }') && \
    apt-get -y install mesos=$VERSION && \
    apt-get install marathon=$MARATHON_VERSION && \
    rm -rf /var/lib/apt/lists/*
