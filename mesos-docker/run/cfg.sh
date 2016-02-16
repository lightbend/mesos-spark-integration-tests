################################################################################
#! /bin/bash
# Author: skonto
# date: 23/11/2015
# purpose: helpers for reading slave configuration from a json file
################################################################################

type jq >/dev/null 2>&1 || { echo >&2 "jq binary is required but it's not installed (https://stedolan.github.io/jq/).  Aborting."; exit 1; }

#
# Gets the number of slaves described in the slave config file
# $1 - the full filename to use
#
function get_number_of_slaves {

  jq '.configs |  length' $1

}

#
# Gets the value for a specific field in the slave config file
# $1 - the field to pick for the slave at specific idnex
# $2 - the full filename to use  (slave config file)
# $3 - the index to use in the array of slave configs
#
function get_field_value_from_slave_cfg_at_index {

  RET=$(jq ".configs[$3] .$1" $2)

  if [[ "$RET" == null ]] || [[ "$RET" == "\"\"" ]]; then
    echo ""
  else
    echo "$RET"
  fi
}
