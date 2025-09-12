FROM ubuntu:25.10

# Avoid prompts from apt
ENV DEBIAN_FRONTEND=noninteractive

# Update and install essential packages
RUN apt-get update && apt-get install -y \
    openssh-server \
    sudo \
    curl \
    wget \
    git \
    vim \
    nano \
    htop \
    build-essential \
    python3 \
    python3-pip \
    openjdk-21-jdk \
    maven \
    nodejs \
    npm \
    docker.io \
    docker-compose \
    && rm -rf /var/lib/apt/lists/*

ENV JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64

COPY zscaler.crt /usr/local/share/ca-certificates/zscaler.crt

RUN keytool -import -trustcacerts \
    -alias zscaler \
    -file /usr/local/share/ca-certificates/zscaler.crt \
    -keystore $JAVA_HOME/lib/security/cacerts \
    -storepass changeit -noprompt

RUN update-ca-certificates

# Create SSH directory
RUN mkdir -p /var/run/sshd

# Create a developer user
RUN useradd -rm -d /home/developer -s /bin/bash -g root -G sudo -u 1001 developer

# Set password for developer user (change this!)
ARG SSH_PASSWORD=devpassword
RUN echo "developer:${SSH_PASSWORD}" | chpasswd

# Allow SSH login with password
RUN sed -i 's/#PasswordAuthentication yes/PasswordAuthentication yes/' /etc/ssh/sshd_config
RUN sed -i 's/#PermitRootLogin prohibit-password/PermitRootLogin no/' /etc/ssh/sshd_config

# Allow sudo without password for developer user
RUN echo "developer ALL=(ALL) NOPASSWD:ALL" >> /etc/sudoers

# Create workspace directory
RUN mkdir -p /workspace && chown developer:root /workspace

# Switch to developer user
USER developer
WORKDIR /home/developer

# Create .ssh directory for the user
RUN mkdir -p /home/developer/.ssh && chmod 700 /home/developer/.ssh

# Switch back to root to start SSH service
USER root

# Expose SSH port
EXPOSE 22

# Start SSH service
CMD ["/usr/sbin/sshd", "-D"]