package org.grails.ssh

import com.jcraft.jsch.*
import grails.util.Holders
import groovy.util.logging.Slf4j
import jodd.io.StreamGobbler
import static org.apache.commons.lang3.StringEscapeUtils.unescapeJava

/**
 * Wrapper to simplify SCP/SSH operations via JSCH library
 * Provides a retry policy to help deal with networking hiccups
 */
@Slf4j
class SshClient {

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

  JSch jsch = new JSch()
  Session session

  void putFile( String source , String destination ){
    log.info "Putting file $source to ${this}:/${destination}"
    getSftpChannel().put( source, destination )
  }

  void putFile( InputStream inputStream , String destination ){
    log.info "Putting file $source to ${this}:/${destination}"
    getSftpChannel().put( inputStream, destination )
  }

  void loadFile( String source ){
    log.info "Load file ${this}:/${source} "
    getSftpChannel().get( source )
  }

  ChannelSftp getSftpChannel(){
    def channel = getSession().openChannel("sftp")
    channel.connect()
    (ChannelSftp) channel
  }

  Map exec( String command ){
    ChannelExec channel
    try {
      log.debug("running command ( ${command} ) on ${this}.")
      // Open channel to run command.
      channel = getSession().openChannel("exec")
      channel.setCommand( command )
      channel.setInputStream( null )


      def out = new ByteArrayOutputStream()
      def error = new ByteArrayOutputStream()
      // Right now its just copied from the jsch java examples.
      StreamGobbler stdout = new StreamGobbler( channel.inputStream, out )
      StreamGobbler stderror = new StreamGobbler( channel.errStream, error )


      channel.connect()
      stdout.start()
      stderror.start()

      stdout.join()
      stderror.join()


      def result = [ status: channel.exitStatus, stdout: unescapeJava(  out.toString() ), stderror: error.toString()]
      log.info("[${command}] run at ${this} result: ${result}")
      return result
    }
    catch (JSchException e) {
      log.error("Error trying to execute command.", e)
      throw e
    }
    catch (Exception e) {
      log.error("An unexpected Exception has happened.", e)
      throw e
    } finally {
      channel?.disconnect()
      if ( !keepAlive ) session?.disconnect()
    }
    return null
  }

  private getSession(){
    for( int i = 0 ; i < retries; i++ ) {
      try{
        log.info "Connecting $this attempt (${i})"
        session = connectToSession()
        return session
        break
      }catch( e ){
        //already logged
        log.error "Error trying to connect ",e
        Thread.sleep( retryInterval )
      }
    }
    throw new Exception("Could not connect to $this")
  }

  private connectToSession(){
    if ( session?.isConnected() ) { log.trace "session already connected"; return session }
    try {
      if ( knownHostsFile ) {
        if ( log.traceEnabled ) log.trace("Adding known hosts file ${knownHostsFile} to client.")
        jsch.setKnownHosts( knownHostsFile )
      }

      if ( sshConfigFile ) {
        if ( log.traceEnabled ) log.trace "Loading ssh config file ${sshConfigFile}"
        ConfigRepository configRepository = OpenSSHConfig.parse( sshConfigFile )
        jsch.setConfigRepository( configRepository )
      }

      // If keyFile is set and password is not attempt to use the key to auth
      if ( keyfile && !password ) {
        if ( log.traceEnabled ) log.trace("Attempting an ssh key auth.")
        if ( keypass ) {
          if ( log.traceEnabled ) log.trace("Adding ${keyfile}, and keyFilePassword to identity.")
          jsch.addIdentity( keyfile, keypass )
        } else {
          if ( log.traceEnabled ) log.trace("Adding ${keyfile} to identity.")
          jsch.addIdentity( keyfile )
        }
      }
      if ( log.traceEnabled ) log.trace("Opening session to $this.")
      session = jsch.getSession( user, host, port )
      // If the connectionTimeout is set use it instead of jsch default.
      if ( connectionTimeout ) {
        session.timeout = connectionTimeout
      }

      if (password) {
        // If this is not set maybe its a key auth?
        session.setPassword( password )
      }
      session.setConfig("StrictHostKeyChecking", strictHostKeyChecking)

      // Connect to the server to run the command.
      session.connect()
      log.debug "connected to $this"
      return session
    }
    catch (JSchException e) {
      log.error( "Failed to create session to host.", e )
      throw e
    }
  }

  @Override
  protected void finalize( ) throws Throwable {
    if ( session.connected ) session.disconnect()
  }

  String toString(){
    "ssh://${user}@${host}:${port}"
  }
}
