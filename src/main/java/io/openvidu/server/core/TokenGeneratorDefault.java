/*
 * (C) Copyright 2017-2020 OpenVidu (https://openvidu.io)
 * (C) Modifications Copyright 2020 imicloud/MaXiaoMing (http://imicloud.net)
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

package io.openvidu.server.core;

import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import io.openvidu.java.client.OpenViduRole;
import io.openvidu.server.coturn.CoturnCredentialsService;
import io.openvidu.server.coturn.TurnCredentials;
import io.openvidu.server.kurento.core.KurentoTokenOptions;
import net.imicloud.VmeetingServer.OpenviduConfig;

public class TokenGeneratorDefault implements TokenGenerator {

	@Autowired
	private CoturnCredentialsService coturnCredentialsService;

	@Autowired
	protected OpenviduConfig openviduConfig;

	@Override
	public Token generateToken(String sessionId, OpenViduRole role, String serverMetadata,
			KurentoTokenOptions kurentoTokenOptions) {
		String token = openviduConfig.wsUrl;
		token += "?sessionId=" + sessionId;
		token += "&token=" + IdentifierPrefixes.TOKEN_ID + RandomStringUtils.randomAlphabetic(1).toUpperCase()
				+ RandomStringUtils.randomAlphanumeric(15);
		token += "&role=" + role.name();
		token += "&version=" + openviduConfig.getOpenViduServerVersion();
		TurnCredentials turnCredentials = null;
		if (this.coturnCredentialsService.isCoturnAvailable()) {
			turnCredentials = coturnCredentialsService.createUser();
			if (turnCredentials != null) {
				token += "&coturnIp=" + openviduConfig.getCoturnIp();
				token += "&turnUsername=" + turnCredentials.getUsername();
				token += "&turnCredential=" + turnCredentials.getCredential();
			}
		}
		return new Token(token, role, serverMetadata, turnCredentials, kurentoTokenOptions);
	}
}
