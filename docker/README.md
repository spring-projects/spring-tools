# Running Claude Code in Docker

This folder contains a container setup for running [Claude Code](https://claude.com/claude-code) against
this repository in an isolated environment. Start it with `../docker-claude.sh` (see the
[project README](../README.md#running-claude-code-in-docker)).

## Idea

- **Isolation:** Claude Code runs as a regular user in a container (Liberica JDK 25, Node.js 22, git, gh). It only sees
  the checkout that is mounted at `/workspace`, not the rest of your machine.
- **Login:** The Claude Code login, settings and history live in the named volume `sts4-claude-config`, shared by
  all checkouts and git worktrees. Run `/login` once, or set `ANTHROPIC_API_KEY`.
- **Maven:** Your host's `~/.m2/repository` is mounted read-only and offered as an extra repository
  (`docker-maven-settings.xml`). Artifacts are cached in a container-local write area (volume `<checkout>_maven-repo`,
  one per checkout or worktree), so builds never modify your host repository and snapshot builds of different
  worktrees stay separate.
- **Git worktrees:** In a worktree, `.git` is a file pointing into the main checkout's `.git` directory by absolute
  path. `docker-claude.sh` detects this and mounts that directory at the same path inside the container, so git
  works there. The mount is read-write, so commits made in the container reach the shared repository.
- **GitHub:** Set `GH_TOKEN` on the host to make `gh` work inside the container.

## Files

| File | Purpose |
|---|---|
| `../docker-claude.sh` | Entry point: detects worktrees, names the compose project after the checkout, runs the container |
| `docker-compose.yaml` | Mounts, volumes and environment |
| `Dockerfile` | Image with the build tools and Claude Code |
| `docker-maven-settings.xml` | Maven settings that add the read-only host repository |

The Tycho-based Eclipse builds are not set up in this image.

## Security updates

The image is a snapshot taken at build time and is not updated automatically. Rebuild it regularly to pick up
patches for the JDK, OS packages, Node.js and Claude Code:

```bash
./docker-claude.sh --rebuild
```

This pulls the newest base image and ignores cached layers. The image is built per checkout or worktree, so run
it in each one you use.
