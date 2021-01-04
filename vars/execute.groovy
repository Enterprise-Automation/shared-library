def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
    pipeline {
        agent {
            kubernetes {
                label 'kaniko'
                yamlFile 'podspec.yaml'
            }
        }
        stages {
            stage("configuration") {
                steps {
                    script { 
                        options = readYaml (file: config.file) 
                    }
                    echo options.deployments.toString()
                }
            }
            stage("Build/Push Docker Image") {
                environment {
                    PATH = "/busybox:/kaniko:$PATH"
                }
                steps {
                    container(name: 'kaniko', shell: '/busybox/sh') {
                        script{
                                sh '''#!/busybox/sh
                                /kaniko/executor -f `pwd`/react_app/Dockerfile -c `pwd`/react_app --insecure --skip-tls-verify --cache=false --destination=registry.easlab.co.uk/ethan/weather:react'''
                        }
                        script{
                                sh '''#!/busybox/sh
                                /kaniko/executor -f `pwd`/node_app/Dockerfile -c `pwd`/node_app --insecure --skip-tls-verify --cache=false --destination=registry.easlab.co.uk/ethan/weather:node'''
                        }
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