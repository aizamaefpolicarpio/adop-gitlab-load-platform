/**
 * Author: john.bryan.j.sazon@accenture.com
 */

import jenkins.model.*
import hudson.util.Secret;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.cloudbees.plugins.credentials.CredentialsScope;

/**
 * Get environment variables
 */
def env = System.getenv()
def username = env['INITIAL_ADMIN_USER']
def password = env['INITIAL_ADMIN_PASSWORD']

if (!username || !password) {
    println "username or password is empty, credentials setup will not proceed."
    return
}

def credentialDescription = "ADOP Administrator Credentials"
def credentialsId = "adop-admin-credentials"
def instance = Jenkins.getInstance()
def systemCredentialsProvider = SystemCredentialsProvider.getInstance()
def credentialScope = CredentialsScope.GLOBAL
def credentialDomain = com.cloudbees.plugins.credentials.domains.Domain.global()
def credentialToCreate = new UsernamePasswordCredentialsImpl(credentialScope, credentialsId, credentialDescription, username, password)

/**
 * Check if credentials with @credentialsId already exists and
 * removeCredentials the @credentialsId if it exists.
 */
systemCredentialsProvider.getCredentials().each {
  credentials = (com.cloudbees.plugins.credentials.Credentials) it
  if (credentials.getDescription() == credentialDescription) {
    println "Found existing credentials: " + credentialDescription
    systemCredentialsProvider.removeCredentials(credentialDomain,credentialToCreate)
    println credentialDescription + " is removed and will be recreated.."
  }
}

/**
 * Create the credentials
 */
println "--> Registering ${credentialDescription}.."
systemCredentialsProvider.addCredentials(credentialDomain,credentialToCreate)
println credentialDescription + " created.."