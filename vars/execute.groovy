def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    options = readYaml (file: config.configFile) 

    pipeline {
        agent {
            kubernetes {
                label 'kaniko'
                yamlFile 'podspec.yaml'
            }
        }
        stages {
            stage("Build/Push Docker Image") {
                environment {
                    PATH = "/busybox:/kaniko:$PATH"
                }
                steps {
                    container(name: 'kaniko', shell: '/busybox/sh') {
                        buildImages(options.deployments)
                    }
                }
            }
            stage('K8s staging deploy') {
                environment {
                    APPNAME = "weather"
                }
                steps {
                    container(name: 'kube') {
                        // Deploy to k8s cluster
                        script {
                            kubernetesDeploy configs: "manifests/*.yaml", kubeconfigId: 'kubeconfig'
                        }
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

def buildImages(deployments) {
    deployments.each { deployment -> 
        script{
            sh """#!/busybox/sh \
                /kaniko/executor -f `pwd`/${deployment.build.dockerfile} -c `pwd`/${deployment.build.context} --insecure --skip-tls-verify --cache=false --destination=${deployment.build.destination}"""
        }

    }
}