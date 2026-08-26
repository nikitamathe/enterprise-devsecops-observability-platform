pipeline {
    agent any
    options {
        timeout(time: 30, unit: 'MINUTES')
    }

    environment {
        IMAGE_TAG    = "${env.BUILD_NUMBER}"
        CACHE_DAYS   = '5'
        AWS_REGION   = 'us-east-1'
        AWS_ACCOUNT_ID = '340529311540'
        ECR_REGISTRY = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
    }

    stages {

        stage('Checkout') {
            steps {
                git(
                    branch: 'main',
                    credentialsId: 'github_nikitamathe',
                    url: 'https://github.com/nikitamathe/enterprise-devsecops-observability-platform.git'
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

                    if [ "$gitleaks_exit" -ne 0 ]; then
                        echo "ERROR: Gitleaks detected secrets. Failing build."
                        exit 1
                    fi
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
            post {
                always {
                    archiveArtifacts(
                        artifacts: 'reports/gitleaks-report.*',
                        allowEmptyArchive: true,
                        fingerprint: true
                    )
                }
            }
        }

        stage('Semgrep Scan') {
            steps {
                sh '''
                    echo "Running Semgrep SAST Scan..."
                    mkdir -p reports
                    chmod 755 reports
                    
                    docker run --rm \
                    -v "$WORKSPACE:/src" \
                    -w /src \
                    returntocorp/semgrep:1.171.0-nonroot \
                    semgrep scan --config auto --junit-xml -o reports/semgrep-report.xml .

                    # Fail if HIGH or CRITICAL findings exist
                    if [ -f reports/semgrep-report.xml ]; then
                        high_critical=$(grep -c 'severity="[HIGH\|CRITICAL]"' reports/semgrep-report.xml || true)
                        if [ "$high_critical" -gt 0 ]; then
                            echo "ERROR: Semgrep found $high_critical HIGH/CRITICAL findings. Failing build."
                            exit 1
                        fi
                    fi
                '''
            }
            post {
                always {
                    // Archive the raw XML file
                    archiveArtifacts artifacts: 'reports/semgrep-report.xml', allowEmptyArchive: true
                    
                    // Use Jenkins built-in JUnit parser to display the results in the UI
                    junit testResults: 'reports/semgrep-report.xml', allowEmptyResults: true
                }
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
                withSonarQubeEnv('SonarQube') { 
                    sh '''
                        echo "======================================="
                        echo "Running SonarScanner CLI via Docker..."
                        echo "======================================="

                        if ! docker image inspect sonarsource/sonar-scanner-cli:latest >/dev/null 2>&1; then
                            docker pull sonarsource/sonar-scanner-cli:latest
                        fi

                        # ADDED --network host so localhost:9000 resolves to the host machine
                        docker run --rm \
                          --network host \
                          -e SONAR_HOST_URL="$SONAR_HOST_URL" \
                          -e SONAR_TOKEN="$SONAR_AUTH_TOKEN" \
                          -v "$WORKSPACE:/usr/src" \
                          sonarsource/sonar-scanner-cli:latest \
                          -Dsonar.projectKey=edop \
                          -Dsonar.projectName="Enterprise DevSecOps Observability Platform" \
                          -Dsonar.sources=. \
                          -Dsonar.java.binaries=. \
                          -Dsonar.exclusions="**/reports/**"
                    '''
                }
            }
        }

        stage('Quality Gate') {
            steps {
                timeout(time: 5, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
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

                    ensure_image_available ${ECR_REGISTRY}/grype-with-db:latest

                    mkdir -p reports

                    set +e
                    docker run --rm \
                      -v "$WORKSPACE:/src" \
                      -v "$WORKSPACE/reports:/reports" \
                      ${ECR_REGISTRY}/grype-with-db:latest \
                      dir:/src \
                      -o json > reports/grype-report.json 2> reports/grype-report.log
                    grype_exit=$?
                    set -e

                    echo "grype_exit_code=$grype_exit" > reports/grype-status.txt

                    # Fail if HIGH or CRITICAL vulnerabilities found
                    if [ -f reports/grype-report.json ]; then
                        high_critical=$(python3 -c "
import json, sys
try:
    data = json.load(open('reports/grype-report.json'))
    matches = data.get('matches', []) if isinstance(data, dict) else data
    count = sum(1 for m in matches if m.get('vulnerability', {}).get('severity', '') in ('High', 'Critical', 'HIGH', 'CRITICAL'))
    print(count)
except: print(0)
" 2>/dev/null || echo 0)
                        if [ "$high_critical" -gt 0 ]; then
                            echo "ERROR: Grype found $high_critical HIGH/CRITICAL vulnerabilities. Failing build."
                            exit 1
                        fi
                    fi
                '''

                sh '''
                    python3 - <<'PY'
import json
from pathlib import Path

reports_dir = Path('reports')
html_path = reports_dir / 'grype-report.html'
json_path = reports_dir / 'grype-report.json'
log_path = reports_dir / 'grype-report.log'
status_path = reports_dir / 'grype-status.txt'

findings = []
if json_path.exists():
    try:
        payload = json.loads(json_path.read_text(errors='ignore'))
        if isinstance(payload, list):
            findings = payload
        elif isinstance(payload, dict):
            findings = payload.get('matches', [])
    except Exception:
        findings = []

log_text = log_path.read_text(errors='ignore') if log_path.exists() else ''
status_text = status_path.read_text(errors='ignore') if status_path.exists() else ''
status = 'completed'
if 'grype_exit_code=0' in status_text:
    status = 'passed'
elif 'grype_exit_code=1' in status_text:
    status = 'findings detected'
else:
    status = 'review required'

rows = ''.join(
    f"<tr><td>{index + 1}</td><td>{item.get('artifact', {}).get('name', 'N/A')}</td><td>{item.get('vulnerability', {}).get('id', 'N/A')}</td><td>{item.get('vulnerability', {}).get('severity', 'N/A')}</td></tr>"
    for index, item in enumerate(findings[:20])
)
summary = f"<h2>Grype Scan Report</h2><p>Status: {status}</p><p>Findings: {len(findings)}</p>"
if rows:
    summary += "<table><tr><th>#</th><th>Artifact</th><th>Vulnerability</th><th>Severity</th></tr>" + rows + "</table>"
else:
    summary += "<p>No package vulnerabilities detected.</p>"
summary += "<h3>Log</h3><pre>" + chr(10).join(log_text.splitlines()[-40:]) + "</pre>"
html_path.write_text('<html><body>' + summary + '</body></html>')
PY
                '''
            }
            post {
                always {
                    archiveArtifacts(
                        artifacts: 'reports/grype-report.*',
                        allowEmptyArchive: true,
                        fingerprint: true
                    )
                }
            }
        }

        

        

        

        stage('Build Docker Images') {
            steps {
                sh '''
                    set -e

                    services="auth-service account-service transaction-service notification-service api-gateway frontend"

                    for svc in $services; do
                        image_ref="$svc:latest"

                        if docker image inspect "$image_ref" >/dev/null 2>&1; then
                            created_at=$(docker image inspect "$image_ref" --format '{{.Created}}')
                            created_epoch=$(date -d "$created_at" +%s 2>/dev/null || echo 0)
                            now_epoch=$(date +%s)
                            age_days=$(( (now_epoch - created_epoch) / 86400 ))

                            if [ "$age_days" -lt ${CACHE_DAYS} ]; then
                                echo "Using cached local image $image_ref (age $age_days days)"
                                docker tag "$image_ref" "$svc:${IMAGE_TAG}"
                                continue
                            fi
                        fi

                        echo "Building $svc..."
                        docker build --pull=false \
                          -t $svc:${IMAGE_TAG} \
                          ./$svc

                        docker tag \
                          $svc:${IMAGE_TAG} \
                          $svc:latest
                    done
                '''
            }
        }

        stage('Trivy Image Scan') {
            steps {
                sh '''
                    set -e

                    mkdir -p reports
                    services="auth-service account-service transaction-service notification-service api-gateway frontend"

                    for svc in $services; do
                        echo "Scanning $svc:${IMAGE_TAG}..."
                        trivy image --severity HIGH,CRITICAL --exit-code 1 --format json -o reports/trivy-${svc}.json $svc:${IMAGE_TAG}
                    done
                '''
            }
            post {
                always {
                    archiveArtifacts(
                        artifacts: 'reports/trivy-*.json',
                        allowEmptyArchive: true,
                        fingerprint: true
                    )
                }
            }
        }

        stage('Login to ECR') {
            steps {
                withCredentials([
                    string(credentialsId: 'aws-access-key-id',     variable: 'AWS_ACCESS_KEY_ID'),
                    string(credentialsId: 'aws-secret-access-key', variable: 'AWS_SECRET_ACCESS_KEY')
                ]) {
                    sh '''
                        aws ecr get-login-password \
                            --region ${AWS_REGION} | \
                            docker login --username AWS --password-stdin ${ECR_REGISTRY}
                    '''
                }
            }
        }

        stage('Push Docker Images') {
            steps {
                withCredentials([
                    string(credentialsId: 'aws-access-key-id',     variable: 'AWS_ACCESS_KEY_ID'),
                    string(credentialsId: 'aws-secret-access-key', variable: 'AWS_SECRET_ACCESS_KEY')
                ]) {
                    sh """
                        services="auth-service account-service transaction-service notification-service api-gateway frontend"

                        for svc in \$services; do
                            echo "Tagging and pushing \$svc to ECR..."

                            # Tag with build number
                            if docker image inspect \$svc:${IMAGE_TAG} >/dev/null 2>&1; then
                                docker tag \$svc:${IMAGE_TAG} ${ECR_REGISTRY}/\$svc:${IMAGE_TAG}
                            elif docker image inspect edop/\$svc:${IMAGE_TAG} >/dev/null 2>&1; then
                                docker tag edop/\$svc:${IMAGE_TAG} ${ECR_REGISTRY}/\$svc:${IMAGE_TAG}
                            else
                                echo "Warning: no local image found for ${IMAGE_TAG} of \$svc"
                            fi

                            # Tag as latest
                            if docker image inspect \$svc:latest >/dev/null 2>&1; then
                                docker tag \$svc:latest ${ECR_REGISTRY}/\$svc:latest
                            elif docker image inspect edop/\$svc:latest >/dev/null 2>&1; then
                                docker tag edop/\$svc:latest ${ECR_REGISTRY}/\$svc:latest
                            else
                                echo "Warning: no local image found for latest of \$svc"
                            fi

                            docker push ${ECR_REGISTRY}/\$svc:${IMAGE_TAG} || true
                            docker push ${ECR_REGISTRY}/\$svc:latest || true
                        done
                    """
                }
            }
        }
    }

    post {
        always {
            sh 'docker logout ${ECR_REGISTRY} || true'
        }

        success {
            echo "Pipeline completed successfully."
        }

        failure {
            echo "Pipeline failed."
        }
    }
}