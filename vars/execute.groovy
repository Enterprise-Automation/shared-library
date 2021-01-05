def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
    def options = [:]
    def slackResponse
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
                        slackResponse = slackSend(channel: "jenkins", message: "Build started for $JOB_NAME")
                        slackSend(channel: slackResponse.threadId, message: "Job URL: $JOB_URL")
                        options = readYaml (file: config.configFile) 
                    }
                }
            }
            stage("Build") {
                environment {
                    PATH = "/busybox:/kaniko:$PATH"
                }
                steps {
                    buildImages(options.buildd)
                }
            }
            stage('Deploy') {
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
                script {
                    slackResponse.addReaction("whitecheckmark")
                    slackSend(channel: slackResponse.threadId, message: "Job has passed and is available at https://${options.deploy.hostname}")
                }
                // slackSend channel: slackResponse.threadId, message: "$JOB_NAME has passed and is available at https://${hostname}."
            }
            failure { 
                script {
                    slackResponse.addReaction("octagonal_sign")
                    slackSend(channel: slackResponse.threadId, message: "Job has failed")
                }
                // slackSend channel: slackResponse.threadId, message: "$JOB_NAME has failed. Check $JOB_URL"
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