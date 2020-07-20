/*
 * (C) Copyright 2017-2020 OpenVidu (https://openvidu.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.openvidu.server.rest;

import org.apache.http.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.openvidu.server.cdr.CDREventName;
import io.openvidu.server.config.OpenviduConfig;

/**
 *
 * @author Pablo Fuente (pablofuenteperez@gmail.com)
 */
@RestController
@CrossOrigin
@RequestMapping("/config")
public class ConfigRestController {

	private static final Logger log = LoggerFactory.getLogger(ConfigRestController.class);

	@Autowired
	private OpenviduConfig openviduConfig;

	@RequestMapping(value = "/openvidu-version", method = RequestMethod.GET)
	public String getOpenViduServerVersion() {

		log.info("REST API: GET /config/openvidu-version");

		return openviduConfig.getOpenViduServerVersion();
	}

	@RequestMapping(value = "/openvidu-publicurl", method = RequestMethod.GET)
	public String getOpenViduPublicUrl() {

		log.info("REST API: GET /config/openvidu-publicurl");

		return openviduConfig.getFinalUrl();
	}

	@RequestMapping(value = "/openvidu-recording", method = RequestMethod.GET)
	public Boolean getOpenViduRecordingEnabled() {

		log.info("REST API: GET /config/openvidu-recording");

		return openviduConfig.isRecordingModuleEnabled();
	}

	@RequestMapping(value = "/openvidu-recording-path", method = RequestMethod.GET)
	public String getOpenViduRecordingPath() {

		log.info("REST API: GET /config/openvidu-recording-path");

		return openviduConfig.getOpenViduRecordingPath();
	}

	@RequestMapping(value = "/openvidu-cdr", method = RequestMethod.GET)
	public Boolean getOpenViduCdrEnabled() {

		log.info("REST API: GET /config/openvidu-cdr");

		return openviduConfig.isCdrEnabled();
	}

	@RequestMapping(method = RequestMethod.GET)
	public ResponseEntity<String> getOpenViduConfiguration() {

		log.info("REST API: GET /config");

		JsonObject json = new JsonObject();
		json.addProperty("version", openviduConfig.getVersion());
		JsonArray kmsUris = new JsonArray();
		openviduConfig.getKmsUris().forEach(uri -> kmsUris.add(uri));
		json.add("kmsUris", kmsUris);
		json.addProperty("openviduPublicurl", openviduConfig.getOpenViduPublicUrl());
		json.addProperty("openviduCdr", openviduConfig.isCdrEnabled());
		json.addProperty("maxRecvBandwidth", openviduConfig.getVideoMaxRecvBandwidth());
		json.addProperty("minRecvBandwidth", openviduConfig.getVideoMinRecvBandwidth());
		json.addProperty("maxSendBandwidth", openviduConfig.getVideoMaxSendBandwidth());
		json.addProperty("minSendBandwidth", openviduConfig.getVideoMinSendBandwidth());
		json.addProperty("openviduRecording", openviduConfig.isRecordingModuleEnabled());
		if (openviduConfig.isRecordingModuleEnabled()) {
			json.addProperty("openviduRecordingVersion", openviduConfig.getOpenViduRecordingVersion());
			json.addProperty("openviduRecordingPath", openviduConfig.getOpenViduRecordingPath());
			json.addProperty("openviduRecordingPublicAccess", openviduConfig.getOpenViduRecordingPublicAccess());
			json.addProperty("openviduRecordingNotification", openviduConfig.getOpenViduRecordingNotification().name());
			json.addProperty("openviduRecordingCustomLayout", openviduConfig.getOpenviduRecordingCustomLayout());
			json.addProperty("openviduRecordingAutostopTimeout", openviduConfig.getOpenviduRecordingAutostopTimeout());
			if (openviduConfig.getOpenViduRecordingComposedUrl() != null
					&& !openviduConfig.getOpenViduRecordingComposedUrl().isEmpty()) {
				json.addProperty("openviduRecordingComposedUrl", openviduConfig.getOpenViduRecordingComposedUrl());
			}
		}
		json.addProperty("openviduWebhook", openviduConfig.isWebhookEnabled());
		if (openviduConfig.isWebhookEnabled()) {
			json.addProperty("openviduWebhookEndpoint", openviduConfig.getOpenViduWebhookEndpoint());
			JsonArray webhookHeaders = new JsonArray();
			for (Header header : openviduConfig.getOpenViduWebhookHeaders()) {
				webhookHeaders.add(header.getName() + ": " + header.getValue());
			}
			json.add("openviduWebhookHeaders", webhookHeaders);
			JsonArray webhookEvents = new JsonArray();
			for (CDREventName eventName : openviduConfig.getOpenViduWebhookEvents()) {
				webhookEvents.add(eventName.name());
			}
			json.add("openviduWebhookEvents", webhookEvents);
		}

		return new ResponseEntity<>(json.toString(), getResponseHeaders(), HttpStatus.OK);
	}

	protected HttpHeaders getResponseHeaders() {
		HttpHeaders responseHeaders = new HttpHeaders();
		responseHeaders.setContentType(MediaType.APPLICATION_JSON);
		return responseHeaders;
	}

}
