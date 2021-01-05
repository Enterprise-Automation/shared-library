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
                    buildImages(options.resources)
                }
            }
            stage('Deploy') {
                environment {
                    NAMESPACE = "${options.namespace}"
                    PROJECT_ID = "${options.projectId}"
                }
                steps {
                    generateConfigs(options.resources)
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

def buildImages(resources) {
    resources.each { resource -> 
        container(name: 'kaniko', shell: '/busybox/sh') {
            script{
                sh """#!/busybox/sh 
                    /kaniko/executor -f `pwd`/${resource.build.dockerfile} -c `pwd`/${resource.build.context} --insecure --skip-tls-verify --cache=false --destination=${resource.build.destination}"""
            }
        }
    }
}

def generateConfigs(resources) {
    generateConfig([deploy: [[type: "namespace"]]])
    resources.each { resource -> 
        generateConfig(resource)
    }
}