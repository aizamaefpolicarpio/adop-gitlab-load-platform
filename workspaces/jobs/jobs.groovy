// Constants
def platformToolsGitURL = "git@${GITLAB_HOST_NAME}:root/platform-management.git"

// Folders
def workspaceFolderName = "${WORKSPACE_NAME}"
def workspaceFolder = folder(workspaceFolderName)

def projectManagementFolderName= workspaceFolderName + "/Project_Management"
def projectManagementFolder = folder(projectManagementFolderName) { displayName('Project Management') }

// Jobs
def generateProjectJob = freeStyleJob(projectManagementFolderName + "/Generate_Project")

// Setup Generate_Project
generateProjectJob.with{
    parameters{
        stringParam("PROJECT_NAME","","The name of the project to be generated. It will also be created as a Gitlab subgroup named as WORKSPACE_NAME/PROJECT_NAME")
        stringParam("ADMIN_USERS","subgroup_admin@accenture.com","The list of users' email addresses that should be setup initially as admin. They will have full access to all jobs within the project. They will have a Master role in the Gitlab group named as the WORKSPACE_NAME/PROJECT_NAME")
        stringParam("DEVELOPER_USERS","subgroup_dev@accenture.com","The list of users' email addresses that should be setup initially as developers. They will have full access to all non-admin jobs within the project. They will have a Developer role in the Gitlab group named as the WORKSPACE_NAME/PROJECT_NAME")
        stringParam("VIEWER_USERS","subgroup_viewer@accenture.com","The list of users' email addresses that should be setup initially as viewers. They will have read-only access to all non-admin jobs within the project. They will have a Reporter role in the Gitlab group named as the WORKSPACE_NAME/PROJECT_NAME")
    }
    label("ldap")
    environmentVariables {
        env('WORKSPACE_NAME',workspaceFolderName)
    }
    wrappers {
        preBuildCleanup()
        injectPasswords()
        maskPasswords()
        environmentVariables {
            env('DC',"${DC}")
            env('OU_GROUPS','ou=groups')
            env('OU_PEOPLE','ou=people')
            env('OUTPUT_FILE','output.ldif')
            env('GITLAB_HTTP_URL',GITLAB_HTTP_URL)
            env('GITLAB_HOST_NAME',GITLAB_HOST_NAME)
            env('PLATFORM_GIT_REF',PLATFORM_GIT_REF)
        }
        credentialsBinding {
            usernamePassword("LDAP_ADMIN_USER", "LDAP_ADMIN_PASSWORD", "adop-ldap-admin")
        }
        sshAgent("adop-jenkins-master")
    }
	configure { project ->
		project / 'buildWrappers' / 'org.jenkinsci.plugins.credentialsbinding.impl.SecretBuildWrapper' / 'bindings' / 'org.jenkinsci.plugins.credentialsbinding.impl.StringBinding' {
		    'credentialsId'('gitlab-secrets-id')
			'variable'('GITLAB_TOKEN')
		}
	}
	scm {
        git {
            remote {
                    url(platformToolsGitURL)
                    credentials("adop-jenkins-master")
            }	
            branch("*/${PLATFORM_GIT_REF}")
        }
	}
    steps {
        shell('''#!/bin/bash -e
# Validate Variables
pattern=" |'"
if [[ "${PROJECT_NAME}" =~ ${pattern} ]]; then
	echo "PROJECT_NAME contains a space, please replace with an underscore - exiting..."
	exit 1
fi
		''')
        shell('''set -e
chmod +x $(find . -type f -name "*.sh")

# LDAP
${WORKSPACE}/common/ldap/generate_role.sh -r "admin" -n "${WORKSPACE_NAME}.${PROJECT_NAME}" -d "${DC}" -g "${OU_GROUPS}" -p "${OU_PEOPLE}" -u "${ADMIN_USERS}" -f "${OUTPUT_FILE}" -w "${WORKSPACE}"
${WORKSPACE}/common/ldap/generate_role.sh -r "developer" -n "${WORKSPACE_NAME}.${PROJECT_NAME}" -d "${DC}" -g "${OU_GROUPS}" -p "${OU_PEOPLE}" -u "${DEVELOPER_USERS}" -f "${OUTPUT_FILE}" -w "${WORKSPACE}"
${WORKSPACE}/common/ldap/generate_role.sh -r "viewer" -n "${WORKSPACE_NAME}.${PROJECT_NAME}" -d "${DC}" -g "${OU_GROUPS}" -p "${OU_PEOPLE}" -u "${VIEWER_USERS}" -f "${OUTPUT_FILE}" -w "${WORKSPACE}"

set +e
${WORKSPACE}/common/ldap/load_ldif.sh -h ldap -u "${LDAP_ADMIN_USER}" -p "${LDAP_ADMIN_PASSWORD}" -b "${DC}" -f "${OUTPUT_FILE}"
set -e

ADMIN_USERS=$(echo ${ADMIN_USERS} | tr ',' ' ')
DEVELOPER_USERS=$(echo ${DEVELOPER_USERS} | tr ',' ' ')
VIEWER_USERS=$(echo ${VIEWER_USERS} | tr ',' ' ')

# Gitlab

# install jq
${WORKSPACE}/common/utils/install_jq.sh
export PATH="$PATH:$HOME/bin"

for user in $ADMIN_USERS $DEVELOPER_USERS $VIEWER_USERS
do
	username=$(echo ${user} | cut -d'@' -f1)
	${WORKSPACE}/common/gitlab/create_user.sh -g ${GITLAB_HTTP_URL}/ -t "${GITLAB_TOKEN}" -u "${username}" -p "${username}" -e "${user}" 
done								

# get the namespace id of the group
PARENT_ID="$(curl --header "PRIVATE-TOKEN: $GITLAB_TOKEN" "${GITLAB_HTTP_URL}/api/v4/groups/${WORKSPACE_NAME}" | jq '.id')"
				
# create new subgroup
curl --header "PRIVATE-TOKEN: $GITLAB_TOKEN" -X POST "${GITLAB_HTTP_URL}/api/v4/groups?name=${PROJECT_NAME}&path=${PROJECT_NAME}&parent_id=${PARENT_ID}" | jq '.'

sleep 5
GITLAB_GROUP_ID="$(curl --header "PRIVATE-TOKEN: $GITLAB_TOKEN" "${GITLAB_HTTP_URL}/api/v4/groups/${WORKSPACE_NAME}%2f${PROJECT_NAME}" | jq '.id')"

if [[ $GITLAB_GROUP_ID -eq "null" ]]; then
	echo "${WORKSPACE_NAME}/${PROJECT_NAME} sub group is not created in gitlab."
    exit 1
fi

# add the users to the group as owners
if [[ ! -z $ADMIN_USERS ]]; then
  for user in $ADMIN_USERS
  do
      USERNAME=$(echo ${user} | cut -d'@' -f1)
      USER_ID=$(curl --header "PRIVATE-TOKEN: $GITLAB_TOKEN" "${GITLAB_HTTP_URL}/api/v4/users?username=${USERNAME}" | jq '.[0].id')
      ${WORKSPACE}/common/gitlab/group/add_user_to_group.sh -g ${GITLAB_HTTP_URL}/ -t "${GITLAB_TOKEN}" -i "${GITLAB_GROUP_ID}" -u "${USER_ID}" -a 50
  done
fi

# add the users to the group as developers
if [[ ! -z $DEVELOPER_USERS ]]; then
  for user in $DEVELOPER_USERS
  do
      USERNAME=$(echo ${user} | cut -d'@' -f1)
      USER_ID=$(curl --header "PRIVATE-TOKEN: $GITLAB_TOKEN" "${GITLAB_HTTP_URL}/api/v4/users?username=${USERNAME}" | jq '.[0].id')
      ${WORKSPACE}/common/gitlab/group/add_user_to_group.sh -g ${GITLAB_HTTP_URL}/ -t "${GITLAB_TOKEN}" -i "${GITLAB_GROUP_ID}" -u "${USER_ID}" -a 30
  done
fi

# add the users to the group as reporter
if [[ ! -z $VIEWER_USERS ]]; then
  for user in $VIEWER_USERS
  do
      USERNAME=$(echo ${user} | cut -d'@' -f1)
      USER_ID=$(curl --header "PRIVATE-TOKEN: $GITLAB_TOKEN" "${GITLAB_HTTP_URL}/api/v4/users?username=${USERNAME}" | jq '.[0].id')
      ${WORKSPACE}/common/gitlab/group/add_user_to_group.sh -g ${GITLAB_HTTP_URL}/ -t "${GITLAB_TOKEN}" -i "${GITLAB_GROUP_ID}" -u "${USER_ID}" -a 20
  done
fi
''')
        dsl {
            external("projects/jobs/**/*.groovy")
        }
        systemGroovyScriptFile('${WORKSPACE}/projects/groovy/acl_admin.groovy')
        systemGroovyScriptFile('${WORKSPACE}/projects/groovy/acl_developer.groovy')
        systemGroovyScriptFile('${WORKSPACE}/projects/groovy/acl_viewer.groovy')
    }
}
