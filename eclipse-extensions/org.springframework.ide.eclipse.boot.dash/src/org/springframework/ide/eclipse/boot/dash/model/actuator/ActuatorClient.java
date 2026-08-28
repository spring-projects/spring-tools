/*******************************************************************************
 * Copyright (c) 2015, 2019 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.model.actuator;

import java.util.List;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.json.JSONException;
import org.json.JSONObject;
import org.osgi.framework.Version;
import org.osgi.framework.VersionRange;
import org.springframework.ide.eclipse.beans.ui.live.model.JsonParser;
import org.springframework.ide.eclipse.beans.ui.live.model.LiveBeansJsonParser;
import org.springframework.ide.eclipse.beans.ui.live.model.LiveBeansJsonParser2;
import org.springframework.ide.eclipse.beans.ui.live.model.LiveBeansModel;
import org.springframework.ide.eclipse.beans.ui.live.model.TypeLookup;
import org.springframework.ide.eclipse.boot.dash.model.actuator.env.LiveEnvJsonParser1x;
import org.springframework.ide.eclipse.boot.dash.model.actuator.env.LiveEnvJsonParser2x;
import org.springframework.ide.eclipse.boot.dash.model.actuator.env.LiveEnvModel;
import org.springsource.ide.eclipse.commons.livexp.util.Log;

/**
 * Abstract implementation of a ActuatorClient. The actuar client connects
 * to an actuator endpoint retrieving some information from a running spring boot app.
 * <p>
 * This implementation is abstract because there is more than one way that we can
 * connect to an actuator endpoint and retrieve the data from it. The method
 * to retrieve the data is therefore an abstract method.
 *
 * @author Kris De Volder
 */
public abstract class ActuatorClient {

	private static final VersionRange BEANS_PARSER_VERSION_1_RANGE = new VersionRange("[1.0.0, 2.0.0)");
		//Note: this use of osgi.framework.VersionRange appears to be okay for now.
		//  It is not applied to a version derived from spring boot itself but to a version from
		// 'org.springframework.ide.eclipse.boot.dash.model.actuator.JMXActuatorClient.OperationInfo'
		// This is data fully produced by us amd just consisting of a simple number like "1" or "2" at the moment,
		// OSGI version parser handles it fine. (Whereas spring-boot specific version parser actually does not).

	private final TypeLookup typeLookup;

	public ActuatorClient(TypeLookup typeLookup) {
		this.typeLookup = typeLookup;
	}


	private List<RequestMapping> parseRequestMappings(String json, String version) throws JSONException {
 		JSONObject obj = new JSONObject(json);
 		RequestMappingsParser parser;
		if ("2".equals(version)) {
			// Boot 2.x
			parser = new Boot2RequestMappingsParser();
		} else {
			//Boot 1.x
			parser = new Boot1RequestMappingsParser();
		}
		return parser.parse(obj, typeLookup);
	}

	public List<RequestMapping> getRequestMappings() {
		try {
			ImmutablePair<String, String> data = getRequestMappingData();
			if (data != null) {
				String json = data.left;
				if (json != null) {
					return parseRequestMappings(json, data.right);
				}
			}
		} catch (Exception e) {
			Log.log(e);
		}
		return null;
	}

	public LiveBeansModel getBeans() {
		try {
			ImmutablePair<String, String> data = getBeansData();
			if (data != null) {
				String json = data.left;
				String version = data.right;
				if (json != null) {
					if (version != null) {
						if (BEANS_PARSER_VERSION_1_RANGE.includes(Version.valueOf(version))) {
							return new LiveBeansJsonParser(typeLookup, json).parse();
						}
					}
					return new LiveBeansJsonParser2(typeLookup, json).parse();
				}
			}
		} catch (Exception e) {
			Log.log(e);
		}
		return null;
	}

	public LiveEnvModel getEnv() {
		try {
			ImmutablePair<String, String> data = getEnvData();
			if (data != null) {
				String json = data.left;
				if (json != null) {
					return parseEnv(json, data.right);
				}
			}
		} catch (Exception e) {
			Log.log(e);
		}
		return null;
	}

	private LiveEnvModel parseEnv(String json, String version) throws Exception {
		JsonParser<LiveEnvModel> parser = null;
		if ("2".equals(version)) {
			// Boot 2.x
			parser = new LiveEnvJsonParser2x();
		} else {
			//Boot 1.x
			parser = new LiveEnvJsonParser1x();
		}
		return parser.parse(json);
	}

	protected abstract ImmutablePair<String, String> getRequestMappingData() throws Exception;

	protected abstract ImmutablePair<String, String> getBeansData() throws Exception;

	protected abstract ImmutablePair<String, String> getEnvData() throws Exception;

}
