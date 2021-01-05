def call (type, values) {
    switch(type) {
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
    }
}