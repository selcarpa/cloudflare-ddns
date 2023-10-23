FROM debian:stable-slim
WORKDIR /cf-ddns/
ARG TARGETPLATFORM
ARG CF_DDNS_VERSION

COPY build/bin/*/releaseExecutable/cf-ddns*.kexe ./temp/

RUN if [ "$TARGETPLATFORM" = "linux/arm64" ]; then \
        mv "./temp/cf-ddns-linux-arm64-$CF_DDNS_VERSION.kexe"  "./cf-ddns.kexe"; \
    elif [ "$TARGETPLATFORM" = "linux/amd64" ]; then \
        mv "./temp/cf-ddns-linux-x64-$CF_DDNS_VERSION.kexe"  "./cf-ddns.kexe"; \
    else \
        echo "Unsupported platform"; \
    fi \
    && rm -rf temp \
    && apt update \
    && apt install curl -y \
    && apt clean autoclean \
    && apt autoremove --yes \
    && rm -rf /var/lib/{apt,dpkg,cache,log}/

CMD ["./cf-ddns.kexe"]
