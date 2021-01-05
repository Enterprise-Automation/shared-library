def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
    def options = [:]

    pipeline {
        agent {
            kubernetes {
                label 'kaniko'
                yamlFile 'podspec.yaml'
            }
        }
        stages {
            stage('config'){
                steps{
                    script {
                        options = readYaml (file: config.configFile) 
                    }
                }
            }
            stage("Build/Push Docker Image") {
                environment {
                    PATH = "/busybox:/kaniko:$PATH"
                }
                steps {
                    buildImages(options.deployments)
                }
            }
            stage('K8s staging deploy') {
                environment {
                    NAMESPACE = "${options.namespace}"
                    PROJECT_ID = "${options.projectId}"
                }
                steps {
                    generateConfigs()
                    container(name: 'kube') {
                        // Deploy to k8s cluster
                        script {
                            kubernetesDeploy configs: "k8s/*.yaml", kubeconfigId: 'kubeconfig'
                        }
                    }
                }
            }
        }
        post { 
            success { 
                slackSend color: "good", message: "$JOB_NAME has passed."
            }
            failure { 
                slackSend color: "danger", message: "$JOB_NAME has failed. Check $JOB_URL"
            }
        }
    }
}

def buildImages(deployments) {
    deployments.each { deployment -> 
        container(name: 'kaniko', shell: '/busybox/sh') {
            script{
                sh """#!/busybox/sh 
                    /kaniko/executor -f `pwd`/${deployment.build.dockerfile} -c `pwd`/${deployment.build.context} --insecure --skip-tls-verify --cache=false --destination=${deployment.build.destination}"""
            }
        }
    }
}

def generateConfigs(deployments) {
    script{
        writeFile file: "k8s/0namespace.yaml", '''
kind: Namespace
apiVersion: v1
metadata:
  annotations:
    field.cattle.io/projectId: $PROJECT_ID
  name: $NAMESPACE
  labels:
    name: $NAMESPACE'''
    }
}