pipeline {
    agent any

    environment {
        IMAGE_TAG = "${env.BUILD_NUMBER}"
        CACHE_DAYS = '5'
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

                    ensure_image_available() {
                        local image="$1"
                        if docker image inspect "$image" >/dev/null 2>&1; then
                            echo "Using cached image: $image"
                        else
                            echo "Pulling missing image: $image"
                            docker pull "$image"
                        fi
                    }

                    ensure_image_available zricethezav/gitleaks:v8.18.2

                    mkdir -p reports

                    set +e
                    docker run --rm \
                      -v "$WORKSPACE:/src" \
                      -w /src \
                      zricethezav/gitleaks:v8.18.2 detect \
                      --source . \
                      --report-format json \
                      --report-path reports/gitleaks-report.json \
                      --verbose > reports/gitleaks-report.log 2>&1
                    gitleaks_exit=$?
                    set -e

                    echo "gitleaks_exit_code=$gitleaks_exit" > reports/gitleaks-status.txt
                '''

                sh '''
                    python3 - <<'PY'
import json
from pathlib import Path

reports_dir = Path('reports')
html_path = reports_dir / 'gitleaks-report.html'
log_path = reports_dir / 'gitleaks-report.log'
json_path = reports_dir / 'gitleaks-report.json'

log_text = log_path.read_text(errors='ignore') if log_path.exists() else ''
findings = []
if json_path.exists():
    try:
        payload = json.loads(json_path.read_text(errors='ignore'))
        if isinstance(payload, list):
            findings = payload
        elif isinstance(payload, dict):
            findings = payload.get('findings', [])
    except Exception:
        findings = []

summary = f"<h2>Gitleaks Scan Report</h2><p>Findings: {len(findings)}</p>"
if findings:
    rows = ''.join(
        f"<tr><td>{index + 1}</td><td>{entry.get('description', 'N/A')}</td><td>{entry.get('file', 'N/A')}</td><td>{entry.get('line', 'N/A')}</td></tr>"
        for index, entry in enumerate(findings[:20])
    )
    summary += f"<table><tr><th>#</th><th>Description</th><th>File</th><th>Line</th></tr>{rows}</table>"
else:
    summary += '<p>No secrets detected.</p>'

summary += '<h3>Log</h3><pre>' + chr(10).join(log_text.splitlines()[-40:]) + '</pre>'
html_path.write_text('<html><body>' + summary + '</body></html>')
PY
                '''
            }
        }

        stage('Semgrep Scan') {
            steps {
                sh '''
                    echo "======================================="
                    echo "Running Semgrep SAST Scan..."
                    echo "======================================="

                    ensure_image_available() {
                        local image="$1"
                        if docker image inspect "$image" >/dev/null 2>&1; then
                            echo "Using cached image: $image"
                        else
                            echo "Pulling missing image: $image"
                            docker pull "$image"
                        fi
                    }

                    ensure_image_available returntocorp/semgrep:1.94.0

                    mkdir -p reports

                    set +e
                    docker run --rm \
                      -v "$WORKSPACE:/src" \
                      -w /src \
                      returntocorp/semgrep:1.94.0 \
                      semgrep scan --config auto --json --output reports/semgrep-report.json . > reports/semgrep-report.log 2>&1
                    semgrep_exit=$?
                    set -e

                    echo "semgrep_exit_code=$semgrep_exit" > reports/semgrep-status.txt
                '''

                sh '''
                    python3 - <<'PY'
import json
from pathlib import Path

reports_dir = Path('reports')
html_path = reports_dir / 'semgrep-report.html'
log_path = reports_dir / 'semgrep-report.log'
json_path = reports_dir / 'semgrep-report.json'

log_text = log_path.read_text(errors='ignore') if log_path.exists() else ''
findings = []
if json_path.exists():
    try:
        payload = json.loads(json_path.read_text(errors='ignore'))
        findings = payload.get('results', []) if isinstance(payload, dict) else []
    except Exception:
        findings = []

summary = f"<h2>Semgrep Scan Report</h2><p>Findings: {len(findings)}</p>"
if findings:
    rows = ''.join(
        f"<tr><td>{index + 1}</td><td>{item.get('check_id', 'N/A')}</td><td>{item.get('path', 'N/A')}</td></tr>"
        for index, item in enumerate(findings[:20])
    )
    summary += f"<table><tr><th>#</th><th>Check</th><th>Path</th></tr>{rows}</table>"
else:
    summary += '<p>No issues detected.</p>'

summary += '<h3>Log</h3><pre>' + chr(10).join(log_text.splitlines()[-40:]) + '</pre>'
html_path.write_text('<html><body>' + summary + '</body></html>')
PY
                '''
            }
        }

        stage('Build Artifacts') {
            steps {
                sh '''
                    echo "======================================="
                    echo "Building Java microservices with Docker Compose..."
                    echo "======================================="

                    ensure_image_available() {
                        local image="$1"
                        if docker image inspect "$image" >/dev/null 2>&1; then
                            echo "Using cached image: $image"
                        else
                            echo "Pulling missing image: $image"
                            docker pull "$image"
                        fi
                    }

                    ensure_image_available maven:3.9.9-eclipse-temurin-21

                    if ! docker compose version >/dev/null 2>&1; then
                        echo "docker compose is not available on this Jenkins agent"
                        exit 1
                    fi

                    docker compose build \
                      auth-service \
                      account-service \
                      transaction-service \
                      notification-service \
                      api-gateway
                '''
            }
        }

        stage('SonarQube Analysis') {
            steps {
                sh '''
                    echo "======================================="
                    echo "Running SonarQube Analysis..."
                    echo "======================================="

                    mkdir -p reports

                    set +e
                    if [ -n "${SONAR_HOST_URL:-}" ] && [ -n "${SONAR_TOKEN:-}" ]; then
                        docker run --rm \
                          -e SONAR_HOST_URL \
                          -e SONAR_TOKEN \
                          -v "$WORKSPACE:/src" \
                          -w /src \
                          sonarsource/sonar-scanner-cli:5 \
                          -Dsonar.projectKey=bankapp-devops \
                          -Dsonar.projectName="Enterprise DevSecOps Observability Platform" \
                          -Dsonar.sources=. \
                          -Dsonar.java.binaries=. \
                          -Dsonar.exclusions=**/node_modules/**,**/target/**,**/*.jar,**/*.zip,**/*.tar.gz \
                          > reports/sonarqube-report.log 2>&1
                        sonar_exit=$?
                    else
                        echo "SonarQube host URL or token is not configured. Skipping analysis and generating a placeholder report." > reports/sonarqube-report.log
                        sonar_exit=0
                    fi
                    set -e

                    echo "sonarqube_exit_code=$sonar_exit" > reports/sonarqube-status.txt
                '''

                sh '''
                    python3 - <<'PY'
from pathlib import Path

reports_dir = Path('reports')
html_path = reports_dir / 'sonarqube-report.html'
log_path = reports_dir / 'sonarqube-report.log'

log_text = log_path.read_text(errors='ignore') if log_path.exists() else ''
status = 'completed'
if 'ANALYSIS SUCCESSFUL' in log_text or 'SUCCESS' in log_text.upper():
    status = 'successful'
elif 'not configured' in log_text.lower() or 'skipping' in log_text.lower():
    status = 'skipped'
else:
    status = 'review required'

summary = f"<h2>SonarQube Scan Report</h2><p>Status: {status}</p><pre>{log_text[-4000:]}</pre>"
html_path.write_text('<html><body>' + summary + '</body></html>')
PY
                '''
            }
        }

        stage('Grype Filesystem Scan') {
            steps {
                sh '''
                    echo "======================================="
                    echo "Running Grype Filesystem Scan..."
                    echo "======================================="

                    ensure_image_available() {
                        local image="$1"
                        if docker image inspect "$image" >/dev/null 2>&1; then
                            echo "Using cached image: $image"
                        else
                            echo "Pulling missing image: $image"
                            docker pull "$image"
                        fi
                    }

                    ensure_image_available anchore/grype:latest

                    mkdir -p reports grype-db

                    if [ ! -d grype-db ]; then
                        mkdir -p grype-db
                    fi

                    if [ ! -f reports/html.tmpl ]; then
                        echo "Missing Grype HTML template at reports/html.tmpl"
                        exit 1
                    fi

                    docker run --rm \
                      -v "$WORKSPACE:/src" \
                      -v "$WORKSPACE/reports:/reports" \
                      -v "$WORKSPACE/grype-db:/root/.cache/grype/db" \
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
                archiveArtifacts(
                    artifacts: 'reports/grype-report.html',
                    allowEmptyArchive: true,
                    fingerprint: true
                )
            }
        }

        stage('Publish Scan Reports') {
            steps {
                sh '''
                    echo "======================================="
                    echo "Archiving scan reports..."
                    echo "======================================="

                    if [ -d reports ]; then
                        find reports -maxdepth 1 -type f | sort
                    else
                        echo "No reports directory found"
                    fi
                '''

                archiveArtifacts(
                    artifacts: 'reports/*',
                    allowEmptyArchive: true,
                    fingerprint: true
                )
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
                            image_ref="\$DOCKERHUB_USER/\$svc:latest"

                            if docker image inspect "\$image_ref" >/dev/null 2>&1; then
                                created_at=\$(docker image inspect "\$image_ref" --format '{{.Created}}')
                                created_epoch=\$(date -d "\$created_at" +%s 2>/dev/null || echo 0)
                                now_epoch=\$(date +%s)
                                age_days=\$(( (now_epoch - created_epoch) / 86400 ))

                                if [ "\$age_days" -lt ${CACHE_DAYS} ]; then
                                    echo "Using cached build image \$image_ref (age \$age_days days)"
                                    docker tag "\$image_ref" "\$DOCKERHUB_USER/\$svc:${IMAGE_TAG}"
                                    continue
                                fi
                            fi

                            echo "Building \$svc..."
                            docker build --pull=false \
                              -t \$DOCKERHUB_USER/\$svc:${IMAGE_TAG} \
                              ./\$svc

                            docker tag \
                              \$DOCKERHUB_USER/\$svc:${IMAGE_TAG} \
                              "\$image_ref"
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
                        set -e

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