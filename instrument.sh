#! /usr/bin/bash
set -e

export UNIFI=$HOME/repos/unifi
export CLASSPATH="$UNIFI/classes/unifi.jar:$UNIFI/lib/bcel-5.2.jar:$UNIFI/lib/log4j-1.2.15.jar:$UNIFI/lib/commons-logging-1.1.1.jar:$UNIFI/lib/gson-1.5.jar"
export TARGET=$1
export CLASSPATH=`pwd`"/$TARGET:$CLASSPATH"

echo $CLASSPATH;
java -Xmx1g -classpath $CLASSPATH unifi.drivers.Instrumenter $1
