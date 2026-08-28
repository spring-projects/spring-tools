/*******************************************************************************
 * Copyright (c) 2016-2017 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/

package org.springframework.ide.vscode.commons.javadoc.internal;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.springframework.ide.vscode.commons.java.IField;
import org.springframework.ide.vscode.commons.java.IMethod;
import org.springframework.ide.vscode.commons.java.IType;

public class JavadocContents {
	private static final int[] UNKNOWN_FORMAT = new int[0]; 
	
//	private IType type;
	private char[] content;
	
	private int childrenStart;
	
	private boolean hasComputedChildrenSections = false;
	private int indexOfFieldDetails;
	private int indexOfEnumConstantsDetails;
	private int indexOfConstructorDetails;
	private int indexOfMethodDetails;
	private int indexOfEndOfClassData;
	
	private int indexOfFieldsBottom;
	private int indexOfEnumConstantsBottom;
	private int indexOfAllMethodsTop;
	private int indexOfAllMethodsBottom;
	
	private int[] typeDocRange;
	private HashtableOfObjectToIntArray fieldDocRanges;
	private HashtableOfObjectToIntArray methodDocRanges;
	
	private int[] fieldAnchorIndexes;
	private int fieldAnchorIndexesCount;
	private int fieldLastAnchorFoundIndex;
	private int[] methodAnchorIndexes;
	private int methodAnchorIndexesCount;
	private int methodLastAnchorFoundIndex;
	private int[] unknownFormatAnchorIndexes;
	private int unknownFormatAnchorIndexesCount;
	private int unknownFormatLastAnchorFoundIndex;
	private int[] tempAnchorIndexes;
	private int tempAnchorIndexesCount;
	private int tempLastAnchorFoundIndex;
	
//	public JavadocContents(IType type, String content) {
//		this(content);
//		this.type = type;
//	}
	
	public JavadocContents(String content) {
		this.content = content != null ? content.toCharArray() : null;
	}
	/*
	 * Returns the part of the javadoc that describe the type
	 */
	public String getTypeDoc(IType type) throws Exception {
		if (this.content == null) return null;
		
		synchronized (this) {
			if (this.typeDocRange == null) {
				computeTypeRange(type);
			}
		}
		
		if (this.typeDocRange != null) {
			if (this.typeDocRange == UNKNOWN_FORMAT) throw new Exception("Unknown javadoc format " + type);
			return String.valueOf(CharOperation.subarray(this.content, this.typeDocRange[0], this.typeDocRange[1]));
		}
		return null;
	}
	
//	public String getPackageDoc() throws JavaModelException {
//		if (this.content == null) return null;
//		int[] range = null;
//		int index = CharOperation.indexOf(JavadocConstants.PACKAGE_DESCRIPTION_START2, this.content, false, 0);
//		if (index == -1) {
//			index = CharOperation.indexOf(JavadocConstants.PACKAGE_DESCRIPTION_START, this.content, false, 0);
//		}
//		if (index == -1) return null;
//		index = CharOperation.indexOf(JavadocConstants.ANCHOR_SUFFIX, this.content, false, index);
//		if (index == -1) return null;
//		
//		int start = CharOperation.indexOf(JavadocConstants.H2_PREFIX, this.content, false, index);
//		if (start != -1) {
//			start = CharOperation.indexOf(JavadocConstants.H2_SUFFIX, this.content, false, start);
//			if (start != -1) index = start + JavadocConstants.H2_SUFFIX_LENGTH;
//		}
//		if (index != -1) {
//			int end = CharOperation.indexOf(JavadocConstants.BOTTOM_NAVBAR, this.content, false, index);
//			if (end == -1) end = this.content.length -1;
//			range = new int[]{index, end};
//			return String.valueOf(CharOperation.subarray(this.content, range[0], range[1]));
//		}
//		return null;
//	}
	
	/*
	 * Returns the part of the javadoc that describe a field of the type
	 */
	public String getFieldDoc(IField child) throws Exception {
		if (this.content == null) return null;
		
		int[] range = null;
		synchronized (this) {
			if (this.fieldDocRanges == null) {
				this.fieldDocRanges = new HashtableOfObjectToIntArray();
			} else {
				range = this.fieldDocRanges.get(child);
			}
			
			if (range == null) {
				range = computeFieldRange(child);
				this.fieldDocRanges.put(child, range);
			}
		}
		
		if (range != null) {
			if (range == UNKNOWN_FORMAT) throw new Exception("Unknown javadoc format " + child);
			return String.valueOf(CharOperation.subarray(this.content, range[0], range[1]));
		}
		return null;
	}
	
	/*
	 * Returns the part of the javadoc that describe a method of the type
	 */
	public String getMethodDoc(IMethod method) throws Exception {
		if (this.content == null) return null;
		
		int[] range = null;
		synchronized (this) {
			if (this.methodDocRanges == null) {
				this.methodDocRanges = new HashtableOfObjectToIntArray();
			} else {
				range = this.methodDocRanges.get(method);
			}
			
			if (range == null) {
				range = computeMethodRange(method);
				this.methodDocRanges.put(method, range);
			}
		}
		
		if (range != null) {
			if (range == UNKNOWN_FORMAT) {
				throw new Exception("Unknown javadoc format " + method);
			}
			return String.valueOf(CharOperation.subarray(this.content, range[0], range[1]));
		}
		return null;
	}
	
	/*
	 * Compute the ranges of the parts of the javadoc that describe each method of the type
	 */
	private int[] computeChildRange(char[] anchor, int indexOfSectionBottom) throws Exception {

		// checks each known anchor locations
		if (this.tempAnchorIndexesCount > 0) {
			for (int i = 0; i < this.tempAnchorIndexesCount; i++) {
				int anchorEndStart = this.tempAnchorIndexes[i];

				if (anchorEndStart != -1 && CharOperation.prefixEquals(anchor, this.content, false, anchorEndStart)) {

					this.tempAnchorIndexes[i] = -1;

					return computeChildRange(anchorEndStart, anchor, indexOfSectionBottom);
				}
			}
		}

		int fromIndex = this.tempLastAnchorFoundIndex;
		int[] index;

		// check each next unknown anchor locations
		index = getAnchorIndex(fromIndex);
		while (index[0] != -1 && (index[0] < indexOfSectionBottom || indexOfSectionBottom == -1)) {
			fromIndex = index[0] + 1;

			int anchorEndStart = index[0] + index[1];

			this.tempLastAnchorFoundIndex = anchorEndStart;

			if (CharOperation.prefixEquals(anchor, this.content, false, anchorEndStart)) {
				return computeChildRange(anchorEndStart, anchor, indexOfSectionBottom);
			} else {
				if (this.tempAnchorIndexes.length == this.tempAnchorIndexesCount) {
					System.arraycopy(this.tempAnchorIndexes, 0, this.tempAnchorIndexes = new int[this.tempAnchorIndexesCount + 20], 0, this.tempAnchorIndexesCount);
				}

				this.tempAnchorIndexes[this.tempAnchorIndexesCount++] = anchorEndStart;
			}
			index = getAnchorIndex(fromIndex);
		}

		return null;
	}
	private int[] getAnchorIndex(int fromIndex) {
		int index = CharOperation.indexOf(JavadocConstants.ANCHOR_PREFIX_START, this.content, false, fromIndex);
		if (index != -1) {
			return new int[]{index, JavadocConstants.ANCHOR_PREFIX_START_LENGTH};
		}
		if (index == -1) {
			index = CharOperation.indexOf(JavadocConstants.ANCHOR_PREFIX_START_2, this.content, false, fromIndex);
		}
		if (index == -1) {
			return new int[]{-1, -1};
		} else {
			return new int[]{index, JavadocConstants.ANCHOR_PREFIX_START2_LENGTH};
		}
	}
	
	private int[] computeChildRange(int anchorEndStart, char[] anchor, int indexOfBottom) {
		int[] range = null;

		// try to find the bottom of the section
		if (indexOfBottom != -1) {
			// try to find the end of the anchor
			int indexOfEndLink = CharOperation.indexOf(JavadocConstants.ANCHOR_SUFFIX, this.content, false, anchorEndStart + anchor.length);
			if (indexOfEndLink != -1) {
				// try to find the next anchor
				int indexOfNextElement = getAnchorIndex(indexOfEndLink)[0];

				int javadocStart = indexOfEndLink + JavadocConstants.ANCHOR_SUFFIX_LENGTH;
				int javadocEnd = indexOfNextElement == -1 ? indexOfBottom : Math.min(indexOfNextElement, indexOfBottom);
				range = new int[]{javadocStart, javadocEnd};
			} else {
				// the anchor has no suffix
				range = UNKNOWN_FORMAT;
			}
		} else {
			// the detail section has no bottom
			range = UNKNOWN_FORMAT;
		}

		return range;
	}

	private void computeChildrenSections() {
		// try to find the next separator part
		int lastIndex = CharOperation.indexOf(JavadocConstants.SEPARATOR_START, this.content, false, this.childrenStart);
		lastIndex = lastIndex == -1 ? this.childrenStart : lastIndex;

		// try to find field detail start
		this.indexOfFieldDetails = CharOperation.indexOf(JavadocConstants.FIELD_DETAIL, this.content, false, lastIndex);
		lastIndex = this.indexOfFieldDetails == -1 ? lastIndex : this.indexOfFieldDetails;

		// try to find constructor detail start
		this.indexOfConstructorDetails = CharOperation.indexOf(JavadocConstants.CONSTRUCTOR_DETAIL, this.content, false, lastIndex);
		lastIndex = this.indexOfConstructorDetails == -1 ? lastIndex : this.indexOfConstructorDetails;

		// try to find method detail start
		this.indexOfMethodDetails = CharOperation.indexOf(JavadocConstants.METHOD_DETAIL, this.content, false, lastIndex);
		lastIndex = this.indexOfMethodDetails == -1 ? lastIndex : this.indexOfMethodDetails;

		// we take the end of class data
		this.indexOfEndOfClassData = CharOperation.indexOf(JavadocConstants.END_OF_CLASS_DATA, this.content, false, lastIndex);

		// try to find the field detail end
		this.indexOfFieldsBottom =
			this.indexOfConstructorDetails != -1 ? this.indexOfConstructorDetails :
				this.indexOfMethodDetails != -1 ? this.indexOfMethodDetails :
					this.indexOfEndOfClassData;

		this.indexOfAllMethodsTop =
			this.indexOfConstructorDetails != -1 ?
					this.indexOfConstructorDetails :
						this.indexOfMethodDetails;

		this.indexOfAllMethodsBottom = this.indexOfEndOfClassData;

		this.hasComputedChildrenSections = true;
	}

	
	private int trimBadEnding(String badEnding, int end) {
		return trimBadEnding(badEnding, end, badEnding.length());
	}

	private int trimBadEnding(String badEnding, int end, int numberOfCharsToTrim) {
		if (Arrays.equals(CharOperation.subarray(content, end - badEnding.length(), end), badEnding.toCharArray())) {
			return end - numberOfCharsToTrim;
		} else {
			return end;
		}
	}
	
	/*
	 * Compute the ranges of the parts of the javadoc that describe each child of the type (fields, methods)
	 */
	private int[] computeFieldRange(IField field) throws Exception {
		IType type = field.getDeclaringType();

		if (!this.hasComputedChildrenSections) {
			computeChildrenSections();
		}

		StringBuffer buffer = new StringBuffer(field.getElementName());
		buffer.append(JavadocConstants.ANCHOR_PREFIX_END);
		char[] anchor = String.valueOf(buffer).toCharArray();

		int[] range = null;

		if (this.indexOfFieldDetails == -1 || this.indexOfFieldsBottom == -1) {
			// the detail section has no top or bottom, so the doc has an unknown format
			if (this.unknownFormatAnchorIndexes == null) {
				this.unknownFormatAnchorIndexes = new int[(int)type.getFields().count()];
				this.unknownFormatAnchorIndexesCount = 0;
				this.unknownFormatLastAnchorFoundIndex = this.childrenStart;
			}

			this.tempAnchorIndexes = this.unknownFormatAnchorIndexes;
			this.tempAnchorIndexesCount = this.unknownFormatAnchorIndexesCount;
			this.tempLastAnchorFoundIndex = this.unknownFormatLastAnchorFoundIndex;

			range = computeChildRange(anchor, this.indexOfFieldsBottom);

			this.unknownFormatLastAnchorFoundIndex = this.tempLastAnchorFoundIndex;
			this.unknownFormatAnchorIndexesCount = this.tempAnchorIndexesCount;
			this.unknownFormatAnchorIndexes = this.tempAnchorIndexes;
		} else {
			if (this.fieldAnchorIndexes == null) {
				this.fieldAnchorIndexes = new int[(int)type.getFields().count()];
				this.fieldAnchorIndexesCount = 0;
				this.fieldLastAnchorFoundIndex = this.indexOfFieldDetails;
			}

			this.tempAnchorIndexes = this.fieldAnchorIndexes;
			this.tempAnchorIndexesCount = this.fieldAnchorIndexesCount;
			this.tempLastAnchorFoundIndex = this.fieldLastAnchorFoundIndex;

			range = computeChildRange(anchor, this.indexOfFieldsBottom);

			this.fieldLastAnchorFoundIndex = this.tempLastAnchorFoundIndex;
			this.fieldAnchorIndexesCount = this.tempAnchorIndexesCount;
			this.fieldAnchorIndexes = this.tempAnchorIndexes;
		}

		return range;
	}

	/*
	 * Compute the ranges of the parts of the javadoc that describe each method of the type
	 */
	private int[] computeMethodRange(IMethod method) throws Exception {
		IType type = method.getDeclaringType();
		
		if (!this.hasComputedChildrenSections) {
			computeChildrenSections();
		}
		
		char[] anchor = computeMethodAnchorPrefixEnd(method).toCharArray();
		
		int[] range = null;
		
		
		if (this.indexOfAllMethodsTop == -1 || this.indexOfAllMethodsBottom == -1) {
			// the detail section has no top or bottom, so the doc has an unknown format
			if (this.unknownFormatAnchorIndexes == null) {
				int childernCount = (int)(type.getMethods().count() + type.getFields().count());
				this.unknownFormatAnchorIndexes = new int[childernCount];
				this.unknownFormatAnchorIndexesCount = 0;
				this.unknownFormatLastAnchorFoundIndex = this.childrenStart;
			}
			
			this.tempAnchorIndexes = this.unknownFormatAnchorIndexes;
			this.tempAnchorIndexesCount = this.unknownFormatAnchorIndexesCount;
			this.tempLastAnchorFoundIndex = this.unknownFormatLastAnchorFoundIndex;
			
			range = computeChildRange(anchor, this.indexOfFieldsBottom);
			if (range == null) {
				range = computeChildRange(getJavadoc8Anchor(anchor), this.indexOfAllMethodsBottom);
			}
			
			this.unknownFormatLastAnchorFoundIndex = this.tempLastAnchorFoundIndex;
			this.unknownFormatAnchorIndexesCount = this.tempAnchorIndexesCount;
			this.unknownFormatAnchorIndexes = this.tempAnchorIndexes;
		} else {			
			if (this.methodAnchorIndexes == null) {
				this.methodAnchorIndexes = new int[(int)type.getMethods().count()];
				this.methodAnchorIndexesCount = 0;
				this.methodLastAnchorFoundIndex = this.indexOfAllMethodsTop;
			}
			
			this.tempAnchorIndexes = this.methodAnchorIndexes;
			this.tempAnchorIndexesCount = this.methodAnchorIndexesCount;
			this.tempLastAnchorFoundIndex = this.methodLastAnchorFoundIndex;
			
			range = computeChildRange(anchor, this.indexOfAllMethodsBottom);
			if (range == null) {
				range = computeChildRange(getJavadoc8Anchor(anchor), this.indexOfAllMethodsBottom);
			}
			
			this.methodLastAnchorFoundIndex = this.tempLastAnchorFoundIndex;
			this.methodAnchorIndexesCount = this.tempAnchorIndexesCount;
			this.methodAnchorIndexes = this.tempAnchorIndexes;
		}
		
		return range;
	}
	
	private static char[] getJavadoc8Anchor(char[] anchor) {
		// fix for bug 432284: [1.8] Javadoc-8-style anchors not found by IMethod#getAttachedJavadoc(..)
		char[] anchor8 = new char[anchor.length];
		int i8 = 0;
		for (int i = 0; i < anchor.length; i++) {
			char ch = anchor[i];
			switch (ch) {
				case '(':
				case ')':
				case ',':
					anchor8[i8++] = '-';
					break;
				case '[':
					anchor8[i8++] = ':';
					anchor8[i8++] = 'A';
					break;
				case ' ': // handled by preceding ','
				case ']': // handled by preceding '['
					break;
				default:
					anchor8[i8++] = ch;
			}
		}
		if (i8 != anchor.length) {
			anchor8 = CharOperation.subarray(anchor8, 0, i8);
		}
		return anchor8;
	}

	private String computeMethodAnchorPrefixEnd(IMethod method) throws Exception {
//		String typeQualifiedName = null;
//		if (this.type.isMember()) {
//			IType currentType = this.type;
//			StringBuffer buffer = new StringBuffer();
//			while (currentType != null) {
//				buffer.insert(0, currentType.getElementName());
//				currentType = currentType.getDeclaringType();
//				if (currentType != null) {
//					buffer.insert(0, '.');
//				}
//			}
//			typeQualifiedName = new String(buffer.toString());
//		} else {
//			typeQualifiedName = this.type.getElementName();
//		}
		
//		String methodName = method.getElementName();
//		if (method.getElementName().equals(method.getDeclaringType().getElementName())) {
//			methodName = typeQualifiedName;
//		}

		String anchor = createMethodAnchor(method);
		
//		char[] genericSignature = info.getGenericSignature();
//		if (genericSignature != null) {
//			genericSignature = CharOperation.replaceOnCopy(genericSignature, '/', '.');
//			anchor = Util.toAnchor(0, genericSignature, methodName, Flags.isVarargs(method.getFlags()));
//			if (anchor == null) throw new JavaModelException(new JavaModelStatus(IJavaModelStatusConstants.UNKNOWN_JAVADOC_FORMAT, method));
//		} else {
//			anchor = Signature.toString(method.getSignature().replace('/', '.'), methodName, null, true, false, Flags.isVarargs(method.getFlags()));
//		}
		
//		IType declaringType = /*this.type*/method.getDeclaringType();
//		if (declaringType.isMember()) {
//			// might need to remove a part of the signature corresponding to the synthetic argument (only for constructor)
//			if (method.getElementName().equals(method.getDeclaringType().getElementName()) && !Flags.isStatic(declaringType.getFlags())) {
//				int indexOfOpeningParen = anchor.indexOf('(');
//				if (indexOfOpeningParen == -1) {
//					// should not happen as this is a method signature
//					return null;
//				}
//				int index = indexOfOpeningParen;
//				indexOfOpeningParen++;
//				int indexOfComma = anchor.indexOf(',', index);
//				if (indexOfComma != -1) {
//					index = indexOfComma + 2;
//				} else {
//					// no argument, but a synthetic argument
//					index = anchor.indexOf(')', index);
//				}
//				anchor = anchor.substring(0, indexOfOpeningParen) + anchor.substring(index);
//			}
//		}
		return anchor + JavadocConstants.ANCHOR_PREFIX_END;
	}
	
	private String createMethodAnchor(IMethod method) {
		StringBuilder sb = new StringBuilder(method.getElementName());
		sb.append('(');
		sb.append(String.join(",", method.parameters().map(p -> p.toString()).collect(Collectors.toList())));
		sb.append(')');
		return sb.toString();
	}

	/*
	 * Compute the range of the part of the javadoc that describe the type
	 */
	private void computeTypeRange(IType type) throws Exception {
		final int indexOfStartOfClassData = CharOperation.indexOf(JavadocConstants.START_OF_CLASS_DATA, this.content, false);
		if (indexOfStartOfClassData == -1) {
			this.typeDocRange = UNKNOWN_FORMAT;
			return;
		}
		int indexOfNextSeparator = CharOperation.indexOf(JavadocConstants.SEPARATOR_START, this.content, false, indexOfStartOfClassData);
		if (indexOfNextSeparator == -1) {
			this.typeDocRange = UNKNOWN_FORMAT;
			return;
		}
		int indexOfNextSummary = CharOperation.indexOf(JavadocConstants.NESTED_CLASS_SUMMARY, this.content, false, indexOfNextSeparator);
		if (indexOfNextSummary == -1 && type.isEnum()) {
			// try to find enum constant summary start
			indexOfNextSummary = CharOperation.indexOf(JavadocConstants.ENUM_CONSTANT_SUMMARY, this.content, false, indexOfNextSeparator);
		}
		if (indexOfNextSummary == -1 && type.isAnnotation()) {
			// try to find required enum constant summary start
			indexOfNextSummary = CharOperation.indexOf(JavadocConstants.ANNOTATION_TYPE_REQUIRED_MEMBER_SUMMARY, this.content, false, indexOfNextSeparator);
			if (indexOfNextSummary == -1) {
				// try to find optional enum constant summary start
				indexOfNextSummary = CharOperation.indexOf(JavadocConstants.ANNOTATION_TYPE_OPTIONAL_MEMBER_SUMMARY, this.content, false, indexOfNextSeparator);
			}
		}
		if (indexOfNextSummary == -1) {
			// try to find field summary start
			indexOfNextSummary = CharOperation.indexOf(JavadocConstants.FIELD_SUMMARY, this.content, false, indexOfNextSeparator);
		}
		if (indexOfNextSummary == -1) {
			// try to find constructor summary start
			indexOfNextSummary = CharOperation.indexOf(JavadocConstants.CONSTRUCTOR_SUMMARY, this.content, false, indexOfNextSeparator);
		}
		if (indexOfNextSummary == -1) {
			// try to find method summary start
			indexOfNextSummary = CharOperation.indexOf(JavadocConstants.METHOD_SUMMARY, this.content, false, indexOfNextSeparator);
		}
		
		if (indexOfNextSummary == -1) {
			// we take the end of class data
			indexOfNextSummary = CharOperation.indexOf(JavadocConstants.END_OF_CLASS_DATA, this.content, false, indexOfNextSeparator);
		} else {
			// improve performance of computation of children ranges
			this.childrenStart = indexOfNextSummary + 1;
		}
		
		if (indexOfNextSummary == -1) {
			this.typeDocRange = UNKNOWN_FORMAT;
			return;
		}
		/*
		 * Cut off the type hierarchy, see bug 119844.
		 * We remove the contents between the start of class data and where
		 * we guess the actual class comment starts.
		 */
		int start = indexOfStartOfClassData + JavadocConstants.START_OF_CLASS_DATA_LENGTH;
		int indexOfFirstParagraph = CharOperation.indexOf(JavadocConstants.P.toCharArray(), this.content, false, start, indexOfNextSummary);
		int indexOfFirstDiv = CharOperation.indexOf(JavadocConstants.DIV_CLASS_BLOCK.toCharArray(), this.content, false, start, indexOfNextSummary);
		int afterHierarchy = indexOfNextSummary;
		if (indexOfFirstParagraph != -1 && indexOfFirstParagraph < afterHierarchy) {
			afterHierarchy = indexOfFirstParagraph;
		}
		if (indexOfFirstDiv != -1 && indexOfFirstDiv < afterHierarchy) {
			afterHierarchy = indexOfFirstDiv;
		}
		if (afterHierarchy != indexOfNextSummary) {
			start = afterHierarchy;

			int indexOfClassDescriptionEnd = trimBadEnding("<div class=\"summary\">\n<ul class=\"blockList\">\n<li class=\"blockList\">\n", indexOfNextSummary);
			indexOfClassDescriptionEnd = trimBadEnding("</li>\n</ul>\n</div>\n", indexOfClassDescriptionEnd);
			this.typeDocRange = new int[]{start, indexOfClassDescriptionEnd};
			this.typeDocRange = sanitizeRange(typeDocRange, "ul", "li");
		} else {
			// No room left for class comment;
			this.typeDocRange = null;
		}
		
		
	}
	
	private int[] trimRange(int[] range) {
		int start = range[0];
		int end = range[1];
		
		while (start < content.length && start < end && Character.isWhitespace(content[start])) {
			start++;
		}

		while (end > 0 && start < end && Character.isWhitespace(content[end-1])) {
			end--;
		}
		
		return new int[] { start, end };
	}
	
	private int[] trimTag(int[] range, CharSequence tag) {
		int start = range[0];
		int end = range[1];
		
		char[] startingTag = ("<" + tag).toCharArray();
		char[] closingTag = ("</" + tag + ">").toCharArray(); 
		char[] ending = CharOperation.subarray(content, end - closingTag.length, end);
		char[] starting = CharOperation.subarray(content, start, start + startingTag.length);
		if (Arrays.equals(closingTag, ending) && Arrays.equals(startingTag, starting)) {
			return new int[] { CharOperation.indexOf('>', content, start, end) + 1, end - closingTag.length};
		} else {
			return range;
		}
	}
	
	private int[] sanitizeRange(int[] range, CharSequence... removeWrapperTags) {
		boolean changed = false;
		do {
			int[] newRange = trimRange(range);
			for (CharSequence tag : removeWrapperTags) {
				newRange = trimTag(newRange, tag);
			}
//			newRange = trimTag(newRange, "div");
//			newRange = trimTag(newRange, "ul");
//			newRange = trimTag(newRange, "li");
			changed = !Arrays.equals(newRange, range);
			range = newRange;
		} while (changed);
		return range;
	}
	
}
