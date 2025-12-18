/*******************************************************************************
 * Copyright (c) 2024, 2025 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.languageserver.semantic.tokens;

import java.util.Arrays;
import java.util.Objects;

import org.springframework.ide.vscode.commons.util.text.IRegion;
import org.springframework.ide.vscode.commons.util.text.Region;

public record SemanticTokenData(
		IRegion range,
		String type,
		String[] modifiers
	) implements Comparable<SemanticTokenData> {
	
	public SemanticTokenData(int start, int end, String type, String[] modifiers) {
		this(new Region(start, end -start), type, modifiers);
	}

	public static Builder builder(String type) {
		return new Builder().withType(type);
	}
	
	@Override
	public int compareTo(SemanticTokenData o) {
		if (range.getOffset() == o.range().getOffset()) {
			return range.getLength() - o.range().getLength();
		}
		return range.getOffset() - o.range().getOffset();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(modifiers);
		result = prime * result + Objects.hash(range, type);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		SemanticTokenData other = (SemanticTokenData) obj;
		return range.getLength() == other.range.getLength() && Arrays.equals(modifiers, other.modifiers) && range.getOffset() == other.range().getOffset()
				&& Objects.equals(type, other.type);
	}
	
	public int getStart() {
		return range.getStart();
	}
	
	public int getEnd() {
		return range.getEnd();
	}
	
	public static class Builder {
		int offset = 0;
		String text = "";
		String[] modifiers = new String[0];
		String type;
		
		public Builder withOffset(int offset) {
			this.offset = offset;
			return this;
		}
		
		public Builder withText(String text) {
			this.text = text;
			return this;
		}
		
		public Builder withType(String type) {
			this.type = type;
			return this;
		}
		
		public Builder withModifiers(String[] modifiers) {
			this.modifiers = modifiers;
			return this;
		}
		
		public Builder addOffset(int offset) {
			this.offset += offset;
			return this;
		}
		
		public Builder addOffset(String space) {
			this.offset += space.length();
			return this;
		}
		
		public Builder space() {
			this.offset++;
			return this;
		}
		
		public Builder withPrevious(SemanticTokenData previous) {
			return withOffset(previous.getEnd());
		}
		
		public SemanticTokenData build() {
			return new SemanticTokenData(new Region(offset, text.length()), type, modifiers);
		}

	}
}
