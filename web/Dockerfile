FROM public.ecr.aws/amazonlinux/amazonlinux:latest

# Install Jose
ADD https://github.com/peteschaefer/jose/releases/download/1.5.2/jose-152-linux.zip .
#ADD https://github.com/peteschaefer/jose/releases/download/1.5.6rc1/jose-156-patch.zip .
ADD https://github.com/peteschaefer/jose/releases/download/Latest/jose-latest-patch.zip .
RUN unzip jose-152-linux.zip -C /opt
#RUN unzip jose-156-patch.zip -C .
RUN unzip jose-latest-patch.zip -C /opt

ENV jose_workdir=/opt/jose
ENV jose_db_port=3306
ENV jose_splash=off
ENV JAVA_HOME=/opt/jose/jre

# Install Apache Tomcat
ADD https://dlcdn.apache.org/tomcat/tomcat-9/v9.0.104/bin/apache-tomcat-9.0.104.zip .
RUN unzip apache-tomcat-9.0.104.zip -C /opt/tomcat

ENV CATALINA_HOME=/opt/tomcat
ENV CATALINA_BASE=/opt/tomcat

# Deploy jose Web App
# we could use a .war file, but we do it explicitly to have the latest version
# & avoid copying
#ADD https://github.com/peteschaefer/jose/releases/download/Latest/jose-latest.war .
#RUN cp /opt/jose/web/jose.war $CATALINA_HOME/webapps
RUN $jose_workdir/web/deploy.sh $jose_workdir $CATALINA_HOME/webapps/jose

EXPOSE 80
EXPOSE 3306

CMD $CATALINA_HOME/catalina.sh run