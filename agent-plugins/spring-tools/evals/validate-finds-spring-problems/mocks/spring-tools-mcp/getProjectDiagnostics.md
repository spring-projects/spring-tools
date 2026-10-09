---
expect:
  projectName: sf7-validation
---
[
  {
    "uri": "file:///workspace/sf7-validation/src/main/java/com/example/demo/Sf7ValidationApplication.java",
    "startLine": 7,
    "startColumn": 1,
    "endLine": 7,
    "endColumn": 56,
    "severity": "warning",
    "message": "Unnecessary `@Autowired` annotation",
    "code": "JAVA_AUTOWIRED_CONSTRUCTOR",
    "source": "vscode-spring-boot"
  },
  {
    "uri": "file:/workspace/sf7-validation/pom.xml",
    "startLine": 0,
    "startColumn": 0,
    "endLine": 0,
    "endColumn": 1,
    "severity": "info",
    "message": "Newer minor version of Spring Boot available: 4.1.1",
    "code": "BOOT_VERSION_VALIDATION_CODE",
    "source": null
  },
  {
    "uri": "file:/workspace/sf7-validation/pom.xml",
    "startLine": 0,
    "startColumn": 0,
    "endLine": 0,
    "endColumn": 1,
    "severity": "warning",
    "message": "Newer patch version of Spring Boot available: 4.0.8",
    "code": "BOOT_VERSION_VALIDATION_CODE",
    "source": null
  }
]
