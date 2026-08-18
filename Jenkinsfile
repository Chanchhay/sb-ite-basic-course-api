pipeline {
    agent any

    environment {
        REGISTRY = 'asia-southeast1-docker.pkg.dev'
        PROJECT_ID = 'project-6f8e390c-7ad4-4d23-b1b'
        REPOSITORY = 'backend-images'
        APP_NAME = 'ipos-api'

        ISSUER_URI = 'https://auth.chanchhay.site/realms/istad-fluxipos-auth'
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Prepare') {
            steps {
                sh '''
                    chmod +x gradlew
                    java -version
                    ./gradlew --version
                '''
            }
        }

        stage('Test') {
            steps {
                sh './gradlew clean test --no-daemon'
            }
        }

        stage('Image Tag') {
            steps {
                script {
                    env.GIT_SHA = sh(
                        script: 'git rev-parse --short HEAD',
                        returnStdout: true
                    ).trim()

                    env.IMAGE = "${REGISTRY}/${PROJECT_ID}/${REPOSITORY}/${APP_NAME}:${GIT_SHA}"

                    echo "Docker image: ${env.IMAGE}"
                }
            }
        }

        stage('Docker Build') {
            steps {
                sh 'docker build -t "$IMAGE" .'
            }
        }

        stage('Push Image') {
            steps {
                sh 'docker push "$IMAGE"'
            }
        }
    }

    post {
        success {
            echo "CI SUCCESS"
            echo "Image: ${env.IMAGE}"
        }

        failure {
            echo "CI FAILED"
        }

        always {
            sh 'docker image prune -f || true'
        }
    }
}