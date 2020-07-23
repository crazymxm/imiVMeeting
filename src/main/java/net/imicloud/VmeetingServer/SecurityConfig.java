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

package net.imicloud.VmeetingServer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.annotation.web.configurers.ExpressionUrlAuthorizationConfigurer;
import org.springframework.stereotype.Component;

import io.openvidu.server.config.OpenviduConfig;

@Configuration
public class SecurityConfig extends WebSecurityConfigurerAdapter {

	@Autowired
	OpenviduConfig openviduConf;

	@Override
	protected void configure(HttpSecurity http) throws Exception {
		System.out.println(" --- SecurityConfig ----");
		
		
		
		// Security for API REST
		ExpressionUrlAuthorizationConfigurer<HttpSecurity>.ExpressionInterceptUrlRegistry conf = http.cors().and()
				.csrf().disable().authorizeRequests()
				.antMatchers(HttpMethod.GET, "/config/openvidu-publicurl").permitAll()
				.antMatchers(HttpMethod.GET, "/config/**").hasRole("ADMIN")
				.anyRequest().authenticated();

		// Security for recorded videos
		if (openviduConf.getOpenViduRecordingPublicAccess()) {
			conf = conf.antMatchers("/recordings/**").permitAll();
		} else {
			conf = conf.antMatchers("/recordings/**").authenticated();
		}

		conf.and().httpBasic();
	}

	@Autowired
	public void configureGlobal(AuthenticationManagerBuilder auth) throws Exception {
		//prefix {noop} to password in order for the DelegatingPasswordEncoder use the NoOpPasswordEncoder to validate these passwords
		auth.inMemoryAuthentication().withUser("OPENVIDUAPP").password("{noop}" + openviduConf.getOpenViduSecret())
				.roles("USER");
		auth.inMemoryAuthentication().withUser("VMEETINGADMIN").password("{noop}" + openviduConf.getOpenViduSecret() + "_ADM") //todo set adminpassword to config
				.roles("ADMIN");
	}

}