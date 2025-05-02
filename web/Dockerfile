FROM public.ecr.aws/amazonlinux/amazonlinux:latest

# Install Jose
ADD https://github.com/peteschaefer/jose/releases/download/1.5.2/jose-152-linux.zip .
#ADD https://github.com/peteschaefer/jose/releases/download/1.5.6rc1/jose-156-patch.zip .
ADD https://github.com/peteschaefer/jose/releases/download/Latest/jose-latest-patch.zip .
RUN unzip jose-152-linux.zip -C /opt
#RUN unzip jose-156-patch.zip -C .
RUN unzip jose-latest-patch.zip -C /opt

ENV jose_workdir=/opt/jose
ENV jose_db=MySQL-standalone
# ..or any other available database, see config/datasources.xml
ENV jose_db_port=3306
ENV jose_splash=off
ENV JAVA_HOME=/opt/jose/jre

# Install Apache Tomcat
ADD https://dlcdn.apache.org/tomcat/tomcat-9/v9.0.104/bin/apache-tomcat-9.0.104.zip .
RUN unzip apache-tomcat-9.0.104.zip -C /opt/tomcat

ENV CATALINA_HOME=/opt/tomcat
ENV CATALINA_BASE=/opt/tomcat

# use port 80 for web server
RUN sed -i 's/port="8080"/port="80"/g' /opt/tomcat/conf/server.xml

# Deploy jose Web App
ADD https://github.com/peteschaefer/jose/releases/download/Latest/jose-latest.war .
RUN cp /opt/jose/web/jose.war $CATALINA_HOME/webapps

# make ports visible
# 80 for web server
EXPOSE 80
# 3360 for mysql database (optional)
EXPOSE 3306

CMD $CATALINA_HOME/catalina.sh run