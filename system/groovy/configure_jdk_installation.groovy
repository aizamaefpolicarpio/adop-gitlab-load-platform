import hudson.model.JDK
import hudson.tools.JDKInstaller
import hudson.tools.InstallSourceProperty
import jenkins.model.Jenkins

// https://gist.github.com/hikoma/9e3702b8f11cb1c82c9f
def jdkVersion = "jdk-8u144-oth-JPR"
def descriptor = new JDK.DescriptorImpl();

println "--> Configuring JDK8 ${jdkVersion} in Global Tools Configuration"
Jenkins.instance.updateCenter.getById('default').updateDirectlyNow(true)
def jdkInstaller = new JDKInstaller(jdkVersion, true)

def jdk = new JDK("JDK8", null, [new InstallSourceProperty([jdkInstaller])])
descriptor.setInstallations(jdk)