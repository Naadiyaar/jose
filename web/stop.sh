# set enviroment variable
# $1 path to jose installation
# $2 path to Tomcat installation

export JAVA_HOME=$1/jre
export JRE_HOME=$1/jre

export CATALINA_HOME=$2
export CATALINA_BASE=$2

$CATALINA_HOME/bin/catalina.sh stop

