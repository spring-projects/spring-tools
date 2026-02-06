/*******************************************************************************
 * Copyright (c) 2017, 2025 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.beans.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.app.SpringSymbolIndex;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.bootiful.SymbolProviderTestConf;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.languageserver.testharness.Editor;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * @author Udayani V
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(SymbolProviderTestConf.class)
public class BeanCompletionProviderTest {
	
	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringMetamodelIndex springIndex;
	@Autowired private SpringSymbolIndex indexer;

	private File directory;
	private IJavaProject project;
	private Bean[] indexedBeans;
	private String tempJavaDocUri;
	private Bean bean1;
	private Bean bean2;
	private Bean bean3;
	private Bean bean4;
	private Bean bean5;
	private Bean bean6;
	private Bean bean7;
	private Bean bean8;
	
	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);

		directory = new File(ProjectsHarness.class.getResource("/test-projects/test-spring-indexing/").toURI());
		
		harness.changeConfiguration(Map.of("boot-java", Map.of("java", Map.of("completions", Map.of("inject-bean", true)))));

		String projectDir = directory.toURI().toString();
		project = projectFinder.find(new TextDocumentIdentifier(projectDir)).get();

		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(5, TimeUnit.SECONDS);
		
		indexedBeans = springIndex.getBeansOfProject(project.getElementName());
		
        tempJavaDocUri = directory.toPath().resolve("src/main/java/org/test/TempClass.java").toUri().toString();
		bean1 = new Bean("ownerRepository", "org.springframework.samples.petclinic.owner.OwnerRepository", new Location(tempJavaDocUri, new Range(new Position(1,1), new Position(1, 20))), null, null, null, false, "symbolLabel");
		bean2 = new Bean("ownerService", "org.springframework.samples.petclinic.owner.OwnerService", new Location(tempJavaDocUri, new Range(new Position(1,1), new Position(1, 20))), null, null, null, false, "symbolLabel");
		bean3 = new Bean("visitRepository", "org.springframework.samples.petclinic.owner.VisitRepository", new Location(tempJavaDocUri, new Range(new Position(1,1), new Position(1, 20))), null, null, null, false, "symbolLabel");
		bean4 = new Bean("visitService", "org.springframework.samples.petclinic.owner.VisitService", new Location(tempJavaDocUri, new Range(new Position(1,1), new Position(1, 20))), null, null, null, false, "symbolLabel");
		bean5 = new Bean("petService", "org.springframework.samples.petclinic.pet.Inner.PetService", new Location(tempJavaDocUri, new Range(new Position(1,1), new Position(1, 20))), null, null, null, false, "symbolLabel");
		bean6 = new Bean("testBeanCompletionClass", "org.sample.test.TestBeanCompletionClass", new Location(tempJavaDocUri, new Range(new Position(1,1), new Position(1, 20))), null, null, null, false, "symbolLabel");
		bean7 = new Bean("testIntBean", "java.lang.Integer", new Location(tempJavaDocUri, new Range(new Position(1,1), new Position(1, 20))), null, null, null, false, "symbolLabel");
		bean8 = new Bean("testIntBean", "java.lang.Integer", new Location(tempJavaDocUri, new Range(new Position(1,1), new Position(1, 20))), null, null, null, false, "symbolLabelDuplicate");
		
		springIndex.updateBeans(project.getElementName(), new Bean[] {bean1, bean2, bean3, bean4, bean5, bean6, bean7, bean8});
	}
	
	@AfterEach
	public void restoreIndexState() {
		this.springIndex.updateBeans(project.getElementName(), indexedBeans);
	}
	
	@Test
	public void testBeanCompletion_firstCompletion() throws Exception {
		assertCompletions(getCompletion("ownerRe<*>"), new String[] {"ownerRepository", "ownerService"}, 0, 
				"""
package org.sample.test;

import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.stereotype.Controller;

@Controller
public class TestBeanCompletionClass {

    private final OwnerRepository ownerRepository;

    TestBeanCompletionClass(OwnerRepository ownerRepository) {
        this.ownerRepository = ownerRepository;
    }

		public void test() {
ownerRepository<*>
		}
}	    
									""");
	}
	
	@Test
	public void testBeanCompletion_secondCompletion() throws Exception {
		assertCompletions(getCompletion("ownerRe<*>"), new String[] {"ownerRepository", "ownerService"}, 1, 
				"""
package org.sample.test;

import org.springframework.samples.petclinic.owner.OwnerService;
import org.springframework.stereotype.Controller;

@Controller
public class TestBeanCompletionClass {

    private final OwnerService ownerService;

    TestBeanCompletionClass(OwnerService ownerService) {
        this.ownerService = ownerService;
    }

		public void test() {
ownerService<*>
		}
}	    
									""");
	}
	
	@Test
	public void noPrefix_secondCompletion() throws Exception {
		assertCompletions(getCompletion("<*>"), new String[] {"ownerRepository", "ownerService", "petService", "testIntBean", "visitRepository", "visitService"}, 1, 
				"""
package org.sample.test;

import org.springframework.samples.petclinic.owner.OwnerService;
import org.springframework.stereotype.Controller;

@Controller
public class TestBeanCompletionClass {

    private final OwnerService ownerService;

    TestBeanCompletionClass(OwnerService ownerService) {
        this.ownerService = ownerService;
    }

		public void test() {
ownerService<*>
		}
}	    
									""");
	}
	
	@Test
	public void testBeanCompletion_injectInnerClass() throws Exception {
		assertCompletions(getCompletion("<*>"), new String[] {"ownerRepository", "ownerService", "petService", "testIntBean", "visitRepository", "visitService"}, 2, 
				"""
package org.sample.test;

import org.springframework.samples.petclinic.pet.Inner.PetService;
import org.springframework.stereotype.Controller;

@Controller
public class TestBeanCompletionClass {

    private final Inner.PetService petService;

    TestBeanCompletionClass(Inner.PetService petService) {
        this.petService = petService;
    }

		public void test() {
petService<*>
		}
}	    
									""");
	}
	
	@Test
	public void testBeanCompletion_multipleClasses() throws Exception {
		String content = """
				package org.sample.test;
				
				import org.springframework.samples.petclinic.owner.OwnerRepository;
				import org.springframework.stereotype.Controller;
				
				@Controller
				public class TestBeanCompletionClass {
				    private final OwnerRepository ownerRepository;
				
				    TestBeanCompletionClass(OwnerRepository ownerRepository) {
				        this.ownerRepository = ownerRepository;
				    }
						
						public void test() {
						}
				}
				
				@Controller
				public class TestBeanCompletionSecondClass {
						
						public void test() {
						 ownerRe<*>
						}
				}
				""";
		
		assertCompletions(content, new String[] {"ownerRepository", "ownerService"}, 1, 
				"""
package org.sample.test;

import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.OwnerService;
import org.springframework.stereotype.Controller;

@Controller
public class TestBeanCompletionClass {
    private final OwnerRepository ownerRepository;

    TestBeanCompletionClass(OwnerRepository ownerRepository) {
        this.ownerRepository = ownerRepository;
    }

		public void test() {
		}
}	  

@Controller
public class TestBeanCompletionSecondClass {

    private final OwnerService ownerService;

    TestBeanCompletionSecondClass(OwnerService ownerService) {
        this.ownerService = ownerService;
    }

		public void test() {
		 ownerService<*>
		}
}
									""");
	}
	
	@Test
	public void noPrefix_multipleClasses() throws Exception {
		String content = """
				package org.sample.test;
				
				import org.springframework.samples.petclinic.owner.OwnerRepository;
				import org.springframework.stereotype.Controller;
				
				@Controller
				public class TestBeanCompletionClass {
				    private final OwnerRepository ownerRepository;
				
				    TestBeanCompletionClass(OwnerRepository ownerRepository) {
				        this.ownerRepository = ownerRepository;
				    }
						
						public void test() {
						}
				}
				
				@Controller
				public class TestBeanCompletionSecondClass {
						
						public void test() {
						 <*>
						}
				}
				""";
		
		assertCompletions(content, new String[] {"ownerRepository", "ownerService", "petService", "testBeanCompletionClass", "testIntBean", "visitRepository", "visitService"}, 1, 
				"""
package org.sample.test;

import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.OwnerService;
import org.springframework.stereotype.Controller;

@Controller
public class TestBeanCompletionClass {
    private final OwnerRepository ownerRepository;

    TestBeanCompletionClass(OwnerRepository ownerRepository) {
        this.ownerRepository = ownerRepository;
    }

		public void test() {
		}
}	  

@Controller
public class TestBeanCompletionSecondClass {

    private final OwnerService ownerService;

    TestBeanCompletionSecondClass(OwnerService ownerService) {
        this.ownerService = ownerService;
    }

		public void test() {
		 ownerService<*>
		}
}
									""");
	}
	
	@Test
	public void testBeanCompletion_isNotSpringComponent() throws Exception {
		String content = """
				package org.sample.test;
				
				public class TestBeanCompletionClass {
						
						public void test() {
							owner<*>
						}
				}
				""";
		// No suggestions when it is not a spring component
		assertCompletions(content, new String[] {}, 0, "");
	}
	
	@Test
	public void testBeanCompletion_isOutsideMethod() throws Exception {
		String content = """
				package org.sample.test;
				
				import org.springframework.stereotype.Controller;
				
				@Controller
				public class TestBeanCompletionClass {
						owner<*>
				}
				""";
		assertCompletions(content, new String[] {}, 0, "");
	}
	
	@Test
	public void testBeanCompletion_nestedComponent() throws Exception {
		String content = """
package org.sample.test;

import org.springframework.stereotype.Component;

@Component
public class TestBeanCompletionClass {
		@Component
		public class Inner {
		
			public void test() {
				ownerRe<*>
			}
		}
}	
				""";
		
		assertCompletions(content, new String[] {"ownerRepository", "ownerService"}, 0, 
				"""
package org.sample.test;

import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.stereotype.Component;

@Component
public class TestBeanCompletionClass {
		@Component
		public class Inner {

            private final OwnerRepository ownerRepository;

            Inner(OwnerRepository ownerRepository) {
                this.ownerRepository = ownerRepository;
            }

			public void test() {
				ownerRepository<*>
			}
		}
}	  
							""");
	}
	
	private String getCompletion(String completionLine) {
		String content = """
				package org.sample.test;
				
				import org.springframework.stereotype.Controller;
				
				@Controller
				public class TestBeanCompletionClass {
						
						public void test() {
						 """ +
						completionLine + "\n" +
						"""
						}
				}
				""";
		return content;
	}
	
	@Test
	public void beansPresent() throws Exception {
		String content = """
				package org.sample.test;
				
				import org.springframework.stereotype.Controller;
				
				@Controller
				public class TestBeanCompletionSecondClass {
					private final TestBeanCompletionClass testBeanCompletionClass;
				
				    TestBeanCompletionSecondClass(TestBeanCompletionClass testBeanCompletionClass) {
				        this.testBeanCompletionClass = testBeanCompletionClass;
				    }
						
						public void test() {
							ownerRe<*>
						}
				}				
				
				@Controller
				public class TestBeanCompletionClass {
						
						public void test() {
						}
				}
				""";
		
		assertCompletions(content, new String[] {"ownerRepository", "ownerService"}, 0, 
				"""
package org.sample.test;

import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.stereotype.Controller;

@Controller
public class TestBeanCompletionSecondClass {

    private final OwnerRepository ownerRepository;
	private final TestBeanCompletionClass testBeanCompletionClass;

    TestBeanCompletionSecondClass(TestBeanCompletionClass testBeanCompletionClass, OwnerRepository ownerRepository) {
        this.testBeanCompletionClass = testBeanCompletionClass;
        this.ownerRepository = ownerRepository;
    }

		public void test() {
			ownerRepository<*>
		}
}	  

@Controller
public class TestBeanCompletionClass {

		public void test() {
		}
}
									""");
	}
	
	@Test
	public void beanCompletionWithThis() throws Exception {
		String content = """
				package org.sample.test;
				
				import org.springframework.stereotype.Controller;
				
				@Controller
				public class TestBeanCompletionClass {
					public void test() {
						this.ownerRe<*>
					}
				}
				""";
		
		
		assertCompletions(content, new String[] {"ownerRepository", "ownerService"}, 0, 
				"""
				package org.sample.test;
				
				import org.springframework.samples.petclinic.owner.OwnerRepository;
				import org.springframework.stereotype.Controller;
				
				@Controller
				public class TestBeanCompletionClass {
				
				    private final OwnerRepository ownerRepository;
				
				    TestBeanCompletionClass(OwnerRepository ownerRepository) {
				        this.ownerRepository = ownerRepository;
				    }
					public void test() {
						this.ownerRepository<*>
					}
				}
				""");
	}

	@Test
	public void beanCompletionWithConstructorParameterPresent() throws Exception {
		String content = """
				package org.sample.test;
				
				import org.springframework.stereotype.Controller;
				
				@Controller
				public class TestBeanCompletionClass {
				
				    TestBeanCompletionClass(Integer testIntBean) {
				    }
				    
					public void test() {
						this.testI<*>
					}
				}
				""";
		
		
		assertCompletions(content, new String[] {"testIntBean"}, 0, 
				"""
				package org.sample.test;
				
				import org.springframework.stereotype.Controller;
				
				@Controller
				public class TestBeanCompletionClass {
				
				    private final Integer testIntBean;
				
				    TestBeanCompletionClass(Integer testIntBean) {
				        this.testIntBean = testIntBean;
				    }
				    
					public void test() {
						this.testIntBean<*>
					}
				}
				""");
	}

	@Test
	public void noCompletionsInMethod_1() throws Exception {
		String content = """
				package org.sample.test;
				
				import org.springframework.stereotype.Controller;
				
				@Controller
				public class TestBeanCompletionClass {
					public void test(String s) {
						s.owner<*>
					}
				}
				""";
		
		
		assertCompletions(content, new String[0], 0, null);
	}
	
	@Test
	public void noCompletionsInMethod_2() throws Exception {
		String content = """
				package org.sample.test;
				
				import org.springframework.stereotype.Controller;
				
				@Controller
				public class TestBeanCompletionClass {
					public void test(String s) {
						s.<*>
					}
				}
				""";
		
		
		assertCompletions(content, new String[0], 0, null);
	}
	
	@Test
	public void completionsForRestController() throws Exception {
		String content = """
				package org.sample.test;
				
				import org.springframework.web.bind.annotation.RestController;
				
				@RestController
				public class TestBeanCompletionClass {
					public void test() {
						ownerRe<*>
					}
				}
				""";
		
		
		assertCompletions(content, new String[] {"ownerRepository", "ownerService"}, 0, 
				"""
				package org.sample.test;
				
				import org.springframework.samples.petclinic.owner.OwnerRepository;
				import org.springframework.web.bind.annotation.RestController;
				
				@RestController
				public class TestBeanCompletionClass {
				
				    private final OwnerRepository ownerRepository;
				
				    TestBeanCompletionClass(OwnerRepository ownerRepository) {
				        this.ownerRepository = ownerRepository;
				    }
					public void test() {
						ownerRepository<*>
					}
				}
				""");
	}
	
	@Test
	public void beforeStatement() throws Exception {
		String content = """
				package org.sample.test;
				
				import org.springframework.web.bind.annotation.RestController;
				
				@RestController
				public class TestBeanCompletionClass {
					public void test() {
						ownerRe<*>
						System.out.println();
					}
				}
				""";
		
		
		assertCompletions(content, new String[] {"ownerRepository", "ownerService"}, 0, 
				"""
				package org.sample.test;
				
				import org.springframework.samples.petclinic.owner.OwnerRepository;
				import org.springframework.web.bind.annotation.RestController;
				
				@RestController
				public class TestBeanCompletionClass {
				
				    private final OwnerRepository ownerRepository;
				
				    TestBeanCompletionClass(OwnerRepository ownerRepository) {
				        this.ownerRepository = ownerRepository;
				    }
					public void test() {
						ownerRepository<*>
						System.out.println();
					}
				}
				""");
	}
	
	@Test
	public void beforeStatementStartingWithThis() throws Exception {
		String content = """
				package org.sample.test;
				
				import org.springframework.web.bind.annotation.RestController;
				
				@RestController
				public class TestBeanCompletionClass {
					public void test() {
						this.<*>
						System.out.println();
					}
				}
				""";
		
		
		assertCompletions(content, new String[] {"ownerRepository", "ownerService", "petService", "testIntBean", "visitRepository", "visitService"}, 0, 
				"""
				package org.sample.test;
				
				import org.springframework.samples.petclinic.owner.OwnerRepository;
				import org.springframework.web.bind.annotation.RestController;
				
				@RestController
				public class TestBeanCompletionClass {
				
				    private final OwnerRepository ownerRepository;
				
				    TestBeanCompletionClass(OwnerRepository ownerRepository) {
				        this.ownerRepository = ownerRepository;
				    }
					public void test() {
						this.ownerRepository<*>
						System.out.println();
					}
				}
				""");
	}
	
	@Test
	public void sameNameFieldExists_Constructor() throws Exception {
		String content = """
				package org.sample.test;
				
				import org.springframework.web.bind.annotation.RestController;
				
				@RestController
				public class TestBeanCompletionClass {
					String ownerRepository;
					public TestBeanCompletionClass(String ownerRepository) {
						this.ownerRepository = ownerRepository;
						ownerRe<*>
					}
				}
				""";
		
		
		assertCompletions(content, new String[] {"ownerRepository", "ownerService"}, 0, 
				"""
				package org.sample.test;
				
				import org.springframework.samples.petclinic.owner.OwnerRepository;
				import org.springframework.web.bind.annotation.RestController;
				
				@RestController
				public class TestBeanCompletionClass {
				
				    private final OwnerRepository ownerRepository_1;
					String ownerRepository;
					public TestBeanCompletionClass(String ownerRepository, OwnerRepository ownerRepository_1) {
						this.ownerRepository = ownerRepository;
						this.ownerRepository_1 = ownerRepository_1;<*>
					}
				}
				""");
	}
	
	@Test
	public void sameNameFieldExists_Method() throws Exception {
		String content = """
				package org.sample.test;
				
				import org.springframework.web.bind.annotation.RestController;
				
				@RestController
				public class TestBeanCompletionClass {
					String ownerRepository;
					public void test() {
						ownerRe<*>
					}
				}
				""";
		
		
		assertCompletions(content, new String[] {"ownerRepository", "ownerService"}, 0, 
				"""
				package org.sample.test;
				
				import org.springframework.samples.petclinic.owner.OwnerRepository;
				import org.springframework.web.bind.annotation.RestController;
				
				@RestController
				public class TestBeanCompletionClass {
				
				    private final OwnerRepository ownerRepository_1;
					String ownerRepository;
					
				    TestBeanCompletionClass(OwnerRepository ownerRepository_1) {
				        this.ownerRepository_1 = ownerRepository_1;
				    }
					public void test() {
						ownerRepository_1<*>
					}
				}
				""");
	}
	
	@Test
	public void beforeStatementStartingWithThisAndPrefix() throws Exception {
		String content = """
				package org.sample.test;
				
				import org.springframework.web.bind.annotation.RestController;
				
				@RestController
				public class TestBeanCompletionClass {
					public void test() {
						this.ownerRe<*>
						System.out.println();
					}
				}
				""";
		
		
		assertCompletions(content, new String[] {"ownerRepository", "ownerService"}, 0, 
				"""
				package org.sample.test;
				
				import org.springframework.samples.petclinic.owner.OwnerRepository;
				import org.springframework.web.bind.annotation.RestController;
				
				@RestController
				public class TestBeanCompletionClass {
				
				    private final OwnerRepository ownerRepository;
				
				    TestBeanCompletionClass(OwnerRepository ownerRepository) {
				        this.ownerRepository = ownerRepository;
				    }
					public void test() {
						this.ownerRepository<*>
						System.out.println();
					}
				}
				""");
	}

	@Test
	public void constructorLastStatement() throws Exception {
		String content = """
				package org.sample.test;
				
				import org.springframework.web.bind.annotation.RestController;
				
				@RestController
				public class TestBeanCompletionClass {
					public TestBeanCompletionClass() {
						super();
						this.ownerRe<*>
					}
				}
				""";
		
		
		assertCompletions(content, new String[] {"ownerRepository", "ownerService"}, 0, 
				"""
				package org.sample.test;
				
				import org.springframework.samples.petclinic.owner.OwnerRepository;
				import org.springframework.web.bind.annotation.RestController;
				
				@RestController
				public class TestBeanCompletionClass {
				
				    private final OwnerRepository ownerRepository;
					public TestBeanCompletionClass(OwnerRepository ownerRepository) {
						super();
						this.ownerRepository = ownerRepository;<*>
					}
				}
				""");
	}
	
	@Test
	public void constructorAfterStatements() throws Exception {
		String content = """
				package org.sample.test;
				
				import org.springframework.web.bind.annotation.RestController;
				
				@RestController
				public class TestBeanCompletionClass {
					public TestBeanCompletionClass() {
						super();
						this.ownerRe<*>
						System.out.println();
					}
				}
				""";
		
		
		assertCompletions(content, new String[] {"ownerRepository", "ownerService"}, 0, 
				"""
				package org.sample.test;
				
				import org.springframework.samples.petclinic.owner.OwnerRepository;
				import org.springframework.web.bind.annotation.RestController;
				
				@RestController
				public class TestBeanCompletionClass {
				
				    private final OwnerRepository ownerRepository;
					public TestBeanCompletionClass(OwnerRepository ownerRepository) {
						super();
						this.ownerRepository = ownerRepository;<*>
						System.out.println();
					}
				}
				""");
	}
	
	@Test
	public void constructorNamePrefix() throws Exception {
		String content = """
				package org.sample.test;
				
				import org.springframework.web.bind.annotation.RestController;
				
				@RestController
				public class TestBeanCompletionClass {
					public TestBeanCompletionClass() {
						super();
						ownerRe<*>
					}
				}
				""";
		
		
		assertCompletions(content, new String[] {"ownerRepository", "ownerService"}, 0, 
				"""
				package org.sample.test;
				
				import org.springframework.samples.petclinic.owner.OwnerRepository;
				import org.springframework.web.bind.annotation.RestController;
				
				@RestController
				public class TestBeanCompletionClass {
				
				    private final OwnerRepository ownerRepository;
					public TestBeanCompletionClass(OwnerRepository ownerRepository) {
						super();
						this.ownerRepository = ownerRepository;<*>
					}
				}
				""");
	}
	
	@Test
	public void constructorNamePrefixAfterStatements() throws Exception {
		String content = """
				package org.sample.test;
				
				import org.springframework.web.bind.annotation.RestController;
				
				@RestController
				public class TestBeanCompletionClass {
					public TestBeanCompletionClass() {
						super();
						ownerRe<*>
						System.out.println();
					}
				}
				""";
		
		
		assertCompletions(content, new String[] {"ownerRepository", "ownerService"}, 0, 
				"""
				package org.sample.test;
				
				import org.springframework.samples.petclinic.owner.OwnerRepository;
				import org.springframework.web.bind.annotation.RestController;
				
				@RestController
				public class TestBeanCompletionClass {
				
				    private final OwnerRepository ownerRepository;
					public TestBeanCompletionClass(OwnerRepository ownerRepository) {
						super();
						this.ownerRepository = ownerRepository;<*>
						System.out.println();
					}
				}
				""");
	}
	
	@Test
	public void constructorThisStatementWithoutPrefix() throws Exception {
		String content = """
				package org.sample.test;
				
				import org.springframework.web.bind.annotation.RestController;
				
				@RestController
				public class TestBeanCompletionClass {
					public TestBeanCompletionClass() {
						super();
						this.<*>
					}
				}
				""";
		
		
		assertCompletions(content, new String[] {"ownerRepository", "ownerService", "petService", "testIntBean", "visitRepository", "visitService"}, 0, 
				"""
				package org.sample.test;
				
				import org.springframework.samples.petclinic.owner.OwnerRepository;
				import org.springframework.web.bind.annotation.RestController;
				
				@RestController
				public class TestBeanCompletionClass {
				
				    private final OwnerRepository ownerRepository;
					public TestBeanCompletionClass(OwnerRepository ownerRepository) {
						super();
						this.ownerRepository = ownerRepository;<*>
					}
				}
				""");
	}
	
	@Test
	public void constructorThisStatementWithoutPrefixAfterStatements() throws Exception {
		String content = """
				package org.sample.test;
				
				import org.springframework.web.bind.annotation.RestController;
				
				@RestController
				public class TestBeanCompletionClass {
					public TestBeanCompletionClass() {
						super();
						this.<*>
						System.out.println();
					}
				}
				""";
		
		
		assertCompletions(content, new String[] {"ownerRepository", "ownerService", "petService", "testIntBean", "visitRepository", "visitService"}, 0, 
				"""
				package org.sample.test;
				
				import org.springframework.samples.petclinic.owner.OwnerRepository;
				import org.springframework.web.bind.annotation.RestController;
				
				@RestController
				public class TestBeanCompletionClass {
				
				    private final OwnerRepository ownerRepository;
					public TestBeanCompletionClass(OwnerRepository ownerRepository) {
						super();
						this.ownerRepository = ownerRepository;<*>
						System.out.println();
					}
				}
				""");
	}
	
	@Test
	public void methodReturnStatementAfterThisStatementWithoutPrefix() throws Exception {
		String content = """
				package org.sample.test;
				
				import org.springframework.web.bind.annotation.RestController;
				
				@RestController
				public class TestBeanCompletionClass {
					String TestBeanCompletionClass() {
						this.<*>
						return new String("oo");
					}
				}
				""";
		
		
		assertCompletions(content, new String[] {"ownerRepository", "ownerService", "petService", "testIntBean", "visitRepository", "visitService"}, 0, 
				"""
				package org.sample.test;
				
				import org.springframework.samples.petclinic.owner.OwnerRepository;
				import org.springframework.web.bind.annotation.RestController;
				
				@RestController
				public class TestBeanCompletionClass {
				
				    private final OwnerRepository ownerRepository;
					
				    TestBeanCompletionClass(OwnerRepository ownerRepository) {
				        this.ownerRepository = ownerRepository;
				    }
					String TestBeanCompletionClass() {
						this.ownerRepository<*>
						return new String("oo");
					}
				}
				""");
	}

	
	@Test
	public void constructorNoThisNoPrefix() throws Exception {
		String content = """
				package org.sample.test;
				
				import org.springframework.web.bind.annotation.RestController;
				
				@RestController
				public class TestBeanCompletionClass {
					public TestBeanCompletionClass() {
						super();
						<*>
					}
				}
				""";
		
		
		assertCompletions(content, new String[] {"ownerRepository", "ownerService", "petService", "testIntBean", "visitRepository", "visitService"}, 0, 
				"""
				package org.sample.test;
				
				import org.springframework.samples.petclinic.owner.OwnerRepository;
				import org.springframework.web.bind.annotation.RestController;
				
				@RestController
				public class TestBeanCompletionClass {
				
				    private final OwnerRepository ownerRepository;
					public TestBeanCompletionClass(OwnerRepository ownerRepository) {
						super();
						this.ownerRepository = ownerRepository;<*>
					}
				}
				""");
	}
	
	@Test
	public void constructorNoThisNoPrefixAfterStatements() throws Exception {
		String content = """
				package org.sample.test;
				
				import org.springframework.web.bind.annotation.RestController;
				
				@RestController
				public class TestBeanCompletionClass {
					public TestBeanCompletionClass() {
						super();
						<*>
						System.out.println();
					}
				}
				""";
		
		
		assertCompletions(content, new String[] {"ownerRepository", "ownerService", "petService", "testIntBean", "visitRepository", "visitService"}, 0, 
				"""
				package org.sample.test;
				
				import org.springframework.samples.petclinic.owner.OwnerRepository;
				import org.springframework.web.bind.annotation.RestController;
				
				@RestController
				public class TestBeanCompletionClass {
				
				    private final OwnerRepository ownerRepository;
					public TestBeanCompletionClass(OwnerRepository ownerRepository) {
						super();
						this.ownerRepository = ownerRepository;<*>
						System.out.println();
					}
				}
				""");
	}
	
	@Test
	public void constructorNoCompletions_1() throws Exception {
		String content = """
				package org.sample.test;
				
				import org.springframework.web.bind.annotation.RestController;
				
				@RestController
				public class TestBeanCompletionClass {
					public TestBeanCompletionClass(String s) {
						s.<*>
					}
				}
				""";
		
		assertCompletions(content, new String[0], 0, null);
	}
	
	@Test
	public void constructorNoCompletions_2() throws Exception {
		String content = """
				package org.sample.test;
				
				import org.springframework.web.bind.annotation.RestController;
				
				@RestController
				public class TestBeanCompletionClass {
					public TestBeanCompletionClass(String s) {
						s.ow<*>
					}
				}
				""";
		
		assertCompletions(content, new String[0], 0, null);
	}
	
	@Test
	public void constructorNoCompletions_3() throws Exception {
		String content = """
				package org.sample.test;
				
				import org.springframework.stereotype.Component;
				
				@Component
				public class Comp1 {
				
					ow<*>
					
					public Comp1() {
						
					}
				}
				""";
		
		assertCompletions(content, new String[0], 0, null);
	}
	
	@Test
	public void constructorNoCompletions_4() throws Exception {
		String content = """
				package org.sample.test;
				
				import org.springframework.stereotype.Component;
				
				@Component
				public class Comp1 {
				
					ow<*>
					
					Comp1() {
						
					}
				}
				""";
		
		assertCompletions(content, new String[0], 0, null);
	}

	private void assertCompletions(String completionLine, String[] expectedCompletions, int chosenCompletion, String expectedResult) throws Exception {
		Editor editor = harness.newEditor(LanguageId.JAVA, completionLine, tempJavaDocUri);

        List<CompletionItem> completions = editor.getCompletions();
//        assertEquals(noOfExcpectedCompletions, completions.size());
        assertTrue(expectedCompletions.length <= completions.size());

        if (expectedCompletions != null) {
	        String[] completionItems = completions.stream()
	        	.map(item -> item.getLabel())
	        	.limit(expectedCompletions.length == 0 ? Integer.MAX_VALUE : expectedCompletions.length)
	        	.toArray(size -> new String[size]);
	        
	        assertArrayEquals(expectedCompletions, completionItems);
        }
        
        if (expectedCompletions.length > 0) {
	        editor.apply(completions.get(chosenCompletion));
	        assertEquals(expectedResult, editor.getText());
        }
	}

}
