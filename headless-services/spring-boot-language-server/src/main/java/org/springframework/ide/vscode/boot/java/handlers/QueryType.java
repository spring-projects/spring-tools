package org.springframework.ide.vscode.boot.java.handlers;

public enum QueryType {
    SPEL("Explain SpEL Expression with AI", "Explain the following SpEL Expression with a clear summary first, followed by a breakdown of the expression with details: \n\n"),
    JPQL("Explain Query with AI", "Explain the following JPQL query with a clear summary first, followed by a detailed explanation. If the query contains any SpEL expressions, explain those parts as well: \n\n"),
    HQL("Explain Query with AI", "Explain the following HQL query with a clear summary first, followed by a detailed explanation. If the query contains any SpEL expressions, explain those parts as well: \n\n"),
    MONGODB("Explain Query with AI", "Explain the following MongoDB query with a clear summary first, followed by a detailed explanation. If the query contains any SpEL expressions, explain those parts as well: \n\n"),
    AOP("Explain AOP annotation with AI", "Explain the following AOP annotation with a clear summary first, followed by a detailed contextual explanation of annotation and its purpose: \n\n"),
    DEFAULT("Explain Query with AI", "Explain the following query with a clear summary first, followed by a detailed explanation: \n\n"),

    ROUTER_CONVERSION("Convert to Router Builder Pattern with AI",
    		"""
    		Convert the Spring WebFlux/WebMVC functional router method $method_name$ at line $line_no$ from using static imports (RouterFunctions.route(), RequestPredicates.GET(), etc.)
    		to the modern builder pattern (RouterFunctions.route().GET().POST().build()).
    		
    		Provide the complete refactored method with the same functionality.
    		""")

    ;

    private final String title;
    private final String prompt;

    QueryType(String title, String prompt) {
        this.title = title;
        this.prompt = prompt;
    }

    public String getTitle() {
        return title;
    }

    public String getPrompt() {
        return prompt;
    }
}
