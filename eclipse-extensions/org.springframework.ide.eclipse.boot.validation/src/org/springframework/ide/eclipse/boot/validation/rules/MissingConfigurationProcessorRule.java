/*******************************************************************************
 * Copyright (c) 2015 GoPivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.validation.rules;

import static org.springframework.ide.eclipse.boot.validation.framework.BootMarkerUtils.getProject;
import static org.springframework.ide.eclipse.boot.validation.quickfix.GeneratorComposition.NO_RESOLUTIONS;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jdt.core.IAnnotation;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.ui.IMarkerResolution;
import org.eclipse.ui.IMarkerResolutionGenerator2;
import org.springframework.ide.eclipse.boot.core.BootActivator;
import org.springframework.ide.eclipse.boot.core.ISpringBootProject;
import org.springframework.ide.eclipse.boot.core.MavenCoordinates;
import org.springframework.ide.eclipse.boot.core.SpringBootCore;
import org.springframework.ide.eclipse.boot.util.Log;
import org.springframework.ide.eclipse.boot.validation.framework.BootValidationRule;
import org.springframework.ide.eclipse.boot.validation.framework.CompilationUnitElement;
import org.springframework.ide.eclipse.boot.validation.framework.IModelElement;
import org.springframework.ide.eclipse.boot.validation.framework.IValidationContext;
import org.springframework.ide.eclipse.boot.validation.quickfix.MarkerResolutionRegistry;
import org.springframework.ide.eclipse.editor.support.reconcile.ProblemSeverity;
import org.springsource.ide.eclipse.commons.livexp.util.ExceptionUtil;

/**
 * Validation rule that checks
 *
 *   if: found @ConfigurationProperties annotation
 *   then: spring-boot-configuration-processor jar is on the project's classpath
 *
 * Provides a quickfix to add spring-boot-configuration-processor to dependencies in
 * pom-based project.
 *
 * @author Kris De Volder
 */
public class MissingConfigurationProcessorRule extends BootValidationRule {

	private static final String SPRING_BOOT_CONFIGURATION_PROCESSOR = "spring-boot-configuration-processor";
	private static final MavenCoordinates DEP_CONFIGURATION_PROCESSOR =
			new MavenCoordinates("org.springframework.boot", SPRING_BOOT_CONFIGURATION_PROCESSOR, null);

	public static final BootValidationProblemType PROBLEM_ID = new BootValidationProblemType("MISSING_CONFIGURATION_PROCESSOR", ProblemSeverity.WARNING, 
			"Missing Configuration Processor",
			"When using @ConfigurationProperties, it is recommended to add the Spring Boot Configuration Processor to a project's classpath"
	);
	private static final IMarkerResolutionGenerator2 QUICK_FIX = new IMarkerResolutionGenerator2() {

		@Override
		public IMarkerResolution[] getResolutions(IMarker marker) {
			try {
				final ISpringBootProject project = SpringBootCore.create(getProject(marker));
				if (project != null) {
					return new IMarkerResolution[] {
						new IMarkerResolution() {
							@Override
							public String getLabel() {
								return "Add spring-boot-configuration-processor to pom.xml";
							}
	
							@Override
							public void run(IMarker marker) {
								try {
									project.addMavenDependency(DEP_CONFIGURATION_PROCESSOR, true, true);
									project.updateProjectConfiguration(); //needed to actually enable APT, m2e does not
															// automatically trigger this if a dependency gets added
								} catch (Exception e) {
									Log.log(e);
								}
							}
						}
					};
				}
			} catch (Exception e) {
				Log.log(e);
			}
			return NO_RESOLUTIONS;
		}

		@Override
		public boolean hasResolutions(IMarker marker) {
			try {
				IProject project = getProject(marker);
				if (project.hasNature(SpringBootCore.M2E_NATURE)) {
					return true;
				}
			} catch (Exception e) {
				Log.log(e);
			}
			return false;
		}
	};

	static {
		MarkerResolutionRegistry.DEFAULT_INSTANCE.register(PROBLEM_ID, QUICK_FIX);
	}

	/**
	 * Classpath matcher that checks classpath to determine if this rule applies
	 */
	private static final ClasspathMatcher CLASSPATH_MATCHER = new ClasspathMatcher(false) {
		@Override
		protected boolean doMatch(IClasspathEntry[] classpath) {
			for (IClasspathEntry e : classpath) {
				if (
						isJarNameContaining(e, SPRING_BOOT_CONFIGURATION_PROCESSOR) || 
						isProjectWithName(e, SPRING_BOOT_CONFIGURATION_PROCESSOR) ||
						isSourceFolderInProject(e, SPRING_BOOT_CONFIGURATION_PROCESSOR)
				) {
					//The rule is already satisfied so doesn't need to be checked
					return false;
				}
			}
			return true;
		}

	};
	private static final String BUILDSHIP_NATURE = "org.eclipse.buildship.core.gradleprojectnature";

	public static class ValidationVisitor {

		private IValidationContext context;
		private CompilationUnitElement cu;

		public ValidationVisitor(IValidationContext context, CompilationUnitElement cu) {
			this.context = context;
			this.cu = cu;
		}

		public void visit(ICompilationUnit compilationUnit, IProgressMonitor mon) throws Exception {
			if (compilationUnit.exists()) {
				IType[] types = compilationUnit.getAllTypes();
				mon.beginTask(compilationUnit.getElementName(), types.length);
				try {
					for (IType t : types) {
						visit(t, new SubProgressMonitor(mon, 1));
					}
				} finally {
					mon.done();
				}
			}
		}

		private void visit(IType t, IProgressMonitor mon) throws Exception {
			IMethod[] methods = t.getMethods();
			mon.beginTask(t.getElementName(), 1+methods.length);
			try {
				IAnnotation annot = getAnnotation(t);
				if (annot != null && annot.exists()) {
					visit(annot);
					mon.worked(1);
				}
				for (IMethod m : methods) {
					visit(m, new SubProgressMonitor(mon, 1));
				}
			} finally {
				mon.done();
			}
		}

		private IAnnotation getAnnotation(IType t) {
			try {
				IAnnotation[] all = t.getAnnotations();
				if (all != null) {
					for (IAnnotation a : all) {
						String name = a.getElementName();
						//name could be fully qualified or simple, so check for both
						if ("org.springframework.boot.context.properties.ConfigurationProperties".equals(name)
						|| "ConfigurationProperties".equals(name)
						) {
							return a;
						}
					}
				}
			} catch (JavaModelException e) {
				BootActivator.log(e);
			}
			return null;
		}

		private void visit(IAnnotation annot) throws Exception {
			warn("When using @ConfigurationProperties it is recommended to add 'spring-boot-configuration-processor' "
					+ "to your classpath to generate configuration metadata", annot.getNameRange());
		}

		private void visit(IMethod m, SubProgressMonitor mon) throws Exception {
			mon.beginTask(m.getElementName(), 1);
			try {
				IAnnotation annot = m.getAnnotation("ConfigurationProperties");
				if (annot != null && annot.exists()) {
					visit(annot);
					mon.worked(1);
				}
			} finally {
				mon.done();
			}
		}

		void warn(String msg, ISourceRange location) {
			if (location != null) {
				context.problem(cu.getElementResource(), PROBLEM_ID, msg, location.getOffset(), location.getOffset()+location.getLength());

//				context.addProblems(new ValidationProblem(PROBLEM_ID, IMarker.SEVERITY_WARNING,
//						msg, cu.getElementResource(), location));
			}
		}

	}

	@Override
	public boolean supports(IModelElement element) {
		return element instanceof CompilationUnitElement;
	}

	@Override
	public void validate(IModelElement _cu, IValidationContext context, IProgressMonitor mon) {
		CompilationUnitElement cu = (CompilationUnitElement) _cu;
		try{
			if (cu.getCompilationUnit().getJavaProject().getProject().hasNature(BUILDSHIP_NATURE)) {
				//Skip validation. We can consider re-enabling this when buildship adds support for configuring 
				//JDT APT (https://github.com/eclipse/buildship/issues/329). Then it will make sense to look for 
				//annotation processor on the eclipse processor path.
				//Until then annotation processing doesn't really work in Gradle + Buildship anyway and 
				//validation of this rule is hard (need to ask gradle tooling api which we don't have here)... 
				//and counter-productive.
				//See also: 
				// - https://github.com/spring-projects/spring-ide/issues/266
				// - https://www.pivotaltracker.com/story/show/156543983
				return;
			}
			if (CLASSPATH_MATCHER.match(cu.getClasspath())) {
				ValidationVisitor visitor = new ValidationVisitor(context, cu);
				visitor.visit(cu.getCompilationUnit(), mon);
			}
		} catch (Exception e) {
			if (ExceptionUtil.getMessage(e).contains("File not found")) {
				//Somewhat expected see [aer] https://www.pivotaltracker.com/story/show/133998741
				Log.warn(e);
			} else {
				Log.log(e);
			}
		}
	}

	@Override
	public String getId() {
		return PROBLEM_ID.getId();
	}

}
