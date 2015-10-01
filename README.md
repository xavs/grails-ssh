# grails-ssh
# An ssh plugin for grails based on JSCH

Provides sftp and ssh

## Usage:
```
// SshClient has all the functionality and parameterization.
def ssh = new SshClient( host: "127.0.0.1",
                      keyfile: "~/.ssh/id_rsa",
                      keypass: "",
                         user:	"javier"
)
Map result = ssh.exec( "echo hola" )
// result is a map with keys [status,stdout, stderror]
```
## For scp operations, there is access to the underlying ChannelSftp
http://epaul.github.io/jsch-documentation/javadoc/com/jcraft/jsch/ChannelSftp.html
```
ssh.channelSftp
```
## The config options to create the sshclient are:
```
  String host
  int port = 22
  String user
  String password
  String keyfile  // this will be preferred better than password if populated
  String keypass
  
  String sshConfigFile  // optional
  String knownHostsFile // optional
  String strictHostKeyChecking = "no"

  int retries = Holders.config.ssh.defaultRetries ?: 5
  int retryInterval = Holders.config.ssh.defaultRetryInterval ?: 1000 // miliseconds
  int connectionTimeout  // time to wait to get an exception as result of no answer
  boolean keepAlive = false  // use 'true' carefully, it could hog up system resources
```
If using keepAlive=true, the session won't be closed after an exec() or a putFile(), etc. This can be useful in terms of performance when a series of operations are to be performed. Be sure you enclose the code within a try{} block and add a finally { sshClient.session.disconnect() }
