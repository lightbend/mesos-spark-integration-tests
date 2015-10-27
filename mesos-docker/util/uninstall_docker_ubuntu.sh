##################################################
#! /bin/bash
# Author: skonto
# date: 21/10/2015
# purpose: useful if you want to remove everything
# and start clean
##################################################

#To uninstall the Docker package:

sudo apt-get purge docker-engine

#To uninstall the Docker package and dependencies that are no longer needed:

sudo apt-get autoremove --purge docker-engine

#The above commands will not remove images, containers, volumes, or user created configuration files on your host. If you wish to delete all images, containers, and volumes run the following command:

sudo rm -rf /var/lib/docker
