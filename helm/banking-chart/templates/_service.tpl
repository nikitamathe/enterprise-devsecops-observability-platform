{{/*
Generates a Spring service Deployment + ClusterIP Service.
Expects: name, port, replicas, image{repository,tag}, resources,
tracingSamplingProbability, serviceHostEnv, ctx (root context for namespace/values).
*/}}
{{- define "banking.service" -}}
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{ .name }}
  namespace: {{ include "banking.namespace" .ctx }}
spec:
  replicas: {{ .replicas }}
  selector:
    matchLabels:
      app: {{ .name }}
  template:
    metadata:
      labels:
        app: {{ .name }}
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/path: /actuator/prometheus
        prometheus.io/port: "{{ .port }}"
    spec:
      securityContext:
        runAsNonRoot: true
        runAsUser: 1000
        fsGroup: 1000
      containers:
        - name: {{ .name }}
          image: {{ include "banking.image" (dict "registry" .ctx.Values.imageRegistry "repository" .image.repository "tag" .image.tag) }}
          ports:
            - containerPort: {{ .port }}
          securityContext:
            allowPrivilegeEscalation: false
            readOnlyRootFilesystem: true
            capabilities:
              drop: ["ALL"]
          resources:
            {{- toYaml .resources | nindent 12 }}
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: {{ .port }}
            initialDelaySeconds: 30
            periodSeconds: 10
            timeoutSeconds: 5
            failureThreshold: 3
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: {{ .port }}
            initialDelaySeconds: 60
            periodSeconds: 15
            timeoutSeconds: 5
            failureThreshold: 3
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: "prod"
            - name: MANAGEMENT_TRACING_SAMPLING_PROBABILITY
              value: {{ .tracingSamplingProbability | quote }}
            - name: SPRING_DATASOURCE_URL
              value: "jdbc:mysql://{{ .ctx.Values.database.host }}:{{ .ctx.Values.database.port }}/{{ .ctx.Values.database.name }}?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
            - name: SPRING_DATASOURCE_USERNAME
              valueFrom:
                secretKeyRef:
                  name: mysql-secret
                  key: mysql-user
            - name: SPRING_DATASOURCE_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: mysql-secret
                  key: mysql-user-password
            - name: JWT_SECRET
              valueFrom:
                secretKeyRef:
                  name: jwt-secret
                  key: jwt-secret
            {{- range .serviceHostEnv }}
            - name: {{ .name }}
              value: {{ .value | quote }}
            {{- end }}
---
apiVersion: v1
kind: Service
metadata:
  name: {{ .name }}
  namespace: {{ include "banking.namespace" .ctx }}
spec:
  selector:
    app: {{ .name }}
  ports:
    - port: {{ .port }}
      targetPort: {{ .port }}
{{- end }}