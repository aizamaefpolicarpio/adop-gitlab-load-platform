import hudson.model.*
import jenkins.model.*
import hudson.plugins.sonar.*
import hudson.plugins.sonar.model.TriggersConfig
import hudson.plugins.sonar.utils.SQServerVersions

// Check if enabled
def env = System.getenv()

// Variables
def sonarServerUrl = env['SONAR_SERVER_URL']
def sonarToken = new File('/tmp/sonar_token_file').text.trim()
def sonarInstallationName = "ADOP Sonar 5_3_or_HIGHER"

if (!sonarServerUrl || !sonarToken) {
    println "sonarServerUrl and sonarToken is empty, sonarqube installation will not proceed."
    return
}

// Constants
def instance = Jenkins.getInstance()

// Sonar
// Source: http://pghalliday.com/jenkins/groovy/sonar/chef/configuration/management/2014/09/21/some-useful-jenkins-groovy-scripts.html
println "--> Adding SonarQube installation ${sonarInstallationName}"
def SonarGlobalConfiguration sonarConf = instance.getDescriptor(SonarGlobalConfiguration.class)

def sonar = new SonarInstallation(
    sonarInstallationName,
    sonarServerUrl,
    SQServerVersions.SQ_5_3_OR_HIGHER,
    sonarToken,
    "",
    "",
    "",
    "",
    "",
    new TriggersConfig(),
    "",
    "",
    ""
)

sonarConf.setInstallations(sonar)