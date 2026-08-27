# CloudFront distribution in front of the frontend ALB.
#
# The ALB is created by the AWS Load Balancer Controller (from the banking
# k8s/ingress.yaml bootstrapped via ArgoCD), so it is looked up by tag rather
# than created here. TLS terminates at CloudFront via an ACM certificate.

data "aws_lb" "frontend_alb" {
  tags = {
    "kubernetes.io/cluster/${var.cluster_name}" = "owned"
  }
}

data "aws_acm_certificate" "frontend" {
  domain   = var.domain_name
  statuses = ["ISSUED"]
}

resource "aws_s3_bucket" "cloudfront_logs" {
  bucket = "banking-cloudfront-access-logs"
}

resource "aws_s3_bucket_ownership_controls" "cloudfront_logs" {
  bucket = aws_s3_bucket.cloudfront_logs.id

  rule {
    object_ownership = "BucketOwnerPreferred"
  }
}

resource "aws_s3_bucket_acl" "cloudfront_logs" {
  depends_on = [aws_s3_bucket_ownership_controls.cloudfront_logs]

  bucket = aws_s3_bucket.cloudfront_logs.id
  acl    = "log-delivery-write"
}

# No caching for /api/*: the gateway response is dynamic and must reach the
# origin on every request. Cookies/headers that identify the session are
# forwarded.
resource "aws_cloudfront_cache_policy" "api" {
  name        = "banking-api-no-cache"
  comment     = "Forward session cookies/headers to the gateway, zero TTL"
  default_ttl = 0
  min_ttl     = 0
  max_ttl     = 0

  parameters_in_cache_key_and_forwarded_to_origin {
    cookies_config {
      cookie_behavior = "whitelist"
      cookies {
        items = ["JSESSIONID", "SESSION"]
      }
    }

    headers_config {
      header_behavior = "whitelist"
      headers {
        items = ["Authorization", "Origin", "X-Forwarded-Proto", "Accept"]
      }
    }

    query_strings_config {
      query_string_behavior = "all"
    }
  }
}

# Static SPA assets: let origin Cache-Control headers dictate TTL (nginx sets
# immutable/1y for hashed assets), index.html is served un-cached because its
# TTL floor is 0.
resource "aws_cloudfront_cache_policy" "frontend" {
  name        = "banking-frontend-static"
  comment     = "Respect origin cache headers; SPA index.html falls back to TTL 0"
  default_ttl = 0
  min_ttl     = 0
  max_ttl     = 31536000

  parameters_in_cache_key_and_forwarded_to_origin {
    cookies_config {
      cookie_behavior = "none"
    }

    headers_config {
      header_behavior = "none"
    }

    query_strings_config {
      query_string_behavior = "none"
    }

    enable_accept_encoding_brotli = true
    enable_accept_encoding_gzip   = true
  }
}

resource "aws_cloudfront_distribution" "frontend" {
  enabled         = true
  is_ipv6_enabled = true
  comment         = "Banking platform frontend for ${var.domain_name}"
  price_class     = var.cloudfront_price_class
  aliases         = [var.domain_name]

  origin {
    domain_name = data.aws_lb.frontend_alb.dns_name
    origin_id   = "frontend-alb"

    custom_origin_config {
      http_port              = 80
      https_port             = 443
      origin_protocol_policy = "https-only"
      origin_ssl_protocols   = ["TLSv1.2"]
    }

    custom_header {
      name  = "X-Origin-Verify"
      value = var.origin_verify_header_value
    }
  }

  default_cache_behavior {
    target_origin_id       = "frontend-alb"
    viewer_protocol_policy = "redirect-to-https"
    compress               = true
    cache_policy_id        = aws_cloudfront_cache_policy.frontend.id
    allowed_methods        = ["GET", "HEAD", "OPTIONS"]
    cached_methods         = ["GET", "HEAD", "OPTIONS"]
  }

  ordered_cache_behavior {
    path_pattern           = "/api/*"
    target_origin_id       = "frontend-alb"
    viewer_protocol_policy = "https-only"
    compress               = true
    cache_policy_id        = aws_cloudfront_cache_policy.api.id
    allowed_methods        = ["DELETE", "GET", "HEAD", "OPTIONS", "PATCH", "POST", "PUT"]
    cached_methods         = ["GET", "HEAD", "OPTIONS"]
  }

  logging_config {
    bucket          = aws_s3_bucket.cloudfront_logs.bucket_domain_name
    include_cookies = false
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    acm_certificate_arn      = data.aws_acm_certificate.frontend.arn
    ssl_support_method       = "sni-only"
    minimum_protocol_version = "TLSv1.2_2021"
  }

  tags = {
    component = "frontend"
    managed   = "terraform"
  }
}