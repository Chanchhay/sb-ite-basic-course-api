pipeline {
    agent any

    options {
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '20'))
        timeout(time: 30, unit: 'MINUTES')
        disableConcurrentBuilds()
    }

    environment {
        REGISTRY   = 'asia-southeast1-docker.pkg.dev'
        PROJECT_ID = 'project-6f8e390c-7ad4-4d23-b1b'
        REPOSITORY = 'backend-images'
        APP_NAME   = 'ipos-api'

        // Replace YOUR_USER with the Linux user on main-app-server
        DEPLOY_HOST = 'chanchhay@10.148.0.2'
        DEPLOY_DIR  = '/opt/apps/ipos'
    }

    stages {

        stage('Prepare') {
            steps {
                sh '''
                    chmod +x gradlew

                    echo "=== Java ==="
                    java -version

                    echo "=== Gradle ==="
                    ./gradlew --version
                '''
            }
        }

        stage('Test & Build JAR') {
            steps {
                sh '''
                    ./gradlew \
                        test \
                        bootJar \
                        --no-daemon \
                        --build-cache
                '''
            }

            post {
                always {
                    junit(
                        allowEmptyResults: true,
                        testResults: 'build/test-results/test/*.xml'
                    )
                }
            }
        }

        stage('Prepare Docker Artifact') {
            steps {
                sh '''
                    rm -f app.jar

                    JAR_FILE="$(find build/libs \
                        -maxdepth 1 \
                        -type f \
                        -name '*.jar' \
                        ! -name '*-plain.jar' \
                        -print \
                        -quit)"

                    if [ -z "$JAR_FILE" ]; then
                        echo "ERROR: Spring Boot JAR not found"
                        exit 1
                    fi

                    echo "Using JAR: $JAR_FILE"

                    cp "$JAR_FILE" app.jar

                    ls -lh app.jar
                '''
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

                    echo "Git SHA: ${env.GIT_SHA}"
                    echo "Docker image: ${env.IMAGE}"
                }
            }
        }

        stage('Docker Build') {
            when {
                anyOf {
                    branch 'chanchhay-dev'
                    branch 'main'
                }
            }

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
            when {
                anyOf {
                    branch 'chanchhay-dev'
                    branch 'main'
                }
            }

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
                        echo "Deploying $IMAGE"

                        ssh \
                            -o StrictHostKeyChecking=accept-new \
                            "$DEPLOY_HOST" \
                            "DEPLOY_DIR='$DEPLOY_DIR' GIT_SHA='$GIT_SHA' bash -s" <<'REMOTE'

set -euo pipefail

cd "$DEPLOY_DIR"

echo "Updating IMAGE_TAG=$GIT_SHA"

if grep -q '^IMAGE_TAG=' .env; then
    sed -i "s|^IMAGE_TAG=.*|IMAGE_TAG=$GIT_SHA|" .env
else
    echo "IMAGE_TAG=$GIT_SHA" >> .env
fi

echo
echo "Resolved API image:"
docker compose config | grep 'ipos-api:' || true

echo
echo "Pulling new API image..."
docker compose pull api

echo
echo "Deploying API..."
docker compose up -d --no-deps api

echo
echo "Container status:"
docker compose ps

echo
echo "Running image:"
docker inspect "$(docker compose ps -q api)" \
    --format '{{.Config.Image}}'

REMOTE
                    '''
                }
            }
        }
    }

    post {
        success {
            echo '=============================='
            echo 'CI/CD SUCCESS'
            echo "Image: ${env.IMAGE}"
            echo '=============================='
        }

        failure {
            echo '=============================='
            echo 'CI/CD FAILED'
            echo '=============================='
        }

        always {
            sh '''
                rm -f app.jar
                docker image prune -f || true
            '''
        }
    }
}