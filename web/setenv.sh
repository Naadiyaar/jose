# set enviroment variable
# $1 path to jose installation
# $2 path to Tomcat installation

# call with:
#  source setenv.sh

export jose_workdir=$1
export jose_db=MySQL-standalone
export jose_db_port=3306
export jose_splash=off

export JAVA_HOME=$1/jre
export JRE_HOME=$1/jre

export CATALINA_HOME=$2
export CATALINA_BASE=$2



