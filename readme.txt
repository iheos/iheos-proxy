A. Binary distribution 
1) Look in com.predic8.membrane.core\dist\router\membrane-router-2.0.4

B. Building from source
1) Run ant -f build-router.xml or (Run as Ant Build from the Eclipse IDE)

C. Setting up Router/Configuration:
1) Set MEMBRANE_HOME to path\router\membrane-router-2.0.4\
2) Edit monitor-beans.xml
  a) For database loggging: set the database URL 
  b) For filesystem logging: set the logging file path <property name="dir" value="C:/e/temp/router/" />
3) Edit rules.xml
 a) Make sure the certificates keystores are valid in rules.xml
 b) Make sure hostnames are set
 c) Make sure port numbers do no conflict
4) For the CONNECT gateway, update the service endpoint hostname and port number in InternalConnectionInfo.xml on the participant and RI's servers
	a) ParticipantHostname:portnumber -> agentHostname:portnumber
	b) ServerHostname:portnumber -> agentHostname:portnumber
	c) Test by entering the updated endpoint with ?wsdl in a browser window.
5) Run memrouter.bat (on Windows systems) or memrouter.sh (on Linux-compatible systems) 
Note: the application process may require system\admin previliges to create application defined ports

D. Running/Troubleshooting
* If the rules.xml host/port mapping calls for "localhost" and a port, test using exactly the same host "localhost" but not the loop back address 127.0.0.1.
* If IP address binding is desired, create another rule for 127.0.0.1 in the rules.xml file.
* Update monitor-rules.xml and enter a list of known (FQDN) hostnames to automatically map dynamic IP addresses to the hostname

E. Setting up a Message Broker
1) Extract ffmq3-distribution-3.0.5-dist.zip file 
2) Run ffmq-server.bat