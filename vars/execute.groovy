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
                        slackResponse = slackSend(channel: "jenkins", message: "Awww true 'ey. A new build!\n$JOB_URL")
                        // slackSend(channel: slackResponse.threadId, message: "Job URL: $JOB_URL")
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
                    slackResponse.addReaction("white_check_mark")
                    slackSend(channel: slackResponse.threadId, message: "The job passed 'ey!\nhttps://${options.deploy.hostname}\nChuur cuh :beer:")
                }
            }
            failure { 
                script {
                    slackResponse.addReaction("octagonal_sign")
                    slackSend(channel: slackResponse.threadId, message: "The job failed you stringray looking motherfucker!")
                }
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