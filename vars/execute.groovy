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
            stage('Config'){
                steps{
                    script {
                        slackSend color: "good", message: "Build started for $JOB_NAME\n$JOB_URL"
                        options = readYaml (file: config.configFile) 
                    }
                }
            }
            stage("Build") {
                environment {
                    PATH = "/busybox:/kaniko:$PATH"
                }
                steps {
                    buildImages(options.build)
                }
            }
            stage('Deploy') {
                environment {
                    NAMESPACE = "${options.namespace}"
                    PROJECT_ID = "${options.projectId}"
                }
                steps {
                    generateConfigs(options.deploy)
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
                slackSend color: "good", message: "$JOB_NAME has passed and is available at https://${hostname}."
            }
            failure { 
                slackSend color: "danger", message: "$JOB_NAME has failed. Check $JOB_URL"
            }
        }
    }
}

def buildImages(build) {
    build.each { image -> 
        container(name: 'kaniko', shell: '/busybox/sh') {
            script{
                sh """#!/busybox/sh 
                    /kaniko/executor -f `pwd`/${image.dockerfile} -c `pwd`/${image.context} --insecure --skip-tls-verify --cache=false --destination=${image.destination}"""
            }
        }
    }
}

def generateConfigs(deploy) {
    deploy.resources.each { resource -> 
        generateConfig(resource, deploy.namespace, deploy.hostname)
    }
}