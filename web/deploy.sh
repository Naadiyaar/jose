josedir=$1
appdir=$2

# web-apps are usually deployed using .war archives
# we do it the simple way, by just linking into the jose installation directory
# (which we need, anyway)

ln -s $josedir/web         $appdir
ln -s $josedir/config      $appdir/config
ln -s $josedir/xsl         $appdir/xsl
ln -s $josedir/images/nav  $appdir/nav
ln -s $josedir/jose.jar    $appdir/WEB-INF/lib/jose.jar
ln -s $josedir/lib/fop.jar $appdir/WEB-INF/lib/fop.jar

