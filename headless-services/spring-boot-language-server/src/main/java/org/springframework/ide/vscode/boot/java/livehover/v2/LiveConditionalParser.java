/*******************************************************************************
 * Copyright (c) 2017, 2024 Pivotal, Inc.
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
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.commons.util.StringUtil;

/**
 * Parsers @ConditionalOn annotations from a spring boot running app's
 * autoconfig report. An example of a conditional that is parsed from a running
 * app's autoconfig report would be:
 *
 * {"TraceRepositoryAutoConfiguration#traceRepository":[{"condition":"OnBeanCondition","message":"@ConditionalOnMissingBean
 * (types: org.springframework.boot.actuate.trace.TraceRepository;
 * SearchStrategy: all) did not find any beans"}]
 *
 */
public class LiveConditionalParser {
	
	private static final Logger log = LoggerFactory.getLogger(LiveConditionalParser.class);

	private final String autoConfigRecord;
	private final String appProcessName;
	private final String appProcessId;

	public LiveConditionalParser(String autoConfigRecord, String appProcessId, String appProcessName) {
		this.autoConfigRecord = autoConfigRecord;
		this.appProcessId = appProcessId;
		this.appProcessName = appProcessName;
	}

	public LiveConditional[] parse() {
		try {
			List<LiveConditional> allConditionals = new ArrayList<>();
			if (StringUtil.hasText(autoConfigRecord)) {
				JSONObject autoConfigReport = new JSONObject(autoConfigRecord);
				if (autoConfigReport.has("contexts")) {
					//more recently the report is nested inside an 'application' context, but the name of the node depends on the app
					
					JSONObject contexts = autoConfigReport.getJSONObject("contexts");
					Iterator<String> keys = contexts.keys();
					if (keys.hasNext()) {
						autoConfigReport = contexts.getJSONObject(keys.next());
					}
				}
				
				for (LiveConditional c : getConditionalsFromPositiveMatches(autoConfigReport)) {
					allConditionals.add(c);
				}
				
				for (LiveConditional c : getConditionalsFromNegativeMatches(autoConfigReport)) {
					allConditionals.add(c);
				}
			}
			if (!allConditionals.isEmpty()) {
				return (LiveConditional[]) allConditionals.toArray(new LiveConditional[allConditionals.size()]);
			}
		} catch (Exception e) {
			log.warn(e.getMessage());
		}
		return new LiveConditional[0];
	}

	/**
	 * Fetches the "positiveMatches" element in the autoconfig report JSON that contains conditional information.
	 */
	private Optional<JSONObject> getPositiveMatchesJson(JSONObject autoConfigReport) {
		return Optional.ofNullable(autoConfigReport.optJSONObject("positiveMatches"));
	}

	/**
	 * Fetches the "negativeMatches" element in the autoconfig report JSON that contains conditional information.
	 */
	private Optional<JSONObject> getNegativeMatchesJson(JSONObject autoConfigReport) {
		return Optional.ofNullable(autoConfigReport.optJSONObject("negativeMatches"));
	}

	/**
	 * Fetches all the conditionals listed in the "positiveMatches" element in the autoconfig report.
	 *
	 */
	private List<LiveConditional> getConditionalsFromPositiveMatches(JSONObject autoConfigReport) {
		List<LiveConditional> conditions = new ArrayList<>();
		getPositiveMatchesJson(autoConfigReport).ifPresent((matches) -> {
			for (String typeInfo : matches.keySet()) {
				// The positive match key contains the bean method information where conditional
				// was applied to
				Object val = matches.get(typeInfo);
				if (val instanceof JSONArray) {
					JSONArray contentList = (JSONArray) val;
					parseConditionalsFromContentList(conditions, typeInfo, contentList);
				}
			}
		});
		return conditions;
	}

	private List<LiveConditional> getConditionalsFromNegativeMatches(JSONObject autoConfigReport) {
		List<LiveConditional> conditions = new ArrayList<>();
		// The JSON structure being parsed is:
//	    "negativeMatches": {
//	        "MyConditionalComponent": {
//	            "notMatched": [
//	                {
//	                    "condition": "OnClassCondition",
//	                    "message": "@ConditionalOnClass did not find required class 'java.lang.String2'"
//	                }
//	            ],
//	            "matched": []
//	        }
		getNegativeMatchesJson(autoConfigReport).ifPresent((matches) -> {
			// The key in the "matches" JSON contains the live type information where the conditional was applied to
			for (String typeInfo : matches.keySet()) {
				// The positive match key contains the bean method information where conditional
				// was applied to
				Object val = matches.get(typeInfo);
				if (val instanceof JSONObject) {
					JSONObject negativeMatches = (JSONObject) val;
					negativeMatches.keySet().stream().forEach((key) -> {
						JSONArray contentList = (JSONArray) negativeMatches.get(key);
						parseConditionalsFromContentList(conditions, typeInfo, contentList);
					});
				}

			}
		});
		return conditions;
	}

	private void parseConditionalsFromContentList(List<LiveConditional> conditionals, String typeInfo, JSONArray contentList) {
		for (Object content : contentList) {
			if (content instanceof JSONObject) {
				JSONObject conditionalJson = (JSONObject) content;
				String condition = (String) conditionalJson.get("condition");
				String message = (String) conditionalJson.get("message");
				// We care about the message itself as it contains the actual annotation as well
				// as the reason it matched
				if (StringUtil.hasText(message)) {
					LiveConditional conditional = LiveConditional.builder().processId(appProcessId)
							.processName(appProcessName).condition(condition).message(message).typeInfo(typeInfo)
							.build();
					conditionals.add(conditional);
				}
			}
		}
	}

	public static LiveConditional[] parse(String autoConfigRecord, String appProcessId, String appProcessName) {
		return new LiveConditionalParser(autoConfigRecord, appProcessId, appProcessName).parse();
	}
}
