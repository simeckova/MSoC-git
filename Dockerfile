FROM ubuntu:22.04

RUN apt-get update
RUN DEBIAN_FRONTEND=noninteractive apt-get -y install tzdata
RUN apt-get -y install apache2
RUN apt-get -y install php libapache2-mod-php



RUN echo "ServerName localhost" >> /etc/apache2/apache2.conf
RUN service apache2 restart



ENV APACHE_RUN_DIR /var/run/apache2

COPY ./webapp /var/www/html
COPY ./data /var/www

EXPOSE 80

CMD ["apachectl", "-DFOREGROUND"]

