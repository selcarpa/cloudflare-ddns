FROM debian:stable-slim AS builder
WORKDIR /cf-ddns-build/
ARG TARGETOS
ARG TARGETARCH
ENV TARGETOS=$TARGETOS
ENV TARGETARCH=$TARGETARCH

COPY docker-entrypoint.sh /cf-ddns-build/docker-entrypoint.sh
COPY ../../../cloudflare-ddns/ /cf-ddns-build/cloudflare-ddns/

ENTRYPOINT ["/cf-ddns-build/docker-entrypoint.sh"]

CMD ["sh"]

FROM debian:stable-slim
WORKDIR /cf-ddns/
ARG TARGETPLATFORM

COPY --from=builder /cf-ddns-build/cloudflare-ddns/graalvm-build/build/nativeCompile/cf-ddns /cf-ddns/cf-ddns

ENTRYPOINT  ["/cf-ddns/cf-ddns"]
