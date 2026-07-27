pipeline {
    agent any

    environment {
        IMAGE_TAG = "${env.BUILD_NUMBER}"
    }

    stages {

        stage('Checkout') {
            steps {
                git(
                    branch: 'main',
                    credentialsId: 'github_nikitamathe',
                    url: 'https://github.com/nikitamathe/bankapp-devops.git'
                )
            }
        }

        stage('Gitleaks Scan') {
            steps {
                sh '''
                    echo "======================================="
                    echo "Running Gitleaks Secret Scan..."
                    echo "======================================="

                    gitleaks detect --source . --verbose
                '''
            }
        }

        stage('Semgrep Scan') {
            steps {
                sh '''
                    echo "======================================="
                    echo "Running Semgrep SAST Scan..."
                    echo "======================================="

                    semgrep scan --config auto .
                '''
            }
        }

        stage('Build Artifacts') {
            steps {
                sh '''
                    echo "======================================="
                    echo "Compiling Java Microservices..."
                    echo "======================================="

                    services="auth-service account-service transaction-service notification-service api-gateway"

                    for svc in $services; do
                        echo "Building $svc..."
                        cd $svc
                        mvn clean package -DskipTests
                        cd ..
                    done
                '''
            }
        }

        stage('SonarQube Analysis') {
            steps {
                script {
                    def scannerHome = tool 'SonarScanner'

                    withSonarQubeEnv('SonarQube') {
                        sh """
                            ${scannerHome}/bin/sonar-scanner \
                              -Dsonar.projectKey=bankapp-devops \
                              -Dsonar.projectName="Enterprise DevSecOps Observability Platform" \
                              -Dsonar.sources=. \
                              -Dsonar.java.binaries=. \
                              -Dsonar.exclusions=**/node_modules/**,**/target/**,**/*.jar,**/*.zip,**/*.tar.gz
                        """
                    }
                }
            }
        }

        stage('Grype Filesystem Scan') {
            steps {
                sh '''
                    echo "======================================="
                    echo "Running Grype Filesystem Scan..."
                    echo "======================================="

                    docker volume create grype-db || true

                    mkdir -p reports

                    if [ ! -f reports/html.tmpl ]; then
                        echo "Missing Grype HTML template at reports/html.tmpl"
                        exit 1
                    fi

                    docker run --rm \
                      -v "$WORKSPACE:/src" \
                      -v "$WORKSPACE/reports:/reports" \
                      -v grype-db:/root/.cache/grype/db \
                      anchore/grype:latest \
                      dir:/src \
                      -o template \
                      -t /reports/html.tmpl \
                      > reports/grype-report.html
                '''
            }
        }

        stage('Publish Grype Report') {
            steps {
                publishHTML(
                    target: [
                        allowMissing: false,
                        alwaysLinkToLastBuild: true,
                        keepAll: true,
                        reportDir: 'reports',
                        reportFiles: 'grype-report.html',
                        reportName: 'Grype HTML Report',
                        reportTitles: 'Grype Vulnerability Report'
                    ]
                )
                archiveArtifacts artifacts: 'reports/grype-report.html', fingerprint: true
            }
        }

        stage('Login to Docker Hub') {
            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: 'dockerhub_nikitamathe',
                        usernameVariable: 'DOCKERHUB_USER',
                        passwordVariable: 'DOCKERHUB_PASS'
                    )
                ]) {
                    sh '''
                        echo "$DOCKERHUB_PASS" | docker login -u "$DOCKERHUB_USER" --password-stdin
                    '''
                }
            }
        }

        stage('Build Docker Images') {
            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: 'dockerhub_nikitamathe',
                        usernameVariable: 'DOCKERHUB_USER',
                        passwordVariable: 'DOCKERHUB_PASS'
                    )
                ]) {
                    sh """
                        set -e

                        services="auth-service account-service transaction-service notification-service api-gateway frontend"

                        for svc in \$services; do
                            echo "Building \$svc..."

                            docker build \
                              -t \$DOCKERHUB_USER/\$svc:${IMAGE_TAG} \
                              ./\$svc

                            docker tag \
                              \$DOCKERHUB_USER/\$svc:${IMAGE_TAG} \
                              \$DOCKERHUB_USER/\$svc:latest
                        done
                    """
                }
            }
        }

        stage('Trivy Image Scan') {
            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: 'dockerhub_nikitamathe',
                        usernameVariable: 'DOCKERHUB_USER',
                        passwordVariable: 'DOCKERHUB_PASS'
                    )
                ]) {
                    sh """
                        services="auth-service account-service transaction-service notification-service api-gateway frontend"

                        for svc in \$services; do
                            echo "Scanning \$svc..."

                            trivy image \
                              --severity HIGH,CRITICAL \
                              --exit-code 1 \
                              \$DOCKERHUB_USER/\$svc:${IMAGE_TAG}
                        done
                    """
                }
            }
        }

        stage('Push Docker Images') {
            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: 'dockerhub_nikitamathe',
                        usernameVariable: 'DOCKERHUB_USER',
                        passwordVariable: 'DOCKERHUB_PASS'
                    )
                ]) {
                    sh """
                        services="auth-service account-service transaction-service notification-service api-gateway frontend"

                        for svc in \$services; do
                            echo "Pushing \$svc..."

                            docker push \$DOCKERHUB_USER/\$svc:${IMAGE_TAG}
                            docker push \$DOCKERHUB_USER/\$svc:latest
                        done
                    """
                }
            }
        }
    }

    post {
        always {
            sh 'docker logout || true'
        }

        success {
            echo "Pipeline completed successfully."
        }

        failure {
            echo "Pipeline failed."
        }
    }
}