############################################
#! /bin/bash
# Author: skonto
# date: 21/10/2015
# purpose:to be run on nodes to set hadoop..
#############################################


ssh-keygen -t rsa -P "" -f ~/.ssh/id_rsa > /dev/null

cat $HOME/.ssh/id_rsa.pub >> $HOME/.ssh/authorized_keys

cat <<EOF > /root/.ssh/config
Host *
  UserKnownHostsFile /dev/null
  StrictHostKeyChecking no
  LogLevel quiet
  Port 2122
EOF

mkdir -p /root/data/datanode
mkdir -p /root/data/namenode

tar -zxf /var/hadoop/hadoop-2.6.0.tar.gz -C /var/hadoop/
cp -r /var/hadoop/hadoop-2.6.0/* /usr/local/
files=( "core-site.xml" "hdfs-site.xml" )

for i in "${files[@]}"
do
  :
  cp /var/hadoop/config/$i /usr/local/etc/hadoop/$i
done

if [ "$1" = "SLAVE" ]; then
  read -d '' rep_text <<EOF
  <property>
  <name>dfs.namenode.servicerpc-address</name>
  <value>localhost:8020</value>
  </property>

  <property>
  <name>dfs.datanode.address</name>
  <value>localhost:$IT_DFS_DATANODE_ADDRESS_PORT</value>
  </property>

  <property>
  <name>dfs.datanode.http.address</name>
  <value>localhost:$IT_DFS_DATANODE_HTTP_ADDRESS_PORT</value>
  </property>

  <property>
  <name>dfs.datanode.ipc.address</name>
  <value>localhost:$IT_DFS_DATANODE_IPC_ADDRESS_PORT</value>
  </property>
EOF
else
  rep_text=""
fi

awk -v r="$rep_text" '{gsub(/REPLACE/,r)}1' /usr/local/etc/hadoop/hdfs-site.xml >  tmp_file && mv tmp_file /usr/local/etc/hadoop/hdfs-site.xml
