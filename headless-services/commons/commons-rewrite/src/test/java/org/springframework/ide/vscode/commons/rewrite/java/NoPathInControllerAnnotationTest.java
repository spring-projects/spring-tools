/*******************************************************************************
 * Copyright (c) 2025 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.rewrite.java;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.Assertions;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

public class NoPathInControllerAnnotationTest implements RewriteTest {
	
	@Override
	public void defaults(RecipeSpec spec) {
		spec.recipe(new NoPathInControllerAnnotation()).parser(JavaParser.fromJavaVersion().classpath("spring-context", "spring-web"));
	}

    @Test
    void movePathFromControllerToRequestMapping() {
        rewriteRun(
            Assertions.java(
                """
                package com.example;
                
                import org.springframework.stereotype.Controller;
                
                @Controller("/api/users")
                public class UserController {
                    // controller methods
                }
                """,
                """
                package com.example;
                
                import org.springframework.stereotype.Controller;
                import org.springframework.web.bind.annotation.RequestMapping;
                
                @Controller
                @RequestMapping("/api/users")
                public class UserController {
                    // controller methods
                }
                """
            )
        );
    }

    @Test
    void movePathFromRestControllerToRequestMapping() {
        rewriteRun(
            Assertions.java(
                """
                package com.example;
                
                import org.springframework.web.bind.annotation.RestController;
                
                @RestController("/api/products")
                public class ProductController {
                    // controller methods
                }
                """,
                """
                package com.example;
                
                import org.springframework.web.bind.annotation.RequestMapping;
                import org.springframework.web.bind.annotation.RestController;
                
                @RestController
                @RequestMapping("/api/products")
                public class ProductController {
                    // controller methods
                }
                """
            )
        );
    }

    @Test
    void updateExistingRequestMapping() {
        rewriteRun(
            Assertions.java(
                """
                package com.example;
                
                import org.springframework.stereotype.Controller;
                import org.springframework.web.bind.annotation.RequestMapping;
                
                @Controller("/api/orders")
                @RequestMapping("/orders")
                public class OrderController {
                    // controller methods
                }
                """,
                """
                package com.example;
                
                import org.springframework.stereotype.Controller;
                import org.springframework.web.bind.annotation.RequestMapping;
                
                @Controller
                @RequestMapping("/api/orders")
                public class OrderController {
                    // controller methods
                }
                """
            )
        );
    }

    @Test
    void updateExistingRequestMappingWithPathParameter() {
        rewriteRun(
            Assertions.java(
                """
                package com.example;
                
                import org.springframework.stereotype.Controller;
                import org.springframework.web.bind.annotation.RequestMapping;
                
                @Controller("/api/customers")
                @RequestMapping(path = "/customers")
                public class CustomerController {
                    // controller methods
                }
                """,
                """
                package com.example;
                
                import org.springframework.stereotype.Controller;
                import org.springframework.web.bind.annotation.RequestMapping;
                
                @Controller
                @RequestMapping(path = "/api/customers")
                public class CustomerController {
                    // controller methods
                }
                """
            )
        );
    }

    @Test
    void updateExistingRequestMappingWithValueParameter() {
        rewriteRun(
            Assertions.java(
                """
                package com.example;
                
                import org.springframework.web.bind.annotation.RestController;
                import org.springframework.web.bind.annotation.RequestMapping;
                
                @RestController("/api/inventory")
                @RequestMapping(value = "/inventory")
                public class InventoryController {
                    // controller methods
                }
                """,
                """
                package com.example;
                
                import org.springframework.web.bind.annotation.RestController;
                import org.springframework.web.bind.annotation.RequestMapping;
                
                @RestController
                @RequestMapping(value = "/api/inventory")
                public class InventoryController {
                    // controller methods
                }
                """
            )
        );
    }

    @Test
    void doNotModifyControllerWithoutPath() {
        rewriteRun(
            Assertions.java(
                """
                package com.example;
                
                import org.springframework.stereotype.Controller;
                
                @Controller
                public class SimpleController {
                    // controller methods
                }
                """
            )
        );
    }

    @Test
    void doNotModifyControllerWithNonPathValue() {
        rewriteRun(
            Assertions.java(
                """
                package com.example;
                
                import org.springframework.stereotype.Controller;
                
                @Controller("someValue")
                public class SimpleController {
                    // controller methods
                }
                """
            )
        );
    }

    @Test
    void handleControllerWithMultipleAnnotations() {
        rewriteRun(
            Assertions.java(
                """
                package com.example;
                
                import org.springframework.stereotype.Controller;
                import org.springframework.stereotype.Component;
                
                @Component
                @Controller("/api/reports")
                public class ReportController {
                    // controller methods
                }
                """,
                """
                package com.example;
                
                import org.springframework.stereotype.Controller;
                import org.springframework.web.bind.annotation.RequestMapping;
                import org.springframework.stereotype.Component;
                
                @Component
                @Controller
                @RequestMapping("/api/reports")
                public class ReportController {
                    // controller methods
                }
                """
            )
        );
    }

    @Test
    void handleRestControllerWithMultipleAnnotations() {
        rewriteRun(
            Assertions.java(
                """
                package com.example;
                
                import org.springframework.web.bind.annotation.RestController;
                import org.springframework.stereotype.Component;
                
                @Component
                @RestController("/api/analytics")
                public class AnalyticsController {
                    // controller methods
                }
                """,
                """
                package com.example;
                import org.springframework.web.bind.annotation.RequestMapping;
                import org.springframework.web.bind.annotation.RestController;
                import org.springframework.stereotype.Component;
                
                @Component
                @RestController
                @RequestMapping("/api/analytics")
                public class AnalyticsController {
                    // controller methods
                }
                """
            )
        );
    }

    @Test
    void handleControllerWithValueParameter() {
        rewriteRun(
            Assertions.java(
                """
                package com.example;
                
                import org.springframework.stereotype.Controller;
                
                @Controller(value = "/api/dashboard")
                public class DashboardController {
                    // controller methods
                }
                """,
                """
                package com.example;
                
                import org.springframework.stereotype.Controller;
                import org.springframework.web.bind.annotation.RequestMapping;
                
                @Controller
                @RequestMapping("/api/dashboard")
                public class DashboardController {
                    // controller methods
                }
                """
            )
        );
    }

    @Test
    void handleRestControllerWithValueParameter() {
        rewriteRun(
            Assertions.java(
                """
                package com.example;
                
                import org.springframework.web.bind.annotation.RestController;
                
                @RestController(value = "/api/notifications")
                public class NotificationController {
                    // controller methods
                }
                """,
                """
                package com.example;
                
                import org.springframework.web.bind.annotation.RequestMapping;
                import org.springframework.web.bind.annotation.RestController;
                
                @RestController
                @RequestMapping("/api/notifications")
                public class NotificationController {
                    // controller methods
                }
                """
            )
        );
    }
}
