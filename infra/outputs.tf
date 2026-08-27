output "cloudfront_distribution_id" {
  description = "CloudFront distribution ID"
  value       = aws_cloudfront_distribution.frontend.id
}

output "cloudfront_domain_name" {
  description = "CloudFront distribution hostname (CNAME target for the domain's DNS record)"
  value       = aws_cloudfront_distribution.frontend.domain_name
}

output "cloudfront_aliases" {
  description = "Alternate domain names attached to the distribution"
  value       = aws_cloudfront_distribution.frontend.aliases
}

output "cloudfront_log_bucket" {
  description = "S3 bucket receiving CloudFront access logs"
  value       = aws_s3_bucket.cloudfront_logs.bucket
}