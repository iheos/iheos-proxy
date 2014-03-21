#!/bin/bash
MEMBRANE_HOME=$(dirname $0)
##MEMBRANE_HOME=/home/ubuntu/sunil/gatewayagent/router/membrane-router-2.0.4
export MEMBRANE_HOME
PATH=.:$PATH
CLASSPATH=.
CLASSPATH=/home/ubuntu/sunil/gatewayagent/router/membrane-router-2.0.4
CLASSPATH=$MEMBRANE_HOME/conf
CLASSPATH=$CLASSPATH:$MEMBRANE_HOME/lib/stax2-api-3.0.1.jar
CLASSPATH=$CLASSPATH:$MEMBRANE_HOME/lib/stax-api-1.0.1.jar
CLASSPATH=$CLASSPATH:$MEMBRANE_HOME/lib/woodstox-core-asl-4.0.5.jar
CLASSPATH=$CLASSPATH:$MEMBRANE_HOME/starter.jar
export CLASSPATH
#cd $MEMBRANE_HOME
echo Membrane Router running...
echo $MEMBRANE_HOME
java -classpath $CLASSPATH com.predic8.membrane.core.Starter $1 $2 $3 $4 $5 $6 $7 $8 $9

