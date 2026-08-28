/*******************************************************************************
 * Copyright (c) 2015, 2016 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.editor.support.yaml.path;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.springframework.ide.eclipse.editor.support.yaml.ast.NodeRef;
import org.springframework.ide.eclipse.editor.support.yaml.ast.NodeRef.RootRef;
import org.springframework.ide.eclipse.editor.support.yaml.ast.NodeRef.SeqRef;
import org.springframework.ide.eclipse.editor.support.yaml.ast.NodeRef.TupleValueRef;
import org.springframework.ide.eclipse.editor.support.yaml.ast.NodeUtil;
import org.springframework.ide.eclipse.editor.support.yaml.path.YamlPathSegment.YamlPathSegmentType;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;

/**
 * @author Kris De Volder
 */
public class YamlPath extends AbstractYamlTraversal {

	public static final YamlPath EMPTY = new YamlPath();
	private final YamlPathSegment[] segments;

	public YamlPath(List<YamlPathSegment> segments) {
		this.segments = segments.toArray(new YamlPathSegment[segments.size()]);
	}

	public YamlPath() {
		this.segments = new YamlPathSegment[0];
	}

	public YamlPath(YamlPathSegment... segments) {
		this.segments = segments;
	}

	public String toPropString() {
		StringBuilder buf = new StringBuilder();
		boolean first = true;
		for (YamlPathSegment s : segments) {
			if (first) {
				buf.append(s.toPropString());
			} else {
				buf.append(s.toNavString());
			}
			first = false;
		}
		return buf.toString();
	}

	public String toNavString() {
		StringBuilder buf = new StringBuilder();
		for (YamlPathSegment s : segments) {
			buf.append(s.toNavString());
		}
		return buf.toString();
	}

	public YamlPathSegment[] getSegments() {
		return segments;
	}

	/**
	 * Parse a YamlPath from a dotted property name. The segments are obtained
	 * by spliting the name at each dot.
	 */
	public static YamlPath fromProperty(String propName) {
		ImmutableList.Builder<YamlPathSegment> segments = ImmutableList.builder();
		String delim = ".[]";
		StringTokenizer tokens = new StringTokenizer(propName, delim, true);
		try {
			while (tokens.hasMoreTokens()) {
				String token = tokens.nextToken(delim);
				if (token.equals(".") || token.equals("]")) {
					//Skip it silently
				} else if (token.equals("[")) {
					String bracketed = tokens.nextToken("]");
					if (bracketed.equals("]")) {
						//empty string between []? Makes no sense, so ignore that.
					} else {
						try {
							int index = Integer.parseInt(bracketed);
							segments.add(YamlPathSegment.valueAt(index));
						} catch (NumberFormatException e) {
							segments.add(YamlPathSegment.valueAt(bracketed));
						}
					}
				} else {
					segments.add(YamlPathSegment.valueAt(token));
				}
			}
		} catch (NoSuchElementException e) {
			//Ran out of tokens.
		}
		return new YamlPath(segments.build());
	}

	/**
	 * Create a YamlPath with a single segment (i.e. like 'fromProperty', but does
	 * not parse '.' as segment separators.
	 */
	public static YamlPath fromSimpleProperty(String name) {
		return new YamlPath(YamlPathSegment.valueAt(name));
	}

	@Override
	public String toString() {
		StringBuilder buf = new StringBuilder();
		buf.append("YamlPath(");
		boolean first = true;
		for (YamlPathSegment s : segments) {
			if (!first) {
				buf.append(", ");
			}
			buf.append(s);
			first = false;
		}
		buf.append(")");
		return buf.toString();
	}

	public int size() {
		return segments.length;
	}

	public YamlPathSegment getSegment(int segment) {
		if (segment >= 0 && segment < segments.length) {
			return segments[segment];
		}
		return null;
	}

	public YamlPath prepend(YamlPathSegment s) {
		YamlPathSegment[] newPath = new YamlPathSegment[segments.length+1];
		newPath[0] = s;
		System.arraycopy(segments, 0, newPath, 1, segments.length);
		return new YamlPath(newPath);
	}

	public YamlPath append(YamlPathSegment s) {
		YamlPathSegment[] newPath = Arrays.copyOf(segments, segments.length+1);
		newPath[segments.length] = s;
		return new YamlPath(newPath);
	}

	@Override
	public <T extends YamlNavigable<T>> Stream<T> traverseAmbiguously(T startNode) {
		if (startNode != null) {
			Stream<T> result = Stream.of(startNode);
			for (YamlPathSegment s : segments) {
				result = result.flatMap((node) -> {
					return node.traverseAmbiguously(s);
				});
			}
			return result;
		}
		return Stream.empty();
	}

	public YamlPath dropFirst(int dropCount) {
		if (dropCount >= size()) {
			return EMPTY;
		}
		if (dropCount == 0) {
			return this;
		}
		YamlPathSegment[] newPath = new YamlPathSegment[segments.length-dropCount];
		for (int i = 0; i < newPath.length; i++) {
			newPath[i] = segments[i+dropCount];
		}
		return new YamlPath(newPath);
	}

	public YamlPath dropLast() {
		return dropLast(1);
	}

	public YamlPath dropLast(int dropCount) {
		if (dropCount >= size()) {
			return EMPTY;
		}
		if (dropCount == 0) {
			return this;
		}
		YamlPathSegment[] newPath = new YamlPathSegment[segments.length-dropCount];
		for (int i = 0; i < newPath.length; i++) {
			newPath[i] = segments[i];
		}
		return new YamlPath(newPath);
	}


	@Override
	public boolean isEmpty() {
		return segments.length == 0;
	}

	public YamlPath tail() {
		return dropFirst(1);
	}

	/**
	 * Attempt to convert a path represented as a list of {@link NodeRef} into YamlPath.
	 * <p>
	 * Note that not all AST path can be converted into a YamlPath. Some paths in AST
	 * do not have a corresponding YamlPath. For such cases this method may return null.
	 */
	public static YamlPath fromASTPath(List<NodeRef<?>> path) {
		List<YamlPathSegment> segments = new ArrayList<>(path.size());
		for (NodeRef<?> nodeRef : path) {
			switch (nodeRef.getKind()) {
			case ROOT:
				RootRef rref = (RootRef) nodeRef;
				segments.add(YamlPathSegment.valueAt(rref.getIndex()));
				break;
			case KEY: {
				String key = NodeUtil.asScalar(nodeRef.get());
				if (key == null) {
					return null;
				} else {
					segments.add(YamlPathSegment.keyAt(key));
				} }
				break;
			case VAL: {
				TupleValueRef vref = (TupleValueRef) nodeRef;
				String key = NodeUtil.asScalar(vref.getTuple().getKeyNode());
				if (key == null) {
					return null;
				} else {
					segments.add(YamlPathSegment.valueAt(key));
				} }
				break;
			case SEQ:
				SeqRef sref = ((SeqRef)nodeRef);
				segments.add(YamlPathSegment.valueAt(sref.getIndex()));
				break;
			default:
				return null;
			}
		}
		return new YamlPath(segments);
	}

	public YamlPathSegment getLastSegment() {
		if (!isEmpty()) {
			return segments[segments.length-1];
		}
		return null;
	}

	/**
	 * Attempt to interpret last segment of path as a bean property name.
	 * @return The name of the property or null if not applicable.
	 */
	public String getBeanPropertyName() {
		if (!isEmpty()) {
			YamlPathSegment lastSegment = getLastSegment();
			YamlPathSegmentType kind = lastSegment.getType();
			if (kind == YamlPathSegmentType.KEY_AT_KEY ||  kind == YamlPathSegmentType.VAL_AT_KEY) {
				return lastSegment.toPropString();
			}
		}
		return null;
	}

	public boolean pointsAtKey() {
		YamlPathSegment s = getLastSegment();
		return s != null && s.getType() == YamlPathSegmentType.KEY_AT_KEY;
	}

	public boolean pointsAtValue() {
		YamlPathSegment s = getLastSegment();
		if (s != null) {
			YamlPathSegmentType type = s.getType();
			return type == YamlPathSegmentType.VAL_AT_KEY || type == YamlPathSegmentType.VAL_AT_INDEX;
		}
		return false;
	}

	public YamlPath commonPrefix(YamlPath other) {
		ArrayList<YamlPathSegment> common = new ArrayList<>(this.size());
		for (int i = 0; i < this.size(); i++) {
			YamlPathSegment s = this.getSegment(i);
			if (s.equals(other.getSegment(i))) {
				common.add(s);
			}
		}
		return new YamlPath(common);
	}

	public static YamlPath decode(List<String> encodedSegments) {
		return Flux.fromIterable(encodedSegments)
				.map(YamlPathSegment::decode)
				.collectList()
				.map(YamlPath::new)
				.block();
	}

	@Override
	public YamlTraversal then(YamlTraversal _other) {
		if (isEmpty()) {
			return _other;
		} else if (_other.isEmpty()) {
			return this;
		} else if (_other instanceof YamlPathSegment) {
			return this.append((YamlPathSegment) _other);
		} else if (_other instanceof YamlPath) {
			YamlPath other = (YamlPath) _other;
			return new YamlPath(
				Stream.concat(
					Arrays.stream(this.segments),
					Arrays.stream(other.segments)
				).toArray(sz -> new YamlPathSegment[sz])
			);
		} else {
			return new SequencingYamlTraversal(this, _other);
		}
	}

	@Override
	public boolean canEmpty() {
		//The empty path is the only one that 'canEmpty' since any step in path moves the cursor.
		return isEmpty();
	}

}
