# web-apps are usually deployed using .war archives
# we do it the simple way, by just linking into the jose installation directory
# (which we need, anyway)

export jose_workdir=$1
export jose_db=MySQL-standalone
export jose_db_port=3306
export jose_splash=off

cp $1/jose.war $2/webapps


