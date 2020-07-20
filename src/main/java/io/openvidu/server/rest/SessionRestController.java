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

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.openvidu.client.OpenViduException;
import io.openvidu.client.internal.ProtocolElements;
import io.openvidu.java.client.MediaMode;
import io.openvidu.java.client.OpenViduRole;
import io.openvidu.java.client.Recording.OutputMode;
import io.openvidu.java.client.RecordingLayout;
import io.openvidu.java.client.RecordingMode;
import io.openvidu.java.client.RecordingProperties;
import io.openvidu.java.client.SessionProperties;
import io.openvidu.server.config.OpenviduConfig;
import io.openvidu.server.core.EndReason;
import io.openvidu.server.core.IdentifierPrefixes;
import io.openvidu.server.core.Participant;
import io.openvidu.server.core.Session;
import io.openvidu.server.core.SessionManager;
import io.openvidu.server.kurento.core.KurentoMediaOptions;
import io.openvidu.server.kurento.core.KurentoTokenOptions;
import io.openvidu.server.recording.Recording;
import io.openvidu.server.recording.service.RecordingManager;

/**
 *
 * @author Pablo Fuente (pablofuenteperez@gmail.com)
 */
@RestController
@CrossOrigin
@RequestMapping("/api")
public class SessionRestController {

	private static final Logger log = LoggerFactory.getLogger(SessionRestController.class);

	@Autowired
	private SessionManager sessionManager;

	@Autowired
	private RecordingManager recordingManager;

	@Autowired
	private OpenviduConfig openviduConfig;

	@RequestMapping(value = "/sessions", method = RequestMethod.POST)
	public ResponseEntity<?> getSessionId(@RequestBody(required = false) Map<?, ?> params) {

		log.info("REST API: POST /api/sessions {}", params != null ? params.toString() : "{}");

		SessionProperties.Builder builder = new SessionProperties.Builder();
		String customSessionId = null;

		if (params != null) {

			String mediaModeString;
			String recordingModeString;
			String defaultOutputModeString;
			String defaultRecordingLayoutString;
			String defaultCustomLayout;
			try {
				mediaModeString = (String) params.get("mediaMode");
				recordingModeString = (String) params.get("recordingMode");
				defaultOutputModeString = (String) params.get("defaultOutputMode");
				defaultRecordingLayoutString = (String) params.get("defaultRecordingLayout");
				defaultCustomLayout = (String) params.get("defaultCustomLayout");
				customSessionId = (String) params.get("customSessionId");
			} catch (ClassCastException e) {
				return this.generateErrorResponse("Type error in some parameter", "/api/sessions",
						HttpStatus.BAD_REQUEST);
			}

			try {

				// Safe parameter retrieval. Default values if not defined
				if (recordingModeString != null) {
					RecordingMode recordingMode = RecordingMode.valueOf(recordingModeString);
					builder = builder.recordingMode(recordingMode);
				} else {
					builder = builder.recordingMode(RecordingMode.MANUAL);
				}
				if (defaultOutputModeString != null) {
					OutputMode defaultOutputMode = OutputMode.valueOf(defaultOutputModeString);
					builder = builder.defaultOutputMode(defaultOutputMode);
				} else {
					builder.defaultOutputMode(OutputMode.COMPOSED);
				}
				if (defaultRecordingLayoutString != null) {
					RecordingLayout defaultRecordingLayout = RecordingLayout.valueOf(defaultRecordingLayoutString);
					builder = builder.defaultRecordingLayout(defaultRecordingLayout);
				} else {
					builder.defaultRecordingLayout(RecordingLayout.BEST_FIT);
				}
				if (mediaModeString != null) {
					MediaMode mediaMode = MediaMode.valueOf(mediaModeString);
					builder = builder.mediaMode(mediaMode);
				} else {
					builder = builder.mediaMode(MediaMode.ROUTED);
				}
				if (customSessionId != null && !customSessionId.isEmpty()) {
					if (!sessionManager.formatChecker.isValidCustomSessionId(customSessionId)) {
						return this.generateErrorResponse(
								"Parameter \"customSessionId\" is wrong. Must be an alphanumeric string",
								"/api/sessions", HttpStatus.BAD_REQUEST);
					}
					builder = builder.customSessionId(customSessionId);
				}
				builder = builder.defaultCustomLayout((defaultCustomLayout != null) ? defaultCustomLayout : "");

			} catch (IllegalArgumentException e) {
				return this.generateErrorResponse("RecordingMode " + params.get("recordingMode") + " | "
						+ "Default OutputMode " + params.get("defaultOutputMode") + " | " + "Default RecordingLayout "
						+ params.get("defaultRecordingLayout") + " | " + "MediaMode " + params.get("mediaMode")
						+ ". Some parameter is not defined", "/api/sessions", HttpStatus.BAD_REQUEST);
			}
		}

		SessionProperties sessionProperties = builder.build();

		String sessionId;
		if (customSessionId != null && !customSessionId.isEmpty()) {
			if (sessionManager.getSessionWithNotActive(customSessionId) != null) {
				return new ResponseEntity<>(HttpStatus.CONFLICT);
			}
			sessionId = customSessionId;
		} else {
			sessionId = IdentifierPrefixes.SESSION_ID + RandomStringUtils.randomAlphabetic(1).toUpperCase()
					+ RandomStringUtils.randomAlphanumeric(9);
		}

		Session sessionNotActive = sessionManager.storeSessionNotActive(sessionId, sessionProperties);
		log.info("New session {} initialized {}", sessionId, this.sessionManager.getSessionsWithNotActive().stream()
				.map(Session::getSessionId).collect(Collectors.toList()).toString());
		JsonObject responseJson = new JsonObject();
		responseJson.addProperty("id", sessionNotActive.getSessionId());
		responseJson.addProperty("createdAt", sessionNotActive.getStartTime());

		return new ResponseEntity<>(responseJson.toString(), getResponseHeaders(), HttpStatus.OK);
	}

	@RequestMapping(value = "/sessions/{sessionId}", method = RequestMethod.GET)
	public ResponseEntity<?> getSession(@PathVariable("sessionId") String sessionId,
			@RequestParam(value = "webRtcStats", defaultValue = "false", required = false) boolean webRtcStats) {

		log.info("REST API: GET /api/sessions/{}", sessionId);

		Session session = this.sessionManager.getSession(sessionId);
		if (session != null) {
			JsonObject response = (webRtcStats == true) ? session.withStatsToJson() : session.toJson();
			return new ResponseEntity<>(response.toString(), getResponseHeaders(), HttpStatus.OK);
		} else {
			Session sessionNotActive = this.sessionManager.getSessionNotActive(sessionId);
			if (sessionNotActive != null) {
				JsonObject response = (webRtcStats == true) ? sessionNotActive.withStatsToJson()
						: sessionNotActive.toJson();
				return new ResponseEntity<>(response.toString(), getResponseHeaders(), HttpStatus.OK);
			} else {
				return new ResponseEntity<>(HttpStatus.NOT_FOUND);
			}
		}
	}

	@RequestMapping(value = "/sessions", method = RequestMethod.GET)
	public ResponseEntity<?> listSessions(
			@RequestParam(value = "webRtcStats", defaultValue = "false", required = false) boolean webRtcStats) {

		log.info("REST API: GET /api/sessions");

		Collection<Session> sessions = this.sessionManager.getSessionsWithNotActive();
		JsonObject json = new JsonObject();
		JsonArray jsonArray = new JsonArray();
		sessions.forEach(s -> {
			JsonObject sessionJson = (webRtcStats == true) ? s.withStatsToJson() : s.toJson();
			jsonArray.add(sessionJson);
		});
		json.addProperty("numberOfElements", sessions.size());
		json.add("content", jsonArray);
		return new ResponseEntity<>(json.toString(), getResponseHeaders(), HttpStatus.OK);
	}

	@RequestMapping(value = "/sessions/{sessionId}", method = RequestMethod.DELETE)
	public ResponseEntity<?> closeSession(@PathVariable("sessionId") String sessionId) {

		log.info("REST API: DELETE /api/sessions/{}", sessionId);

		Session session = this.sessionManager.getSession(sessionId);
		if (session != null) {
			this.sessionManager.closeSession(sessionId, EndReason.sessionClosedByServer);
			return new ResponseEntity<>(HttpStatus.NO_CONTENT);
		}

		Session sessionNotActive = this.sessionManager.getSessionNotActive(sessionId);
		if (sessionNotActive != null) {
			try {
				sessionNotActive.closingLock.writeLock().lock();
				if (sessionNotActive.isClosed()) {
					return new ResponseEntity<>(HttpStatus.NOT_FOUND);
				}
				this.sessionManager.closeSessionAndEmptyCollections(sessionNotActive, EndReason.sessionClosedByServer,
						true);
				return new ResponseEntity<>(HttpStatus.NO_CONTENT);
			} finally {
				sessionNotActive.closingLock.writeLock().unlock();
			}
		} else {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}
	}

	@RequestMapping(value = "/sessions/{sessionId}/connection/{connectionId}", method = RequestMethod.DELETE)
	public ResponseEntity<?> disconnectParticipant(@PathVariable("sessionId") String sessionId,
			@PathVariable("connectionId") String participantPublicId) {

		log.info("REST API: DELETE /api/sessions/{}/connection/{}", sessionId, participantPublicId);

		Session session = this.sessionManager.getSessionWithNotActive(sessionId);
		if (session == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		Participant participant = session.getParticipantByPublicId(participantPublicId);
		if (participant != null) {
			this.sessionManager.evictParticipant(participant, null, null, EndReason.forceDisconnectByServer);
			return new ResponseEntity<>(HttpStatus.NO_CONTENT);
		} else {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}
	}

	@RequestMapping(value = "/sessions/{sessionId}/stream/{streamId}", method = RequestMethod.DELETE)
	public ResponseEntity<?> unpublishStream(@PathVariable("sessionId") String sessionId,
			@PathVariable("streamId") String streamId) {

		log.info("REST API: DELETE /api/sessions/{}/stream/{}", sessionId, streamId);

		Session session = this.sessionManager.getSessionWithNotActive(sessionId);
		if (session == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		session = this.sessionManager.getSession(sessionId);
		if (session != null) {

			final String participantPrivateId = this.sessionManager.getParticipantPrivateIdFromStreamId(sessionId,
					streamId);

			if (participantPrivateId == null) {
				return new ResponseEntity<>(HttpStatus.NOT_FOUND);
			}

			Participant participant = this.sessionManager.getParticipant(participantPrivateId);
			if (participant.isIpcam()) {
				return new ResponseEntity<>(HttpStatus.METHOD_NOT_ALLOWED);
			}

			this.sessionManager.unpublishStream(session, streamId, null, null, EndReason.forceUnpublishByServer);
			return new ResponseEntity<>(HttpStatus.NO_CONTENT);
		} else {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}
	}

	@RequestMapping(value = "/tokens", method = RequestMethod.POST)
	public ResponseEntity<String> newToken(@RequestBody Map<?, ?> params) {

		if (params == null) {
			return this.generateErrorResponse("Error in body parameters. Cannot be empty", "/api/tokens",
					HttpStatus.BAD_REQUEST);
		}

		log.info("REST API: POST /api/tokens {}", params.toString());

		String sessionId;
		String roleString;
		String metadata;
		try {
			sessionId = (String) params.get("session");
			roleString = (String) params.get("role");
			metadata = (String) params.get("data");
		} catch (ClassCastException e) {
			return this.generateErrorResponse("Type error in some parameter", "/api/tokens", HttpStatus.BAD_REQUEST);
		}

		if (sessionId == null) {
			return this.generateErrorResponse("\"session\" parameter is mandatory", "/api/tokens",
					HttpStatus.BAD_REQUEST);
		}

		final Session session = this.sessionManager.getSessionWithNotActive(sessionId);
		if (session == null) {
			return this.generateErrorResponse("Session " + sessionId + " not found", "/api/tokens",
					HttpStatus.NOT_FOUND);
		}

		JsonObject kurentoOptions = null;

		if (params.get("kurentoOptions") != null) {
			try {
				kurentoOptions = JsonParser.parseString(params.get("kurentoOptions").toString()).getAsJsonObject();
			} catch (Exception e) {
				return this.generateErrorResponse("Error in parameter 'kurentoOptions'. It is not a valid JSON object",
						"/api/tokens", HttpStatus.BAD_REQUEST);
			}
		}

		OpenViduRole role;
		try {
			if (roleString != null) {
				role = OpenViduRole.valueOf(roleString);
			} else {
				role = OpenViduRole.PUBLISHER;
			}
		} catch (IllegalArgumentException e) {
			return this.generateErrorResponse("Parameter role " + params.get("role") + " is not defined", "/api/tokens",
					HttpStatus.BAD_REQUEST);
		}

		KurentoTokenOptions kurentoTokenOptions = null;
		if (kurentoOptions != null) {
			try {
				kurentoTokenOptions = new KurentoTokenOptions(kurentoOptions);
			} catch (Exception e) {
				return this.generateErrorResponse("Type error in some parameter of 'kurentoOptions'", "/api/tokens",
						HttpStatus.BAD_REQUEST);
			}
		}

		metadata = (metadata != null) ? metadata : "";

		// While closing a session tokens can't be generated
		if (session.closingLock.readLock().tryLock()) {
			try {
				String token = sessionManager.newToken(session, role, metadata, kurentoTokenOptions);

				JsonObject responseJson = new JsonObject();
				responseJson.addProperty("id", token);
				responseJson.addProperty("session", sessionId);
				responseJson.addProperty("role", role.toString());
				responseJson.addProperty("data", metadata);
				responseJson.addProperty("token", token);

				if (kurentoOptions != null) {
					JsonObject kurentoOptsResponse = new JsonObject();
					if (kurentoTokenOptions.getVideoMaxRecvBandwidth() != null) {
						kurentoOptsResponse.addProperty("videoMaxRecvBandwidth",
								kurentoTokenOptions.getVideoMaxRecvBandwidth());
					}
					if (kurentoTokenOptions.getVideoMinRecvBandwidth() != null) {
						kurentoOptsResponse.addProperty("videoMinRecvBandwidth",
								kurentoTokenOptions.getVideoMinRecvBandwidth());
					}
					if (kurentoTokenOptions.getVideoMaxSendBandwidth() != null) {
						kurentoOptsResponse.addProperty("videoMaxSendBandwidth",
								kurentoTokenOptions.getVideoMaxSendBandwidth());
					}
					if (kurentoTokenOptions.getVideoMinSendBandwidth() != null) {
						kurentoOptsResponse.addProperty("videoMinSendBandwidth",
								kurentoTokenOptions.getVideoMinSendBandwidth());
					}
					if (kurentoTokenOptions.getAllowedFilters().length > 0) {
						JsonArray filters = new JsonArray();
						for (String filter : kurentoTokenOptions.getAllowedFilters()) {
							filters.add(filter);
						}
						kurentoOptsResponse.add("allowedFilters", filters);
					}
					responseJson.add("kurentoOptions", kurentoOptsResponse);
				}
				return new ResponseEntity<>(responseJson.toString(), getResponseHeaders(), HttpStatus.OK);
			} finally {
				session.closingLock.readLock().unlock();
			}
		} else {
			log.error("Session {} is in the process of closing. Token couldn't be generated", sessionId);
			return this.generateErrorResponse("Session " + sessionId + " not found", "/api/tokens",
					HttpStatus.NOT_FOUND);
		}
	}

	@RequestMapping(value = "/recordings/start", method = RequestMethod.POST)
	public ResponseEntity<?> startRecordingSession(@RequestBody Map<?, ?> params) {

		if (params == null) {
			return this.generateErrorResponse("Error in body parameters. Cannot be empty", "/api/recordings/start",
					HttpStatus.BAD_REQUEST);
		}

		log.info("REST API: POST /api/recordings/start {}", params.toString());

		if (!this.openviduConfig.isRecordingModuleEnabled()) {
			// OpenVidu Server configuration property "openvidu.recording" is set to false
			return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
		}

		String sessionId;
		String name;
		String outputModeString;
		String resolution;
		Boolean hasAudio;
		Boolean hasVideo;
		String recordingLayoutString;
		String customLayout;
		try {
			sessionId = (String) params.get("session");
			name = (String) params.get("name");
			outputModeString = (String) params.get("outputMode");
			resolution = (String) params.get("resolution");
			hasAudio = (Boolean) params.get("hasAudio");
			hasVideo = (Boolean) params.get("hasVideo");
			recordingLayoutString = (String) params.get("recordingLayout");
			customLayout = (String) params.get("customLayout");
		} catch (ClassCastException e) {
			return this.generateErrorResponse("Type error in some parameter", "/api/recordings/start",
					HttpStatus.BAD_REQUEST);
		}

		if (sessionId == null) {
			// "session" parameter not found
			return this.generateErrorResponse("\"session\" parameter is mandatory", "/api/recordings/start",
					HttpStatus.BAD_REQUEST);
		}

		io.openvidu.java.client.Recording.OutputMode finalOutputMode = null;
		RecordingLayout recordingLayout = null;
		if (outputModeString != null && !outputModeString.isEmpty()) {
			try {
				finalOutputMode = io.openvidu.java.client.Recording.OutputMode.valueOf(outputModeString);
			} catch (Exception e) {
				return this.generateErrorResponse("Type error in some parameter", "/api/recordings/start",
						HttpStatus.BAD_REQUEST);
			}
		}
		if (io.openvidu.java.client.Recording.OutputMode.COMPOSED.equals(finalOutputMode)) {
			if (resolution != null && !sessionManager.formatChecker.isAcceptableRecordingResolution(resolution)) {
				return this.generateErrorResponse(
						"Wrong \"resolution\" parameter. Acceptable values from 100 to 1999 for both width and height",
						"/api/recordings/start", HttpStatus.UNPROCESSABLE_ENTITY);
			}
			if (recordingLayoutString != null && !recordingLayoutString.isEmpty()) {
				try {
					recordingLayout = RecordingLayout.valueOf(recordingLayoutString);
				} catch (Exception e) {
					return this.generateErrorResponse("Type error in some parameter", "/api/recordings/start",
							HttpStatus.BAD_REQUEST);
				}
			}
		}
		if ((hasAudio != null && hasVideo != null) && !hasAudio && !hasVideo) {
			// Cannot start a recording with both "hasAudio" and "hasVideo" to false
			return this.generateErrorResponse(
					"Cannot start a recording with both \"hasAudio\" and \"hasVideo\" set to false",
					"/api/recordings/start", HttpStatus.UNPROCESSABLE_ENTITY);
		}

		Session session = sessionManager.getSession(sessionId);
		if (session == null) {
			session = sessionManager.getSessionNotActive(sessionId);
			if (session != null) {
				if (!(MediaMode.ROUTED.equals(session.getSessionProperties().mediaMode()))
						|| this.recordingManager.sessionIsBeingRecorded(session.getSessionId())) {
					// Session is not in ROUTED MediMode or it is already being recorded
					return new ResponseEntity<>(HttpStatus.CONFLICT);
				} else {
					// Session is not active (no connected participants)
					return new ResponseEntity<>(HttpStatus.NOT_ACCEPTABLE);
				}
			}
			// Session does not exist
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}
		if (!(MediaMode.ROUTED.equals(session.getSessionProperties().mediaMode()))
				|| this.recordingManager.sessionIsBeingRecorded(session.getSessionId())) {
			// Session is not in ROUTED MediMode or it is already being recorded
			return new ResponseEntity<>(HttpStatus.CONFLICT);
		}
		if (session.getParticipants().isEmpty()) {
			// Session is not active (no connected participants)
			return new ResponseEntity<>(HttpStatus.NOT_ACCEPTABLE);
		}

		RecordingProperties.Builder builder = new RecordingProperties.Builder();
		builder.outputMode(
				finalOutputMode == null ? session.getSessionProperties().defaultOutputMode() : finalOutputMode);
		if (io.openvidu.java.client.Recording.OutputMode.COMPOSED.equals(finalOutputMode)) {
			if (resolution != null) {
				builder.resolution(resolution);
			}
			builder.recordingLayout(recordingLayout == null ? session.getSessionProperties().defaultRecordingLayout()
					: recordingLayout);
			if (RecordingLayout.CUSTOM.equals(recordingLayout)) {
				builder.customLayout(
						customLayout == null ? session.getSessionProperties().defaultCustomLayout() : customLayout);
			}
		}
		builder.name(name).hasAudio(hasAudio != null ? hasAudio : true).hasVideo(hasVideo != null ? hasVideo : true);

		try {
			Recording startedRecording = this.recordingManager.startRecording(session, builder.build());
			return new ResponseEntity<>(startedRecording.toJson().toString(), getResponseHeaders(), HttpStatus.OK);
		} catch (OpenViduException e) {
			return new ResponseEntity<>("Error starting recording: " + e.getMessage(), getResponseHeaders(),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@RequestMapping(value = "/recordings/stop/{recordingId}", method = RequestMethod.POST)
	public ResponseEntity<?> stopRecordingSession(@PathVariable("recordingId") String recordingId) {

		log.info("REST API: POST /api/recordings/stop/{}", recordingId);

		Recording recording = recordingManager.getStartedRecording(recordingId);

		if (recording == null) {
			if (recordingManager.getStartingRecording(recordingId) != null) {
				// Recording is still starting
				return new ResponseEntity<>(HttpStatus.NOT_ACCEPTABLE);
			}
			// Recording does not exist
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}
		if (!this.recordingManager.sessionIsBeingRecorded(recording.getSessionId())) {
			// Session is not being recorded
			return new ResponseEntity<>(HttpStatus.CONFLICT);
		}

		Session session = sessionManager.getSession(recording.getSessionId());

		Recording stoppedRecording = this.recordingManager.stopRecording(session, recording.getId(),
				EndReason.recordingStoppedByServer);

		session.recordingManuallyStopped.set(true);

		if (session != null && !session.isClosed() && OutputMode.COMPOSED.equals(recording.getOutputMode())
				&& recording.hasVideo()) {
			sessionManager.evictParticipant(
					session.getParticipantByPublicId(ProtocolElements.RECORDER_PARTICIPANT_PUBLICID), null, null, null);
		}

		return new ResponseEntity<>(stoppedRecording.toJson().toString(), getResponseHeaders(), HttpStatus.OK);
	}

	@RequestMapping(value = "/recordings/{recordingId}", method = RequestMethod.GET)
	public ResponseEntity<?> getRecording(@PathVariable("recordingId") String recordingId) {

		log.info("REST API: GET /api/recordings/{}", recordingId);

		try {
			Recording recording = this.recordingManager.getRecording(recordingId);
			if (io.openvidu.java.client.Recording.Status.started.equals(recording.getStatus())
					&& recordingManager.getStartingRecording(recording.getId()) != null) {
				recording.setStatus(io.openvidu.java.client.Recording.Status.starting);
			}
			return new ResponseEntity<>(recording.toJson().toString(), getResponseHeaders(), HttpStatus.OK);
		} catch (Exception e) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}
	}

	@RequestMapping(value = "/recordings", method = RequestMethod.GET)
	public ResponseEntity<?> getAllRecordings() {

		log.info("REST API: GET /api/recordings");

		Collection<Recording> recordings = this.recordingManager.getAllRecordings();
		JsonObject json = new JsonObject();
		JsonArray jsonArray = new JsonArray();
		recordings.forEach(rec -> {
			if (io.openvidu.java.client.Recording.Status.started.equals(rec.getStatus())
					&& recordingManager.getStartingRecording(rec.getId()) != null) {
				rec.setStatus(io.openvidu.java.client.Recording.Status.starting);
			}
			jsonArray.add(rec.toJson());
		});
		json.addProperty("count", recordings.size());
		json.add("items", jsonArray);
		return new ResponseEntity<>(json.toString(), getResponseHeaders(), HttpStatus.OK);
	}

	@RequestMapping(value = "/recordings/{recordingId}", method = RequestMethod.DELETE)
	public ResponseEntity<?> deleteRecording(@PathVariable("recordingId") String recordingId) {

		log.info("REST API: DELETE /api/recordings/{}", recordingId);

		return new ResponseEntity<>(this.recordingManager.deleteRecordingFromHost(recordingId, false));
	}

	@RequestMapping(value = "/signal", method = RequestMethod.POST)
	public ResponseEntity<?> signal(@RequestBody Map<?, ?> params) {

		if (params == null) {
			return this.generateErrorResponse("Error in body parameters. Cannot be empty", "/api/signal",
					HttpStatus.BAD_REQUEST);
		}

		log.info("REST API: POST /api/signal {}", params.toString());

		String sessionId;
		String type;
		ArrayList<String> to;
		String data;
		try {
			sessionId = (String) params.get("session");
			to = (ArrayList<String>) params.get("to");
			type = (String) params.get("type");
			data = (String) params.get("data");
		} catch (ClassCastException e) {
			return this.generateErrorResponse("Type error in some parameter", "/api/signal", HttpStatus.BAD_REQUEST);
		}

		JsonObject completeMessage = new JsonObject();

		if (sessionId == null) {
			// "session" parameter not found
			return this.generateErrorResponse("\"session\" parameter is mandatory", "/api/signal",
					HttpStatus.BAD_REQUEST);
		}
		Session session = sessionManager.getSession(sessionId);
		if (session == null) {
			session = sessionManager.getSessionNotActive(sessionId);
			if (session != null) {
				// Session is not active (no connected participants)
				return new ResponseEntity<>(HttpStatus.NOT_ACCEPTABLE);
			}
			// Session does not exist
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		if (type != null) {
			completeMessage.addProperty("type", type);
		}

		if (data != null) {
			completeMessage.addProperty("data", data);
		}

		if (to != null) {
			try {
				Gson gson = new GsonBuilder().create();
				JsonArray toArray = gson.toJsonTree(to).getAsJsonArray();
				completeMessage.add("to", toArray);
			} catch (IllegalStateException exception) {
				return this.generateErrorResponse("\"to\" parameter is not a valid JSON array", "/api/signal",
						HttpStatus.BAD_REQUEST);
			}
		}

		try {
			sessionManager.sendMessage(completeMessage.toString(), sessionId);
		} catch (OpenViduException e) {
			return this.generateErrorResponse("\"to\" array has no valid connection identifiers", "/api/signal",
					HttpStatus.NOT_ACCEPTABLE);
		}

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@RequestMapping(value = "/sessions/{sessionId}/connection", method = RequestMethod.POST)
	public ResponseEntity<?> publishIpcam(@PathVariable("sessionId") String sessionId, @RequestBody Map<?, ?> params) {

		if (params == null) {
			return this.generateErrorResponse("Error in body parameters. Cannot be empty",
					"/api/sessions/" + sessionId + "/connection", HttpStatus.BAD_REQUEST);
		}

		log.info("REST API: POST /api/sessions/{}/connection {}", sessionId, params.toString());

		Session session = this.sessionManager.getSessionWithNotActive(sessionId);
		if (session == null) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		String type;
		String rtspUri;
		Boolean adaptativeBitrate;
		Boolean onlyPlayWithSubscribers;
		String data;
		try {
			type = (String) params.get("type");
			rtspUri = (String) params.get("rtspUri");
			adaptativeBitrate = (Boolean) params.get("adaptativeBitrate");
			onlyPlayWithSubscribers = (Boolean) params.get("onlyPlayWithSubscribers");
			data = (String) params.get("data");
		} catch (ClassCastException e) {
			return this.generateErrorResponse("Type error in some parameter",
					"/api/sessions/" + sessionId + "/connection", HttpStatus.BAD_REQUEST);
		}
		if (rtspUri == null) {
			return this.generateErrorResponse("\"rtspUri\" parameter is mandatory",
					"/api/sessions/" + sessionId + "/connection", HttpStatus.BAD_REQUEST);
		}

		type = "IPCAM"; // Other possible values in the future
		adaptativeBitrate = adaptativeBitrate != null ? adaptativeBitrate : true;
		onlyPlayWithSubscribers = onlyPlayWithSubscribers != null ? onlyPlayWithSubscribers : true;
		data = data != null ? data : "";

		boolean hasAudio = true;
		boolean hasVideo = true;
		boolean audioActive = true;
		boolean videoActive = true;
		String typeOfVideo = type;
		Integer frameRate = null;
		String videoDimensions = null;
		KurentoMediaOptions mediaOptions = new KurentoMediaOptions(true, null, hasAudio, hasVideo, audioActive,
				videoActive, typeOfVideo, frameRate, videoDimensions, null, false, rtspUri, adaptativeBitrate,
				onlyPlayWithSubscribers);

		// While closing a session IP cameras can't be published
		if (session.closingLock.readLock().tryLock()) {
			try {
				if (session.isClosed()) {
					return new ResponseEntity<>(HttpStatus.NOT_FOUND);
				}
				Participant ipcamParticipant = this.sessionManager.publishIpcam(session, mediaOptions, data);
				return new ResponseEntity<>(ipcamParticipant.toJson().toString(), getResponseHeaders(), HttpStatus.OK);
			} catch (MalformedURLException e) {
				return this.generateErrorResponse("\"rtspUri\" parameter is not a valid rtsp uri",
						"/api/sessions/" + sessionId + "/connection", HttpStatus.BAD_REQUEST);
			} catch (Exception e) {
				return this.generateErrorResponse(e.getMessage(), "/api/sessions/" + sessionId + "/connection",
						HttpStatus.INTERNAL_SERVER_ERROR);
			} finally {
				session.closingLock.readLock().unlock();
			}
		} else {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}
	}

	private ResponseEntity<String> generateErrorResponse(String errorMessage, String path, HttpStatus status) {
		JsonObject responseJson = new JsonObject();
		responseJson.addProperty("timestamp", System.currentTimeMillis());
		responseJson.addProperty("status", status.value());
		responseJson.addProperty("error", status.getReasonPhrase());
		responseJson.addProperty("message", errorMessage);
		responseJson.addProperty("path", path);
		return new ResponseEntity<>(responseJson.toString(), getResponseHeaders(), status);
	}

	private HttpHeaders getResponseHeaders() {
		HttpHeaders responseHeaders = new HttpHeaders();
		responseHeaders.setContentType(MediaType.APPLICATION_JSON);
		return responseHeaders;
	}
}
