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


            case 'registry-secret': 
            script{
                writeFile file: "k8s/${deploy.name}.yaml", text: """
apiVersion: v1
data:
  .dockerconfigjson: ${deploy.dockerconfigjson}
kind: Secret
metadata:
  name: ${deploy.name}
  namespace: \$NAMESPACE
type: kubernetes.io/dockerconfigjson"""
            }
            break; 


            case 'service': 
            script{
                writeFile file: "k8s/${deploy.name}.yaml", text: """
apiVersion: v1
kind: Service
metadata:
  namespace: \$NAMESPACE
  name: ${deploy.name}
spec:
  ports:
    - name: "http"
      port: ${deploy.port}
      targetPort: ${deploy.port}
  selector:
    app: ${deploy.target}"""
            }
            break; 


            case 'ingress': 
            script{
                writeFile file: "k8s/${deploy.name}.yaml", text: """
apiVersion: v1
kind: Service
metadata:
  namespace: \$NAMESPACE
  name: ${deploy.name}
spec:
  ports:
    - name: "http"
      port: ${deploy.port}
      targetPort: ${deploy.port}
  selector:
    app: ${deploy.target}
---
apiVersion: extensions/v1beta1
kind: Ingress
metadata:
  annotations:
    cert-manager.io/cluster-issuer: letsencrypt
    kubernetes.io/ingress.class: nginx
    kubernetes.io/ingress.provider: nginx
  name: ${deploy.name}
  namespace: \$NAMESPACE
spec:
  rules:
  - host: ${deploy.hostname}
    http:
      paths:
      - backend:
          serviceName: ${deploy.name}
          servicePort: ${deploy.port}
  tls:
  - hosts:
    - ${deploy.hostname}
    secretName: ${deploy.name}-tls-cert"""
                slackSend color: "#007aff", message: "ingress resource generated https://${deploy.hostname}"

            }
            break; 
        }
    }
}