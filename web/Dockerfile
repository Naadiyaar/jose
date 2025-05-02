FROM public.ecr.aws/amazonlinux/amazonlinux:latest

RUN yum install -y unzip
RUN yum install -y shadow-utils
# some extra libraries required by mysqld
RUN yum install -y libnsl
ADD --chmod=644 https://github.com/peteschaefer/jose/raw/refs/heads/main/lib/Linux_amd64/libcrypt.so.1 /lib64
# and Java
RUN yum install -y fontconfig

# don't run as root (mysqld don't like it)
RUN groupadd -r jose
RUN adduser -b /home -g jose -l -m jose

USER jose
WORKDIR /home/jose

# Install Jose into /home/jose/jose
ADD --chown=jose:jose https://github.com/peteschaefer/jose/releases/download/1.5.2/jose-152-linux.zip .
#ADD https://github.com/peteschaefer/jose/releases/download/1.5.7/jose-157-patch.zip .
ADD --chown=jose:jose https://github.com/peteschaefer/jose/releases/download/Latest/jose-latest-patch.zip .
#RUN pwd && ls -l
RUN unzip jose-152-linux.zip -d .
RUN unzip -o jose-latest-patch.zip -d jose

#
# tell the WebApp where to find the important stuff:
#
ENV jose_workdir=/home/jose/jose
ENV jose_db=MySQL-standalone
# ..or any other available database, see config/datasources.xml
ENV jose_db_port=3306
ENV jose_splash=off

ENV JAVA_HOME=$jose_workdir/jre
ENV JRE_HOME=$jose_workdir/jre

# Install Apache Tomcat into /home/jose/apache-tomcat-9.0.104
ADD --chown=jose:jose https://dlcdn.apache.org/tomcat/tomcat-9/v9.0.104/bin/apache-tomcat-9.0.104.zip .
RUN unzip apache-tomcat-9.0.104.zip -d .

ENV CATALINA_HOME=/home/jose/apache-tomcat-9.0.104
ENV CATALINA_BASE=/home/jose/apache-tomcat-9.0.104
RUN chmod +x $CATALINA_HOME/bin/catalina.sh

# Deploy jose Web App
#ADD --chown=jose:jose https://github.com/peteschaefer/jose/releases/download/Latest/jose.war .
#COPY --chown=jose:jose /home/nightrider/src/jose/jose.war .
#RUN cp jose.war $CATALINA_HOME/webapps

# make ports visible
# 80 for web server
EXPOSE 8080
# 3360 for mysql database (optional)
EXPOSE 3306

#CMD $CATALINA_HOME/bin/catalina.sh run