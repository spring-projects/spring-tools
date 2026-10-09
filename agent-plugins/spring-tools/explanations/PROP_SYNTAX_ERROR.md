## Explanations
This error appears on a line of a Spring Boot `.properties` file (`application*.properties`, `bootstrap*.properties`) that the language server's properties parser cannot read as a comment, an empty line or a `key<separator>value` pair. The grammar accepts a key made of identifier characters (with `\:` and `\=` as escaped separators), followed by a separator that is a space, a `:` or an `=`, followed by the value. A line that consists of a bare word without any separator, for example `abrakadabra`, does not fit that shape and is flagged over its whole length.

Such lines are almost always the result of an incomplete edit: a value that was pasted without its key, a key whose `=` was deleted, or a comment that lost its leading `#`. Spring Boot itself does not fail on them — `java.util.Properties` and Boot's `OriginTrackedPropertiesLoader` treat a line without a separator as a key with an empty value ("if there are no remaining characters, the element is the empty string") — so the mistake goes unnoticed at runtime while the intended setting silently keeps its default.

The language server raises this diagnostic when all of the following hold:
- the file is recognised as a Spring Boot properties file (`application*.properties` or `bootstrap*.properties`);
- the ANTLR grammar for `.properties` files reports a syntax error for the line; the highlighted range is the offending token(s).

Unlike the other `.properties` diagnostics, this one does not need configuration metadata on the classpath: it is reported even in a project where no `spring-configuration-metadata.json` is available. The default severity is `ERROR`; it can be changed with the setting `spring-boot.ls.problem.application-properties.PROP_SYNTAX_ERROR`. There is no automated quick fix.

For more details, see:
- [Spring Boot: Externalized Configuration — Config Data files](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.files)
- [`java.util.Properties.load(Reader)`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Properties.html) (line format of `.properties` files, including how separator-less lines are read)

## Fixes
**Fix 1: Complete the key/value pair**
Decide what the line was meant to be and add the missing part: a key followed by `=` (or `:`) and the value.

*Before:*
```properties
server.port=8080
8443
```

*After:*
```properties
server.port=8080
server.ssl.port=8443
```

**Fix 2: Turn a stray note into a comment**
If the line is documentation rather than configuration, prefix it with `#` (or `!`).

*Before:*
```properties
Configuration for the local developer machine
server.port=8080
```

*After:*
```properties
# Configuration for the local developer machine
server.port=8080
```

*Note: the multi-document separator `#---` (or `!---`) is a comment too and is accepted by the parser; a line immediately following it that also starts with `#` prevents it from being treated as a document separator (see `PROP_DUPLICATE_KEY`).*
