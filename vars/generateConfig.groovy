def call (resource, namespace, hostname) {
  switch(resource.kind) {
    case 'namespace': 
      script{
        writeFile file: "k8s/0namespace.yaml", text: """
kind: Namespace
apiVersion: v1
metadata:
  annotations:
    field.cattle.io/projectId: ${resource.projectId}
  name: ${namespace}
  labels:
    name: ${namespace}"""
      }
    break; 


//     case 'deployment': 
//       script{
//         writeFile file: "k8s/${resource.kind}-${resource.name}.yaml", text: """
// apiVersion: apps/v1
// kind: Deployment
// metadata:
//   namespace: ${namespace}
//   labels:
//     app: ${resource.name}
//   name: ${resource.name}
// spec:
//   replicas: 1
//   strategy: {}
//   selector:
//     matchLabels:
//       app: ${resource.name}
//   template:
//     metadata:
//       labels:
//         build_number: \$BUILD_NUMBER
//         app: ${resource.name}
//     spec:
//       containers:
//         - image: ${resource.image}
//           name: ${resource.name}
//           imagePullPolicy: Always
//           resources: {}
//           stdin: true
//           tty: true
// ${generateEnvYaml(resource.env)}
//       restartPolicy: Always
//       imagePullSecrets:
//         - name: ${resource.imagePullSecret}"""
//       }
//       break; 


      case 'registry-secret': 
      script{
        writeFile file: "k8s/${resource.kind}-${resource.name}.yaml", text: """
apiVersion: v1
data:
  .dockerconfigjson: ${resource.dockerconfigjson}
kind: Secret
metadata:
  name: ${resource.name}
  namespace: ${namespace}
type: kubernetes.io/dockerconfigjson"""
      }
      break; 


//       case 'service': 
//       script{
//         writeFile file: "k8s/${resource.kind}-${resource.name}.yaml", text: """
// apiVersion: v1
// kind: Service
// metadata:
//   namespace: ${namespace}
//   name: ${resource.name}
// spec:
//   ports:
//     - name: "http"
//       port: ${resource.port}
//       targetPort: ${resource.port}
//   selector:
//     app: ${resource.target}"""
//       }
//       break; 


//       case 'ingress': 
//       script{
//         writeFile file: "k8s/${resource.kind}-${resource.name}.yaml", text: """
// apiVersion: v1
// kind: Service
// metadata:
//   namespace: ${namespace}
//   name: ${resource.name}
// spec:
//   ports:
//     - name: "http"
//       port: ${resource.port}
//       targetPort: ${resource.port}
//   selector:
//     app: ${resource.target}
// ---
// apiVersion: extensions/v1beta1
// kind: Ingress
// metadata:
//   annotations:
//     cert-manager.io/cluster-issuer: letsencrypt
//     kubernetes.io/ingress.class: nginx
//     kubernetes.io/ingress.provider: nginx
//   name: ${resource.name}
//   namespace: ${namespace}
// spec:
//   rules:
//   - host: ${hostname}
//     http:
//       paths:
//       - backend:
//           serviceName: ${resource.name}
//           servicePort: ${resource.port}
//   tls:
//   - hosts:
//     - ${hostname}
//     secretName: ${resource.name}-tls-cert"""
//       }
//     break; 

    case 'app': 
      script{
        writeFile file: "k8s/${resource.kind}-${resource.name}.yaml", text: """
apiVersion: apps/v1
kind: Deployment
metadata:
  namespace: ${namespace}
  labels:
    app: ${resource.name}
  name: ${resource.name}
spec:
  replicas: 1
  strategy: {}
  selector:
    matchLabels:
      app: ${resource.name}
  template:
    metadata:
      labels:
        build_number: \$BUILD_NUMBER
        app: ${resource.name}
    spec:
      containers:
        - image: ${resource.image}
          name: ${resource.name}
          imagePullPolicy: Always
          resources: {}
          stdin: true
          tty: true
${generateVolumeMountYaml(resource)}
${generateEnvYaml(resource.env)}
${generateVolumeYaml(resource)}
      restartPolicy: Always
      imagePullSecrets:
        - name: ${resource.imagePullSecret}
---
apiVersion: v1
kind: Service
metadata:
  namespace: ${namespace}
  name: ${resource.name}
spec:
  ports:
    - name: "http"
      port: ${resource.port}
      targetPort: ${resource.port}
  selector:
    app: ${resource.name}
${generateIngressYaml(resource, namespace)}
${generatePVCYaml(resource, namespace)}
"""
      }
      break; 

  } 
}


def generateEnvYaml (env) {
  def returnString = """
          env:"""
  env.each { e ->
      returnString += """
          - name: ${e.name}
            value: ${e.value}
"""
  }
  return env ? returnString : ""
}


def generateIngressYaml (resource, namespace) {
  def returnString = """
---
apiVersion: extensions/v1beta1
kind: Ingress
metadata:
  annotations:
    cert-manager.io/cluster-issuer: letsencrypt
    kubernetes.io/ingress.class: nginx
    kubernetes.io/ingress.provider: nginx
  name: ${resource.name}
  namespace: ${namespace}
spec:
  rules:
  - host: ${resource.hostname}
    http:
      paths:
      - backend:
          serviceName: ${resource.name}
          servicePort: ${resource.port}
  tls:
  - hosts:
    - ${resource.hostname}
    secretName: ${resource.name}-tls-cert
"""
  return resource.hostname ? returnString : ""
}


def generatePVCYaml (resource, namespace) {
  def returnString = """
---
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: ${resource.name}
  namespace: ${namespace}
spec:
  accessModes:
  - ReadWriteOnce
  resources:
    requests:
      storage: 20Gi
  storageClassName: vsphere-thin
  volumeMode: Filesystem
"""

  return resource.persist ? returnString : ""
}


def generateVolumeYaml (resource) {
  def returnString = """
      volumes:
      - name: data
        persistentVolumeClaim:
          claimName: ${resource.name}
"""

  return resource.persist ? returnString : ""
}

def generateVolumeMountYaml (resource) {
  def returnString = """
          volumeMounts:
          - mountPath: \"${resource.persist}\"
            name: data
"""

  return resource.persist ? returnString : ""
}


