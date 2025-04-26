josedir=$1
appdir=$2

# web-apps are usually deployed using .war archives
# we do it the simple way, by just linking into the jose installation directory
# (which we need, anyway)

export jose_workdir=S1
export jose_db_port=3306
export jose_splash=off

ln -sf $josedir/web         $appdir
#ln -sf $josedir/config      $appdir/config
#ln -sf $josedir/xsl         $appdir/xsl
ln -sf $josedir/images/nav  $appdir/nav
ln -sf $josedir/lib/fop.jar $appdir/WEB-INF/lib/fop.jar
#ln -s $josedir/jose.jar    $appdir/WEB-INF/lib/jose.jar
ln -sf $josedir/classes     $appdir/WEB-INF/classes


