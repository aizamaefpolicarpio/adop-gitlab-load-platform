/**
 * Author: john.bryan.j.sazon@accenture.com
 */

import jenkins.model.*
import com.cloudbees.plugins.credentials.SystemCredentialsProvider
import com.cloudbees.plugins.credentials.CredentialsScope
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl
import hudson.util.Secret

/**
 * Get environment variables
 */
def env = System.getenv()
def gitlabToken = env['GITLAB_TOKEN'] ?: new File('/tmp/gitlab_token_file').text.trim()
if (!gitlabToken) {
    println "gitlabToken is empty credentials setup will not proceed."
    return
}
def credentialDescription = "ADOP Gitlab Integration token - Secret Text"
def credentialsId = "gitlab-secrets-id"
def instance = Jenkins.getInstance()
def systemCredentialsProvider = SystemCredentialsProvider.getInstance()
def credentialScope = CredentialsScope.GLOBAL
def credentialDomain = com.cloudbees.plugins.credentials.domains.Domain.global()
def credentialToCreate = new StringCredentialsImpl(credentialScope, credentialsId, credentialDescription, Secret.fromString(gitlabToken))

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
println "--> Registering Gitlab API token as Secret text.."
systemCredentialsProvider.addCredentials(credentialDomain,credentialToCreate)
println credentialDescription + " created.."
