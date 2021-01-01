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
            stage("Build/Push") {
                environment {
                    PATH = "/busybox:/kaniko:$PATH"
                }
                steps {
                    container(name: "kaniko", shell: "/busybox/sh") {
                        script{
                                sh '''
                                    #!/busybox/sh \
                                        /kaniko/executor \
                                            -f `pwd`/Dockerfile \
                                            -c `pwd` \
                                            --insecure \
                                            --skip-tls-verify \
                                            --cache=false \
                                            --destination=registry.easlab.co.uk/''' + config.project + '''/''' + config.name + ''':latest
                                '''
                        }
                    }
                }
            }
            stage('Deploy') {
                environment {
                    APPNAME = config.name
                }
                steps {
                    container(name: 'kube') {
                        script {
                            kubernetesDeploy configs: "manifests/*.yaml", kubeconfigId: "kubeconfig"
                        }
                    }
                }
            }
        }
    }
}