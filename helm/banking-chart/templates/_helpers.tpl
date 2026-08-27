{{/*
Namespace the chart installs into.
*/}}
{{- define "banking.namespace" -}}
{{- .Values.namespace | default "banking" -}}
{{- end -}}

{{/*
Standard pod labels for a workload.
*/}}
{{- define "banking.labels" -}}
app: {{ .app }}
app.kubernetes.io/name: {{ .app }}
app.kubernetes.io/part-of: banking
{{- end -}}

{{/*
Container image reference from registry/repository/tag.
*/}}
{{- define "banking.image" -}}
{{- $reg := .registry | default "" -}}
{{- $repo := .repository | required "image.repository is required" -}}
{{- $tag := .tag | default "latest" -}}
{{- if $reg -}}{{ printf "%s/%s:%s" $reg $repo $tag }}{{- else -}}{{ printf "%s:%s" $repo $tag }}{{- end -}}
{{- end -}}