pipeline {
    agent any

    options {
        timestamps()
        buildDiscarder(
                logRotator(
                        numToKeepStr: '20',
                        artifactNumToKeepStr: '5'
                )
        )
        timeout(time: 30, unit: 'MINUTES')
        disableConcurrentBuilds()
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
                            "DEPLOY_DIR='$DEPLOY_DIR' IMAGE='$IMAGE' bash -s" <<'REMOTE'

set -euo pipefail

cd "$DEPLOY_DIR"

compose() {
    docker compose \
        --env-file .env \
        --env-file deploy.env \
        "$@"
}

echo "========================================"
echo "Deploying iPOS API"
echo "Image: $IMAGE"
echo "========================================"

echo
echo "Checking required files..."

if [ ! -f compose.yml ]; then
    echo "ERROR: compose.yml not found"
    exit 1
fi

if [ ! -f .env ]; then
    echo "ERROR: .env not found"
    exit 1
fi

if [ ! -f deploy.env ]; then
    echo "Creating deploy.env"
    touch deploy.env
fi

echo
echo "Backing up deploy.env..."
cp deploy.env deploy.env.bak

echo
echo "Updating IPOS_IMAGE..."

if grep -q '^IPOS_IMAGE=' deploy.env; then
    sed -i "s|^IPOS_IMAGE=.*|IPOS_IMAGE=$IMAGE|" deploy.env
else
    echo "IPOS_IMAGE=$IMAGE" >> deploy.env
fi

echo
echo "Validating Docker Compose..."

if ! compose config >/dev/null; then
    echo "ERROR: Docker Compose validation failed"
    mv deploy.env.bak deploy.env
    exit 1
fi

echo "Docker Compose configuration valid."

echo
echo "Resolved API image:"
compose config \
    | grep 'image:' \
    | grep 'ipos-api' \
    || true

echo
echo "Pulling API image..."
compose pull api

echo
echo "Deploying API..."
compose up -d --no-deps api

echo
echo "Waiting for container..."
sleep 5

API_CONTAINER="$(compose ps -q api)"

if [ -z "$API_CONTAINER" ]; then
    echo "ERROR: API container not found"
    mv deploy.env.bak deploy.env
    exit 1
fi

echo
echo "Container status:"
compose ps

echo
echo "Running image:"
docker inspect "$API_CONTAINER" \
    --format '{{.Config.Image}}'

echo
echo "Container state:"
docker inspect "$API_CONTAINER" \
    --format 'Status={{.State.Status}} Running={{.State.Running}} Restarting={{.State.Restarting}}'

RUNNING="$(docker inspect "$API_CONTAINER" \
    --format '{{.State.Running}}')"

RESTARTING="$(docker inspect "$API_CONTAINER" \
    --format '{{.State.Restarting}}')"

if [ "$RUNNING" != "true" ] || [ "$RESTARTING" = "true" ]; then
    echo
    echo "ERROR: API container failed to start correctly"

    echo
    echo "Recent API logs:"
    docker logs --tail=100 "$API_CONTAINER" || true

    echo
    echo "Restoring previous deployment configuration..."
    mv deploy.env.bak deploy.env

    echo
    echo "Restoring previous API..."
    compose pull api || true
    compose up -d --no-deps api || true

    exit 1
fi

rm -f deploy.env.bak

echo
echo "========================================"
echo "Deployment successful"
echo "========================================"

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

                echo "Cleaning dangling Docker images..."
                docker image prune -f || true

                echo "Cleaning Docker build cache older than 2 days..."
                docker builder prune \
                    -af \
                    --filter "until=48h" \
                    || true
            '''
        }
    }
}