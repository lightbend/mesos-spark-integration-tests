############################################
#! /bin/bash
# Author: skonto
# date: 21/10/2015
# purpose:to be run on nodes to set hadoop..
#############################################


addgroup hadoop

adduser --ingroup hadoop -w none hduser

apt-get update

apt-get install openssh-server

sudo su hduser

ssh-keygen -t rsa -P ""

cat $HOME/.ssh/id_rsa.pub >> $HOME/.ssh/authorized_keys
