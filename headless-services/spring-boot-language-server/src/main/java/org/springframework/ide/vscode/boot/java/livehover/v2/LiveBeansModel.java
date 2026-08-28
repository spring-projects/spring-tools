/*******************************************************************************
 * Copyright (c) 2017 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.livehover.v2;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.commons.util.StringUtil;

import com.google.common.collect.ImmutableListMultimap;

/**
 * @author Martin Lippert
 * @author Kris De Volder
 */
public class LiveBeansModel {

	private static final Logger log = LoggerFactory.getLogger(LiveBeansModel.class);

	private static final String[] NO_STRINGS = new String[] {};

	public static class Builder {
		private final ImmutableListMultimap.Builder<String, LiveBean> beansViaName = ImmutableListMultimap.builder();
		private final ImmutableListMultimap.Builder<String, LiveBean> beansViaType = ImmutableListMultimap.builder();
		private final ImmutableListMultimap.Builder<String, LiveBean> beansViaDependency = ImmutableListMultimap.builder();

		public LiveBeansModel build() {
			return new LiveBeansModel(beansViaName.build(), beansViaType.build(), beansViaDependency.build());
		}

		public Builder add(LiveBean bean) {
			String type = bean.getType();
			if (type != null) {
				beansViaType.put(type, bean);
			}

			String name = bean.getId();
			if (name != null) {
				beansViaName.put(name, bean);
			}

			String[] deps = bean.getDependencies();
			if (deps != null) {
				for (String dep : deps) {
					beansViaDependency.put(dep, bean);
				}
			}
			return this;
		}

	}

	public static LiveBeansModel.Builder builder() {
		return new Builder();
	}

	interface Parser {
		LiveBeansModel parse(String json) throws Exception;
	}

	private static class Boot15Parser implements Parser {
		@Override
		public LiveBeansModel parse(String json) throws Exception {
			Builder model = LiveBeansModel.builder();
			JSONArray mainArray = new JSONArray(json);
			for (int i = 0; i < mainArray.length(); i++) {
				JSONObject appContext = mainArray.getJSONObject(i);
				if (appContext == null) continue;

				JSONArray beansArray = appContext.optJSONArray("beans");
				if (beansArray == null) continue;

				for (int j = 0; j < beansArray.length(); j++) {
					JSONObject beanObject = beansArray.getJSONObject(j);
					if (beanObject == null) continue;

					LiveBean bean = parseBean(beanObject);
					if (bean != null) {
						model.add(bean);
					}
				}
			}
			return model.build();
		}
		private LiveBean parseBean(JSONObject beansJSON) {
			String id = beansJSON.optString("bean");
			String type = beansJSON.optString("type");
			String scope = beansJSON.optString("scope");
			String resource = beansJSON.optString("resource");

			JSONArray aliasesJSON = beansJSON.optJSONArray("aliases");
			String[] aliases;
			if (aliasesJSON != null) {
				aliases = new String[aliasesJSON.length()];
				for (int i = 0; i < aliasesJSON.length(); i++) {
					aliases[i] = aliasesJSON.optString(i);
				}
			} else {
				aliases = NO_STRINGS;
			}

			JSONArray dependenciesJSON = beansJSON.getJSONArray("dependencies");
			String[] dependencies = new String[dependenciesJSON.length()];
			for (int i = 0; i < dependenciesJSON.length(); i++) {
				dependencies[i] = dependenciesJSON.optString(i);
			}

			return new LiveBean(id, aliases, scope, type, resource, dependencies);
		}

	}

	private static class Boot20Parser implements Parser {

		@Override
		public LiveBeansModel parse(String json) throws Exception {
			Builder model = LiveBeansModel.builder();
			JSONObject mainObject = new JSONObject(json);

			mainObject = mainObject.getJSONObject("contexts");
			for (String contextId : mainObject.keySet()) {
				JSONObject contextObject = mainObject.getJSONObject(contextId);
				JSONObject beansObject = contextObject.getJSONObject("beans");
				for (String id : beansObject.keySet()) {
					JSONObject beanObject = beansObject.getJSONObject(id);
					LiveBean bean = parseBean(id, contextId, beanObject);
					if (bean != null) {
						model.add(bean);
					}
				}
			}
			return model.build();
		}

		private LiveBean parseBean(String id, String contextId, JSONObject beansJSON) {
			String type = beansJSON.optString("type");
			String scope = beansJSON.optString("scope");
			String resource = beansJSON.optString("resource");

			JSONArray aliasesJSON = beansJSON.optJSONArray("aliases");
			String[] aliases;
			if (aliasesJSON == null) {
				aliases = NO_STRINGS;
			} else {
				aliases = new String[aliasesJSON.length()];
				for (int i = 0; i < aliasesJSON.length(); i++) {
					aliases[i] = aliasesJSON.optString(i);
				}
			}

			JSONArray dependenciesJSON = beansJSON.optJSONArray("dependencies");
			String[] dependencies;
			if (dependenciesJSON == null) {
				dependencies = NO_STRINGS;
			} else {
				dependencies = new String[dependenciesJSON.length()];
				for (int i = 0; i < dependenciesJSON.length(); i++) {
					dependencies[i] = dependenciesJSON.optString(i);
				}
			}

			return new LiveBean(id, aliases, scope, type, resource, dependencies);
		}
	}

	private static final Parser[] PARSERS = {
			new Boot15Parser(),
			new Boot20Parser(),
	};

	public static LiveBeansModel parse(String json) {
		List<Exception> exceptions = new ArrayList<>(PARSERS.length);
		if (StringUtil.hasText(json)) {
			for (Parser parser : PARSERS) {
				try {
					LiveBeansModel model = parser.parse(json);
					if (model == null) {
						throw new NullPointerException("Parser returned a null model (it should not!)");
					}
					return model; //good!
				} catch (Exception e) {
					exceptions.add(e);
				}
			}
		}
		//Only getting here if none of the parsers worked... So if at least one parser works,
		// we won't log any exceptions.
		for (Exception e : exceptions) {
			log.warn(e.getMessage());
		}
		return LiveBeansModel.builder().build(); // always return at least an empty model.
	}

	private final ImmutableListMultimap<String, LiveBean> beansViaType;
	private final ImmutableListMultimap<String, LiveBean> beansViaName;
	private final ImmutableListMultimap<String, LiveBean> beansViaDependency;

	protected LiveBeansModel(
			ImmutableListMultimap<String, LiveBean> beansViaName,
			ImmutableListMultimap<String, LiveBean> beansViaType,
			ImmutableListMultimap<String, LiveBean> beansViaDependency) {
		this.beansViaName = beansViaName;
		this.beansViaType = beansViaType;
		this.beansViaDependency = beansViaDependency;
	}

	public List<LiveBean> getBeansOfType(String fullyQualifiedType) {
		return beansViaType.get(fullyQualifiedType);
	}

	public List<LiveBean> getBeansOfName(String beanName) {
		return beansViaName.get(beanName);
	}

	public List<LiveBean> getBeansDependingOn(String beanName) {
		return beansViaDependency.get(beanName);
	}

	public boolean isEmpty() {
		return beansViaName.isEmpty(); //Assumes every bean has a name.
	}

	public Set<String> getBeanNames() {
		return beansViaName.keySet();
	}

}
