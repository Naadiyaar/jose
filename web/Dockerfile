FROM public.ecr.aws/amazonlinux/amazonlinux:latest

RUN yum install -y unzip
# Install Jose
ADD https://github.com/peteschaefer/jose/releases/download/1.5.2/jose-152-linux.zip .
#ADD https://github.com/peteschaefer/jose/releases/download/1.5.6rc1/jose-156-patch.zip .
ADD https://github.com/peteschaefer/jose/releases/download/Latest/jose-latest-patch.zip .
RUN unzip jose-152-linux.zip -d /opt
#RUN unzip jose-157-patch.zip -d.
RUN unzip jose-latest-patch.zip -d /opt

ENV jose_workdir=/opt/jose
ENV jose_db=MySQL-standalone
# ..or any other available database, see config/datasources.xml
ENV jose_db_port=3306
ENV jose_splash=off

ENV JAVA_HOME=/opt/jose/jre
ENV JRE_HOME=/opt/jose/jre

# Install Apache Tomcat
ADD https://dlcdn.apache.org/tomcat/tomcat-9/v9.0.104/bin/apache-tomcat-9.0.104.zip .
RUN unzip apache-tomcat-9.0.104.zip -d /opt

ENV CATALINA_HOME=/opt/apache-tomcat-9.0.104
ENV CATALINA_BASE=/opt/apache-tomcat-9.0.104
RUN chmod +x $CATALINA_HOME/bin/catalina.sh

# Deploy jose Web App
ADD https://github.com/peteschaefer/jose/releases/download/Latest/jose.war .
RUN cp jose.war $CATALINA_HOME/webapps

# make ports visible
# 80 for web server
EXPOSE 8080
# 3360 for mysql database (optional)
EXPOSE 3306

CMD $CATALINA_HOME/bin/catalina.sh run