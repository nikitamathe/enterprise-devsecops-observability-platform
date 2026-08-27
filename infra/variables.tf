variable "aws_region" {
  description = "AWS region where the EKS cluster and ACM certificate live"
  type        = string
  default     = "us-east-1"
}

variable "cluster_name" {
  description = "Name of the EKS cluster owning the frontend ALB"
  type        = string
  default     = "banking-eks"
}

variable "domain_name" {
  description = "Public domain served by CloudFront, e.g. bank.example.com"
  type        = string
}

variable "cloudfront_price_class" {
  description = "CloudFront price class (PriceClass_100, PriceClass_200, PriceClass_All)"
  type        = string
  default     = "PriceClass_100"
}

variable "origin_verify_header_value" {
  description = "Shared secret value sent as X-Origin-Verify by CloudFront to the ALB; the ALB ingress must reject requests without it"
  type        = string
  sensitive   = true
}