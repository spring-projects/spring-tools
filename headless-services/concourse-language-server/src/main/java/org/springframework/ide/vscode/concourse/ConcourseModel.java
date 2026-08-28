/*******************************************************************************
 * Copyright (c) 2016, 2018 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.concourse;

import static org.springframework.ide.vscode.commons.yaml.path.YamlPathSegment.anyChild;
import static org.springframework.ide.vscode.commons.yaml.path.YamlPathSegment.keyAt;
import static org.springframework.ide.vscode.commons.yaml.path.YamlPathSegment.valueAt;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.commons.languageserver.reconcile.IProblemCollector;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.languageserver.util.SnippetBuilder;
import org.springframework.ide.vscode.commons.util.Assert;
import org.springframework.ide.vscode.commons.util.CollectorUtil;
import org.springframework.ide.vscode.commons.util.SimpleGlob;
import org.springframework.ide.vscode.commons.util.SimpleGlob.Match;
import org.springframework.ide.vscode.commons.util.Streams;
import org.springframework.ide.vscode.commons.util.StringUtil;
import org.springframework.ide.vscode.commons.util.text.IDocument;
import org.springframework.ide.vscode.commons.yaml.ast.NodeUtil;
import org.springframework.ide.vscode.commons.yaml.ast.YamlAstCache;
import org.springframework.ide.vscode.commons.yaml.ast.YamlFileAST;
import org.springframework.ide.vscode.commons.yaml.path.ASTRootCursor;
import org.springframework.ide.vscode.commons.yaml.path.NodeCursor;
import org.springframework.ide.vscode.commons.yaml.path.YamlPath;
import org.springframework.ide.vscode.commons.yaml.path.YamlPathSegment;
import org.springframework.ide.vscode.commons.yaml.path.YamlTraversal;
import org.springframework.ide.vscode.commons.yaml.reconcile.ASTTypeCache;
import org.springframework.ide.vscode.commons.yaml.reconcile.ASTTypeCache.NodeTypes;
import org.springframework.ide.vscode.commons.yaml.reconcile.YamlSchemaProblems;
import org.springframework.ide.vscode.commons.yaml.schema.BasicYValueHint;
import org.springframework.ide.vscode.commons.yaml.schema.DynamicSchemaContext;
import org.springframework.ide.vscode.commons.yaml.schema.YType;
import org.springframework.ide.vscode.commons.yaml.schema.YTypeFactory;
import org.springframework.ide.vscode.commons.yaml.schema.YTypeFactory.AbstractType;
import org.springframework.ide.vscode.commons.yaml.schema.YTypeFactory.YBeanUnionType;
import org.springframework.ide.vscode.commons.yaml.schema.YTypedProperty;
import org.springframework.ide.vscode.commons.yaml.schema.YValueHint;
import org.springframework.ide.vscode.commons.yaml.schema.constraints.Constraint;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.ImmutableMultiset.Builder;
import com.google.common.collect.Multiset;

/**
 * ConcourseModels is responsible for extracting various bits of information
 * out of .yml documents and caching them for use by various tools (reconcile engine
 * and completion engine).
 */
public class ConcourseModel {
	
	private static final Logger log = LoggerFactory.getLogger(ConcourseModel.class);

	/**
	 * Verification of a 'isUsed' contraint. Basically this consults the ast-type cache, (which should be
	 * fully populated at the end reconciling) to see if the nodes of any nodes of a given type (representing
	 * a 'use' of something, contain the value of the current node (which is supposed to be a definition of
	 * that same type of something).
	 */
	public Constraint isUsed(YType refType, String entityTypeName) {
		astTypeCache.addInterestingType(refType); //ensure the type is tracked in the type-cache
		return new Constraint() {
			@Override
			public void verify(DynamicSchemaContext dc, Node parent, Node node, YType type, IProblemCollector problems) {
				String defName = NodeUtil.asScalar(node);
				if (StringUtil.hasText(defName)) { //Avoid silly 'not used' errors for empty names (will have an other error already).
					NodeTypes nodeTypes = astTypeCache.getNodeTypes(dc.getDocument().getUri());
					if (nodeTypes != null) {
						Optional<Node> reference = nodeTypes.getNodes(refType).stream()
							.filter(refNode -> defName.equals(NodeUtil.asScalar(refNode)))
							.findAny();
						if (!reference.isPresent()) {
							problems.accept(YamlSchemaProblems.problem(PipelineYmlSchemaProblems.UNUSED_RESOURCE, "Unused '"+entityTypeName+"'", node));
						}
					}
				}
			}
		};
	}

	/**
	 * Verification of contraint: a job used in the 'passed' attribute of a step
	 * must interact with the resource in question.
	 */
	public final void passedJobHasInteractionWithResource(DynamicSchemaContext dc, Node parent, Node node, YType type, IProblemCollector problems) {
		YamlPath path = dc.getPath();
		// Expecting a path like this: YamlPath([0], .jobs, [1], .plan, [0], .passed, [0])
		path = path.dropLast();
		if (YamlPathSegment.valueAt("passed").equals(path.getLastSegment())) {
			String jobName = NodeUtil.asScalar(node);
			JobModel job = getJob(dc.getDocument(), jobName);
			if (job != null) {
				//Only check if the job exists. Otherwise the extra checks will show 'redundant' errors (e.g.
				//  complaining that 'some-job' doesn't ineract with a resource (because the resource doesn't exist).
				YamlFileAST root = asts.getSafeAst(dc.getDocument());
				if (root != null) {
					Node stepNode = path.dropLast().traverseToNode(root);
					if (stepNode != null) {
						StepModel step = newStep(stepNode);
						String resourceName = step.getResourceName();
						if (resourceName != null && getResource(dc.getDocument(), resourceName) != null) {
							Set<String> interactions = job.getInteractedResources();
							if (interactions != null && !interactions.contains(resourceName)) {
								problems.accept(YamlSchemaProblems.schemaProblem("Job '"+jobName+"' does not interact with resource '"+resourceName+"'", node));
							}
						}
					}
				}
			}
		}
	}



	/**
	 * Verification of constraint: if at least one job is assigned to a group, then all jobs must be assigned to a group.
	 */
	public final void jobAssignmentIsComplete(DynamicSchemaContext dc, Node parent, Node node, YType type, IProblemCollector problems) {
		Multiset<String> assignedJobs = getStringsFromAst(dc.getDocument(), JOBS_ASSIGNED_TO_GROUPS);
		if (assignedJobs != null && !assignedJobs.isEmpty()) {
			Map<String, SimpleGlob> assignedJobMatchers = new HashMap<>();
			for (String jobPattern : assignedJobs) {
				if (!assignedJobMatchers.containsKey(jobPattern)) {
					assignedJobMatchers.put(jobPattern, SimpleGlob.create(jobPattern));
				}
			}
			
			getJobNameNodes(dc).forEach(jobDefName -> {
				String name = NodeUtil.asScalar(jobDefName);
				if (StringUtil.hasText(name)) { //'not assigned to a group' errors for empty names are a bit silly, so avoid that
					if (!assignedJobMatchers.values().stream().anyMatch(jobPat -> jobPat.match(name) != Match.FAIL)) {
						problems.accept(YamlSchemaProblems.schemaProblem("'"+name+"' belongs to no group", jobDefName));
					}
				}
			});
		}
	}

	/**
	 * Get the job with given name. If there is no such job, or if there is more than one, this will return null.
	 */
	private JobModel getJob(IDocument doc, String jobName) {
		return Streams.getSingle(
			 getFromAst(doc, ast ->
				JOBS_PATH.traverseAmbiguously(ast)
				.filter(node -> jobName.equals(NodeUtil.getScalarProperty(node, "name")))
				.map(JobModel::new)
			)
		);
	}

	public StepModel newStep(Node _node) {
		MappingNode node = (MappingNode) _node;
		Set<String> keys = NodeUtil.getScalarKeys(node);
		for (Entry<String, AbstractType> primary : stepType.typesByPrimary().entrySet()) {
			String stepType = primary.getKey();
			if (keys.contains(primary.getKey())) {
				return new StepModel(stepType, node);
			}
		}
		throw new IllegalArgumentException("Node does not look like step node: "+node);
	}

	private static final YamlTraversal JobModel_GET_PUT_STEP_PATH = new YamlPath()
			.then(
					valueAt("plan")
					.or(valueAt("on_success"))
					.or(valueAt("on_failure"))
					.or(valueAt("ensure"))
			)
			.then(anyChild().repeat())
			.has(keyAt("get").or(keyAt("put")));

	/**
	 * Wraps around a Node in the AST that represents a 'job' and
	 * provides methods for accessing information from the node.
	 */
	public class JobModel {

		private Node node;

		JobModel(Node node) {
			this.node = node;
		}

		public Set<String> getInteractedResources() {
			return JobModel_GET_PUT_STEP_PATH
					.traverseAmbiguously(node)
					.map(node -> newStep(node))
					.flatMap(step -> Streams.fromNullable(step.getResourceName()))
					.collect(Collectors.toSet());
		}

	}

	/**
	 * Wraps around a Node in the AST that represents a 'step' and
	 * provides methods for accessing information from the node.
	 */
	public static class StepModel {

		private final String stepType;
		private final MappingNode step;

		public StepModel(String stepType, MappingNode step) {
			this.stepType = stepType;
			this.step = step;
		}

		public Node getResourceNameNode() {
			Assert.isLegal("put".equals(stepType) || "get".equals(stepType));
			Node node = NodeUtil.getProperty(step, "resource");
			return node != null ? node : NodeUtil.getProperty(step, stepType);
		}

		public String getResourceName() {
			return NodeUtil.asScalar(getResourceNameNode());
		}
	}

	public static class ResourceModel {

		private final Node resource;

		public ResourceModel(Node resource) {
			this.resource = resource;
		}

		public String getType() {
			return NodeUtil.getScalarProperty(resource, "type");
		}

		public boolean hasSourceProperty(String propName) {
			YamlPath path = new YamlPath(YamlPathSegment.valueAt("source"), YamlPathSegment.keyAt(propName));
			return path.traverseAmbiguously(resource).findFirst().isPresent();
		}

	}

	public static final YamlPath JOBS_PATH = new YamlPath(
			anyChild(),
			valueAt("jobs"),
			anyChild()
	);

	public static final YamlPath JOBS_ASSIGNED_TO_GROUPS = new YamlPath(
			anyChild(),
			valueAt("groups"),
			anyChild(),
			valueAt("jobs"),
			anyChild()
	);

	public static final YamlPath JOB_NAMES_PATH = new YamlPath(
		anyChild(),
		valueAt("jobs"),
		anyChild(),
		valueAt("name")
	);

	public static final YamlPath RESOURCE_TYPE_NAMES_PATH = new YamlPath(
		anyChild(),
		valueAt("resource_types"),
		anyChild(),
		valueAt("name")
	);

	public static final YamlPath RESOURCES_PATH = new YamlPath(
		anyChild(), // skip over the root node which contains multiple doces
		valueAt("resources"),
		anyChild()
	);

	public static final YamlPath RESOURCE_NAMES_PATH = RESOURCES_PATH.append(
		valueAt("name")
	);

	private final ASTTypeCache astTypeCache;

	private final YamlAstCache asts = new YamlAstCache();

	private ResourceTypeRegistry resourceTypes;

	private final Supplier<SnippetBuilder> snippetBuilderFactory;

	private YBeanUnionType stepType;

	public ConcourseModel(SimpleLanguageServer languageServer, ASTTypeCache astTypeCache) {
		this.astTypeCache = astTypeCache;
		this.snippetBuilderFactory = languageServer::createSnippetBuilder;
	}

	/**
	 * Returns the resource names that are defined by given IDocument. If the contents
	 * of IDocument is not currently parseable then this may return stale information
	 * retained from a previous successful parse.
	 * <p>
	 * It may also return null if its not currently possible to obtain the list of resource
	 * names (e.g. because there hasn't been a successful parse yet and current document contents
	 * can not be parsed).
	 */
	public Multiset<String> getResourceNames(DynamicSchemaContext dc) {
		return getStringsFromAst(dc.getDocument(), RESOURCE_NAMES_PATH);
	}

	/**
	 * Get the resource type tag associated with a given resourceName in the given document.
	 * <p>
	 * If the content of IDocument is not currently parseable then this may return stale information
	 * retained from a previous successful parse.
	 * <p>
	 * It may also return null if its not currently possible to obtain type of the resource. E.g
	 * because there is no such resource, the resource has no valid type tag, or the document
	 * was never successfully parsed.
	 */
	public String getResourceType(IDocument doc, String resourceName) {
		ResourceModel resource = getResource(doc, resourceName);
		if (resource != null) {
			return resource.getType();
		}
		return null;
	}

	public ResourceModel getResource(IDocument doc, String resourceName) {
		return getFromAst(doc, (ast) -> {
			Node resource = RESOURCES_PATH.traverseAmbiguously(new ASTRootCursor(ast))
			.map((cursor) -> ((NodeCursor)cursor).getNode())
			.filter((resourceNode) -> resourceName.equals(NodeUtil.getScalarProperty(resourceNode, "name")))
			.findFirst().orElse(null);
			if (resource != null) {
				return new ResourceModel(resource);
			}
			return null;
		});

	}

	/**
	 * Returns the job names that are defined by given IDocument. If the contents
	 * of IDocument is not currently parseable then this may return stale information
	 * retained from a previous successful parse.
	 * <p>
	 * It may also return null if its not currently possible to obtain the list of resource
	 * names (e.g. because there hasn't been a successful parse yet and current document contents
	 * can not be parsed).
	 */
	public Multiset<String> getJobNames(DynamicSchemaContext dc) {
		return getStringsFromAst(dc.getDocument(), JOB_NAMES_PATH);
	}

	public Stream<Node> getJobNameNodes(DynamicSchemaContext dc) {
		return getFromAst(dc.getDocument(), ast -> JOB_NAMES_PATH.traverseAmbiguously(ast));
	}


	private Multiset<String> getStringsFromAst(IDocument doc, YamlPath path) {
		return getFromAst(doc, (ast) -> {
			return path
				.traverseAmbiguously(ast)
				.map(NodeUtil::asScalar)
				.filter((string) -> string != null)
				.collect(CollectorUtil.toMultiset());
		});
	}

	public Multiset<String> getResourceTypeNames(DynamicSchemaContext dc, boolean includeBuiltin) {
		Collection<YValueHint> hints = getResourceTypeNameHints(dc, includeBuiltin);
		if (hints != null) {
			return ImmutableMultiset.copyOf(YTypeFactory.values(hints));
		}
		return null;
	}

	public Collection<YValueHint> getResourceTypeNameHints(DynamicSchemaContext dc, boolean includeBuiltin) {
		IDocument doc = dc.getDocument();
		Multiset<String> userDefined = getStringsFromAst(doc, RESOURCE_TYPE_NAMES_PATH);
		if (userDefined != null) {
			Builder<YValueHint> builder = ImmutableMultiset.builder();
			builder.addAll(YTypeFactory.hints(userDefined));
			if (includeBuiltin) {
				builder.addAll(
						Arrays.stream(PipelineYmlSchema.BUILT_IN_RESOURCE_TYPES)
						.map(h -> addExtraInsertion(h, dc))
						.collect(Collectors.toList())
				);
			}
			return builder.build();
		}
		return null;
	}

	public Node getParentPropertyNode(String propName, DynamicSchemaContext dc) {
		YamlPath path = dc.getPath();
		if (path != null) {
			YamlFileAST root = asts.getSafeAst(dc.getDocument());
			if (root != null) {
				return path.dropLast().append(YamlPathSegment.valueAt(propName)).traverseToNode(root);
			}
		}
		return null;
	}

	private YValueHint addExtraInsertion(YValueHint h, DynamicSchemaContext dc) {
		return new BasicYValueHint(h.getValue(), h.getLabel()).setExtraInsertion(() -> {
			String resourceTypeName = h.getValue();
			AbstractType sourceType = (AbstractType) resourceTypes.getSourceType(resourceTypeName);
			if (sourceType != null && getParentPropertyNode("source", dc) == null) { //don't auto insert what's already there!
				List<YTypedProperty> requiredProps = sourceType.getProperties().stream().filter(p -> p.isRequired()).collect(Collectors.toList());
				if (!requiredProps.isEmpty()) {
					SnippetBuilder snippet = snippetBuilderFactory.get();
					snippet.text("\nsource:");
					for (YTypedProperty p : requiredProps) {
						snippet.text("\n  "+p.getName()+": ");
						snippet.placeHolder();
					}
					return snippet.build();
				}
			}
			return null;
		});
	}


	private <T> T getFromAst(IDocument doc, Function<YamlFileAST, T> astFunction) {
		try {
			if (doc != null) {
				String uri = doc.getUri();
				if (uri != null) {
					YamlFileAST ast = asts.getAst(doc, true);
					return astFunction.apply(ast);
				}
			}
		} catch (YAMLException e) {
			// ignore: found garbage in the doc. Can't compute stuff and that's to be expected.
		} catch (Exception e) {
			log.error("", e);
		}
		return null;
	}


	public void setResourceTypeRegistry(ResourceTypeRegistry resourceTypes) {
		this.resourceTypes = resourceTypes;
	}

	public void setStepType(YBeanUnionType step) {
		Assert.isNull("stepType already set", this.stepType);
		this.stepType = step;
	}

	public YamlAstCache getAstCache() {
		return this.asts;
	}

}
