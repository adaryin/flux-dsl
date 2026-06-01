# CI/CD pipeline configuration

pipeline {
  name: "myapp-build-and-deploy"
  trigger: "push"

  stages [
    {
      name: "lint"
      image: "node:20"
      commands: ["npm ci", "npm run lint"]
    }

    {
      name: "test"
      image: "node:20"
      commands: ["npm ci", "npm run test -- --coverage"]
      artifacts {
        paths: ["coverage/"]
        retention_days: 30
      }
    }

    {
      name: "build"
      image: "docker:24"
      commands: ["docker build -t myapp:latest ."]
      depends_on: ["lint", "test"]
    }

    {
      name: "deploy-staging"
      image: "kubectl:1.28"
      commands: ["kubectl apply -f k8s/staging"]
      depends_on: ["build"]
      only: ["main", "develop"]
    }
  ]

  notifications {
    slack {
      webhook: "https://hooks.slack.com/..."
      on: ["failure", "success"]
    }

    email {
      to: ["team-eng@example.com"]
      on: ["failure"]
    }
  }
}

# Inline schema for environment validation
_schema {
  pipeline {
    stages {
      items { type: "object", properties {
        name: { type: "string", required: true }
        image: { type: "string", required: true }
        commands: { type: "list", items { type: "string" }, required: true }
        depends_on: { type: "list", items { type: "string" } }
      } }
    }
  }
}
