def call (resource) {
    resource.deploy.each { deploy -> 
        switch(deploy.type) {
            case 'namespace': 
            script{
                writeFile file: "k8s/0namespace.yaml", text: '''
kind: Namespace
apiVersion: v1
metadata:
  annotations:
    field.cattle.io/projectId: $PROJECT_ID
  name: $NAMESPACE
  labels:
    name: $NAMESPACE'''
            }
            break; 
            case 'deployment': 
            script{
                writeFile file: "k8s/${deploy.name}.yaml", text: """
apiVersion: apps/v1
kind: Deployment
metadata:
  namespace: \$NAMESPACE
  labels:
    app: ${deploy.name}
  name: ${deploy.name}
spec:
  replicas: 1
  strategy: {}
  selector:
    matchLabels:
      app: ${deploy.name}
  template:
    metadata:
      labels:
        build_number: \$BUILD_NUMBER
        app: ${deploy.name}
    spec:
      containers:
        - image: ${resource.build.destination}
          name: ${deploy.name}
          imagePullPolicy: Always
          resources: {}
          stdin: true
          tty: true
      restartPolicy: Always
      imagePullSecrets:
        - name: ${deploy.imagePullSecret}"""
            }
            break; 
        }
    }
}