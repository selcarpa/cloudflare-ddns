FROM debian:stable-slim AS builder
WORKDIR /cf-ddns-build/
ARG TARGETOS
ARG TARGETARCH
ENV TARGETOS=$TARGETOS
ENV TARGETARCH=$TARGETARCH

COPY ./graalvm-build/docker-build/build.sh /cf-ddns-build/build.sh
COPY ./ /cf-ddns-build/cloudflare-ddns/

RUN sh build.sh

FROM debian:stable-slim
WORKDIR /cf-ddns/
ARG TARGETPLATFORM

COPY --from=builder /cf-ddns-build/cloudflare-ddns/graalvm-build/build/native/nativeCompile/cf-ddns /cf-ddns/cf-ddns

ENTRYPOINT  ["/cf-ddns/cf-ddns"]
