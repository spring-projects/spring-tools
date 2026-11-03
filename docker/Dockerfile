# Environment for running Claude Code against this repository (see docker-compose.yaml).
# Covers the Maven language-server builds (Liberica JDK 25, as in CI) and the npm-based VSCode extensions.
# The Tycho-based Eclipse builds are not set up here.

# Liberica JDK base image (Debian). The floating tag follows the latest release of that major version;
# pin a full version (e.g. 25.0.5) for reproducible builds. JAVA_HOME is set by the image.
ARG JAVA_MAJOR=25
FROM bellsoft/liberica-openjdk-debian:${JAVA_MAJOR}

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
         ca-certificates curl git gh jq zip unzip procps ripgrep \
    && curl -fsSL https://deb.nodesource.com/setup_22.x | bash - \
    && apt-get install -y --no-install-recommends nodejs \
    && rm -rf /var/lib/apt/lists/*

# Run as a regular user. Directories that back named volumes are created here so the volumes
# inherit this ownership.
# The uid is pinned: the shared claude-config volume stores files owned by this uid, and a uid that
# varies between image builds (base image changes, other worktrees) would make the volume unwritable.
RUN useradd --uid 1000 --create-home --shell /bin/bash claude \
    && mkdir -p /claude-config /home/claude/.m2 /workspace \
    && chown -R claude:claude /claude-config /home/claude /workspace

USER claude
ENV HOME=/home/claude
ENV PATH="/home/claude/.local/bin:$PATH"

# The mounted repository is owned by the host user, not by this container user; without this git
# refuses to operate on it ("dubious ownership").
RUN git config --global --add safe.directory '*'

RUN java -version

# Install Claude Code (native installer)
RUN curl -fsSL https://claude.ai/install.sh | bash

WORKDIR /workspace

CMD ["claude"]
