# MoChat build container — Android SDK 35 + NDK 27 + JDK 17.
# Usage:
#   docker build -t mochat-build docker/
#   docker run --rm -v "$PWD":/work -w /work mochat-build ./docker/build.sh
FROM eclipse-temurin:17-jdk

ENV ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    NDK_VERSION=27.1.12297006 \
    CMAKE_VERSION=3.22.1

RUN apt-get update && apt-get install -y --no-install-recommends \
        unzip wget git ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Install command-line tools.
RUN mkdir -p ${ANDROID_HOME}/cmdline-tools && \
    wget -q -O /tmp/cmdline-tools.zip \
        "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip" && \
    unzip -q /tmp/cmdline-tools.zip -d ${ANDROID_HOME}/cmdline-tools && \
    mv ${ANDROID_HOME}/cmdline-tools/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest && \
    rm /tmp/cmdline-tools.zip

ENV PATH="${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${PATH}"

# Accept licenses and install SDK + NDK + CMake + platform/build-tools.
RUN yes | sdkmanager --licenses > /dev/null && \
    sdkmanager \
        "platform-tools" \
        "platforms;android-35" \
        "build-tools;35.0.0" \
        "ndk;${NDK_VERSION}" \
        "cmake;${CMAKE_VERSION}" > /dev/null

WORKDIR /work
