## Explanations
This diagnostic appears at the very first line of a project's build file (`pom.xml` or `build.gradle`) and reports something about the Spring Boot version the project resolves to. It is an umbrella code: the language server's version validation publishes all of its findings on the build file under this one code, and the message tells you which of the following situations applies.

- **OSS support ending or ended.** Based on the generation data of the Spring Boot project on spring.io, the language server reports when the open-source support period of the used Spring Boot generation (for example 3.4.x) is about to end (`OSS support for Spring Boot 3.4.x ends on: <date>`) or has already ended (`OSS support for Spring Boot 3.4.x ended on <date>, get commercial support until <date> via Tanzu Spring Runtime at https://spring.io/support`). If the version cannot be mapped to any known generation at all, it reports `OSS support for Spring Boot <version> not available!`. Analogous messages exist for the end of commercial (enterprise) support. According to the Spring support policy, minor Spring Boot releases receive OSS support for at least 13 months and enterprise support for at least 25 months; the last minor of a major line (2.7, 3.5, ...) gets an additional five years of enterprise support. By default the "supported" cases are silent, the "unsupported" (ended) cases are warnings.
- **Newer version available.** The language server compares the project's Spring Boot version to the newest available releases and reports `Newer major version of Spring Boot available: <version>`, `Newer minor version ...` or `Newer patch version ...`. By default a newer patch is a warning, a newer minor is an information, and a newer major is silent. When the setting `spring-boot.ls.problem-parameters.version-validation.use-project-build-file` is enabled (the default) and the project is a Maven project, the candidate versions are read from the Maven metadata of `org.springframework.boot:spring-boot` in the repositories configured for the project, so versions that are not reachable through your repositories (e.g. behind a corporate mirror) are not suggested; otherwise the release list from spring.io is used.
- **Spring Cloud incompatible with Spring Boot.** If Spring Cloud is detected on the classpath (via `spring-cloud-commons`, `spring-cloud-function-core` or `spring-cloud-task-core`) and the Spring Cloud release train does not list the project's Spring Boot generation as compatible, the language server reports `Spring Cloud <train> is not compatible with Spring Boot <version>. Supported Spring Boot versions: <list>` (a warning by default).

How the language server decides: the project's Spring Boot version is resolved from the project classpath (the `spring-boot` JAR), not by parsing the build file, so a version inherited from a parent POM or a BOM is validated too. Nothing is reported if the project has no build file at the project root or if the Spring Boot version cannot be determined. Each sub-check has its own severity that can be adjusted in the Spring Boot tools "Versions and Support Ranges" problem settings (category id `version-validation`; set it to ignore to silence a check). The diagnostic carries quick fixes as code actions where applicable: "Open Release Notes for Spring Boot <version>" (opens the GitHub release page for that tag), for Maven projects "Upgrade to Spring Boot <version> (Maven dependency version changes only)" for patch updates, and "Get commercial Spring Boot support via Tanzu Spring Runtime" for support-related findings. If the Spring Tools MCP server is used, the tools `getLatestReleaseInformation` and `getLatestBootVersionsFromMavenRepo` return the concrete latest versions and support dates, so you do not need to guess them.

For more details, see:
- [Spring Boot project page - Support timeline](https://spring.io/projects/spring-boot#support) (OSS and enterprise support end dates per generation)
- [Spring Support Policy](https://spring.io/support-policy) (support durations for major and minor releases)
- [Tanzu Spring support](https://spring.io/support) (commercial support after OSS end of life, the target of the quick fix)
- [Spring Boot: Upgrading Spring Boot](https://docs.spring.io/spring-boot/upgrading.html) (release notes and migration guides, `spring-boot-properties-migrator`)
- [Spring Boot releases on GitHub](https://github.com/spring-projects/spring-boot/releases) (release notes per version, opened by the quick fix)
- [Spring Boot wiki - release notes and migration guides](https://github.com/spring-projects/spring-boot/wiki)
- [Spring Cloud project page - Release train / Spring Boot compatibility](https://spring.io/projects/spring-cloud)

## Fixes
**Fix 1: Upgrade to the newest patch release of your Spring Boot line**
Patch releases contain bug and security fixes and are designed to be drop-in. Change the version of the Spring Boot parent (or the `spring-boot-dependencies` BOM) in Maven, or the Spring Boot plugin version in Gradle. Look up the exact latest patch on the Spring Boot release page or via the MCP tool `getLatestBootVersionsFromMavenRepo`; for Maven projects the "Upgrade to Spring Boot ... (Maven dependency version changes only)" quick fix performs this edit for you.

*Before:*
```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.5.0</version>
    <relativePath/>
</parent>
```

*After:*
```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.5.9</version> <!-- use the latest 3.5.x patch available in your repositories -->
    <relativePath/>
</parent>
```

*Before (Gradle):*
```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.5.0'
    id 'io.spring.dependency-management' version '1.1.7'
}
```

*After (Gradle):*
```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.5.9' // use the latest 3.5.x patch
    id 'io.spring.dependency-management' version '1.1.7'
}
```

*Note: The version numbers above are placeholders for the current patch of the respective line; always take the actual value from the diagnostic message, the release page, or the MCP tools rather than hard-coding a version from memory.*

**Fix 2: Move to a supported minor or major generation before OSS support ends**
When the message says OSS support for your generation ends soon or has ended, upgrade to a generation that is still within its OSS support window (see the support timeline on the project page). Upgrading instructions are always the first item in each release's notes; if you skip several releases, read the notes of every release in between as well. For a major upgrade, follow the migration guide on the Spring Boot wiki (for example the 3.0 or 4.0 migration guide) and consider adding `spring-boot-properties-migrator` temporarily to detect renamed or removed configuration properties at startup.

*Before:*
```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.3.13</version>
    <relativePath/>
</parent>
```

*After:*
```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.5.9</version> <!-- a generation whose OSS support has not ended; verify on spring.io -->
    <relativePath/>
</parent>

<dependencies>
    <!-- temporary, remove once the property migration is done -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-properties-migrator</artifactId>
        <scope>runtime</scope>
    </dependency>
</dependencies>
```

*Note: Use the MCP tool `getLatestReleaseInformation` (project `spring-boot`) to retrieve the latest version together with its OSS and commercial support end dates. Upgrading across a major version usually also requires a newer Java baseline and dependency updates (Jakarta EE namespaces for 3.0, module and starter reorganizations for 4.0); plan it as a proper migration, not as a version bump.*

**Fix 3: Align the Spring Cloud release train with the Spring Boot version**
Each Spring Cloud release train supports specific Spring Boot generations (for example 2025.0.x for Spring Boot 3.5.x, 2024.0.x for 3.4.x, 2025.1.x for 4.0.x and 4.1.x). When the diagnostic reports an incompatibility, either change the `spring-cloud-dependencies` BOM version to the train that matches your Spring Boot version, or upgrade Spring Boot to a version listed as supported by your train. Always pick the latest service release of the chosen train.

*Before:*
```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.5.9</version>
    <relativePath/>
</parent>

<properties>
    <spring-cloud.version>2024.0.1</spring-cloud.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>${spring-cloud.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

*After:*
```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.5.9</version>
    <relativePath/>
</parent>

<properties>
    <!-- 2025.0.x is the release train for Spring Boot 3.5.x; use its latest service release -->
    <spring-cloud.version>2025.0.1</spring-cloud.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>${spring-cloud.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

*Note: The compatibility table on the Spring Cloud project page is authoritative; some trains only gained support for a newer Boot minor starting with a specific service release (e.g. 2025.1.x supports Spring Boot 4.1.x starting with 2025.1.2), so the service release number matters.*

**Fix 4: Get commercial support or adjust the severity if staying on the version is intentional**
If the project must stay on a generation whose OSS support has ended, the "Get commercial Spring Boot support via Tanzu Spring Runtime" quick fix points to the commercial support offering, which continues to provide patches after OSS end of life. If a finding is known and accepted (for example a newer major version that you deliberately do not adopt yet), lower or ignore that sub-check's severity in the Spring Boot tools "Versions and Support Ranges" settings instead of ignoring the whole build file diagnostic. To make the "newer version" checks reflect only versions that are actually reachable through your Maven repositories (e.g. an internal mirror), keep the default of the `spring-boot.ls.problem-parameters.version-validation.use-project-build-file` setting enabled.

*Before (VS Code `settings.json`, default value shown):*
```json
{
    "spring-boot.ls.problem.version-validation.UPDATE_LATEST_PATCH_VERSION": "WARNING"
}
```

*After:*
```json
{
    "spring-boot.ls.problem.version-validation.UPDATE_LATEST_PATCH_VERSION": "IGNORE"
}
```

*Note: In VS Code the keys follow the pattern shown above (the last segment names the sub-check, e.g. the OSS support, commercial support, latest major/minor/patch, or Spring Cloud compatibility check); in Eclipse the same options are available in the Spring Boot problem severity preferences under "Versions and Support Ranges". Ignoring a support-related finding does not change the fact that the version no longer receives OSS fixes; treat it as a documented, time-boxed decision.*
