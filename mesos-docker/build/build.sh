#!/usr/bin/env bash

################################ VARIABLES #####################################

SCRIPT=`basename ${BASH_SOURCE[0]}`
SCRIPTPATH="$( cd "$(dirname "$0")" ; pwd -P )"
IMAGE_VERSION=latest
REPO=lightbend/spark_mesos_dind
IS_QUIET=
TO_PUBLISH=

################################ FUNCTIONS #####################################

function show_help {

cat<< EOF
This script creates an ubuntu image with mesos latest version pre-installed.
Usage: $SCRIPT [OPTIONS]

eg: ./build.sh

Options:

-h|--help prints this message.
-q|quiet no output is shown to the console regarding execution status.
--tag version of image to build as it appears when executing docker ps. Default: latest
--repo docker repo to use. Default: lightbend/spark_mesos
--with-publish whether to publish the image to the repo, it prompts for docker login
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
          --tag)       # Takes an option argument, ensuring it has been specified.
              if [ -n "$2" ]; then
                  IMAGE_VERSION=$2
                  shift 2
                  continue
              else
                  printf 'ERROR: "--tag" requires a non-empty option argument.\n' >&2
                  show_help
                  exit 1
              fi
              ;;
           --repo)       # Takes an option argument, ensuring it has been specified.
              if [ -n "$2" ]; then
                  REPO=$2
                  shift 2
                  continue
              else
                  printf 'ERROR: "--repo" requires a non-empty option argument.\n' >&2
                  show_help
                  exit 1
              fi
              ;;
           --with-publish)   # Call a "show_help" function to display a synopsis, then exit.
                  TO_PUBLISH=1
                  shift 1
                  continue
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
}

function printMsg {

if [[ ! -n "$IS_QUIET" ]]; then
   echo -e "$1\n"
fi

}

################################## MAIN ########################################

parse_args $@
cat $SCRIPTPATH/message.txt
echo -e "\n"

type docker >/dev/null 2>&1 || { echo >&2 "docker binary is required but it's not installed.  Aborting."; exit 1; }

printMsg "Building image:$REPO":"$IMAGE_VERSION"

docker build -t "$REPO":"$IMAGE_VERSION" ./images/mesos

if [[ -n "$TO_PUBLISH" ]]; then
  printMsg "Publishing to... $REPO:$IMAGE_VERSION"
  docker login
  docker push "$REPO":"$IMAGE_VERSION"
fi
