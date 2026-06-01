# Kubernetes Deployment + Service in FluxDSL

apiVersion: "apps/v1"
kind: "Deployment"
metadata {
  name: "myapp"
  namespace: "production"
  labels {
    app: "myapp"
    tier: "backend"
    managed_by: "fluxdsl"
  }
}

spec {
  replicas: 3
  selector {
    matchLabels {
      app: "myapp"
    }
  }

  template {
    metadata {
      labels {
        app: "myapp"
      }
    }

    spec {
      containers [
        {
          name: "myapp"
          image: "myapp/api:2.1.0"
          ports [
            {
              containerPort: 8080
              protocol: "TCP"
            }
          ]

          env [
            {
              name: "DB_HOST"
              value: "db.internal"
            }
            {
              name: "DB_PORT"
              value: "5432"
            }
            {
              name: "LOG_LEVEL"
              value: "info"
            }
          ]

          resources {
            requests {
              cpu: "250m"
              memory: "256Mi"
            }
            limits {
              cpu: "500m"
              memory: "512Mi"
            }
          }

          livenessProbe {
            httpGet {
              path: "/health"
              port: 8080
            }
            initialDelaySeconds: 10
            periodSeconds: 15
          }

          volumeMounts [
            {
              name: "config"
              mountPath: "/etc/myapp"
            }
          ]
        }
      ]

      volumes [
        {
          name: "config"
          configMap {
            name: "myapp-config"
          }
        }
      ]
    }
  }
}

# Separate document: Service object
apiVersion: "v1"
kind: "Service"
metadata {
  name: "myapp"
  namespace: "production"
}

spec {
  selector {
    app: "myapp"
  }
  ports [
    {
      port: 80
      targetPort: 8080
      protocol: "TCP"
      name: "http"
    }
    {
      port: 443
      targetPort: 8080
      protocol: "TCP"
      name: "https"
    }
  ]
  type: "ClusterIP"
}
