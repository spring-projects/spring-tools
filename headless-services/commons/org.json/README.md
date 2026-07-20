Repackaged org.json version 20260719. 

There are no changes in this package except for renaming packages to avoid
confict with the 'original' org.json. 

However, projects depending on this org.json to play some tricks with the
classpath to replace some of the original code with a modified copy
to allow for controlling order of map keys in JSONObject. See JSONObject.java 
in spring-boot-language-server. 