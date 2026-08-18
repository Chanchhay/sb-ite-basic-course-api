pipeline {
    agent any

    options {
        timestamps()
        // The declarative "Checkout SCM" already clones; a second explicit
        // checkout stage only refetched the same commit.
        buildDiscarder(logRotator(numToKeepStr: '20'))
        timeout(time: 30, unit: 'MINUTES')
    }

    environment {
        REGISTRY   = 'asia-southeast1-docker.pkg.dev'
        PROJECT_ID = 'project-6f8e390c-7ad4-4d23-b1b'
        REPOSITORY = 'backend-images'
        APP_NAME   = 'ipos-api'

        // Deployment target. The app lives in /opt/apps/ipos; Traefik in
        // /opt/platform owns the public ports.
        DEPLOY_HOST = 'user@your-server'
        DEPLOY_DIR  = '/opt/apps/ipos'

        // Jenkins credential ids — create these before the first run.
        GAR_KEY  = credentials('gar-service-account')
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

                    env.IMAGE_REPO = "${REGISTRY}/${PROJECT_ID}/${REPOSITORY}/${APP_NAME}"
                    env.IMAGE      = "${env.IMAGE_REPO}:${env.GIT_SHA}"

                    echo "Docker image: ${env.IMAGE}"
                }
            }
        }

        stage('Docker Build') {
            steps {
                // Tagged twice: the SHA is what gets deployed and what a
                // rollback pins to; :latest is only a convenience pointer.
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
                // _json_key logs in with the service account file directly, so
                // the agent needs no gcloud SDK installed.
                sh '''
                    docker login -u _json_key --password-stdin \
                        "https://$REGISTRY" < "$GAR_KEY"
                    docker push "$IMAGE"
                    docker push "$IMAGE_REPO:latest"
                    docker logout "https://$REGISTRY"
                '''
            }
        }

        stage('Deploy') {
            when { branch 'chanchhay-dev' }
            steps {
                sshagent(credentials: ['ipos-server-ssh']) {
                    // The tag is written into the server's .env rather than
                    // passed inline, so a later manual `docker compose up -d`
                    // on the box brings up the same build instead of drifting
                    // back to :latest.
                    sh '''
                        ssh -o StrictHostKeyChecking=accept-new "$DEPLOY_HOST" bash -s <<REMOTE
set -euo pipefail
cd "$DEPLOY_DIR"

if grep -q '^IMAGE_TAG=' .env; then
    sed -i "s|^IMAGE_TAG=.*|IMAGE_TAG=$GIT_SHA|" .env
else
    echo "IMAGE_TAG=$GIT_SHA" >> .env
fi

docker compose pull
docker compose up -d
docker compose ps
REMOTE
                    '''
                }
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
