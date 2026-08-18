pipeline {
    agent any

    options {
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '20'))
        timeout(time: 30, unit: 'MINUTES')
    }

    environment {
        REGISTRY   = 'asia-southeast1-docker.pkg.dev'
        PROJECT_ID = 'project-6f8e390c-7ad4-4d23-b1b'
        REPOSITORY = 'backend-images'
        APP_NAME   = 'ipos-api'

        DEPLOY_HOST = 'chanchhay@10.148.0.2'
        DEPLOY_DIR  = '/opt/apps/ipos'
    }

    stages {

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

            post {
                always {
                    junit allowEmptyResults: true,
                          testResults: 'build/test-results/test/*.xml'
                }
            }
        }

        stage('Image Tag') {
            steps {
                script {
                    env.GIT_SHA = sh(
                        script: 'git rev-parse --short HEAD',
                        returnStdout: true
                    ).trim()

                    env.IMAGE_REPO =
                        "${REGISTRY}/${PROJECT_ID}/${REPOSITORY}/${APP_NAME}"

                    env.IMAGE =
                        "${env.IMAGE_REPO}:${env.GIT_SHA}"

                    echo "Docker image: ${env.IMAGE}"
                }
            }
        }

        stage('Docker Build') {
            steps {
                sh '''
                    docker build \
                        -t "$IMAGE" \
                        -t "$IMAGE_REPO:latest" \
                        .
                '''
            }
        }

        stage('Push Image') {
            steps {
                sh '''
                    docker push "$IMAGE"
                    docker push "$IMAGE_REPO:latest"
                '''
            }
        }

        stage('Deploy') {
            when {
                branch 'chanchhay-dev'
            }

            steps {
                sshagent(credentials: ['ipos-server-ssh']) {
                    sh '''
                        ssh \
                          -o StrictHostKeyChecking=accept-new \
                          "$DEPLOY_HOST" bash -s <<REMOTE

set -euo pipefail

cd "$DEPLOY_DIR"

if grep -q '^IMAGE_TAG=' .env; then
    sed -i "s|^IMAGE_TAG=.*|IMAGE_TAG=$GIT_SHA|" .env
else
    echo "IMAGE_TAG=$GIT_SHA" >> .env
fi

echo "Deploying image tag: $GIT_SHA"

docker compose pull api
docker compose up -d api
docker compose ps

REMOTE
                    '''
                }
            }
        }
    }

    post {
        success {
            echo "CI/CD SUCCESS"
            echo "Image: ${env.IMAGE}"
        }

        failure {
            echo "CI/CD FAILED"
        }

        always {
            sh 'docker image prune -f || true'
        }
    }
}