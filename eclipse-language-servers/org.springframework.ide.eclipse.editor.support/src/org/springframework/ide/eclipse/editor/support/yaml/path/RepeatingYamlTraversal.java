package org.springframework.ide.eclipse.editor.support.yaml.path;

import java.util.stream.Stream;

import org.eclipse.core.runtime.Assert;
import org.springframework.ide.eclipse.editor.support.util.Streams;

public class RepeatingYamlTraversal extends AbstractYamlTraversal {

	private YamlTraversal step;

	public RepeatingYamlTraversal(YamlTraversal step) {
		Assert.isLegal(!step.canEmpty()); //This implementation is still too simplistic to handle that properly!
				// If you hit this assert, then it may be time to make it more sophisticated.
		this.step = step;
	}

	@Override
	public <T extends YamlNavigable<T>> Stream<T> traverseAmbiguously(T start) {
		if (start == null) {
			return Stream.empty();
		} else {
			return Stream.concat(
				Streams.fromNullable(start),
				step.traverseAmbiguously(start).flatMap(next -> {
					return this.traverseAmbiguously(next);
				})
			);
		}
	}

	@Override
	public String toString() {
		return "Repeat("+step+")";
	}

	@Override
	public YamlTraversal repeat() {
		//don't make 'Repeat(Repeat(...))'
		return this;
	}

	@Override
	public boolean canEmpty() {
		return true;
	}
}
