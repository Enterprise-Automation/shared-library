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
                    buildImages(options.components)
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

def buildImages(components) {
    components.each { component -> 
        container(name: 'kaniko', shell: '/busybox/sh') {
            script{
                sh """#!/busybox/sh 
                    /kaniko/executor -f `pwd`/${component.build.dockerfile} -c `pwd`/${component.build.context} --insecure --skip-tls-verify --cache=false --destination=${component.build.destination}"""
            }
        }
    }
}

def generateConfigs(components) {
    generateConfig([deploy: [[type: "namespace"]]])


    components.each { component -> 
        generateConfig(component)
    }
}