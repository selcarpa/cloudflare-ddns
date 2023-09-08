#FROM alpine
#WORKDIR /cf-ddns/
#RUN apk add gcompat curl libgcc libc6-compat
#
#COPY build/bin/linuxX64/releaseExecutable/cf-ddns.kexe .
#CMD ["./cf-ddns.kexe"]


FROM debian:stable-slim
WORKDIR /cf-ddns/
RUN apt update \
    && apt install curl -y \
    && apt clean autoclean \
    && apt autoremove --yes \
    && rm -rf /var/lib/{apt,dpkg,cache,log}/

COPY build/bin/linuxX64/releaseExecutable/cf-ddns.kexe .
CMD ["./cf-ddns.kexe"]


#FROM gcr.io/distroless/base-debian11
#WORKDIR /cf-ddns/
#
#COPY build/bin/linuxX64/releaseExecutable/cf-ddns.kexe .
#CMD ["./cf-ddns.kexe"]
