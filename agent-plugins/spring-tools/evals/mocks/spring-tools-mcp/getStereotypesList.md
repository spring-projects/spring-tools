---
expect:
  projectName: sf7-validation
---
[
  {
    "assignments": [
      {
        "target": "jakarta.persistence.AttributeConverter"
      },
      {
        "target": "javax.persistence.AttributeConverter"
      }
    ],
    "sources": [
      "jar:nested:/plugin/language-server/2.5.0/spring-boot-language-server-standalone-exec.jar/!BOOT-INF/lib/spring-boot-language-server-2.5.0-SNAPSHOT.jar!/stereotype-defaults/jpa-jmolecules-stereotypes.json"
    ],
    "stereotype": {
      "displayName": "Attribute Converter",
      "groups": [
        "jpa"
      ],
      "identifier": "jpa.AttributeConverter",
      "inherited": true,
      "priority": 0
    }
  },
  {
    "assignments": [
      {
        "target": "jakarta.persistence.Embeddable"
      },
      {
        "target": "javax.persistence.Embeddable"
      }
    ],
    "sources": [
      "jar:nested:/plugin/language-server/2.5.0/spring-boot-language-server-standalone-exec.jar/!BOOT-INF/lib/spring-boot-language-server-2.5.0-SNAPSHOT.jar!/stereotype-defaults/jpa-jmolecules-stereotypes.json"
    ],
    "stereotype": {
      "displayName": "Embeddable",
      "groups": [
        "jpa"
      ],
      "identifier": "jpa.Embeddable",
      "inherited": true,
      "priority": 0
    }
  },
  {
    "assignments": [
      {
        "target": "jakarta.persistence.Entity"
      },
      {
        "target": "javax.persistence.Entity"
      }
    ],
    "sources": [
      "jar:nested:/plugin/language-server/2.5.0/spring-boot-language-server-standalone-exec.jar/!BOOT-INF/lib/spring-boot-language-server-2.5.0-SNAPSHOT.jar!/stereotype-defaults/jpa-jmolecules-stereotypes.json"
    ],
    "stereotype": {
      "displayName": "Entity",
      "groups": [
        "jpa"
      ],
      "identifier": "jpa.Entity",
      "inherited": true,
      "priority": 0
    }
  },
  {
    "assignments": [
      {
        "target": "jakarta.persistence.MappedSuperclass"
      },
      {
        "target": "javax.persistence.MappedSuperclass"
      }
    ],
    "sources": [
      "jar:nested:/plugin/language-server/2.5.0/spring-boot-language-server-standalone-exec.jar/!BOOT-INF/lib/spring-boot-language-server-2.5.0-SNAPSHOT.jar!/stereotype-defaults/jpa-jmolecules-stereotypes.json"
    ],
    "stereotype": {
      "displayName": "Mapped Superclass",
      "groups": [
        "jpa"
      ],
      "identifier": "jpa.MappedSuperclass",
      "inherited": true,
      "priority": 0
    }
  },
  {
    "assignments": [
      {
        "target": "org.springframework.context.annotation.Configuration"
      }
    ],
    "sources": [
      "jar:nested:/plugin/language-server/2.5.0/spring-boot-language-server-standalone-exec.jar/!BOOT-INF/lib/spring-boot-language-server-2.5.0-SNAPSHOT.jar!/stereotype-defaults/spring-jmolecules-stereotypes.json"
    ],
    "stereotype": {
      "displayName": "Configuration class",
      "groups": [
        "spring"
      ],
      "identifier": "spring.Configuration",
      "inherited": true,
      "priority": 0
    }
  },
  {
    "assignments": [
      {
        "target": "org.springframework.context.ApplicationListener"
      },
      {
        "target": "org.springframework.context.event.EventListener"
      },
      {
        "target": "org.springframework.data.rest.core.event.AbstractRepositoryEventListener"
      }
    ],
    "sources": [
      "jar:nested:/plugin/language-server/2.5.0/spring-boot-language-server-standalone-exec.jar/!BOOT-INF/lib/spring-boot-language-server-2.5.0-SNAPSHOT.jar!/stereotype-defaults/spring-jmolecules-stereotypes.json"
    ],
    "stereotype": {
      "displayName": "Event Listener",
      "groups": [
        "spring"
      ],
      "identifier": "spring.EventListener",
      "inherited": true,
      "priority": 0
    }
  },
  {
    "assignments": [
      {
        "target": "org.springframework.format.Formatter"
      }
    ],
    "sources": [
      "jar:nested:/plugin/language-server/2.5.0/spring-boot-language-server-standalone-exec.jar/!BOOT-INF/lib/spring-boot-language-server-2.5.0-SNAPSHOT.jar!/stereotype-defaults/spring-jmolecules-stereotypes.json"
    ],
    "stereotype": {
      "displayName": "Formatter",
      "groups": [
        "spring"
      ],
      "identifier": "spring.Formatter",
      "inherited": true,
      "priority": 0
    }
  },
  {
    "assignments": [
      {
        "target": "org.springframework.stereotype.Service"
      }
    ],
    "sources": [
      "jar:nested:/plugin/language-server/2.5.0/spring-boot-language-server-standalone-exec.jar/!BOOT-INF/lib/spring-boot-language-server-2.5.0-SNAPSHOT.jar!/stereotype-defaults/spring-jmolecules-stereotypes.json"
    ],
    "stereotype": {
      "displayName": "Service",
      "groups": [
        "spring",
        "ddd"
      ],
      "identifier": "spring.Service",
      "inherited": true,
      "priority": 0
    }
  },
  {
    "assignments": [
      {
        "target": "org.springframework.validation.Validator"
      }
    ],
    "sources": [
      "jar:nested:/plugin/language-server/2.5.0/spring-boot-language-server-standalone-exec.jar/!BOOT-INF/lib/spring-boot-language-server-2.5.0-SNAPSHOT.jar!/stereotype-defaults/spring-jmolecules-stereotypes.json"
    ],
    "stereotype": {
      "displayName": "Validator",
      "groups": [
        "spring"
      ],
      "identifier": "spring.Validator",
      "inherited": true,
      "priority": 0
    }
  },
  {
    "assignments": [
      {
        "target": "org.springframework.aot.hint.RuntimeHintsRegistrar"
      }
    ],
    "sources": [
      "jar:nested:/plugin/language-server/2.5.0/spring-boot-language-server-standalone-exec.jar/!BOOT-INF/lib/spring-boot-language-server-2.5.0-SNAPSHOT.jar!/stereotype-defaults/spring-jmolecules-stereotypes.json"
    ],
    "stereotype": {
      "displayName": "Runtime Hints",
      "groups": [
        "spring"
      ],
      "identifier": "spring.aot.RuntimeHints",
      "inherited": true,
      "priority": 0
    }
  },
  {
    "assignments": [
      {
        "target": "org.springframework.boot.context.properties.ConfigurationProperties"
      }
    ],
    "sources": [
      "jar:nested:/plugin/language-server/2.5.0/spring-boot-language-server-standalone-exec.jar/!BOOT-INF/lib/spring-boot-language-server-2.5.0-SNAPSHOT.jar!/stereotype-defaults/spring-jmolecules-stereotypes.json"
    ],
    "stereotype": {
      "displayName": "Configuration Properties",
      "groups": [
        "spring.boot"
      ],
      "identifier": "spring.boot.ConfigurationProperties",
      "inherited": true,
      "priority": 0
    }
  },
  {
    "assignments": [
      {
        "target": "org.springframework.boot.jackson.JsonMixin"
      }
    ],
    "sources": [
      "jar:nested:/plugin/language-server/2.5.0/spring-boot-language-server-standalone-exec.jar/!BOOT-INF/lib/spring-boot-language-server-2.5.0-SNAPSHOT.jar!/stereotype-defaults/spring-jmolecules-stereotypes.json"
    ],
    "stereotype": {
      "displayName": "JSON Mixin",
      "groups": [
        "spring.boot",
        "spring.web.rest"
      ],
      "identifier": "spring.boot.JsonMixin",
      "inherited": true,
      "priority": 0
    }
  },
  {
    "assignments": [
      {
        "target": "org.springframework.data.repository.Repository"
      },
      {
        "target": "org.springframework.data.repository.RepositoryDefinition"
      },
      {
        "target": "org.springframework.stereotype.Repository"
      }
    ],
    "sources": [
      "jar:nested:/plugin/language-server/2.5.0/spring-boot-language-server-standalone-exec.jar/!BOOT-INF/lib/spring-boot-language-server-2.5.0-SNAPSHOT.jar!/stereotype-defaults/spring-jmolecules-stereotypes.json"
    ],
    "stereotype": {
      "displayName": "Repository",
      "groups": [
        "spring"
      ],
      "identifier": "spring.data.Repository",
      "inherited": true,
      "priority": 0
    }
  },
  {
    "assignments": [
      {
        "target": "org.springframework.data.rest.core.config.Projection"
      }
    ],
    "sources": [
      "jar:nested:/plugin/language-server/2.5.0/spring-boot-language-server-standalone-exec.jar/!BOOT-INF/lib/spring-boot-language-server-2.5.0-SNAPSHOT.jar!/stereotype-defaults/spring-jmolecules-stereotypes.json"
    ],
    "stereotype": {
      "displayName": "Projection",
      "groups": [
        "spring.data"
      ],
      "identifier": "spring.data.rest.Projection",
      "inherited": true,
      "priority": 0
    }
  },
  {
    "assignments": [
      {
        "target": "org.springframework.hateoas.RepresentationModel"
      }
    ],
    "sources": [
      "jar:nested:/plugin/language-server/2.5.0/spring-boot-language-server-standalone-exec.jar/!BOOT-INF/lib/spring-boot-language-server-2.5.0-SNAPSHOT.jar!/stereotype-defaults/spring-jmolecules-stereotypes.json"
    ],
    "stereotype": {
      "displayName": "Representation Model",
      "groups": [
        "spring.web.rest.hypermedia"
      ],
      "identifier": "spring.hateoas.RepresentationModel",
      "inherited": true,
      "priority": 0
    }
  },
  {
    "assignments": [
      {
        "target": "org.springframework.hateoas.server.RepresentationModelProcessor"
      }
    ],
    "sources": [
      "jar:nested:/plugin/language-server/2.5.0/spring-boot-language-server-standalone-exec.jar/!BOOT-INF/lib/spring-boot-language-server-2.5.0-SNAPSHOT.jar!/stereotype-defaults/spring-jmolecules-stereotypes.json"
    ],
    "stereotype": {
      "displayName": "Representation Model Processor",
      "groups": [
        "spring.web.rest.hypermedia"
      ],
      "identifier": "spring.hateoas.RepresentationModelProcessor",
      "inherited": true,
      "priority": 0
    }
  },
  {
    "assignments": [
      {
        "target": "org.springframework.messaging.handler.annotation.MessageMapping"
      },
      {
        "target": "org.springframework.kafka.annotation.KafkaListener"
      },
      {
        "target": "org.springframework.amqp.rabbit.annotation.RabbitListener"
      }
    ],
    "sources": [
      "jar:nested:/plugin/language-server/2.5.0/spring-boot-language-server-standalone-exec.jar/!BOOT-INF/lib/spring-boot-language-server-2.5.0-SNAPSHOT.jar!/stereotype-defaults/spring-jmolecules-stereotypes.json"
    ],
    "stereotype": {
      "displayName": "Message Listener",
      "groups": [
        "spring.messaging"
      ],
      "identifier": "spring.messaging.MessageListener",
      "inherited": true,
      "priority": 0
    }
  },
  {
    "assignments": [
      {
        "target": "org.springframework.data.rest.webmvc.BasePathAwareController"
      },
      {
        "target": "org.springframework.stereotype.Controller"
      },
      {
        "target": "org.springframework.web.bind.annotation.RestController"
      }
    ],
    "sources": [
      "jar:nested:/plugin/language-server/2.5.0/spring-boot-language-server-standalone-exec.jar/!BOOT-INF/lib/spring-boot-language-server-2.5.0-SNAPSHOT.jar!/stereotype-defaults/spring-jmolecules-stereotypes.json"
    ],
    "stereotype": {
      "displayName": "Controller",
      "groups": [
        "spring.web"
      ],
      "identifier": "spring.web.Controller",
      "inherited": true,
      "priority": 0
    }
  },
  {
    "assignments": [
      {
        "target": "org.springframework.web.bind.annotation.RequestMapping"
      }
    ],
    "sources": [
      "jar:nested:/plugin/language-server/2.5.0/spring-boot-language-server-standalone-exec.jar/!BOOT-INF/lib/spring-boot-language-server-2.5.0-SNAPSHOT.jar!/stereotype-defaults/spring-jmolecules-stereotypes.json"
    ],
    "stereotype": {
      "displayName": "Request Mappings",
      "groups": [
        "spring.web"
      ],
      "identifier": "spring.web.RequestMapping",
      "inherited": true,
      "priority": 0
    }
  }
]
