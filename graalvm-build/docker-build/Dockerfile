FROM debian:stable-slim AS builder
WORKDIR /cf-ddns-build/
ARG TARGETPLATFORM

COPY docker-entrypoint.sh /cf-ddns-build/docker-entrypoint.sh

ENTRYPOINT ["/cf-ddns-build/docker-entrypoint.sh"]

CMD ["sh"]

FROM debian:stable-slim
WORKDIR /cf-ddns/
ARG TARGETPLATFORM

COPY --from=builder /cf-ddns-build/cloudflare-ddns/graalvm-build/build/nativeCompile/cf-ddns /cf-ddns/cf-ddns

ENTRYPOINT  ["/cf-ddns/cf-ddns"]
