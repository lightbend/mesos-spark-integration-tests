FROM ubuntu:16.04
MAINTAINER LightBend <skonto>

#Install java properly
ENV DEBIAN_FRONTEND noninteractive

RUN apt-get update && apt-get -y install software-properties-common python-software-properties

RUN echo oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | debconf-set-selections && \
  add-apt-repository -y ppa:webupd8team/java && \
  apt-get update && \
  apt-get install -y oracle-java8-installer && \
  rm -rf /var/lib/apt/lists/* && \
  rm -rf /var/cache/oracle-jdk8-installer

ENV JAVA_HOME /usr/lib/jvm/java-8-oracle

RUN java -version

# Let's start with some basic stuff.
RUN apt-get update -qq && apt-get install -qqy \
    curl \
    apt-transport-https \
    ca-certificates \
    curl \
    lxc \
    iptables

# Install Docker from Docker Inc. repositories.
RUN curl -sSL https://get.docker.com | sh

# Install the magic wrapper.d
ADD ./wrapdocker /usr/local/bin/wrapdocker
RUN chmod +x /usr/local/bin/wrapdocker

# Define additional metadata for our image.
VOLUME /var/lib/docker

#install mesos - latest
RUN apt-get update -qq && apt-get -y install upstart-sysv

RUN apt-key adv --keyserver keyserver.ubuntu.com --recv E56151BF && \
  DISTRO=$(lsb_release -is | tr '[:upper:]' '[:lower:]') && \
    CODENAME=$(lsb_release -cs) && \
    echo $CODENAME && \
    echo $DISTRO && \
    echo "deb http://repos.mesosphere.io/${DISTRO} trusty main" | tee /etc/apt/sources.list.d/mesosphere.list && \
    apt-get -y update && \
    VERSION=$(apt-cache madison mesos | head -1 | awk '{ print $3 }')  && \
    MARATHON_VERSION=$(apt-cache madison marathon | head -1 | awk '{ print $3 }') && \
    apt-get -y install mesos=$VERSION && \
    apt-get -y install marathon=$MARATHON_VERSION && \
    rm -rf /var/lib/apt/lists/*
