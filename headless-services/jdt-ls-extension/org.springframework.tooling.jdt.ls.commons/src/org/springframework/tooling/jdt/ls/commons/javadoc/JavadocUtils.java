package org.springframework.tooling.jdt.ls.commons.javadoc;

import java.net.URI;
import java.util.function.Function;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.ITypeParameter;
import org.springframework.tooling.jdt.ls.commons.java.JavaData;

public class JavadocUtils {

	public static final String javadoc(Function<IJavaElement, String> contentProvider, URI projectUri, String bindingKey, boolean lookInOtherProjects) throws Exception {
		IJavaElement element = JavaData.findElement(projectUri, bindingKey, lookInOtherProjects);
		return computeJavadoc(contentProvider, element);
	}

	private static String computeJavadoc(Function<IJavaElement, String> contentProvider, IJavaElement element) {
		if (element == null) {
			return null;
		}
		if (element instanceof ITypeParameter tp) {
			element = tp.getDeclaringMember();
		}
		return contentProvider.apply(element);
	}

	public static String alternateBinding(String bindingKey) {
		int idxStartParams = bindingKey.indexOf('(');
		if (idxStartParams >= 0) {
			int idxEndParams = bindingKey.indexOf(')', idxStartParams);
			if (idxEndParams > idxStartParams) {
				String params = bindingKey.substring(idxStartParams, idxEndParams);
				return bindingKey.substring(0, idxStartParams) + params.replace('/', '.') + bindingKey.substring(idxEndParams);
			}
		}
		return null;
	}


}