## 2025-12-22 (5.0.1 RELEASE, incl. language servers version 2.0.1)

#### all fixes and improvements in detail

* _(Spring Boot)_ [5.0.x] ai-related code lenses around Copilot do not show up [#1746](https://github.com/spring-projects/spring-tools/issues/1746)

## 2025-12-09 (5.0.0 RELEASE, incl. language servers version 2.0.0)

#### important highlights

* _(Spring Boot)_ Logical Structure view supporting jMolecules stereotypes
* _(Spring Boot)_ Spring Framework 7 and Boot 4 support: API versioning, `BeanRegistrar`, Spring Data AOT, JSpecify
* _(Spring Boot)_ AI Support: Copilot, Cursor IDE, embedded MCP server (experimental)
* _(Spring Boot)_ Leaner Spring Boot Language Server JAR

#### all fixes and improvements in detail

* _(Spring Boot)_ [structure view] add expand/collapse all actions [#1689](https://github.com/spring-projects/spring-tools/issues/1689)
* _(Spring Boot)_ [structure view] icons for Eclipse variant don't work for dark mode [#1688](https://github.com/spring-projects/spring-tools/issues/1688)
* _(Spring Boot)_ update core open-rewrite to the latest version [#1678](https://github.com/spring-projects/spring-tools/issues/1678)
* _(Spring Boot)_ [structure view] main application package is not yet found for annotation declarations [#1675](https://github.com/spring-projects/spring-tools/issues/1675)
* _(Spring Boot)_ [structure view] projects disappear from structure view [#1677](https://github.com/spring-projects/spring-tools/issues/1677)
* _(Spring Boot)_ [structure view] create project structure view UI for Eclipse [#1493](https://github.com/spring-projects/spring-tools/issues/1493)
* _(Spring Boot)_ [structure view] create project structure view UI for VSCode [#1492](https://github.com/spring-projects/spring-tools/issues/1492)
* _(Spring Boot)_ [structure view] show details of types as sub-nodes [#1644](https://github.com/spring-projects/spring-tools/issues/1644)
* _(Spring Boot)_ [structure view] keep expanded/collapsed state of tree nodes when updating the tree [#1643](https://github.com/spring-projects/spring-tools/issues/1643)
* _(Spring Boot)_ [structure view] show catalog definition location when hovering over a stereotype node [#1642](https://github.com/spring-projects/spring-tools/issues/1642)
* _(Spring Boot)_ [structure view] allow users to define stereotypes in source code [#1640](https://github.com/spring-projects/spring-tools/issues/1640)
* _(Spring Boot)_ take BasePathAwareController into account as controller class [#1637](https://github.com/spring-projects/spring-tools/issues/1637)
* _(Spring Boot)_ [structural-view] Polish Modulith support [#1636](https://github.com/spring-projects/spring-tools/pull/1636)
* _(Spring Boot)_ take Lombok constructor annotations into account for `@Autowired` validation [#1635](https://github.com/spring-projects/spring-tools/issues/1635)
* _(Spring Boot)_ [structure view] performance optimizations [#1633](https://github.com/spring-projects/spring-tools/issues/1633)
* _(Spring Boot)_ [structural-view] UI polishing [#1632](https://github.com/spring-projects/spring-tools/pull/1632)
* _(Spring Boot)_ [structure view] add fallback to standard Spring stereotypes [#1630](https://github.com/spring-projects/spring-tools/issues/1630)
* _(Spring Boot)_ [structure view] allow users to select the groups to display [#1629](https://github.com/spring-projects/spring-tools/issues/1629)
* _(Spring Boot)_ [structure view] show all groups by default [#1628](https://github.com/spring-projects/spring-tools/issues/1628)
* _(Spring Boot)_ [structure view] fine-tune icons for tree nodes [#1627](https://github.com/spring-projects/spring-tools/issues/1627)
* _(Spring Boot)_ [structure view] update catalog registry when classpath changes [#1626](https://github.com/spring-projects/spring-tools/issues/1626)
* _(Spring Boot)_ Nesting in Spring symbols view [#1424](https://github.com/spring-projects/spring-tools/issues/1424)
* _(Spring Boot)_ add Spring Modulith layer in structural view [#1425](https://github.com/spring-projects/spring-tools/issues/1425)
* _(Spring Boot)_ [structure view] throws NPE when expanding node with non-stereotype subnode [#1708](https://github.com/spring-projects/spring-tools/issues/1708)
* _(Spring Boot)_ [structure view] Eclipse WIP version throws exception [#1686](https://github.com/spring-projects/spring-tools/issues/1686)
* _(Spring Boot)_ [structure view] enable symbol-based child nodes by default [#1717](https://github.com/spring-projects/spring-tools/issues/1717)
* _(Spring Boot)_ VSCode can't open files from Logical Structure [#1655](https://github.com/spring-projects/spring-tools/issues/1655)
* _(Spring Boot)_ [framework 7] API versioning - trigger re-validation when web config in property files changes [#1666](https://github.com/spring-projects/spring-tools/issues/1666)
* _(Spring Boot)_ [framework 7] API versioning - validation for version attribute (is versioning configured) - for property-based configs [#1658](https://github.com/spring-projects/spring-tools/issues/1658)
* _(Spring Boot)_ [framework 7] API versioning - validation for path segment configuration collision [#1657](https://github.com/spring-projects/spring-tools/issues/1657)
* _(Spring Boot)_ [framework 7] API versioning - add support for versioning when using WebFlux [#1654](https://github.com/spring-projects/spring-tools/issues/1654)
* _(Spring Boot)_ [framework 7] API versioning - take path prefix into account [#1652](https://github.com/spring-projects/spring-tools/issues/1652)
* _(Spring Boot)_ [framework 7] API versioning - take boot config via properties into account [#1650](https://github.com/spring-projects/spring-tools/issues/1650)
* _(Spring Boot)_ [framework 7] API versioning - validation for version attribute (is versioning configured) [#1649](https://github.com/spring-projects/spring-tools/issues/1649)
* _(Spring Boot)_ [framework 7] API versioning - validation for version attribute (parseable) [#1648](https://github.com/spring-projects/spring-tools/issues/1648)
* _(Spring Boot)_ [framework 7] add support for API versioning [#1600](https://github.com/spring-projects/spring-tools/issues/1600)
* _(Spring Boot)_ [framework 7] API versioning - version validation fails with non standard version values [#1665](https://github.com/spring-projects/spring-tools/issues/1665)
* _(Spring Boot)_ [jspecify] configure JSpecify automatically in Eclipse and VSCode [#1624](https://github.com/spring-projects/spring-tools/issues/1624)
* _(Spring Boot)_ [mcp] integrate a preference to enable/disable the embedded MCP server [#1621](https://github.com/spring-projects/spring-tools/issues/1621)
* _(Spring Boot)_ [mcp] integrate dynamic port assignment to embedded MCP server [#1620](https://github.com/spring-projects/spring-tools/issues/1620)
* _(Spring Boot)_ [aot repositories] adapt support to changed naming convention [#1617](https://github.com/spring-projects/spring-tools/issues/1617)
* _(Spring Boot)_ [aot repositories] improve generation of aot metadata [#1550](https://github.com/spring-projects/spring-tools/issues/1550)
* _(Spring Boot)_ [aot repositories] add support for Spring Data JDBC repositories in metadata codelenses [#1724](https://github.com/spring-projects/spring-tools/issues/1724)
* _(Spring Boot)_ [aot repositories] Cannot navigate to implementation in Gradle project [#1723](https://github.com/spring-projects/spring-tools/issues/1723)
* _(Spring Boot)_ unify `explain with...` across AI assistants [#1608](https://github.com/spring-projects/spring-tools/issues/1608)
* _(Spring Boot)_ [mcp] automatically configure embedded mcp server (Windsurf) [#1597](https://github.com/spring-projects/spring-tools/issues/1597)
* _(Spring Boot)_ [mcp] integrate tool calls into the embedded MCP server to deliver version and support range information [#1594](https://github.com/spring-projects/spring-tools/issues/1594)
* _(Spring Boot)_ [mcp] integrate basic MCP server into the language server [#1593](https://github.com/spring-projects/spring-tools/issues/1593)
* _(Spring Boot)_ update splash screen for new major version [#1592](https://github.com/spring-projects/spring-tools/issues/1592)
* _(Spring Boot)_ [validation] detect path in controller annotation param [#1582](https://github.com/spring-projects/spring-tools/issues/1582)
* _(Spring Boot)_ [validation] `@Value` annotations with classpath should be of type `Resource` or similar [#1581](https://github.com/spring-projects/spring-tools/issues/1581)
* _(Spring Boot)_ Introduce dedicated Explorer pane for stable Spring outline [#1537](https://github.com/spring-projects/spring-tools/issues/1537)
* _(Spring Boot)_ [validation] add quickfix to move detected path in controller annotation [#1656](https://github.com/spring-projects/spring-tools/issues/1656)
* _(Spring Boot)_ [functional endpoints] code lens to convert static method usage to builder pattern [#1707](https://github.com/spring-projects/spring-tools/issues/1707)
* _(Spring Boot)_ semantic token highlights cause broken highlighting in java editor [#1714](https://github.com/spring-projects/spring-tools/issues/1714)
* _(Spring Boot)_ UnsupportedOperationException [#1703](https://github.com/spring-projects/spring-tools/issues/1703)
* _(Spring Boot)_ modulith service breaks for projects using JDK25 [#1699](https://github.com/spring-projects/spring-tools/issues/1699)
* _(Spring Boot)_ reading support ranges from spring.io API throws exception [#1692](https://github.com/spring-projects/spring-tools/issues/1692)
* _(Spring Boot)_ [exception from inside jdt ls] Pending response rejected since connection got disposed [#1683](https://github.com/spring-projects/spring-tools/issues/1683)
* _(Spring Boot)_ spring tools related exceptions thrown inside of the Java language server [#1680](https://github.com/spring-projects/spring-tools/issues/1680)
* _(Spring Boot)_ NullPointerException: Cannot invoke "org.eclipse.core.resources.IResourceDelta.getResource()" because "resourceDelta" is null [#1679](https://github.com/spring-projects/spring-tools/issues/1679)
* _(Spring Boot)_ YAML editor barfs on sequences [#1660](https://github.com/spring-projects/spring-tools/issues/1660)
* _(Spring Boot)_ java.lang.NoClassDefFoundError: com/sun/tools/javac/code/Type$UnknownType while perform quickfix for "Unnecessary path variable definition" [#1641](https://github.com/spring-projects/spring-tools/issues/1641)
* _(Spring Boot)_ language server throws NPE [#1639](https://github.com/spring-projects/spring-tools/issues/1639)
* _(Spring Boot)_ language server throws NumberFormatException [#1638](https://github.com/spring-projects/spring-tools/issues/1638)
* _(Spring Boot)_ PostgreSQL syntax highlighting doesn't recognize jsonb question mark (?) operator [#1614](https://github.com/spring-projects/spring-tools/issues/1614)
* _(Spring Boot)_ correct property values from enums are sometimes flagged as error [#1605](https://github.com/spring-projects/spring-tools/issues/1605)
* _(Spring Boot)_ Request workspace/executeCommand failed [#1018](https://github.com/spring-projects/spring-tools/issues/1018)
* _(Spring Boot)_ language server throws `StringIndexOutOfBoundsException` [#1730](https://github.com/spring-projects/spring-tools/issues/1730)
* _(Spring Boot)_ content-assist seems to be broken in Eclipse (adds a return statement somehow) [#1729](https://github.com/spring-projects/spring-tools/issues/1729)

#### known issues

* _(Spring Boot)_: Support for Spring Boot major and minor version upgrades via OpenRewrite recipes got removed. We recommend
  use the Maven or Gradle plugins on the command line instead.


## 2025-12-02 (5.0.0 RC2, incl. language servers version 2.0.0 RC2)

#### all fixes and improvements in detail

* _(Spring Boot)_ [structure view] enable symbol-based child nodes by default [#1717](https://github.com/spring-projects/spring-tools/issues/1717)
* _(Spring Boot)_ [aot repositories] improve generation of aot metadata [#1550](https://github.com/spring-projects/spring-tools/issues/1550)

## 2025-11-18 (5.0.0 RC1, incl. language servers version 2.0.0 RC1)

#### important highlights

* _(Spring Boot)_ Logical Structure view supporting jMolecules stereotypes
* _(Spring Boot)_ Spring Framework 7 and Boot 4 support: API versioning, `BeanRegistrar`, Spring Data AOT, JSpecify
* _(Spring Boot)_ AI Support: Copilot, Cursor IDE, embedded MCP server (experimental)
* _(Spring Boot)_ Leaner Spring Boot Language Server JAR

#### updates to the Spring Tools for Eclipse distribution

* updated to the latest Eclipse 2025-12 milestones (including support for JDK 25)

#### all fixes and improvements in detail

* _(Spring Boot)_ [structure view] add expand/collapse all actions [#1689](https://github.com/spring-projects/spring-tools/issues/1689)
* _(Spring Boot)_ [structure view] icons for Eclipse variant don't work for dark mode [#1688](https://github.com/spring-projects/spring-tools/issues/1688)
* _(Spring Boot)_ update core open-rewrite to the latest version [#1678](https://github.com/spring-projects/spring-tools/issues/1678)
* _(Spring Boot)_ [structure view] main application package is not yet found for annotation declarations [#1675](https://github.com/spring-projects/spring-tools/issues/1675)
* _(Spring Boot)_ [structure view] projects disappear from structure view [#1677](https://github.com/spring-projects/spring-tools/issues/1677)
* _(Spring Boot)_ [structure view] create project structure view UI for Eclipse [#1493](https://github.com/spring-projects/spring-tools/issues/1493)
* _(Spring Boot)_ [structure view] create project structure view UI for VSCode [#1492](https://github.com/spring-projects/spring-tools/issues/1492)
* _(Spring Boot)_ [structure view] show details of types as sub-nodes [#1644](https://github.com/spring-projects/spring-tools/issues/1644)
* _(Spring Boot)_ [structure view] keep expanded/collapsed state of tree nodes when updating the tree [#1643](https://github.com/spring-projects/spring-tools/issues/1643)
* _(Spring Boot)_ [structure view] show catalog definition location when hovering over a stereotype node [#1642](https://github.com/spring-projects/spring-tools/issues/1642)
* _(Spring Boot)_ [structure view] allow users to define stereotypes in source code [#1640](https://github.com/spring-projects/spring-tools/issues/1640)
* _(Spring Boot)_ take BasePathAwareController into account as controller class [#1637](https://github.com/spring-projects/spring-tools/issues/1637)
* _(Spring Boot)_ [structural-view] Polish Modulith support [#1636](https://github.com/spring-projects/spring-tools/pull/1636)
* _(Spring Boot)_ take Lombok constructor annotations into account for `@Autowired` validation [#1635](https://github.com/spring-projects/spring-tools/issues/1635)
* _(Spring Boot)_ [structure view] performance optimizations [#1633](https://github.com/spring-projects/spring-tools/issues/1633)
* _(Spring Boot)_ [structural-view] UI polishing [#1632](https://github.com/spring-projects/spring-tools/pull/1632)
* _(Spring Boot)_ [structure view] add fallback to standard Spring stereotypes [#1630](https://github.com/spring-projects/spring-tools/issues/1630)
* _(Spring Boot)_ [structure view] allow users to select the groups to display [#1629](https://github.com/spring-projects/spring-tools/issues/1629)
* _(Spring Boot)_ [structure view] show all groups by default [#1628](https://github.com/spring-projects/spring-tools/issues/1628)
* _(Spring Boot)_ [structure view] fine-tune icons for tree nodes [#1627](https://github.com/spring-projects/spring-tools/issues/1627)
* _(Spring Boot)_ [structure view] update catalog registry when classpath changes [#1626](https://github.com/spring-projects/spring-tools/issues/1626)
* _(Spring Boot)_ Nesting in Spring symbols view [#1424](https://github.com/spring-projects/spring-tools/issues/1424)
* _(Spring Boot)_ add Spring Modulith layer in structural view [#1425](https://github.com/spring-projects/spring-tools/issues/1425)
* _(Spring Boot)_ [structure view] throws NPE when expanding node with non-stereotype subnode [#1708](https://github.com/spring-projects/spring-tools/issues/1708)
* _(Spring Boot)_ [structure view] Eclipse WIP version throws exception [#1686](https://github.com/spring-projects/spring-tools/issues/1686)
* _(Spring Boot)_ VSCode can't open files from Logical Structure [#1655](https://github.com/spring-projects/spring-tools/issues/1655)
* _(Spring Boot)_ [framework 7] API versioning - trigger re-validation when web config in property files changes [#1666](https://github.com/spring-projects/spring-tools/issues/1666)
* _(Spring Boot)_ [framework 7] API versioning - validation for version attribute (is versioning configured) - for property-based configs [#1658](https://github.com/spring-projects/spring-tools/issues/1658)
* _(Spring Boot)_ [framework 7] API versioning - validation for path segment configuration collision [#1657](https://github.com/spring-projects/spring-tools/issues/1657)
* _(Spring Boot)_ [framework 7] API versioning - add support for versioning when using WebFlux [#1654](https://github.com/spring-projects/spring-tools/issues/1654)
* _(Spring Boot)_ [framework 7] API versioning - take path prefix into account [#1652](https://github.com/spring-projects/spring-tools/issues/1652)
* _(Spring Boot)_ [framework 7] API versioning - take boot config via properties into account [#1650](https://github.com/spring-projects/spring-tools/issues/1650)
* _(Spring Boot)_ [framework 7] API versioning - validation for version attribute (is versioning configured) [#1649](https://github.com/spring-projects/spring-tools/issues/1649)
* _(Spring Boot)_ [framework 7] API versioning - validation for version attribute (parseable) [#1648](https://github.com/spring-projects/spring-tools/issues/1648)
* _(Spring Boot)_ [framework 7] add support for API versioning [#1600](https://github.com/spring-projects/spring-tools/issues/1600)
* _(Spring Boot)_ [framework 7] API versioning - version validation fails with non standard version values [#1665](https://github.com/spring-projects/spring-tools/issues/1665)
* _(Spring Boot)_ [jspecify] configure JSpecify automatically in Eclipse and VSCode [#1624](https://github.com/spring-projects/spring-tools/issues/1624)
* _(Spring Boot)_ [mcp] integrate a preference to enable/disable the embedded MCP server [#1621](https://github.com/spring-projects/spring-tools/issues/1621)
* _(Spring Boot)_ [mcp] integrate dynamic port assignment to embedded MCP server [#1620](https://github.com/spring-projects/spring-tools/issues/1620)
* _(Spring Boot)_ [aot repositories] adapt support to changed naming convention [#1617](https://github.com/spring-projects/spring-tools/issues/1617)
* _(Spring Boot)_ unify `explain with...` across AI assistants [#1608](https://github.com/spring-projects/spring-tools/issues/1608)
* _(Spring Boot)_ [mcp] automatically configure embedded mcp server (Windsurf) [#1597](https://github.com/spring-projects/spring-tools/issues/1597)
* _(Spring Boot)_ [mcp] integrate tool calls into the embedded MCP server to deliver version and support range information [#1594](https://github.com/spring-projects/spring-tools/issues/1594)
* _(Spring Boot)_ [mcp] integrate basic MCP server into the language server [#1593](https://github.com/spring-projects/spring-tools/issues/1593)
* _(Spring Boot)_ update splash screen for new major version [#1592](https://github.com/spring-projects/spring-tools/issues/1592)
* _(Spring Boot)_ [validation] detect path in controller annotation param [#1582](https://github.com/spring-projects/spring-tools/issues/1582)
* _(Spring Boot)_ [validation] `@Value` annotations with classpath should be of type `Resource` or similar [#1581](https://github.com/spring-projects/spring-tools/issues/1581)
* _(Spring Boot)_ Introduce dedicated Explorer pane for stable Spring outline [#1537](https://github.com/spring-projects/spring-tools/issues/1537)
* _(Spring Boot)_ [validation] add quickfix to move detected path in controller annotation [#1656](https://github.com/spring-projects/spring-tools/issues/1656)
* _(Spring Boot)_ [functional endpoints] code lens to convert static method usage to builder pattern [#1707](https://github.com/spring-projects/spring-tools/issues/1707)
* _(Spring Boot)_ semantic token highlights cause broken highlighting in java editor [#1714](https://github.com/spring-projects/spring-tools/issues/1714)
* _(Spring Boot)_ UnsupportedOperationException [#1703](https://github.com/spring-projects/spring-tools/issues/1703)
* _(Spring Boot)_ modulith service breaks for projects using JDK25 [#1699](https://github.com/spring-projects/spring-tools/issues/1699)
* _(Spring Boot)_ reading support ranges from spring.io API throws exception [#1692](https://github.com/spring-projects/spring-tools/issues/1692)
* _(Spring Boot)_ [exception from inside jdt ls] Pending response rejected since connection got disposed [#1683](https://github.com/spring-projects/spring-tools/issues/1683)
* _(Spring Boot)_ spring tools related exceptions thrown inside of the Java language server [#1680](https://github.com/spring-projects/spring-tools/issues/1680)
* _(Spring Boot)_ NullPointerException: Cannot invoke "org.eclipse.core.resources.IResourceDelta.getResource()" because "resourceDelta" is null [#1679](https://github.com/spring-projects/spring-tools/issues/1679)
* _(Spring Boot)_ YAML editor barfs on sequences [#1660](https://github.com/spring-projects/spring-tools/issues/1660)
* _(Spring Boot)_ java.lang.NoClassDefFoundError: com/sun/tools/javac/code/Type$UnknownType while perform quickfix for "Unnecessary path variable definition" [#1641](https://github.com/spring-projects/spring-tools/issues/1641)
* _(Spring Boot)_ language server throws NPE [#1639](https://github.com/spring-projects/spring-tools/issues/1639)
* _(Spring Boot)_ language server throws NumberFormatException [#1638](https://github.com/spring-projects/spring-tools/issues/1638)
* _(Spring Boot)_ PostgreSQL syntax highlighting doesn't recognize jsonb question mark (?) operator [#1614](https://github.com/spring-projects/spring-tools/issues/1614)
* _(Spring Boot)_ correct property values from enums are sometimes flagged as error [#1605](https://github.com/spring-projects/spring-tools/issues/1605)
* _(Spring Boot)_ Request workspace/executeCommand failed [#1018](https://github.com/spring-projects/spring-tools/issues/1018)

#### known issues

* _(Spring Boot)_: Support for Spring Boot major and minor version upgrades via OpenRewrite recipes got removed. We recommend
  use the Maven or Gradle plugins on the command line instead.

## 2025-10-29 (4.32.1 RELEASE, incl. language servers version 1.64.1)

* _(Spring Boot)_ [backport] language server throws NumberFormatException [#1696](https://github.com/spring-projects/spring-tools/issues/1696)
* _(Spring Boot)_ [backport] NullPointerException: Cannot invoke "org.eclipse.core.resources.IResourceDelta.getResource()" because "resourceDelta" is null [#1695](https://github.com/spring-projects/spring-tools/issues/1695)
* _(Spring Boot)_ [backport] YAML editor barfs on sequences [#1694](https://github.com/spring-projects/spring-tools/issues/1694)
* _(Spring Boot)_ [backport] reading support ranges from spring.io API throws exception [#1693](https://github.com/spring-projects/spring-tools/issues/1693)

## 2025-09-10 (4.32.0 RELEASE, incl. language servers version 1.64.0)

* _(Spring Boot)_ [backport to 4.x] [aot repositories] adapt support to changed naming convention [#1622](https://github.com/spring-projects/spring-tools/issues/1622)
* _(Spring Boot)_ PostgreSQL syntax highlighting doesn't recognize jsonb question mark (?) operator [#1615](https://github.com/spring-projects/spring-tools/issues/1615)
* _(Spring Boot)_ [backport into 4.x] correct property values from enums are sometimes flagged as error [#1606](https://github.com/spring-projects/spring-tools/issues/1606)

# 2025-06-11 (4.31.0 RELEASE, incl. language servers version 1.63.0)

#### important highlights

* _(Spring Boot)_ Spring Data repositories generated queries CodeLenses with AOT enabled
* _(Spring Boot)_ Spring Boot 3.5 upgrade (build files and properties only)
* _(Spring Boot)_ Hierarchical document symbols supported in both VSCode and Eclipse

#### all fixes and improvements in detail

* _(Spring Boot)_ show document symbols tree expanded by default [#1564](https://github.com/spring-projects/spring-tools/issues/1564)
* _(Spring Boot)_ enable hierarchical document symbols and workspace symbols from index by default [#1557](https://github.com/spring-projects/spring-tools/issues/1557)
* _(Spring Boot)_ [aot repositories] provide go to definition support [#1552](https://github.com/spring-projects/spring-tools/issues/1552)
* _(Spring Boot)_ [aot repositories] add tests for code lenses and index elements + symbols when aot repository metadata is around [#1548](https://github.com/spring-projects/spring-tools/issues/1548)
* _(Spring Boot)_ [aot repositories] add quick fix to turn generated query into manual query annotation [#1547](https://github.com/spring-projects/spring-tools/issues/1547)
* _(Spring Boot)_ [aot repositories] do not show generated query statement when query annotation exists on query method [#1546](https://github.com/spring-projects/spring-tools/issues/1546)
* _(Spring Boot)_ [aot repositories] enable query statement extraction for MongoDB queries [#1545](https://github.com/spring-projects/spring-tools/issues/1545)
* _(Spring Boot)_ [aot repositories] add preference to show/hide code lenses for query methods [#1544](https://github.com/spring-projects/spring-tools/issues/1544)
* _(Spring Boot)_ [aot repositories] implement more precise method signature matching [#1543](https://github.com/spring-projects/spring-tools/issues/1543)
* _(Spring Boot)_ Symbols view should nest elements by their surrounding scope [#1431](https://github.com/spring-projects/spring-tools/issues/1431)
* _(Spring Boot)_ remove wrong support for function beans in indexer [#1572](https://github.com/spring-projects/spring-tools/issues/1572)
* _(Spring Boot)_ Spring Boot 3.5.x upgrade recipe and UI support [#1561](https://github.com/spring-projects/spring-tools/issues/1561)
* _(Spring Boot)_ property key completions include duplicates [#1560](https://github.com/spring-projects/spring-tools/issues/1560)

## 2025-04-23 (4.30.0 RELEASE, incl. language servers version 1.62.0)

#### important highlights

* _(Spring Boot)_ BeanRegistrar from Spring Framework 7 support: reconciling and quick fixes
* _(Spring Boot)_ Improvements to Bean Injection completion proposals

#### all fixes and improvements in detail

* _(Spring Boot)_ adjust and enhance `NotRegisteredBeansReconciler` [#1530](https://github.com/spring-projects/spring-tools/issues/1530)
* _(Spring Boot)_ [spring framework 7] add validation to check for `@Component` like annotations on bean registrars [#1523](https://github.com/spring-projects/spring-tools/issues/1523)
* _(Spring Boot)_ [spring framework 7] add preference for new bean registrar validation [#1522](https://github.com/spring-projects/spring-tools/issues/1522)
* _(Spring Boot)_ No Syntax highlighting for queries in a `@NativeQuery` [#1502](https://github.com/spring-projects/spring-tools/issues/1502)
* _(Spring Boot)_ [spring framework 7] add validation + quickfixes to identify new BeanRegistrars that are not imported [#1499](https://github.com/spring-projects/spring-tools/issues/1499)
* _(Spring Boot)_ [spring framework 7] add support for indexing functional bean registrations via BeanRegistrar [#1498](https://github.com/spring-projects/spring-tools/issues/1498)
* _(Spring Boot)_ `NotRegisteredBeansReconciler` doesn't take factory files into account anymore [#1529](https://github.com/spring-projects/spring-tools/issues/1529)
* _(Spring Boot)_ [bean completion proposals] avoid duplicate proposals [#1519](https://github.com/spring-projects/spring-tools/issues/1519)
* _(Spring Boot)_ [bean completion proposals] completions show up at class level [#1528](https://github.com/spring-projects/spring-tools/issues/1528)
* _(Spring Boot)_ [bean completion proposals] bean completion does not show up [#1501](https://github.com/spring-projects/spring-tools/issues/1501)
* _(Spring Boot)_ Polish visualization of repository nodes in Spring outline [#1536](https://github.com/spring-projects/spring-tools/issues/1536)
* _(Spring Boot)_ [structure view] add properties as children to configuration property nodes [#1494](https://github.com/spring-projects/spring-tools/issues/1494)
* _(Spring Boot)_ replace remaining occurrences of `Spring Tools 4` with `Spring Tools` [#1459](https://github.com/spring-projects/spring-tools/issues/1459)
* _(Spring Boot)_ update `jdt.core` to `3.41.0` [#1539](https://github.com/spring-projects/spring-tools/issues/1539)
* _(Spring Boot)_ `ConcurrentModificationException` thrown while removing project [#1535](https://github.com/spring-projects/spring-tools/issues/1535)
* _(Spring Boot)_ stop showing progress when exception happens deep inside the language server [#1510](https://github.com/spring-projects/spring-tools/issues/1510)
* _(Spring Boot)_ cleanup messages to `Error Log View` [#1515](https://github.com/spring-projects/spring-tools/issues/1515)

## 2025-03-19 (4.29.1 RELEASE, incl. language server version 1.61.1)

#### important highlights

* _(Spring Boot)_ Spring Boot completion proposals show up at higher priority than regular Java proposals

#### all fixes and improvements in detail

* _(Spring Boot)_ [bean completion proposals] completion proposals show up at higher priority than regular Java proposals [#1505](https://github.com/spring-projects/sts4/issues/1505)
* _(Spring Boot)_ Fine grain locking to avoid deadlocks in CU cache [#1509](https://github.com/spring-projects/sts4/pull/1509)
* _(Spring Boot)_ More false positive SQL errors [#1504](https://github.com/spring-projects/sts4/issues/1504)

## 2025-03-12 (4.29.0 RELEASE, incl. language servers version 1.61.0)

#### important highlights

* _(Spring Boot)_ Inject Bean completion proposal available in Spring Components
* _(Spring Boot)_ Experimental Spring Structural view in the Outline (Work in Progress)

#### all fixes and improvements in detail

* _(Spring Boot)_ [structure view] add query methods as children to repository nodes [#1491](https://github.com/spring-projects/sts4/issues/1491)
* _(Spring Boot)_ [structural view] add on/off preference for structural outline [#1490](https://github.com/spring-projects/sts4/issues/1490)
* _(Spring Boot)_ bean completion proposal [#1325](https://github.com/spring-projects/sts4/pull/1325)
* _(Spring Boot)_ Inject a bean completion proposal [#1196](https://github.com/spring-projects/sts4/issues/1196)
* _(Spring Boot)_ do not create repository bean symbols for `@NoRepositoryBean` annotated types [#1500](https://github.com/spring-projects/sts4/issues/1500)
* _(Spring Boot)_ [bean completion proposals] add on/off preference [#1489](https://github.com/spring-projects/sts4/issues/1489)
* _(Spring Boot)_ [bean completion proposals] already having a field with the name of the bean causes weird results [#1488](https://github.com/spring-projects/sts4/issues/1488)
* _(Spring Boot)_ [bean completion proposals] polish the proposal appearance in the completion list [#1485](https://github.com/spring-projects/sts4/issues/1485)
* _(Spring Boot)_ [bean completion proposals] need to polish result when invoking autowire bean completion inside of constructor [#1484](https://github.com/spring-projects/sts4/issues/1484)
* _(Spring Boot)_ [bean completion proposals] autowire bean proposals do not show up before statements [#1483](https://github.com/spring-projects/sts4/issues/1483)
* _(Spring Boot)_ [bean completion proposals] shout not include own class in proposals [#1476](https://github.com/spring-projects/sts4/issues/1476)
* _(Spring Boot)_ [bean completion proposals] proposals should show up for `this.` prefix [#1470](https://github.com/spring-projects/sts4/issues/1470)
* _(Spring Boot)_ [bean completion proposals] proposals should show up for empty prefix [#1469](https://github.com/spring-projects/sts4/issues/1469)
* _(Spring Boot)_ [bean completion proposals] assignment in constructor not generated [#1497](https://github.com/spring-projects/sts4/issues/1497)
* _(Spring Boot)_ [bean completion proposals] completion proposals sometimes do not show up [#1475](https://github.com/spring-projects/sts4/issues/1475)
* _(Spring Boot)_ [bean completion proposals] bean proposal filtering on language server side prevents proposals from showing up [#1471](https://github.com/spring-projects/sts4/issues/1471)
* _(Spring Boot)_ [bean completion proposals] beans of type `Object` results in no constructor param being generated [#1468](https://github.com/spring-projects/sts4/issues/1468)
* _(Spring Boot)_ [events] take event type attribute into account when indexing event listeners [#1465](https://github.com/spring-projects/sts4/issues/1465)
* _(Spring Boot)_ correctly deal with concatenated values in annotation attributes [#1463](https://github.com/spring-projects/sts4/issues/1463)
* _(Spring Boot)_ [events] take type hierarchy of events into account when finding references across event listeners and publishers [#1461](https://github.com/spring-projects/sts4/issues/1461)
* _(Spring Boot)_ JPQL syntax highlighting support with constant string concatenation [#1460](https://github.com/spring-projects/sts4/issues/1460)
* _(Spring Boot)_ Remove `EnhancedSymbolInformation` wrapper class from indexer codebase [#1450](https://github.com/spring-projects/sts4/issues/1450)
* _(Spring Boot)_ Navigational aid for Spring Events [#1348](https://github.com/spring-projects/sts4/issues/1348)
* _(Spring Boot)_ Wrong or undocumented proxy exclusions list [#1495](https://github.com/spring-projects/sts4/issues/1495)
* _(Spring Boot)_ index cache struggles to deserialize JSON due to duplicated usage of `type` [#1473](https://github.com/spring-projects/sts4/issues/1473)
* _(Spring Boot)_ `Upgrade Spring Boot...` UI does not show Spring Boot 3.4 upgrade [#1466](https://github.com/spring-projects/sts4/issues/1466)

## 2025-02-05 (4.28.0 RELEASE, incl. language servers version 1.60.0)

#### important highlights

* _(Spring Boot)_ Spring Index cache management optimization leading to performance improvements
* _(Spring Boot)_ Boot LS JAR size decreased to 207Mb from 271Mb

#### all fixes and improvements in detail

* _(Spring Boot)_  [spring-index] optimize cache operations [#1071](https://github.com/spring-projects/sts4/issues/1071)
* _(Spring Boot)_  [spring index] take function beans into account for new spring index [#1043](https://github.com/spring-projects/sts4/issues/1043)
* _(Spring Boot)_  [spring index] replace EnhancedSymbolInformation for beans with new spring index information [#1041](https://github.com/spring-projects/sts4/issues/1041)
* _(Spring Boot)_  Boot LS JAR size [#1436](https://github.com/spring-projects/sts4/issues/1436)
* _(Spring Boot)_  avoid creating symbols for annotations from `org.springframework.lang` package [#1458](https://github.com/spring-projects/sts4/issues/1458)
* _(Spring Boot)_  `ComponentSymbolProvider` should take value attribute into account when identifying the bean name [#1451](https://github.com/spring-projects/sts4/issues/1451)
* _(Spring Boot)_  Eclipse Spring Tools Sometimes Doesn't Honor Profile or any Override Properties [#1432](https://github.com/spring-projects/sts4/issues/1432)
* _(Spring Boot)_  completion for `@DependsOn` should not suggest its own bean [#1429](https://github.com/spring-projects/sts4/issues/1429)
* _(Spring Boot)_  update jdt.core to `3.40` [#1428](https://github.com/spring-projects/sts4/issues/1428)
* _(Spring Boot)_  Avoid simple `@NonNullApi` Nodes [#1426](https://github.com/spring-projects/sts4/issues/1426)
* _(Spring Boot)_  Validate non-saved docs on close [#1420](https://github.com/spring-projects/sts4/pull/1420)
* _(Spring Boot)_  [property editing] problem marker remains present after closing editor without saving content [#1387](https://github.com/spring-projects/sts4/issues/1387)
* _(Spring Boot)_  [performance] annotation processing needs overhaul [#1288](https://github.com/spring-projects/sts4/issues/1288)
* _(Spring Boot)_  webflux code lenses for handler methods disappear when route changes [#1449](https://github.com/spring-projects/sts4/issues/1449)
* _(Spring Boot)_  Spring Data JPA entityName variable triggers HQL Query Syntax validation error [#1448](https://github.com/spring-projects/sts4/issues/1448)
* _(Spring Boot)_  extracting media type for webflux route does not work for `MediaType.APPLICATION\_JSON` [#1447](https://github.com/spring-projects/sts4/issues/1447)
* _(Spring Boot)_   Missing system library [#1442](https://github.com/spring-projects/sts4/issues/1442)
* _(Spring Boot)_  SQL Parsing Error [#1439](https://github.com/spring-projects/sts4/issues/1439)
* _(Spring Boot)_  `FileSystemNotFoundException`: Provider "jdt" not installed [#1435](https://github.com/spring-projects/sts4/issues/1435)
* _(Spring Boot)_  wrap all calls to `StringLiteral.getLiteralValue` via `ASTUtils` to avoid `IllegalStateException` [#1434](https://github.com/spring-projects/sts4/issues/1434)
* _(Spring Boot)_  Spring Boot Tools Plugin Causes Java Searching to Hang in Large Projects (3300 Java Files) [#1419](https://github.com/spring-projects/sts4/issues/1419)
* _(Spring Boot)_  VSCode: Error reported when "CALL {h-schema}" is used in `@Query`  [#1395](https://github.com/spring-projects/sts4/issues/1395)

## 2024-12-04 (4.27.0 RELEASE, incl. language servers version 1.59.0)

#### important highlights

* _(Spring Boot)_ Support for `@ConditionalOnProperty` annotations: completions, navigate to definition and find references
* _(Spring Boot)_ Find references support for property keys in `@Value` annotation
* _(Spring Boot)_ Support concatenated Strings for data queries inside `@Query` annotations

#### all fixes and improvements in detail

* _(Spring Boot)_ create more precise range for implicit web annotation names validation [#1411](https://github.com/spring-projects/sts4/issues/1411)
* _(Spring Boot)_ analyze memory footprint of newly introduced location information inside of annotation metadata in spring index [#1406](https://github.com/spring-projects/sts4/issues/1406)
* _(Spring Boot)_ take setter injection methods into account for new spring index [#1405](https://github.com/spring-projects/sts4/issues/1405)
* _(Spring Boot)_ internal index needs more precise location information for annotations and their attributes [#1400](https://github.com/spring-projects/sts4/issues/1400)
* _(Spring Boot)_ take property keys inside of annotations into account when looking for property key references [#1397](https://github.com/spring-projects/sts4/issues/1397)
* _(Spring Boot)_ Correct typo from 'propeties' to 'properties' [#1389](https://github.com/spring-projects/sts4/pull/1389)
* _(Spring Boot)_ improve type proposals for `ConditionalOnBean` with shorter labels [#1386](https://github.com/spring-projects/sts4/issues/1386)
* _(Spring Boot)_ use DiagnosticTag.Unnecessary for validations that point out unnecessary things [#1345](https://github.com/spring-projects/sts4/issues/1345)
* _(Spring Boot)_ add find references support for property keys in `@Value` annotation [#1306](https://github.com/spring-projects/sts4/issues/1306)
* _(Spring Boot)_ add support for `@ConditionalOnProperty` annotations [#1305](https://github.com/spring-projects/sts4/issues/1305)
* _(Spring Boot)_ JPQL syntax - Support Concatenated Strings [#1262](https://github.com/spring-projects/sts4/issues/1262)
* _(Spring Boot)_ live hover for `@ConditionalOnProperty` doesn't work for Boot 3.x [#1410](https://github.com/spring-projects/sts4/issues/1410)
* _(Spring Boot)_ content-assist proposals show up for annotation attribute names [#1409](https://github.com/spring-projects/sts4/issues/1409)
* _(Spring Boot)_ Refactoring does not work [#1408](https://github.com/spring-projects/sts4/issues/1408)
* _(Spring Boot)_ `NoSuchFileException` beim AdHocSpringPropertyProvider [#1404](https://github.com/spring-projects/sts4/issues/1404)
* _(Spring Boot)_ VSCode - Error reported when using collate on `@Query` annotation [#1393](https://github.com/spring-projects/sts4/issues/1393)

## 2024-10-23 (4.26.0 RELEASE, incl. language servers version 1.58.0)

#### important highlights

* _(Spring Boot)_ CRON expression description inlay-hint and completion proposals support
* _(Spring Boot)_ `@ConditionalOnBean` and `@ConditinalOnMissingBean` completion proposals, go to bean definitions support
* _(Spring Boot)_ SPEL expressions bean and method references go to definition in Java support
* _(Spring Boot)_ Improvements to completion proposals ordering
* _(Spring Boot)_ Fixes to PostgreSql validation

#### all fixes and improvements in detail
* _(Spring Boot)_ improve Go To Definition to bean definitions in `@ConditionalOnBean` and `@ConditionalOnMissingBean` [#1381](https://github.com/spring-projects/sts4/issues/1381)
* _(Spring Boot)_ do not show bean name proposals for qualifier annotations on bean definitions [#1375](https://github.com/spring-projects/sts4/issues/1375)
* _(Spring Boot)_ Slight refactorings and remove maven-model dependency (#1365) [#1366](https://github.com/spring-projects/sts4/pull/1366)
* _(Spring Boot)_ GH-1307: add support for `@ConditionalOnBean` annotation attributes [#1361](https://github.com/spring-projects/sts4/pull/1361)
* _(Spring Boot)_ goto definition for beans and methods references in spel expressions [#1360](https://github.com/spring-projects/sts4/pull/1360)
* _(Spring Boot)_ GH-1323, GH-1324: Cron Expressions completion proposals and inlay hints [#1357](https://github.com/spring-projects/sts4/pull/1357)
* _(Spring Boot)_ Duplicate spring symbol cannot be found [#1346](https://github.com/spring-projects/sts4/issues/1346)
* _(Spring Boot)_ Order precise matches over fuzzy ones in application property suggestions [#1342](https://github.com/spring-projects/sts4/issues/1342)
* _(Spring Boot)_ Makes auto-completion in application.yml consistent with application.properties. [#1341](https://github.com/spring-projects/sts4/issues/1341)
* _(Spring Boot)_ CodeLens or Inline values for embedded CRON expressions [#1324](https://github.com/spring-projects/sts4/issues/1324)
* _(Spring Boot)_ Completion Proposals for embedded CRON expressions [#1323](https://github.com/spring-projects/sts4/issues/1323)
* _(Spring Boot)_ add support for `@ConditionalOnMissingBean` annotation attributes [#1309](https://github.com/spring-projects/sts4/issues/1309)
* _(Spring Boot)_ add support for `@ConditionalOnBean` annotation attributes [#1307](https://github.com/spring-projects/sts4/issues/1307)
* _(Spring Boot)_ Semantic Token Lost with Lombok Annotations in Enum Due to Conflict with Eclipse Java Plugin in VS Code [#1383](https://github.com/spring-projects/sts4/issues/1383)
* _(Spring Boot)_ regular Java document highlighting broken in VSCode when extension is active [#1380](https://github.com/spring-projects/sts4/issues/1380)
* _(Spring Boot)_ The JPQL validation in Spring Tool Suite (STS) is showing several errors for valid JPQL/PostgreSQL syntax in combination with Eclipselink [#1369](https://github.com/spring-projects/sts4/issues/1369)
* _(Spring Boot)_ Spring Boot language server doesn't start when spring.config.location variable is set with custom location in environment [#1367](https://github.com/spring-projects/sts4/issues/1367)
* _(Spring Boot)_ Request textDocument/inlayHint failed [#1359](https://github.com/spring-projects/sts4/issues/1359)
* _(Spring Boot)_ Crash after updating vscode extension to 1.57.0  [#1355](https://github.com/spring-projects/sts4/issues/1355)
* _(Spring Boot)_ Not auto prompt application-dev.properties config [#1354](https://github.com/spring-projects/sts4/issues/1354)
* _(Spring Boot)_ SPEL: no viable alternative at input '#{' [JAVA\_SPEL\_EXPRESSION\_SYNTAX] [#1353](https://github.com/spring-projects/sts4/issues/1353)
* _(Spring Boot)_ 4.24/4.25 cannot view JMX beans in dashboard [#1350](https://github.com/spring-projects/sts4/issues/1350)
* _(Spring Boot)_ Error on `@ConditionalOnExpression` [#1349](https://github.com/spring-projects/sts4/issues/1349)
 
## 2024-09-11 (4.25.0 RELEASE, incl. language servers version 1.57.0)

#### important highlights

* _(Spring Boot)_ Explain SPEL expressions and AOP annotations via CodeLens with Copilot
* _(Spring Boot)_ Support language features for `@Named`, `@Resource`, `@Innject` and `@ConditionalOnResource` annotations
* _(Spring Boot)_ Syntax highlighting and validation for CRON expressions inside `@Scheduled` annotation
* _(Spring Boot)_ Navigating to definition for some elements of Data Queries embedded into `@Query` annotations

#### all fixes and improvements in detail

* _(Spring Boot)_ Refactor ConditionalOnResource attribute to be based on AnnotationAttributeCompletionProcessor [#1344](https://github.com/spring-projects/sts4/issues/1344)
* _(Spring Boot)_ GH-1310: Add support for ConditionalOnResource annotation [#1343](https://github.com/spring-projects/sts4/pull/1343)
* _(Spring Boot)_ GH-1330: Explain AOP annotations with copilot [#1338](https://github.com/spring-projects/sts4/pull/1338)
* _(Spring Boot)_ [Spring Data Query] Navigate to parameter definitions in Java source [#1336](https://github.com/spring-projects/sts4/issues/1336)
* _(Spring Boot)_ [Spring Data Queries] Show inline hint for query parameter [#1333](https://github.com/spring-projects/sts4/issues/1333)
* _(Spring Boot)_ [Spring Data Queries] Show document highlights when the cursor is at query parameter AST node [#1332](https://github.com/spring-projects/sts4/issues/1332)
* _(Spring Boot)_ add code lens to explain expressions using Copilot to AOP related annotations [#1330](https://github.com/spring-projects/sts4/issues/1330)
* _(Spring Boot)_ Fix missing proposal if the prefix starts with "/" for `@ContextConfiguration` [#1327](https://github.com/spring-projects/sts4/pull/1327)
* _(Spring Boot)_ Syntax Highlighting and Validation for embedded CRON expressions [#1322](https://github.com/spring-projects/sts4/issues/1322)
* _(Spring Boot)_ add support for `@Named` annotation [#1313](https://github.com/spring-projects/sts4/issues/1313)
* _(Spring Boot)_ create generic symbols for `@Resource` and `@Inject` [#1312](https://github.com/spring-projects/sts4/issues/1312)
* _(Spring Boot)_ add support for `@ConditionalOnResource` annotation attributes [#1310](https://github.com/spring-projects/sts4/issues/1310)
* _(Spring Boot)_ change name of "Remove Unnecessary `@Autowired`" validation marker description [#1302](https://github.com/spring-projects/sts4/issues/1302)
* _(Spring Boot)_ change name of problem description for "unnecessary web annotation names" validation [#1301](https://github.com/spring-projects/sts4/issues/1301)
* _(Spring Boot)_ content-assist for annotation attributes doesn't work when spaces are involved [#1299](https://github.com/spring-projects/sts4/issues/1299)
* _(Spring Boot)_ [performance] explore options to deploy and run the language server from JAR files [#1293](https://github.com/spring-projects/sts4/issues/1293)
* _(Spring Boot)_ Recognise `!---` as a document separator in properties files [#1129](https://github.com/spring-projects/sts4/issues/1129)
* _(Spring Boot)_ Add file completion from classpath for `@ContextConfiguration` [#650](https://github.com/spring-projects/sts4/issues/650)
* _(Spring Boot)_ SpEL parsing issue in Query annotation [#1320](https://github.com/spring-projects/sts4/issues/1320)
* _(Spring Boot)_ When editing a Spring XML configuration file, the package names and class names do not appear accurately [#1317](https://github.com/spring-projects/sts4/issues/1317)
* _(Spring Boot)_ No syntax highlighting and validation for data qieries inside `@NamedQuery` [#1314](https://github.com/spring-projects/sts4/issues/1314)
* _(Spring Boot)_ False positive SQL error [#1304](https://github.com/spring-projects/sts4/issues/1304)
* _(Spring Boot)_ Replace `OkHttp3ClientHttpRequestFactory` with `JdkClientHttpRequestFactory` [#1296](https://github.com/spring-projects/sts4/issues/1296)

## 2024-07-24 (4.24.0 RELEASE, incl. language servers version 1.56.0)

#### important highlights

* _(Spring Boot)_ SPEL syntax highlighting and validation inside Java and embedded Spring Data queries. MySQL and PostgreSQL queries syntax highlighting and validation
* _(Spring Boot)_ Improvements Content Assist, Navigation and/or Find References for `@Profile`, `@Resource`, `@Qualifier`, `@Value` annotations

#### all fixes and improvements in detail
* _(Spring Boot)_ add code completion and to go definition support for `@Resource` annotation (name attribute) [#1298](https://github.com/spring-projects/sts4/issues/1298)
* _(Spring Boot)_ support `jakarta` version of `Inject` annotation (in addition to `javax` variant) [#1297](https://github.com/spring-projects/sts4/issues/1297)
* _(Spring Boot)_ add content-assist and find references for `@Profile` annotation [#1294](https://github.com/spring-projects/sts4/issues/1294)
* _(Spring Boot)_ allow navigation to classpath resources from `@Value` annotation [#1287](https://github.com/spring-projects/sts4/issues/1287)
* _(Spring Boot)_ do not show hidden files as proposals for classpath resources (in `@Value` annotation) [#1286](https://github.com/spring-projects/sts4/issues/1286)
* _(Spring Boot)_ support classpath resources in code completion for `@Value` annotation [#1285](https://github.com/spring-projects/sts4/issues/1285)
* _(Spring Boot)_ add content-assist for `@Qualifier` annotation [#1261](https://github.com/spring-projects/sts4/issues/1261)
* _(Spring Boot)_ improve parser-based validation for SpEL snippets [#1237](https://github.com/spring-projects/sts4/issues/1237)
* _(Spring Boot)_ SpEL Syntax Highlighting [#1234](https://github.com/spring-projects/sts4/issues/1234)
* _(Spring Boot)_ Error when edit spring xml bean  config file [#1295](https://github.com/spring-projects/sts4/issues/1295)
* _(Spring Boot)_ avoid mapping content-assist proposals to show up at class level [#1280](https://github.com/spring-projects/sts4/issues/1280)
* _(Spring Boot)_ weird vscode JPQL/HQL syntax error for `@Query` text block in Spring Boot [#1279](https://github.com/spring-projects/sts4/issues/1279)
* _(Spring Boot)_ Support SPEL expressions inside SQL queries [#1270](https://github.com/spring-projects/sts4/issues/1270)

## 2024-06-17 (4.23.1 RELEASE, incl. language servers version 1.55.1)

#### important highlights

* _(Spring Boot)_ SQL query syntax validation is off by default. Settings to toggle JPQL, HQL, SQL query validation enablement
* _(Spring Boot)_ Fixed `StackOverflowException` coming from `AnnotationHierarchies`

#### all fixes and improvements in detail

* _(Spring Boot)_ Ability to switch on/off Query syntax validation and problem severities for query syntax problems [#1275](https://github.com/spring-projects/sts4/issues/1275)
* _(Spring Boot)_ Unable to view beans for multiple modules in version v1.55 [#1271](https://github.com/spring-projects/sts4/issues/1271)
* _(Spring Boot)_ navigation to bean class of live bean in dashboard is broken [#1269](https://github.com/spring-projects/sts4/issues/1269)
* _(Spring Boot)_ navigation to class of wired bean is broken [#1268](https://github.com/spring-projects/sts4/issues/1268)
* _(Spring Boot)_ Opening Spring Boot project in VSCode leads to StackOverflowError [#1166](https://github.com/spring-projects/sts4/issues/1166)

## 2024-06-12 (4.23.0 RELEASE, incl. language servers version 1.55.0)

#### important highlights

* _(Spring Boot)_ Syntax validation for JPQL, SQL, HQL embedded in Java code
* _(Spring Boot)_ Syntax highlighting for SQL embedded into Spring Data query annotations
* _(Spring Boot)_ Navigation to beans from `@DependsOn`
* _(Spring Boot)_ Upgrade to Spring Boot 3.3 is available via OpenRewrite recipes.

#### all fixes and improvements in detail

* _(Spring Boot)_ verify upgrade support to Spring Boot 3.3.0 [#1255](https://github.com/spring-projects/sts4/issues/1255)
* _(Spring Boot)_ vscode `@DependsOn` navigation not linked [#1254](https://github.com/spring-projects/sts4/issues/1254)
* _(Spring Boot)_ add parser-based validation for JPQL, SQL, HQL snippets [#1236](https://github.com/spring-projects/sts4/issues/1236)
* _(Spring Boot)_ inlay hint in pom.xml file to upgrade to latest Spring Boot version [#1211](https://github.com/spring-projects/sts4/issues/1211)
* _(Spring Boot)_ VSCode Spring Boot extension causes semantic token loss upon loading [#1260](https://github.com/spring-projects/sts4/issues/1260)
* _(Spring Boot)_ Occasional NPE when opening / importing / creating Maven projects [#1257](https://github.com/spring-projects/sts4/issues/1257)
* _(Spring Boot)_ `Map<K,V>` with escaped key not works properly in `application.yml` [#1243](https://github.com/spring-projects/sts4/issues/1243)
* _(Spring Boot)_ Variable assignment/reference syntax highlighting lost between v1.53.0 and v1.54.0 [#1249](https://github.com/spring-projects/sts4/issues/1249)

## 2024-05-02 (4.22.1 RELEASE, incl. language servers version 1.54.0)

#### important highlights

* _(Spring Boot)_ Support JPQL syntax in Java sources and Named Query properties files
* _(Spring Boot)_ Spring Symbol indexing has order of magnitude smaller memory footprint allowing for indexing very large Spring Boot projects
* _(Spring Boot)_ New UI to show/hide/refresh Live Data for running Spring Boot apps
* _(Spring Boot)_ .properties `<->` .yml conversions available in Eclipse and VSCode
* _(Spring Boot)_ Show active profiles for running spring boot applications with Boot Dashboard extension

#### all fixes and improvements in detail

* _(Spring Boot)_ Support JPQL syntax [#1114](https://github.com/spring-projects/sts4/issues/1114)
* _(Spring Boot)_ Indexing runs out of memory for large projects [#1219](https://github.com/spring-projects/sts4/issues/1219)
* _(Spring Boot)_ .properties `<->` .yml converter [#739](https://github.com/spring-projects/sts4/issues/739)
* _(Spring Boot)_ change the actions to refresh live information to show application right away [#1204](https://github.com/spring-projects/sts4/issues/1204)
* _(Spring Boot)_ Running Boot processes from IDE client [#1190](https://github.com/spring-projects/sts4/issues/1190)
* _(Spring Boot)_ Show active profiles in Boot Dash [#1225](https://github.com/spring-projects/sts4/issues/1225)
* _(Spring Boot)_ show a nice message when trying to change log levels without a connected running app [#1232](https://github.com/spring-projects/sts4/issues/1232)
* _(Spring Boot)_ request mapping symbol doesn't take superclass mapping path into account [#12[24](https://github.com/spring-projects/sts4/actions/runs/8881415855/job/24383700045#step:5:25)](https://github.com/spring-projects/sts4/issues/1224)
* _(Spring Boot)_ Remove `org.springframework.ide.vscode.boot.java.livehover.v2.SpringProcessTracker` and its references [#1221](https://github.com/spring-projects/sts4/issues/1221)
* _(Spring Boot)_ Set the vmArg HeapDumpOnOutOfMemoryError to be optional [#1212](https://github.com/spring-projects/sts4/issues/1212)
* _(Spring Boot)_ provide preference to open code lens URLs in simple browser [#1207](https://github.com/spring-projects/sts4/issues/1207)
* _(Spring Boot)_ Refactor Gradle plugin for `StsToolingModel` [#1201](https://github.com/spring-projects/sts4/issues/1201)
* _(Spring Boot)_ remove logic to handle "magic" client-side indentation [#1154](https://github.com/spring-projects/sts4/issues/1154)
* _(Spring Boot)_ NPE while updating Spring Boot version [#1241](https://github.com/spring-projects/sts4/issues/1241)
* _(Spring Boot)_ NPE exception while scanning for symbols [#1239](https://github.com/spring-projects/sts4/issues/1239)
* _(Spring Boot)_ Activating 'vscode-spring-boot' extension [#1238](https://github.com/spring-projects/sts4/issues/1238)
* _(Spring Boot)_ CompilationUnitCache: gracefully cleanup the name environment [#1233](https://github.com/spring-projects/sts4/issues/1233)
* _(Spring Boot)_ renaming of files and packages causes symbols to get out of sync [#1231](https://github.com/spring-projects/sts4/issues/1231)
* _(Spring Boot)_ java.lang.ClassNotFoundException: org.eclipse.jdt.ls.core.internal.javadoc.JavaElementLinks cannot be found by ... [#1229](https://github.com/spring-projects/sts4/issues/1229)
* _(Spring Boot)_ SpringToolSuite (x\_86\_64 option) IDE keeps crashing frequently. [#1228](https://github.com/spring-projects/sts4/issues/1228)
* _(Spring Boot)_ latest snapshot builds have wrong port in live hover request mapping URLs [#12[26](https://github.com/spring-projects/sts4/actions/runs/8881415855/job/24383700045#step:5:27)](https://github.com/spring-projects/sts4/issues/1226)
* _(Spring Boot)_ JPA Repo Entity ID composite key reports problem [#1220](https://github.com/spring-projects/sts4/issues/1220)

## 2024-03-13 (4.22.0 RELEASE, incl. language servers version 1.53.0)

#### important highlights

* _(Spring Boot)_ **Add Spring Boot Starters...** InlayHint in Spring Boot projects `pom.xml` next to `<dependencies>` tag (VSCode only at the moment)

#### all fixes and improvements in detail

* _(Spring Boot)_ `Add Starters` CodeLens/InlayHint in the Spring Boot's project pom [#1194](https://github.com/spring-projects/sts4/issues/1194)
* _(Spring Boot)_ adjustments to version validations [#1199](https://github.com/spring-projects/sts4/issues/1199)
* _(Spring Boot)_ TestJars support [#1197](https://github.com/spring-projects/sts4/issues/1197)
* _(Spring Boot)_ Bind update of Spring Modulith metadata to a full project build [#1172](https://github.com/spring-projects/sts4/issues/1172)
* _(Spring Boot)_ exception thrown under the hood when file URL protocol is unknown [#1192](https://github.com/spring-projects/sts4/issues/1192)
* _(Spring Boot)_ Fix JavaModelException with non-Java projects [#1188](https://github.com/spring-projects/sts4/pull/1188)
* _(Spring Boot)_ throws exception when closing editors [#1179](https://github.com/spring-projects/sts4/issues/1179)

## 2024-02-07 (4.21.1 RELEASE, incl. language servers version 1.52.0)

#### important highlights

* _(Spring Boot)_ **Log Levels** viewing and editing is available in VSCode for live running Boot apps if enabled on the the app via Spring Boot Actuators
* _(Spring Boot)_ Ability to show "Refactor Preview" in VSCode before applying the changes from OpenRewrite recipes
* _(Spring Boot)_ New content assist proposals available for creating Spring XML config files

#### all fixes and improvements in detail

* _(Spring Boot)_ Log Levels View [#1138](https://github.com/spring-projects/sts4/pull/1138)
* _(Spring Boot)_ Evaluate `AnnotatedTextEdit` and `ChangeAnnotation#needsConfirmation` [#1155](https://github.com/spring-projects/sts4/issues/1155)
* _(Spring Boot)_ support for creating Spring XML config files [#1163](https://github.com/spring-projects/sts4/issues/1163)
* _(Spring Boot)_ [refactoring] reduce size of execute rewrite recipes messages [#897](https://github.com/spring-projects/sts4/issues/897)
* _(Spring Boot)_ Opening Spring Boot project in VSCode leads to StackOverflowError [#1166](https://github.com/spring-projects/sts4/issues/1166)
* _(Spring Boot)_ upgrade command shows different recipes than quick fixes [#1165](https://github.com/spring-projects/sts4/issues/1165)
* _(Spring Boot)_ process shows up twice when changing log levels of running app [#1164](https://github.com/spring-projects/sts4/issues/1164)
* _(Spring Boot)_ The IDE compiler have a lot of wrong problem report, maven build is okay [#1159](https://github.com/spring-projects/sts4/issues/1159)
* _(Spring Boot)_ VS Code extension fails Spring Boot update [#1142](https://github.com/spring-projects/sts4/issues/1142)
* _(Spring Boot)_ Add Trusted Authoritites for STS [#1092](https://github.com/spring-projects/sts4/issues/1092)

## 2023-12-06 (4.21.0 RELEASE, incl. language servers version 1.51.0)

#### important highlights

* _(Spring Boot)_ Completions experience for request mappings on class method level has been improved
* _(Spring Boot)_ Spring Boot 3.2 upgrade available via OpenRewrite integration

#### all fixes and improvements in detail

* _(Spring Boot)_ [optimization] avoid writing spring index cache twice on didSave event [#1143](https://github.com/spring-projects/sts4/issues/1143)
* _(Spring Boot)_ Adopt latest OpenRewrite version [#1135](https://github.com/spring-projects/sts4/issues/1135)
* _(Spring Boot)_ [vscode-spring-boot] Provide better Spring component code completion experience on class and method level [#758](https://github.com/spring-projects/sts4/issues/758)
* _(Spring Boot)_ VS Code extension does not use proxy settings [#1147](https://github.com/spring-projects/sts4/issues/1147)
* _(Spring Boot)_ [validation] exception thrown while validating spring boot versions [#1144](https://github.com/spring-projects/sts4/issues/1144)
* _(Spring Boot)_ `HttpSecurityLambdaDslReconciler` marks more than required [#1141](https://github.com/spring-projects/sts4/issues/1141)
* _(Spring Boot)_ Cant use compsite keys for domain id when extending JpaRepository [#1139](https://github.com/spring-projects/sts4/issues/1139)
* _(Spring Boot)_ Strange error port out of range:-1 for language server [#1137](https://github.com/spring-projects/sts4/issues/1137)

## 2023-11-01 (4.20.1 RELEASE, incl. language servers version 1.50.0)

#### important highlights

* _(Spring Boot)_ Completions for Spring Properties show up 10 times faster initially (as fast as on the consecutive invocations)
* _(Spring Boot)_ Various performance optimizations around symbols scanning and Java reconciling

#### all fixes and improvements in detail

* _(Spring Boot)_ fixed: [optimization] avoid running symbol indexing and cache write for empty affected files array [#1124](https://github.com/spring-projects/sts4/issues/1124)
* _(Spring Boot)_ fixed: [optimization] Boot properties completions slow to show up [#1120](https://github.com/spring-projects/sts4/issues/1120)
* _(Spring Boot)_ fixed: [optimization] reuse compilation unit cache to compute symbols for document [#1119](https://github.com/spring-projects/sts4/issues/1119)
* _(Spring Boot)_ fixed: [optimization] avoid writing caches multiple times on file delete events [#1118](https://github.com/spring-projects/sts4/issues/1118)
* _(Spring Boot)_ fixed: [optimization] Dependency versions re-calculated often [#1116](https://github.com/spring-projects/sts4/issues/1116)
* _(Spring Boot)_ fixed: [boot version validation] add progress support and error messages [#1115](https://github.com/spring-projects/sts4/issues/1115)
* _(Spring Boot)_ fixed: Completion Proposal for query methods doesn't work with `record` entities [#1113](https://github.com/spring-projects/sts4/issues/1113)
* _(Spring Boot)_ fixed: Entity Id type from domain type reconciler should account for JPA `@Id` annotations [#1112](https://github.com/spring-projects/sts4/issues/1112)
* _(Spring Boot)_ fixed: further refinements for quick fixes around mapping annotations [#1109](https://github.com/spring-projects/sts4/issues/1109)
* _(Spring Boot)_ fixed: Multiple quick fixes offered for  `@RequestMapping` into `GetMapping` etc [#1107](https://github.com/spring-projects/sts4/issues/1107)
* _(Spring Boot)_ fixed: Spring Modulith verifications broken [#1127](https://github.com/spring-projects/sts4/issues/1127)
* _(Spring Boot)_ fixed: various OpenRewrite based quick fixes are broken [#1126](https://github.com/spring-projects/sts4/issues/1126)
* _(Spring Boot)_ fixed: Plugin does not use eclipse proxy settings [#1101](https://github.com/spring-projects/sts4/issues/1101)
* _(Spring Boot)_ fixed: [validation] reconciler throws NPE [#1117](https://github.com/spring-projects/sts4/issues/1117)
* _(VSCode)_ fixed: VS Code - Document dependency on the Red Hat Language Support for Java [#1131](https://github.com/spring-projects/sts4/issues/1131)

## 2023-09-13 (4.20.0 RELEASE, incl. language servers version 1.49.0)

#### important highlights

* _(Spring Boot):_ 5x-10x faster Java Reconciling support to show Spring specific validations re-built from the ground up tuned for high performance to work seamlessly in large code bases

#### all fixes and improvements in detail

* _(Spring Boot)_ fixed: Performance of Reconciling Spring Java ([#1068](https://github.com/spring-projects/sts4/issues/1068))
* _(Spring Boot)_ fixed: Spring Language Server hangs forever ([#1075](https://github.com/spring-projects/sts4/issues/1075))
* _(Spring Boot)_ fixed: Spring Boot Validations Builder hung seemingly forever ([#1103](https://github.com/spring-projects/sts4/issues/1103))
* _(Spring Boot)_ fixed: [new validation engine] remove old open-rewrite based validation code ([#1084](https://github.com/spring-projects/sts4/issues/1084))
* _(Spring Boot)_ fixed: [new validation engine] convert reconcilers to new validation engine ([#1085](https://github.com/spring-projects/sts4/issues/1085))
* _(Spring Boot)_ fixed: [new validation engine] take preference changes into account beyond open files ([#1083](https://github.com/spring-projects/sts4/issues/1083))
* _(Spring Boot)_ fixed: Incorrect MISSING_CONFIGURATION_ANNOTATION warning in vscode ([#1100](https://github.com/spring-projects/sts4/issues/1100))
* _(Spring Boot)_ fixed: quick fix to remove unnecessary `@Autowired` appears twice ([#1106](https://github.com/spring-projects/sts4/issues/1106))
* _(Spring Boot)_ fixed: quick fix to convert '@RequestMapping' to '@GetMapping' fails in VSCode ([#1105](https://github.com/spring-projects/sts4/issues/1105))
* _(VSCode)_ fixed: Change the scope of `spring-boot.ls.java.home` to `machine-overridable` ([#1094](https://github.com/spring-projects/sts4/issues/1094))

## 2023-06-14 (4.19.1 RELEASE, incl. language servers version 1.48.0)

* _(Spring Boot)_ fixed: Evaluate Spring Modulith actuators ([#953](https://github.com/spring-projects/sts4/issues/953))
* _(Spring Boot)_ fixed: No classpath update after source is saved ([#987](https://github.com/spring-projects/sts4/issues/987))
* _(Spring Boot)_ fixed: duplicates of symbols showing up ([#10056](https://github.com/spring-projects/sts4/issues/1056))
* _(Spring Boot)_ fixed: WebSecurityConfigurerAdapter quick fix not showing ([#1062](https://github.com/spring-projects/sts4/issues/1062))
* _(Spring Boot)_ fixed: Let show optional: prefix for spring.sql.init.schema-locations and spring.sql.init.data-locations ([#1067](https://github.com/spring-projects/sts4/issues/1067))
* _(Spring Boot)_ fixed: index cache not working after project update ([#1079](https://github.com/spring-projects/sts4/issues/1079))
* _(VSCode)_ fixed: Spring Boot Tools vscode extension error popup: Request textDocument/definition failed ([#1076](https://github.com/spring-projects/sts4/issues/1076))

## 2023-06-14 (4.19.0 RELEASE, incl. language servers version 1.47.0)

* _(Spring Boot)_ fixed: Spring Data Repositories: Validate ID type ([#457](https://github.com/spring-projects/sts4/issues/457))
* _(Spring Boot)_ fixed: Add support for spring potential injection points discovery while indexing ([#994](https://github.com/spring-projects/sts4/issues/994))
* _(Spring Boot)_ fixed: replace internals of symbols machinery with more comprehensive model of spring components ([#1006](https://github.com/spring-projects/sts4/issues/1006))
* _(Spring Boot)_ fixed: [spring index] optimize empty array objects in internal index storage structure ([#1040](https://github.com/spring-projects/sts4/issues/1040))
* _(Spring Boot)_ fixed: [spring index] add request method to lsp extension to identify matching beans ([#1049](https://github.com/spring-projects/sts4/issues/1049))
* _(Spring Boot)_ fixed: upgrading from Spring Boot 3.0.x to 3.1.0 doesn't work ([#1051](https://github.com/spring-projects/sts4/issues/1051))

## 2023-05-05 (4.18.1 RELEASE, incl. language servers version 1.46.0)

* _(Spring Boot)_ fixed: [vscode-spring-boot] Support navigating to a Spring property file when inspecting on @Value annotations ([#761](https://github.com/spring-projects/sts4/issues/761))
* _(Spring Boot)_ fixed: automatically add import for types when applying content-assist proposals for query methods ([#991](https://github.com/spring-projects/sts4/issues/991))
* _(Spring Boot)_ fixed: do not fetch metrics data right after connecting live data to an app ([#1003](https://github.com/spring-projects/sts4/issues/1003))
* _(Spring Boot)_ fixed: No hyperlink on a hover to a type from JAR ([#1004](https://github.com/spring-projects/sts4/issues/1004))
* _(Spring Boot)_ fixed: [spring-data-support] take type hierarchy of domain types into account ([#1013](https://github.com/spring-projects/sts4/issues/1013))
* _(Spring Boot)_ fixed: [spring-data-support] remove pure findBy method completion from proposal list ([#1014](https://github.com/spring-projects/sts4/issues/1014))
* _(Spring Boot)_ fixed: several live hovers are missing when running Spring Boot 3 applications ([#1019](https://github.com/spring-projects/sts4/issues/1019))
* _(Spring Boot)_ fixed: application.properties/yaml autocomplete for nested enums regression ([#1021](https://github.com/spring-projects/sts4/issues/1021))
* _(Spring Boot)_ fixed: [boot-upgrade] Upgrade petclinic to boot 3 results in compiler errors ([#1022](https://github.com/spring-projects/sts4/issues/1022))
* _(Spring Boot)_ fixed: Bump json from 20160810 to 20230227 in /headless-services/spring-boot-language-server ([#1027](https://github.com/spring-projects/sts4/issues/1027))
* _(Spring Boot)_ fixed: spring-configuration-metadata : must have primitive type property before F3 navigation works ([#1030](https://github.com/spring-projects/sts4/issues/1030))
* _(Spring Boot)_ fixed: spring-configuration-metadata : when two properties have the same Object type F3 fails on the later ones ([#1031](https://github.com/spring-projects/sts4/issues/1031))
* _(Spring Boot)_ fixed: support @ConditionalOnProperty in property navigation feature ([#1033](https://github.com/spring-projects/sts4/issues/1033))
* _(VSCode)_ fixed: In Codespaces, workspace symbols sometimes are empty. ([#1012](https://github.com/spring-projects/sts4/issues/1012))

## 2023-03-15 (4.18.0 RELEASE, incl. language servers version 1.45.0)

#### important highlights

* _(Spring Boot):_ new and vastly improved content-assist for Spring Data repository query methods (thanks to contributions from @danthe1st)
* _(Spring Boot):_ the additional reconciling of Spring Boot projects to show Spring specific validations, outdated versions and more, now reports progress, runs faster, and uses less memory - please consider using this and tell us about your experiences and if you hit issues while having this feature enabled
* _(VSCode):_ fixed an extremely annoying issue that caused regular Java content-assist in VSCode to stop working after a short while

#### all fixes and improvements in detail

* _(Spring Boot)_ fixed: language server seems to have issues refreshing live data ([#1002](https://github.com/spring-projects/sts4/issues/1002))
* _(Spring Boot)_ fixed: update default settings for generations validation ([#999](https://github.com/spring-projects/sts4/issues/999))
* _(Spring Boot)_ fixed: Switch to rewrite release versions for 4.18 ([#995](https://github.com/spring-projects/sts4/issues/995))
* _(Spring Boot)_ fixed: completions for predicate keywords in Spring Data repositories ([#988](https://github.com/spring-projects/sts4/issues/988)) - contributed by @danthe1st
* _(Spring Boot)_ fixed: False positive "Unnecessary @Autowired" warning when using abstract classes ([#985](https://github.com/spring-projects/sts4/issues/985))
* _(Spring Boot)_ fixed: [validation] report progress when reconciling projects ([#984](https://github.com/spring-projects/sts4/issues/984))
* _(Spring Boot)_ fixed: Factor out static methods for Spring Data repository completions ([#983](https://github.com/spring-projects/sts4/issues/983)) - contributed by @danthe1st
* _(Spring Boot)_ fixed: Spring Data JPA Content Assist ([#981](https://github.com/spring-projects/sts4/issues/981)) - contributed by @danthe1st
* _(Spring Boot)_ fixed: [boot-upgrade] [Spring-Security] Removed WebSecurityConfigurerAdapter ([#979](https://github.com/spring-projects/sts4/issues/979))
* _(Spring Boot)_ fixed: [validation] [Spring-Security] Authorize HttpServletRequests with AuthorizationFilter ([#978](https://github.com/spring-projects/sts4/issues/978))
* _(Spring Boot)_ fixed: [validation] [Spring Security] Lambda DSL ([#977](https://github.com/spring-projects/sts4/issues/977))
* _(Spring Boot)_ fixed: [validation] Parse sources with Rewrite slower than JDT ([#971](https://github.com/spring-projects/sts4/issues/971))
* _(Spring Boot)_ fixed: [validation] Version validation based on spring.io generations REST API ([#969](https://github.com/spring-projects/sts4/issues/969))
* _(Spring Boot)_ fixed: [refactoring] running Spring Boot 3 open-rewrite migration recipes takes very long ([#863](https://github.com/spring-projects/sts4/issues/863))
* _(Spring Boot)_ fixed: [validation] add navigation to release notes from version validation messages ([#923](https://github.com/spring-projects/sts4/issues/923))
* _(Spring Boot)_ fixed: [validation] investigate highly increased memory consumption with new Java source reconciling ([#922](https://github.com/spring-projects/sts4/issues/922))
* _(Spring Boot)_ fixed: VSCode cross project additional-spring-configuration-metadata.json ([#894](https://github.com/spring-projects/sts4/issues/894))
* _(Spring Boot)_ fixed: [refactoring] investigate memory usage when converting projects ([#877](https://github.com/spring-projects/sts4/issues/877))
* _(Spring Boot)_ fixed: add Spring Data JPA Content Assist to STS 4 ([#107](https://github.com/spring-projects/sts4/issues/107))
* _(VSCode)_ fixed: Some error on WSL and Spring Extension ([#982](https://github.com/spring-projects/sts4/issues/982))
* _(VSCode)_ fixed: vscode ext causes "Header must provide a Content-Length property" continuously ([#968](https://github.com/spring-projects/sts4/issues/968))
* _(VSCode)_ fixed: Unable to install extension 'pivotal.vscode-spring-boot' as it is not compatible with VS Code '1.68.1' ([#967](https://github.com/spring-projects/sts4/issues/967))
* _(VSCode)_ fixed: Expose command to establish live connection to remote apps in vscode ([#947](https://github.com/spring-projects/sts4/issues/947))

## 2023-02-01 (4.17.2 RELEASE, incl. language servers version 1.44.0)

* _(Spring Boot)_ fixed: [symbols] do not auto-limit the result of the workspace symbol request ([#915](https://github.com/spring-projects/sts4/issues/915))
* _(Spring Boot)_ fixed: Unit test MavenProjectParser ([#918](https://github.com/spring-projects/sts4/issues/918))
* _(Spring Boot)_ fixed: Give user more information about what is "java sources reconciling" ([#920](https://github.com/spring-projects/sts4/issues/920))
* _(Spring Boot)_ fixed: [Java-17] Enable jdt.ls.commons.test tests to execute in the maven build ([#928](https://github.com/spring-projects/sts4/issues/928))
* _(Spring Boot)_ fixed: update generated parser for Java properties with latest ANTLR runtime version ([#946](https://github.com/spring-projects/sts4/issues/946))
* _(Spring Boot)_ fixed: Bad Escape exception showing up in log ([#950](https://github.com/spring-projects/sts4/issues/950))
* _(Spring Boot)_ fixed: various exceptions while reconciling ([#951](https://github.com/spring-projects/sts4/issues/951))
* _(Spring Boot)_ fixed: NPE from OpenRewrite Java Parser - Cannot read field "info" because "env" is null ([#952](https://github.com/spring-projects/sts4/issues/952))
* _(Spring Boot)_ fixed: Unknown property error shown in application.yml when using java records ([#955](https://github.com/spring-projects/sts4/issues/955))
* _(Spring Boot)_ fixed: [upgrading] running upgrade recipe for Spring Boot 3 causes exception ([#958](https://github.com/spring-projects/sts4/issues/958))
* _(VSCode)_ fixed: Is it feasible to drop the activation event onLanguage:xml? ([#926](https://github.com/spring-projects/sts4/issues/926))
* _(VSCode)_ fixed: Error: command 'sts.vscode-spring-boot.enableClasspathListening' not found ([#939](https://github.com/spring-projects/

## 2023-01-03 (4.17.1 RELEASE, incl. language servers version 1.43.0)

* _(Spring Boot)_ fixed: Eclipse STS 4.17 takes 96 % processor time on Intel i9 ([#934](https://github.com/spring-projects/sts4/issues/934))
* _(Spring Boot)_ fixed: BootLanguageServerBootApp class is taking all available cpu continuously ([#932](https://github.com/spring-projects/sts4/issues/932))
* _(Spring Boot)_ fixed: OpenRewrite unchecked，but always Language Server Background Job(Loading Rewrite Recipes) ([#925](https://github.com/spring-projects/sts4/issues/925))

#### known issues

* _(Spring Boot)_: The newly introduced additional reconciling for Java source files which is used to show up additional validations and quick fixes can cause increased memory and CPU consumption. In case you stumble upon issues in this area, feel free to disable the reconciling via `Preferences -> Extensions -> Spring Boot Tools -> Open Rewrite` (in VSCode) or `Preferences -> Language Servers -> Spring Language Servers -> Spring Boot Language Server -> Open Rewrite` (in Eclipse).

## 2022-12-08 (1.42.0)

* _(Spring Boot)_ fixed: [vscode] Boot LS is broken if Java LS launch mode is 'Hybrid' ([#919](https://github.com/spring-projects/sts4/issues/919))
* _(Spring Boot)_ fixed: [validation] spring.factories EnableAutoConfiguration key for boot 3.0 ignores 'on' setting ([#917](https://github.com/spring-projects/sts4/issues/917))

## 2022-12-07 (4.17.0 RELEASE, incl. language servers version 1.41.0)

* _(Spring Boot)_ fixed: Refreshing Live Data over HTTP is not working on VS Code ([#872](https://github.com/spring-projects/sts4/issues/872))
* _(Spring Boot)_ fixed: gcPauses -> jvm.gc.pause, memory -> jvm.memory.used ([#875](https://github.com/spring-projects/sts4/issues/875))
* _(Spring Boot)_ fixed: live information hovers are broken when running Spring Boot 3 application ([#862](https://github.com/spring-projects/sts4/issues/862))
* _(Spring Boot)_ fixed: fetch heap & nonHeap memory metrics together ([#874](https://github.com/spring-projects/sts4/issues/874))
* _(Spring Boot)_ fixed: 404 when getting live metrics over HTTP ([#879](https://github.com/spring-projects/sts4/issues/879))
* _(Spring Boot)_ fixed: [validation] Spring Boot support range validation messages appear and disappear again ([#887](https://github.com/spring-projects/sts4/issues/887))
* _(Spring Boot)_ fixed: [validation] check for superfluous @Repository annotations on standard Spring Data repositories ([#898](https://github.com/spring-projects/sts4/issues/898))
* _(Spring Boot)_ fixed: [validation] prepare version validation for 4.17.0 release ([#904](https://github.com/spring-projects/sts4/issues/904))
* _(Spring Boot)_ fixed: [validation] changing the preferences while language server is not around doesn't have any effect ([#836](https://github.com/spring-projects/sts4/issues/836))
* _(Spring Boot)_ fixed: [validation] convert autowired field to constructor should not run on test classes ([#902](https://github.com/spring-projects/sts4/issues/902))
* _(Spring Boot)_ fixed: [validation] add preferences to boot version and generation validations ([#884](https://github.com/spring-projects/sts4/issues/884))
* _(Spring Boot)_ fixed: [validation] add validations for new Spring Boot versions in general ([#885](https://github.com/spring-projects/sts4/issues/885))
* _(Spring Boot)_ fixed: [refactoring] convert autowired field to constructor should make field final ([#896](https://github.com/spring-projects/sts4/issues/896))
* _(Spring Boot)_ fixed: [refactoring] [validation] final polishing work ([#911](https://github.com/spring-projects/sts4/issues/911))
* _(Spring Boot)_ fixed: [refactoring] convert project to Spring Boot 3 doesn't update Java to 17 anymore ([#909](https://github.com/spring-projects/sts4/issues/909))
* _(Spring Boot)_ fixed: [refactoring] when converting a project to Spring Boot 3, always use the latest 3.0.x version ([#869](https://github.com/spring-projects/sts4/issues/869))
* _(Spring Boot)_ fixed: [refactoring] OutOfMemoryError: Java Heap Space ([#899](https://github.com/spring-projects/sts4/issues/899))
* _(Spring Boot)_ fixed: [refactoring] Exception when trying to convert project ([#900](https://github.com/spring-projects/sts4/issues/900))
* _(Spring Boot)_ fixed: [refactoring] converting a simple rest service guide to Spring Boot 3 fails ([#867](https://github.com/spring-projects/sts4/issues/867))
* _(Spring Boot)_ fixed: [refactoring] open rewrite refactoring menu item is broken when no language server is running ([#865](https://github.com/spring-projects/sts4/issues/865))
* _(Spring Boot)_ fixed: [refactoring] enhance Spring Boot version validation with quick fix to update ([#886](https://github.com/spring-projects/sts4/issues/886))
* _(Spring Boot)_ fixed: [refactoring] converting petclinic to Spring Boot 3 results in broken project ([#864](https://github.com/spring-projects/sts4/issues/864))
* _(Spring Boot)_ fixed: [refactoring] Migrate Boot 2 auto-config to Boot ([#908](https://github.com/spring-projects/sts4/issues/908))
* _(VSCode)_ fixed: make sure VSCode shows an error message when running on a JDK <17 ([#903](https://github.com/spring-projects/sts4/issues/903))
* _(VSCode)_ enhancement: Add shortcut to dashboard ([#876](https://github.com/spring-projects/sts4/issues/876)) - contributed by [Eskibear](https://github.com/Eskibear))
* _(VSCode)_ enhancement: refine walkthrough steps ([#907](https://github.com/spring-projects/sts4/issues/907)) - contributed by [Eskibear](https://github.com/Eskibear))
* _(VSCode)_ enhancement: update walkthrough about creating projects ([#916](https://github.com/spring-projects/sts4/issues/916)) - contributed by [Eskibear](https://github.com/Eskibear))

## 2022-10-26 (4.16.1 RELEASE, incl. language servers version 1.40.0)

* _(Spring Boot)_ fixed: Spring XML Config support does not show symbols with scope "File" ([#860](https://github.com/spring-projects/sts4/issues/860))
* _(Spring Boot)_ fixed: [open-rewrite] exception when executing quick fix for project ([#853](https://github.com/spring-projects/sts4/issues/853))
* _(Spring Boot)_ fixed: [refactoring] quick fix to convert autowired field to constructor param shows up even if the constructor param already exists ([#815](https://github.com/spring-projects/sts4/issues/815))
* _(VSCode)_ fixed: Vscode Spring Boot Tools 1.39.0 prevents Java project from Running/Debugging ([#847](https://github.com/spring-projects/sts4/issues/847))

## 2022-09-14 (4.16.0 RELEASE, incl. language servers version 1.39.0)

* _(Spring Boot)_ fixed: Workspace symbol null on custom annotations ([#818](https://github.com/spring-projects/sts4/issues/818))
* _(Spring Boot)_ fixed: organize imports causes communication issues with the language server ([#806](https://github.com/spring-projects/sts4/issues/806))
* _(Spring Boot)_ enhancement: Quick fix action to refactor field injection to constructor injection ([#522](https://github.com/spring-projects/sts4/issues/522))
* _(Spring Boot)_ fixed: Workspace symbol null on custom annotations ([#818](https://github.com/spring-projects/sts4/issues/818))
* _(VSCode)_ fixed: Extension never activates, throwing error "Header must provide a Content-Length property" ([#811](https://github.com/spring-projects/sts4/issues/811))

## 2022-08-11 (4.15.3 RELEASE, incl. language servers version 1.38.0)

* _(Spring Boot)_ fixed: Spring Boot Tools slow down code complete ([#808](https://github.com/spring-projects/sts4/issues/808))
* _(Language Server)_ fixed: Copied files should retain copyright ownership ([#807](https://github.com/spring-projects/sts4/issues/807))

## 2022-08-03 (4.15.2 RELEASE, incl. language servers version 1.37.0)

* _(Spring Boot)_ fixed: Validation errors on @@ placeholders in YAML editor ([#711](https://github.com/spring-projects/sts4/issues/711))
* _(VSCode)_ fixed: Live process is not auto connected when app is launched in internalConsole ([#794](https://github.com/spring-projects/sts4/issues/794))

## 2022-06-17 (1.36.0)

* _(Spring Boot)_ fixed: VScode incorrectly suggests removing @Autowired annotation from methods ([#787](https://github.com/spring-projects/sts4/issues/787))
* _(Spring Boot)_ fixed: VScode quick fix should not suggest removing @Autowired annotation from JUnit tests ([#786](https://github.com/spring-projects/sts4/issues/786))

## 2022-06-15 (1.35.0)

* _(VSCode)_ fixed: Cannot update vscode plugin to 1.34.0 ([#784](https://github.com/spring-projects/sts4/issues/784))

## 2022-06-15 (4.15.0 RELEASE, incl. language servers version 1.34.0)

* _(Spring Boot)_ fixed: vscode-sts: an edge case of workspace symbol for @PutMapping ([#781](https://github.com/spring-projects/sts4/issues/781))
* _(VSCode)_ fixed: Failed to refresh live data from process 12704 - com.xxxx.xx.xxx.BillingServiceApp after retries: 10 ([#748](https://github.com/spring-projects/sts4/issues/748))

## 2022-04-27 (4.14.1 RELEASE, incl. language servers version 1.33.0)

#### import changes

* _(VSCode)_ enhancement: live hovers are now automatically show up when you launch a Spring Boot application in VSCode. Additional JVM args for the Spring Boot app to enable JMX are added to the launch automatically. More details can be found in the user guide section about [Live Application Information](https://github.com/spring-projects/sts4/wiki/Live-Application-Information).

#### fixes and improvements

* _(Spring Boot)_ fixed: use `startupSnapshot` instead of `startup` timer call to avoid wiping out the underlying data
* _(Spring Boot, VSCode)_ fixed: When vscode opens a Java project for about 2 hours, the suggestion function will fail ([#750](https://github.com/spring-projects/sts4/issues/750))
* _(VSCode)_ improvement: add extension APIs to get live data ([#751](https://github.com/spring-projects/sts4/pull/751)) - contributed by @Eskibear

## 2022-03-16 (4.14.0 RELEASE, incl. language servers version 1.32.0)

* _(VSCode)_ fixed: VSCode Spring boot tools 1.30.0 error trying to find JVM ([#726](https://github.com/spring-projects/sts4/issues/726))
* _(VSCode)_ fixed: vscode "Problems" diagnostic entries lack "source" field ([#725](https://github.com/spring-projects/sts4/issues/725))
* _(VSCode)_ fixed: orphan vscode extension processes left running ([#704](https://github.com/spring-projects/sts4/issues/704))
* _(VSCode)_ fixed: deadlock in language server process avoids process to be shutdown ([#741](https://github.com/spring-projects/sts4/issues/741))
* _(VSCode)_ fixed: Outline View is broken again on Windows ([#742](https://github.com/spring-projects/sts4/issues/742))
* _(VSCode)_ fixed: boot-java.live-information.automatic-tracking.on is not working on vscode ([#733](https://github.com/spring-projects/sts4/issues/733))
* _(VSCode)_ fixed: Java 17 is mis-identified as Java 8 ([#713](https://github.com/spring-projects/sts4/issues/713))

## 2022-02-02 (4.13.1 RELEASE, incl. language servers version 1.30.0)

* _(Spring Boot)_ improvement: Consider if content assist can be offered for spring.config.import property keys ([#536](https://github.com/spring-projects/sts4/issues/536))
* _(Spring Boot)_ fixed: YAML editor generates wrong EOL characters in Windows ([#709](https://github.com/spring-projects/sts4/issues/709))
* _(VSCode)_ fixed: Always pop up a prompt box: indexing spring boot properties ([#697](https://github.com/spring-projects/sts4/issues/697))
* _(VSCode)_ fixed: Spring Boot Tools for VS Code incorrectly misidentifies internal JDK for Language Support for Java as a JRE ([#715](https://github.com/spring-projects/sts4/issues/715))

## 2021-12-08 (4.13.0 RELEASE, incl. language servers version 1.29.0)

* _(Spring Boot)_ fixed: STS 4.12.0 (for eclipse) *.yml can't automatic prompt ([#690](https://github.com/spring-projects/sts4/issues/690))
* _(Spring Boot)_ fixed: A StackOverFlow error for serializable Kotlin data class for autocompletion in the Eclipse IDE ([#693](https://github.com/spring-projects/sts4/issues/693))

## 2021-09-15 (4.12.0 RELEASE, incl. language servers version 1.28.0)

* _(VS Code, Spring Boot)_ fixed: [codespaces] vscode extension crashes if specified log output location doesn't work ([#676](https://github.com/spring-projects/sts4/issues/676))

## 2021-08-18 (4.11.1 RELEASE, incl. language servers version 1.27.0)

* _(VS Code, Spring Boot)_ fixed: [codespaces] spring boot extension starting up multiple times in Codespaces setting ([#669](https://github.com/spring-projects/sts4/issues/669))
* _(VS Code, Spring Boot)_ fixed: [codespaces] spring boot extension doesn't find the right JDK when connecting to a codespace ([#670](https://github.com/spring-projects/sts4/issues/670))
* _(VS Code, Spring Boot)_ fixed: JAVA_Home should be right but still shows "Note Java 8 can still be used in your own projects" ([#664](https://github.com/spring-projects/sts4/issues/664))

## 2021-06-18 (4.11.0 RELEASE, incl. language servers version 1.26.0)

* _(Spring Boot)_ fixed: Live data highlights disapearing after closing and reopening editor ([#616](https://github.com/spring-projects/sts4/issues/616))
* _(Spring Boot)_ fixed: In application.yml deprecated 'spring.profiles' is not showing a warning ([#615](https://github.com/spring-projects/sts4/issues/615))
* _(Spring Boot)_ fixed: Rename BootLanguagServerBootApp to BootLanguageServerBootApp ([#631](https://github.com/spring-projects/sts4/issues/631))
* _(VS Code, Spring Boot)_ new: new walkthrough contribution to get started with Spring Boot in VS Code
* _(VS Code, Spring Boot)_ fixed: not work when change LightWeight mode to Standard ([#628](https://github.com/spring-projects/sts4/issues/628))
* _(VS Code, Spring Boot)_ fixed: spring boot language server causing long waits for Java outline view when running in lightweight mode ([#642](https://github.com/spring-projects/sts4/issues/642))
* _(VS Code, Spring Boot)_ fixed: language server processes doesn't get shutdown properly in VSCode ([#636](https://github.com/spring-projects/sts4/issues/636))
* _(VS Code, Spring Boot)_ fixed: nothing shown in VS Code Outline view ([#627](https://github.com/spring-projects/sts4/issues/627))
* _(VS Code, Spring Boot)_ fixed: vscode spring boot language server not starting up ([#635](https://github.com/spring-projects/sts4/issues/635))
* _(VS Code, Spring Boot)_ fixed: VSCode extension JDK version ([#612](https://github.com/spring-projects/sts4/issues/612))

## 2021-03-17 (4.10.0 RELEASE, incl. language servers version 1.25.0)

* _(Spring Boot)_ enhancement: `.sts4` dir location now configurable ([#601](https://github.com/spring-projects/sts4/issues/601))
* _(Spring Boot)_ fixed: sometimes live hovers do not disappear ([#609](https://github.com/spring-projects/sts4/issues/609))

## 2020-12-16 (4.9.0 RELEASE, incl. language servers version 1.24.0)

* _(Spring Boot)_ new: show bean startup performance metrics in live hovers and code lenses (details in the user guide)
* _(Spring Boot)_ new: show basic request mapping performance metrics in live hovers and code lensses (details in the user guide)
* _(Spring Boot)_ new: provide content-assist for constructor-arg name in Spring XML config files ([#562](https://github.com/spring-projects/sts4/issues/562))
* _(Spring Boot)_ fixed: language-server-internal exception happening when saving a file that has a space or other special characters in its name or path

## 2020-10-23 (4.8.1 RELEASE, incl. language servers version 1.23.0)

* _(Spring Boot)_ fixed: Spring Boot Language Server processing java.util.Properties type configuration logic error ([#534](https://github.com/spring-projects/sts4/issues/534))
* _(Spring Boot)_ fixed: Add support for Spring Boot multi-document properties files ([#533](https://github.com/spring-projects/sts4/issues/533))
* _(Spring Boot)_ fixed: Support escaping of map keys with '[]' in Spring Boot property yaml files
* _(Spring Boot)_ fixed: Tolerate '.[' for map navigation in .properties file

## 2020-09-17 (4.8.0 RELEASE, incl. language servers version 1.22.0)

* _(Spring Boot)_ fix: Eclips shows error on `@ConditionalOnExpression("${downlink.active:false}")` ([#529](https://github.com/spring-projects/sts4/issues/529))

## 2020-08-27 (4.7.2 RELEASE, incl. language servers version 1.21.0)

* no major changes

## 2020-07-30 (4.7.1 RELEASE, incl. language servers version 1.20.0)

* _(Spring Boot)_ enhancement: Syntax check for Annotations with Spring Expression Language ([#475](https://github.com/spring-projects/sts4/issues/475))
* _(Spring Boot)_ fixed: hard to reproduce BadLocationException inside of language server fixed now, occurred e.g. in ([#451](https://github.com/spring-projects/sts4/issues/451))

## 2020-06-18 (4.7.0 RELEASE, incl. language servers version 1.19.0)

* _(Spring Boot)_ bugfix: automatic live hover detection works again reliably
* _(VSCode)_ improvement: Flag to configure the "JAVA_HOME or PATH..." message display ([#478](https://github.com/spring-projects/sts4/issues/478))
* _(VSCode)_ bugfix: green live hovers markers are no longer gone after switching to a different editor

## 2020-05-28 (4.6.2 RELEASE, incl. language servers version 1.18.0)

* _(Spring Boot)_ improvement: additional check to auto-connect live hovers only when actuators are on the project classpath ([#450](https://github.com/spring-projects/sts4/issues/450))
* _(Spring Boot)_ improvement: added content assist for keys that exist in YAML and properties files ([#427](https://github.com/spring-projects/sts4/issues/427))
* _(Spring Boot)_ improvement: Yaml editor gives error when using @..@ placeholders ([#190](https://github.com/spring-projects/sts4/issues/190))
* _(Spring Boot)_ bugfix: super type hierarchy lookup might fail
* _(Spring Boot)_ bugfix: quickly restarting app caused error popup from live hover mechanism to show up
* _(VSCode/Theia)_ bugfix: Fix null reference when no JVM was found - contributed by [@tfriem](https://github.com/tfriem)

## 2020-04-23 (4.6.1 RELEASE)

* _(Spring Boot)_ improvement: Spring yaml validation doesn't understand the "<<:" syntax ([#440](https://github.com/spring-projects/sts4/issues/440))
* _(Spring Boot)_ improvement: YAML Property completion: Superclass properties not detected in case of List or Map ([#449](https://github.com/spring-projects/sts4/issues/449))
* _(Spring Boot)_ improvement: improved performance for content-assist for Spring XML config files
* _(Spring Boot)_ bugfix: Quick Fix for unknown properties broken ([#442](https://github.com/spring-projects/sts4/issues/442))

## 2020-03-19 (4.6.0 RELEASE)

* _(Spring Boot)_ performance: improved performance while scanning projects for symbols
* _(Spring Boot)_ performance: improved performance when multiple files change at once (e.g. after a switch to a different branch or a git pull + refresh)

## 2020-01-22 (4.5.1 RELEASE)

* _(Spring Boot)_ improvement: live hover mechanism now reports connection failures

## 2019-12-19 (4.5.0 RELEASE)

* _(Spring Boot)_ improvement: better and more consistent labels for running processes in live hover actions across the board

## 2019-11-21 (4.4.2 RELEASE)

* _(Spring Boot)_ bugfix: CTRL-click in yaml file inaccurate (for 'nested' properties)

## 2019-10-24 (4.4.1 RELEASE)

* _(Spring Boot)_ Provide UI to allow user to explicitly connect/disconnect to/from processes to collect Live Hover data from. See the [wiki](https://github.com/spring-projects/sts4/wiki/Live-Application-Information#managing-live-data-connections-to-local-processes) for details.
* _(Spring Boot)_ enhancement: Goto Symbol now also works from XML bean files.
* _(Spring Boot)_: improve performance of xml symbol scanning.

## 2019-09-19 (4.4.0 RELEASE)

* _(Spring Boot)_ fixed: Adding/removing bean in XML file doesn't update the symbol index
* _(VSCode)_ fixed: show error message when manually configured JDK is not there

## 2019-08-13 (4.3.2 RELEASE)

* _(Spring Boot)_ fixed: CTRL-click navigation does not handle properties on super class correctly ([#326](https://github.com/spring-projects/sts4/issues/326))
* _(Spring Boot)_ fixed: Configuration property analysis does not handle properties written in snake_case correctly ([#327](https://github.com/spring-projects/sts4/issues/327))
* _(VSCode)_ fixed: Spring boot tool vscode extension is causing system to run out of disk space ([#328](https://github.com/spring-projects/sts4/issues/328))

## 2019-07-12 (4.3.1 RELEASE)

* _(all language servers)_ performance: further improvements to the language server startup time
* _(Spring Boot)_ fixed: wrong error markers in properties files([#314](https://github.com/spring-projects/sts4/issues/314))
* _(VS Code)_ fixed: vscode goto definition error with lsp ([#309](https://github.com/spring-projects/sts4/issues/309))

## 2019-06-21 (4.3.0 RELEASE)

* _(Spring Boot)_ improvement: project classpath notifications now happen in batch on startup to further optimize performance and job load on the Eclipse side
* _(Spring Boot)_ improvement: symbols are now being re-created if dependent types change
* _(Spring Boot)_ fixed: Slow code completion takes more than a 1 sec. ([#293](https://github.com/spring-projects/sts4/issues/293))
* _(Spring Boot)_ fixed: content-assist for Spring XML config files now working again in VS Code and Theia
* _(Spring Boot)_ fixed: ClassCast Exception in Boot LS while application.yml file opened in the editor
* _(Spring Boot)_ fixed: Anonymous inner type beans don't have boot hints

## 2019-05-24 (4.2.2 RELEASE)

* _(all language servers)_ performance: additional improvements to language server startup time
* _(Spring Boot)_ new: additional fine-grained preferences for Spring XML config file support
* _(Spring Boot)_ new: navigation for bean identifiers, bean classes, and property names for Spring XML config files
* _(Spring Boot)_ new: content-assist rolled out for many more Spring XML config elements and attributes
* _(Spring Boot)_ new: live bean information now showing up in types from class files (when source code is shown) - _VSCode and Theia only at the moment_
* _(Spring Boot)_ improvement: hugely improved content-assist for bean class attribute in Spring XML config files (incl. package name proposals and vastly improved performance)
* _(Spring Boot)_ improvement: property name content-assist in Spring XML config files now shows proposals from properties defined in supertypes, too
* _(Spring Boot)_ improvement: symbol scanning skips output folders now
* _(Spring Boot)_ fixed: Detect @RequestMapping with path defined as constant instead of literal string ([#281](https://github.com/spring-projects/sts4/issues/281))
* _(Spring Boot)_ fixed: NPE when invoking property name content-assist in XML file without bean class being defined
* _(Spring Boot)_ fixed: tags in yaml files with dollar signs throw IllegalGroupReference in properties editor

## 2019-04-18 (4.2.1 RELEASE)

* _(Spring Boot)_ performance: additional performance and memory footprint improvements to symbol indexing, eats now less memory and is faster while doing the initial indexing run
* _(Spring Boot)_ new: content-assist for bean types in Spring XML config files
* _(Spring Boot)_ new: content-assist for property names in Spring XML config files
* _(Spring Boot)_ new: content-assist for bean references in property definitions in Spring XML config files (very rough early cut, needs a lot more work on proposal content, number of proposals, and sorting)
* _(Spring Boot)_ improvement: limit the number of XML files that are scanned for bean symbols, output folders now ignored
* _(Spring Boot)_ fixed: Ctrl-click in Java editor in Eclipse wasn't working due to issue with hyperlink detector
* _(VSCode, Atom, Theia)_ improvement: JVM args can now be configured for language server processes

## 2019-03-21 (4.2.0 RELEASE)

* _(Spring Boot)_ new: Allow configuration of VM arguments for LSP process "PropertiesLauncher" ([#211](https://github.com/spring-projects/sts4/issues/211))
* _(Spring Boot)_ performance: major performance improvements to symbol indexing infrastructure by caching created symbols across language server starts
* _(Spring Boot)_ performance: replaced internal type indexing with communication to JDT (language server) to save time and memory spend for keeping our own type index

## 2019-02-21 (4.1.2 RELEASE)

* _(Spring Boot)_ new: live hover information for bean wirings now supports war-packaged boot apps running in a local server installation
* _(Spring Boot)_ new: live hover information for `@Value` annotations ([#177](https://github.com/spring-projects/sts4/issues/177))
* _(Spring Boot)_ new: property completion now works for `.yaml` files as well ([#191](https://github.com/spring-projects/sts4/issues/191))
* _(Spring Boot)_ new: bean symbols from XML config files now include exact location information
* _(Spring Boot)_ new: bean symbols from XML config files now generated for beans without a bean ID, too
* _(Spring Boot)_ fixed: navigate to resource in live hovers for apps running on CF works again
* _(Spring Boot)_ fixed: ConcurrentModificationException while retrieving symbols from language server
* _(Spring Boot)_ fixed: race condition that sometimes caused initial project to be not indexed for symbols
* _(Spring Boot)_ fixed: search for symbols in project now happens on the server side to avoid no project-related symbols showing up on the client side before you start typing in a query
* _(Spring Boot)_ performance: improvement to further reduce the CPU load when checking processes for live hovers ([#140](https://github.com/spring-projects/sts4/issues/140))
* _(Spring Boot)_ performance: the language server doesn't trigger a full source and javadoc download for Maven projects anymore
* _(VSCode)_ fixed: "class" snippet is not available ([#192](https://github.com/spring-projects/sts4/issues/192))

## 2019-01-24 (4.1.1 RELEASE)

* (Spring Boot) quick navigation via symbols now available for non-Boot Spring projects
* (Spring Boot) live hover informations for bean wirings now available for non-Boot Spring projects - _for details how to enable this for your apps, take a look at the [user guide](https://github.com/spring-projects/sts4/wiki/Live-Application-Information) section for that_
* (Spring Boot) added support for deprecated properties (including corresponding quick-fix) 
* (Spring Boot) quick fix to generate default metadata for missing properties ([#101](https://github.com/spring-projects/sts4/issues/101))
* (Spring Boot) first steps towards generating symbols for Spring XML config files ([#108](https://github.com/spring-projects/sts4/issues/108#issuecomment-455135918))
* (Spring Boot) fixed: live hovers don't show up for classes with a name starting with multiple upper case charatecters
* (Spring Boot) fixed: type and resource navigation in bean live hovers don't work for types and resources from dependencies

## 2018-12-20 (4.1.0 RELEASE)

* (Spring Boot) first initial version of content-assist for Spring Data repository definitions
* (Spring Boot) live hover links to types now work for projects using JDK 9 and beyond, too
* (Spring Boot) fixed an issue with stopped apps on CF causing boot language server to get stuck when connected to JMX via SSH tunnel

## 2018-11-30 (4.0.2 RELEASE)

* (Spring Boot) Make CTRL-CLICK navigation from application.properties to Java work
* (Spring Boot) Make CTRL-CLICK navigation from application.yml to Java work
* (Spring Boot) Made content-assist for values in lists more consistent across .yml and .properties editors.
* (Spring Boot) Bugfix: Adding eureka client starter to classpath breaks requestmapping live hovers.
* (Spring Boot) More precise autowiring live hovers for @Bean method parameters.
* (Spring Boot) server.servlet.context-path now supported for Request Mapping live hover links.
* (Spring Boot) Improved 'Goto Symbol' support for functional style WebFlux requestmappings.
* (Spring Boot) Improved 'Live Hover' support for functional style WebFlux requestmappings.
* (Spring Boot) Bugfix: Insertion of mapping templates now takes into account leading @ in editor.
* (Spring Boot) Added support for Spring Boot log groups in properties and yaml editor.
* (General) Various bugfixes for bugs causing language servers to hang and become unresponsive.
* (Spring Boot) (Concourse) Bugfix: Quickfix not working (anymore?) in LSP editors.
* (Spring Boot) After adding Spring Boot configuration processor editor automatically becomes aware of new properties metadata.

## 2018-10-31 (4.0.1 RELEASE)

* _(Spring Boot)_ fixed NPE from SpringIndexer ([#105](https://github.com/spring-projects/sts4/issues/105))
* _(Spring Boot)_ filed: Spring Boot configuration property auto-completion does not offer properties on super classes ([#116](https://github.com/spring-projects/sts4/issues/116))
* _(Spring Boot)_ fixed: Lots of NPE noise in language server ([#90](https://github.com/spring-projects/sts4/issues/90))
* _(Spring Boot)_ fixed: Live Boot Hint Decorators not working when app ObjectMapper configured with NON_DEFAULT inclusion  ([#80](https://github.com/spring-projects/sts4/issues/80))
* _(Spring Boot)_ fixed: property support now understand nested project structure

## 2018-09-25 (4.0.0 RELEASE)

* _(Spring Boot)_ `Cmd-6` in Eclipse shows `Go To Symbols in Workspace` first, second `Cmd-6` switches to `Go To Symbols in File`
* _(Spring Boot)_ various bug fixes

## 2018-08-30 (M15)

* _(Spring Boot)_ improved the overall content of bean wiring live hovers
* _(Spring Boot)_ live hover information for bean wirings now show up more precisely on autowired fields and constructors as well as at `@Bean` definitions, including more complete information about the wirings
* _(Spring Boot)_ improved performance of live hovers for remote boot apps
* _(Spring Boot)_ added experimental option to show code lenses for live hover information, including bean wiring and request mapping URLs (use the preferences to switch that on)
* _(Spring Boot)_ added detailed information to the boot dashboard property view about JMX tunnels over SSH to boot apps running on CloudFoundry
* _(Spring Boot)_ added action to enable/disable JMX tunneling through SSH for already deployed and running apps
* _(Spring Boot)_ improved performance and reduced footprint of live hover update mechanism
* _(Spring Boot)_ bugfix: fixed missing line break in live hover for request mappings
* _(Spring Boot)_ bugfix: resource links in live hovers for remote boot apps now working
* _(Spring Boot)_ bugfix: make the overall classpath detection mechanism more reliable in case of project deletions ([#69](https://github.com/spring-projects/sts4/issues/69))

## 2018-08-09 (M14)

* _(Spring Boot)_ added support for showing live hovers for Spring Boot apps running remotely (on Cloud Foundry)
* _(Spring Boot)_ improved and simplified content for live hovers showing bean wiring information
* _(Spring Boot)_ improved look of live hover highlights
* (_Spring Boot)_ improved error handling when source code parsing goes wrong
* (_Spring Boot)_ added specific bean wiring live hovers for `@Autowired` fields and constructors
* (_Spring Boot)_ user-defined values in property files showing up as suggestions for `@Value` completions
* (_Spring Boot)_ bean symbols now directly contain additional annotations (like `@Conditional...` or `@Profile`)
* (_Spring Boot)_ added option to match running process directly to specific project in the workspace (for live hovers) via system property (set `-Dspring.boot.project.name=<project-name-in-workspace>` to show live hovers of that running process exclusively on the project with that name).
* (_Spring Boot)_ fixed bug that prevented property editing support to work on Windows ([#59](https://github.com/spring-projects/sts4/issues/59))

## 2018-07-23 (M13)

* _(Spring Boot)_ early prototype for detecting changed bean definitions in live-running (and restarted) boot applications
* _(Spring Boot)_ @Inject annotation now supported for live hovers
* _(Spring Boot)_ added option to match live running apps and workspace projects manually
* _(Spring Boot)_ improved JMX connector reuse (internal optimization)

## 2018-06-08 (M12)

* _(Spring Boot)_ live hovers now updated in all open editors, not just the active one
* _(Spring Boot)_ more detailed context shown in hover documentation when editing property files ([#265](https://github.com/spring-projects/spring-ide/issues/265))
* _(Spring Boot)_ performance improvement: project symbols now show up a lot faster for the open editors/projects (in a multi-root folder workspace)
* _(Spring Boot)_ bugfix: JDK9 and JDK10 projects supported now even if main editor and/or language server runs on JDK8
* _(Spring Boot)_ bugfix: various NPEs in Spring indexer fixed

## 2018-05-14 (M11)

* _(Spring Boot)_ major performance improvements and footprint reductions (due to a groundbreaking change to how projects are being resolved, this is now delegated to the surrounding Java tooling)
* _(Spring Boot)_ support for JDK10 added
* _(Spring Boot)_ bugfix - custom JMX domains now supported for live hovers ([#44](https://github.com/spring-projects/sts4/issues/44))
* _(Spring Boot)_ bugfix - live hovers now show up for all open editors (not limited to the one with focus anymore)
* _(all)_ JVM used to run the language servers can now be specified via custom settings ([#51](https://github.com/spring-projects/sts4/issues/51))

## 2018-03-15 (M10)

* _(Spring Boot)_ support added for request-mapping-like symbols for webflux router definitions
* _(Spring Boot)_ code lenses for webflux handler methods implemented that visualize the mapped route (VSCode only at the moment)
* _(Spring Boot)_ document highlight support added for webflux router definitions that highlight parts of the routes that belong together
* _(Spring Boot)_ request mapping symbols now include accept and content type defintions
* _(Spring Boot)_ support for direct navigation from live injection reports to source code added for Eclipse
* _(Spring Boot)_ abstract @Bean-annotated methods are now ignored when creating symbols for bean definitions
* _(Spring Boot)_ URLs from hovers (like request mapping URLs from running boot apps) now open in an internal browser that has a navigation and an address bar
* _(Spring Boot)_ bugfix for NPE that happened occasionally when creating a new Java file
* _(all)_ language server processes now show up with their real name when using `jps` instead of just `JarLauncher`

## 2018-02-23 (M9)

__Attention:__ We merged the two different extensions for Spring Boot (for Java code and for properties) into a single extension for the various platforms (Eclipse, VSCode, Atom). This might require that you manually uninstall the old extensions and install the new ones. Automatic updates don't work here. For the Eclipse case, you might want to start with a fresh STS4 M9 distribution and go from there to avoid manual uninstall/install steps.

* _(Spring Boot)_ support added for Spring Data repositories, they show up as bean symbols now
* _(Spring Boot)_ fixed a bug that caused an exception when using content-assist for a non-Spring-Boot java project
* _(Spring Boot, VSCode)_ support for navigation added to live injection reports, they allow you to directly navigate to the source code of the bean type and the resource where the bean got defined. Limitation: this works for VSCode only at the moment, support for Eclipse and Atom still in progress
* _(Spring Boot, Eclipse integration)_ fixed a bug that caused content-assist to be turned off in the java editor
* improved the way the JDK to run the language server is found together with an improved error message if no JDK can be found

## 2018-01-30 (M8)

* _(Spring Boot Java)_ function declarations are now being parsed into symbols for functions that directly inherit from `java.util.Function` ([#18](https://github.com/spring-projects/sts4/issues/18))
* _(Spring Boot Java)_ updated live hover mechanics to work with latest Spring Boot 2.0 snapshot versions 
* _(Spring Boot Java)_ improved the way the JDK (to run the language server) is found
* _(Spring Boot Java)_ improved warning message about missing tools.jar
* _(Spring Boot Java)_ live hovers now show up on class flies that are displayed as source
* _(Spring Boot Java)_ fixed a problem with outdated symbols showing up after file deletion/rename
* _(Spring Boot Java)_ fixed a deadlock issue
* _(Spring Boot Java)_ reduced number of threads used behind the scenes
* _(Spring Boot Java)_ reduced number of CPU cycles used by live hover mechanism
* _(Spring Boot Java, Spring Boot Properties)_ reduced memory footprint
* _(Spring Boot Properties)_ fixed an issue with wrong indentation after inserting property node

## 2017-12-15 (M7)

* _(all)_ issues solved when running on Windows ([#25](https://github.com/spring-projects/sts4/issues/25), [#26](https://github.com/spring-projects/sts4/issues/26), [#29](https://github.com/spring-projects/sts4/issues/29))
* _(Spring Boot Java)_ live hover information now works for inner classes
* _(Spring Boot Properties)_ boot property editing now activated for bootstrap*.yml files in VSCode automatically

## 2017-12-04

* initial public beta launch
