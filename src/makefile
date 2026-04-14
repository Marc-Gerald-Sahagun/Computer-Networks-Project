JAVAC = javac
JAVA  = java
 
.PHONY: all server client clean
 
all: Server.class Client.class
 
Server.class: Server.java
	$(JAVAC) Server.java
 
Client.class: Client.java
	$(JAVAC) Client.java
 
server: Server.class
	$(JAVA) Server
 
client: Client.class
	$(JAVA) Client
 
clean:
	rm -f *.class server_log.txt
 