/*
 * (C) Copyright 2017-2020 OpenVidu (https://openvidu.io)
 * (C) Modifications Copyright 2020 imicloud/MaXiaoMing (http://imicloud.net)
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

package io.openvidu.server.config;

import java.util.Map;

import javax.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

public class HttpHandshakeInterceptor implements HandshakeInterceptor {

	private static final Logger log = LoggerFactory.getLogger(HttpHandshakeInterceptor.class);

	@Override
	public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
			Map<String, Object> attributes) throws Exception {
		
		System.out.println("------ beforeHandshake ------");
		/*解决The extension [x-webkit-deflate-frame] is not supported问题*/
        if (request.getHeaders().containsKey("Sec-WebSocket-Extensions")) {
            request.getHeaders().remove("Sec-WebSocket-Extensions");
        }
 
		if (request instanceof ServletServerHttpRequest) {
			HttpSession session = ((ServletServerHttpRequest) request).getServletRequest().getSession();
			session.setMaxInactiveInterval(1800); // HttpSession will expire in 30 minutes
			attributes.put("httpSession", session);
			log.info("{} HttpSession {}", session.isNew() ? "New" : "Old", session.getId());
		}
		return true;
	}

	@Override
	public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
			Exception ex) {
	}
}