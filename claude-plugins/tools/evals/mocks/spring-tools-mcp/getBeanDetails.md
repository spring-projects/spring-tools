---
expect:
  projectName: sf7-validation
---
[
  {
    "name": "testController",
    "type": "com.example.demo.apiversioning.TestController",
    "location": {
      "range": {
        "end": {
          "character": 27,
          "line": 7
        },
        "start": {
          "character": 13,
          "line": 7
        }
      },
      "uri": "file:///workspace/sf7-validation/src/main/java/com/example/demo/apiversioning/TestController.java"
    },
    "injectionPoints": [],
    "supertypes": [
      "java.lang.Object"
    ],
    "annotations": [
      {
        "annotationName": "RestController",
        "annotationType": "org.springframework.web.bind.annotation.RestController",
        "attributes": {},
        "location": {
          "range": {
            "end": {
              "character": 15,
              "line": 6
            },
            "start": {
              "character": 0,
              "line": 6
            }
          },
          "uri": "file:///workspace/sf7-validation/src/main/java/com/example/demo/apiversioning/TestController.java"
        },
        "metaAnnotation": false
      },
      {
        "annotationName": "Controller",
        "annotationType": "org.springframework.stereotype.Controller",
        "attributes": {},
        "location": null,
        "metaAnnotation": true
      },
      {
        "annotationName": "ResponseBody",
        "annotationType": "org.springframework.web.bind.annotation.ResponseBody",
        "attributes": {},
        "location": null,
        "metaAnnotation": true
      },
      {
        "annotationName": "Component",
        "annotationType": "org.springframework.stereotype.Component",
        "attributes": {},
        "location": null,
        "metaAnnotation": true
      },
      {
        "annotationName": "Indexed",
        "annotationType": "org.springframework.stereotype.Indexed",
        "attributes": {},
        "location": null,
        "metaAnnotation": true
      }
    ],
    "symbolLabel": "@+ 'testController' (@RestController <: @Controller, @Component) TestController",
    "children": [
      {
        "path": "/greeting",
        "httpMethods": [
          "GET"
        ],
        "contentTypes": null,
        "acceptTypes": null,
        "version": "1",
        "methodSignature": "com.example.demo.apiversioning.TestController.sayHello() : java.lang.String",
        "contentHash": "42a21e32ee2e",
        "children": [],
        "documentSymbol": {
          "children": null,
          "deprecated": null,
          "detail": null,
          "kind": "Method",
          "name": "@/greeting -- GET - Version: 1",
          "range": {
            "end": {
              "character": 47,
              "line": 9
            },
            "start": {
              "character": 1,
              "line": 9
            }
          },
          "selectionRange": {
            "end": {
              "character": 47,
              "line": 9
            },
            "start": {
              "character": 1,
              "line": 9
            }
          },
          "tags": null
        }
      }
    ],
    "configuration": false,
    "contentHash": null,
    "documentSymbol": {
      "children": null,
      "deprecated": null,
      "detail": null,
      "kind": "Class",
      "name": "@+ 'testController' (@RestController <: @Controller, @Component) TestController",
      "range": {
        "end": {
          "character": 27,
          "line": 7
        },
        "start": {
          "character": 13,
          "line": 7
        }
      },
      "selectionRange": {
        "end": {
          "character": 27,
          "line": 7
        },
        "start": {
          "character": 13,
          "line": 7
        }
      },
      "tags": null
    }
  },
  {
    "name": "sf7ValidationApplication",
    "type": "com.example.demo.Sf7ValidationApplication",
    "location": {
      "range": {
        "end": {
          "character": 37,
          "line": 6
        },
        "start": {
          "character": 13,
          "line": 6
        }
      },
      "uri": "file:///workspace/sf7-validation/src/main/java/com/example/demo/Sf7ValidationApplication.java"
    },
    "injectionPoints": [],
    "supertypes": [
      "java.lang.Object"
    ],
    "annotations": [
      {
        "annotationName": "SpringBootApplication",
        "annotationType": "org.springframework.boot.autoconfigure.SpringBootApplication",
        "attributes": {},
        "location": {
          "range": {
            "end": {
              "character": 22,
              "line": 5
            },
            "start": {
              "character": 0,
              "line": 5
            }
          },
          "uri": "file:///workspace/sf7-validation/src/main/java/com/example/demo/Sf7ValidationApplication.java"
        },
        "metaAnnotation": false
      },
      {
        "annotationName": "SpringBootConfiguration",
        "annotationType": "org.springframework.boot.SpringBootConfiguration",
        "attributes": {},
        "location": null,
        "metaAnnotation": true
      },
      {
        "annotationName": "EnableAutoConfiguration",
        "annotationType": "org.springframework.boot.autoconfigure.EnableAutoConfiguration",
        "attributes": {},
        "location": null,
        "metaAnnotation": true
      },
      {
        "annotationName": "ComponentScan",
        "annotationType": "org.springframework.context.annotation.ComponentScan",
        "attributes": {
          "excludeFilters": [
            {
              "name": "@Filter(type = public static final [unresolved] org.springframework.context.annotation.FilterType CUSTOM, classes = {TypeExcludeFilter.class})",
              "location": null
            },
            {
              "name": "@Filter(type = public static final [unresolved] org.springframework.context.annotation.FilterType CUSTOM, classes = {AutoConfigurationExcludeFilter.class})",
              "location": null
            }
          ]
        },
        "location": null,
        "metaAnnotation": true
      },
      {
        "annotationName": "Configuration",
        "annotationType": "org.springframework.context.annotation.Configuration",
        "attributes": {},
        "location": null,
        "metaAnnotation": true
      },
      {
        "annotationName": "Indexed",
        "annotationType": "org.springframework.stereotype.Indexed",
        "attributes": {},
        "location": null,
        "metaAnnotation": true
      },
      {
        "annotationName": "AutoConfigurationPackage",
        "annotationType": "org.springframework.boot.autoconfigure.AutoConfigurationPackage",
        "attributes": {},
        "location": null,
        "metaAnnotation": true
      },
      {
        "annotationName": "Import",
        "annotationType": "org.springframework.context.annotation.Import",
        "attributes": {
          "value": [
            {
              "name": "org.springframework.boot.autoconfigure.AutoConfigurationImportSelector",
              "location": null
            }
          ]
        },
        "location": null,
        "metaAnnotation": true
      },
      {
        "annotationName": "Component",
        "annotationType": "org.springframework.stereotype.Component",
        "attributes": {},
        "location": null,
        "metaAnnotation": true
      }
    ],
    "symbolLabel": "@+ 'sf7ValidationApplication' (@SpringBootApplication <: @SpringBootConfiguration, @Configuration, @Component) Sf7ValidationApplication",
    "children": [],
    "configuration": true,
    "contentHash": null,
    "documentSymbol": {
      "children": null,
      "deprecated": null,
      "detail": null,
      "kind": "Class",
      "name": "@+ 'sf7ValidationApplication' (@SpringBootApplication <: @SpringBootConfiguration, @Configuration, @Component) Sf7ValidationApplication",
      "range": {
        "end": {
          "character": 37,
          "line": 6
        },
        "start": {
          "character": 13,
          "line": 6
        }
      },
      "selectionRange": {
        "end": {
          "character": 37,
          "line": 6
        },
        "start": {
          "character": 13,
          "line": 6
        }
      },
      "tags": null
    }
  },
  {
    "name": "webConfig",
    "type": "com.example.demo.apiversioning.WebConfig",
    "location": {
      "range": {
        "end": {
          "character": 22,
          "line": 7
        },
        "start": {
          "character": 13,
          "line": 7
        }
      },
      "uri": "file:///workspace/sf7-validation/src/main/java/com/example/demo/apiversioning/WebConfig.java"
    },
    "injectionPoints": [],
    "supertypes": [
      "org.springframework.web.servlet.config.annotation.WebMvcConfigurer",
      "java.lang.Object"
    ],
    "annotations": [
      {
        "annotationName": "Configuration",
        "annotationType": "org.springframework.context.annotation.Configuration",
        "attributes": {},
        "location": {
          "range": {
            "end": {
              "character": 14,
              "line": 6
            },
            "start": {
              "character": 0,
              "line": 6
            }
          },
          "uri": "file:///workspace/sf7-validation/src/main/java/com/example/demo/apiversioning/WebConfig.java"
        },
        "metaAnnotation": false
      },
      {
        "annotationName": "Component",
        "annotationType": "org.springframework.stereotype.Component",
        "attributes": {},
        "location": null,
        "metaAnnotation": true
      },
      {
        "annotationName": "Indexed",
        "annotationType": "org.springframework.stereotype.Indexed",
        "attributes": {},
        "location": null,
        "metaAnnotation": true
      }
    ],
    "symbolLabel": "@+ 'webConfig' (@Configuration <: @Component) WebConfig",
    "children": [
      {
        "configType": "WEB_CONFIG",
        "pathPrefix": null,
        "pathPrefixPredicate": null,
        "versionSupportStrategies": [
          "Request Header: X-API-Version"
        ],
        "supportedVersions": [],
        "versionParser": "org.springframework.web.accept.SemanticApiVersionParser",
        "location": {
          "range": {
            "end": {
              "character": 22,
              "line": 7
            },
            "start": {
              "character": 13,
              "line": 7
            }
          },
          "uri": "file:///workspace/sf7-validation/src/main/java/com/example/demo/apiversioning/WebConfig.java"
        },
        "children": [],
        "empty": false,
        "versionSupportStrategiesWithRanges": [
          {
            "versioningStrategy": "Request Header: X-API-Version",
            "range": {
              "end": {
                "character": 46,
                "line": 11
              },
              "start": {
                "character": 2,
                "line": 11
              }
            }
          }
        ]
      }
    ],
    "configuration": true,
    "contentHash": null,
    "documentSymbol": {
      "children": null,
      "deprecated": null,
      "detail": null,
      "kind": "Class",
      "name": "@+ 'webConfig' (@Configuration <: @Component) WebConfig",
      "range": {
        "end": {
          "character": 22,
          "line": 7
        },
        "start": {
          "character": 13,
          "line": 7
        }
      },
      "selectionRange": {
        "end": {
          "character": 22,
          "line": 7
        },
        "start": {
          "character": 13,
          "line": 7
        }
      },
      "tags": null
    }
  },
  {
    "name": "config",
    "type": "com.example.demo.Config",
    "location": {
      "range": {
        "end": {
          "character": 19,
          "line": 5
        },
        "start": {
          "character": 13,
          "line": 5
        }
      },
      "uri": "file:///workspace/sf7-validation/src/main/java/com/example/demo/Config.java"
    },
    "injectionPoints": [],
    "supertypes": [
      "java.lang.Object"
    ],
    "annotations": [
      {
        "annotationName": "Configuration",
        "annotationType": "org.springframework.context.annotation.Configuration",
        "attributes": {},
        "location": {
          "range": {
            "end": {
              "character": 14,
              "line": 4
            },
            "start": {
              "character": 0,
              "line": 4
            }
          },
          "uri": "file:///workspace/sf7-validation/src/main/java/com/example/demo/Config.java"
        },
        "metaAnnotation": false
      },
      {
        "annotationName": "Component",
        "annotationType": "org.springframework.stereotype.Component",
        "attributes": {},
        "location": null,
        "metaAnnotation": true
      },
      {
        "annotationName": "Indexed",
        "annotationType": "org.springframework.stereotype.Indexed",
        "attributes": {},
        "location": null,
        "metaAnnotation": true
      }
    ],
    "symbolLabel": "@+ 'config' (@Configuration <: @Component) Config",
    "children": [],
    "configuration": true,
    "contentHash": null,
    "documentSymbol": {
      "children": null,
      "deprecated": null,
      "detail": null,
      "kind": "Class",
      "name": "@+ 'config' (@Configuration <: @Component) Config",
      "range": {
        "end": {
          "character": 19,
          "line": 5
        },
        "start": {
          "character": 13,
          "line": 5
        }
      },
      "selectionRange": {
        "end": {
          "character": 19,
          "line": 5
        },
        "start": {
          "character": 13,
          "line": 5
        }
      },
      "tags": null
    }
  }
]
