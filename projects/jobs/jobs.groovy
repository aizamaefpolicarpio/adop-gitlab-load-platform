// Constants
def platformToolsGitURL = "git@${GITLAB_HOST_NAME}:root/platform-management.git"

// Folders
def workspaceFolderName = "${WORKSPACE_NAME}"

def projectFolderName = workspaceFolderName + "/${PROJECT_NAME}"
def projectFolder = folder(projectFolderName)

def cartridgeManagementFolderName= projectFolderName + "/Cartridge_Management"
def cartridgeManagementFolder = folder(cartridgeManagementFolderName) { displayName('Cartridge Management') }

// Jobs
def loadCartridgeJob = freeStyleJob(cartridgeManagementFolderName + "/Load_Cartridge")

// Setup Load_Cartridge
loadCartridgeJob.with{
    parameters{
        stringParam('CARTRIDGE_CLONE_URL', '', 'Cartridge URL to load')
    }
    environmentVariables {
        env('WORKSPACE_NAME',workspaceFolderName)
        env('PROJECT_NAME',projectFolderName)
        env('GITLAB_HTTP_URL',GITLAB_HTTP_URL)
        env('GITLAB_HOST_NAME',GITLAB_HOST_NAME)
    }
    wrappers {
        preBuildCleanup()
        injectPasswords()
        maskPasswords()
        sshAgent("adop-jenkins-master")
    }
	configure { project ->
		project / 'buildWrappers' / 'org.jenkinsci.plugins.credentialsbinding.impl.SecretBuildWrapper' / 'bindings' / 'org.jenkinsci.plugins.credentialsbinding.impl.StringBinding' {
		    'credentialsId'('gitlab-secrets-id')
			'variable'('GITLAB_TOKEN')
		}
	}
    steps {
        shell('''#!/bin/bash -ex
#!/bin/bash -ex
chmod +x ${WORKSPACE}/common/gitlab/create_project.sh

# We trust everywhere
#echo -e "#!/bin/sh 
#exec ssh -o StrictHostKeyChecking=no "$@" 
#" > ${WORKSPACE}/custom_ssh
#chmod +x ${WORKSPACE}/custom_ssh
#export GIT_SSH="${WORKSPACE}/custom_ssh"        

# install jq
sh ${WORKSPACE}/common/utils/install_jq.sh
export PATH="$PATH:$HOME/bin"

# Clone Cartridge
git clone ${CARTRIDGE_CLONE_URL} cartridge

# Create repositories
mkdir ${WORKSPACE}/tmp
cd ${WORKSPACE}/tmp
while read repo_url; do
    if [ ! -z "${repo_url}" ]; then
        repo_name=$(echo "${repo_url}" | rev | cut -d'/' -f1 | rev | sed 's#.git$##g')
        target_repo_name="${PROJECT_NAME}/${repo_name}"
        
        # get the namespace id of the group/subgroup
        GROUP_NAME=$(echo "${PROJECT_NAME}" | sed "s+/+%2f+g")
		GITLAB_GROUP_ID="$(curl --header "PRIVATE-TOKEN: $GITLAB_TOKEN" "${GITLAB_HTTP_URL}/api/v4/groups/${GROUP_NAME}" | jq '.id')"				
		
        # create new project				
		${WORKSPACE}/common/gitlab/create_project.sh -g ${GITLAB_HTTP_URL}/ -t "${GITLAB_TOKEN}" -w "${GITLAB_GROUP_ID}" -p "${repo_name}"	
        
        # Populate repository
        git clone git@${GITLAB_HOST_NAME}:"${target_repo_name}.git"
        cd "${repo_name}"
        git remote add source "${repo_url}"
        git fetch source
        git push origin +refs/remotes/source/*:refs/heads/*
        cd -
    fi
done < ${WORKSPACE}/cartridge/src/urls.txt

# Provision one-time infrastructure
if [ -d ${WORKSPACE}/cartridge/infra ]; then
    cd ${WORKSPACE}/cartridge/infra
    if [ -f provision.sh ]; then
        source provision.sh
    else
        echo "INFO: cartridge/infra/provision.sh not found"
    fi
fi

# Generate Jenkins Jobs
if [ -d ${WORKSPACE}/cartridge/jenkins/jobs ]; then
    cd ${WORKSPACE}/cartridge/jenkins/jobs
    if [ -f generate.sh ]; then
        source generate.sh
    else
        echo "INFO: cartridge/jenkins/jobs/generate.sh not found"
    fi
fi
''')
        systemGroovyCommand('''
import jenkins.model.*
import groovy.io.FileType

def jenkinsInstace = Jenkins.instance
def projectName = build.getEnvironment(listener).get('PROJECT_NAME')
def xmlDir = new File(build.getWorkspace().toString() + "/cartridge/jenkins/jobs/xml")
def fileList = []

xmlDir.eachFileRecurse (FileType.FILES) { file ->
    if(file.name.endsWith('.xml')) {
        fileList << file
    }
}
fileList.each {
	String configPath = it.path
  	File configFile = new File(configPath)
    String configXml = configFile.text
    ByteArrayInputStream xmlStream = new ByteArrayInputStream( configXml.getBytes() )
    String jobName = configFile.getName().substring(0, configFile.getName().lastIndexOf('.'))

    jenkinsInstace.getItem(projectName,jenkinsInstace).createProjectFromXML(jobName, xmlStream)
}
''')
        dsl {
            ignoreExisting(false)
            external("cartridge/jenkins/jobs/dsl/**/*.groovy")
        }
    }
    scm {
        git {
            remote {
                name("origin")
                url(platformToolsGitURL)
                credentials("adop-jenkins-master")
            }
            branch("*/${PLATFORM_GIT_REF}")
        }
    }
}