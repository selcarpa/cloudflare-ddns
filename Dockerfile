#FROM alpine
#WORKDIR /cf-ddns/
#RUN apk add gcompat curl libgcc libc6-compat
#
#COPY build/bin/native/releaseExecutable/cf-ddns.kexe .
#CMD ["./cf-ddns.kexe"]
#

FROM debian:stable-slim
WORKDIR /cf-ddns/
RUN apt update \
    && apt install curl -y

COPY build/bin/native/releaseExecutable/cf-ddns.kexe .
CMD ["./cf-ddns.kexe"]