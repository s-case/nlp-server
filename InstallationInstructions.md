FORMAT: 1A

# NLP Server Installation Instructions
Installation instructions for setting up the service in Ubuntu using java 8 and jetty 9.
Note that one can also try the service in a development environment with maven using the command
```
mvn jetty:run -Djetty.port=8010
```

### Install java using the following commands:
```
sudo add-apt-repository ppa:webupd8team/java
sudo apt-get update
sudo apt-get install oracle-java8-installer
```

### Install jetty similarly to [here](http://www.ubuntugeek.com/install-jetty-9-java-servlet-engine-and-webserver-on-ubuntu-14-10-server.html)
Download jetty
```
wget http://archive.eclipse.org/jetty/9.3.7.v20160115/dist/jetty-distribution-9.3.7.v20160115.tar.gz 
tar -xvf jetty-distribution-9.3.7.v20160115.tar.gz 
sudo mv jetty-distribution-9.3.7.v20160115 /opt/jetty
```

Create jetty user
```
sudo useradd jetty -U -s /bin/false
sudo chown -R jetty:jetty /opt/jetty
```

Create startup script
```
sudo cp /opt/jetty/bin/jetty.sh /etc/init.d/jetty
```

Create properties file
```
sudo nano /etc/default/jetty
```
and add these
```
JAVA_HOME=/usr/lib/jvm/java-8-oracle/
JAVA_OPTIONS="-Xmx4096m"
JETTY_HOME=/opt/jetty
NO_START=0
JETTY_ARGS=jetty.port=8010
JETTY_HOST=0.0.0.0
JETTY_USER=jetty
```

### Start/stop/restart the server
```
sudo service jetty start/stop/restart
```

### Install the service
Set models directory in file `nlp-server/scase-wp3-nlp-parser/src/main/java/uk/ac/ed/inf/srl/StaticPipeline.java`  
(e.g. if downloaded in home folder is could be `/home/username/nlp-server/scase-wp3-nlp-parser/models/`)
Build the service and copy the generated `nlpserver.war` file into `/opt/jetty/webapps`  
Copy the models folder to the location set in the StaticPipeline file  
Start the server and check that it works by going at `http://localhost:8010/nlpserver`
