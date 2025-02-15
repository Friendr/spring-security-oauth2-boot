/*
 * Copyright 2012-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.autoconfigure.security.oauth2.client;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.autoconfigure.security.oauth2.resource.ResourceServerTokenServicesConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableOAuth2Client;

/**
 * Enable OAuth2 Single Sign On (SSO). It is also required to manually configure
 * {@link org.springframework.security.config.annotation.web.builders.HttpSecurity
 * HttpSecurity} with {@link SsoSecurityConfigurer}. Example: <pre><code>
 *     new SsoSecurityConfigurer(applicationContext).configure(http);
 * </code></pre>
 *
 * @author Dave Syer
 * @since 1.3.0
 * @deprecated See the <a href=
 * "https://github.com/spring-projects/spring-security/wiki/OAuth-2.0-Migration-Guide">
 * OAuth 2.0 Migration Guide</a> for Spring Security 5.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@EnableOAuth2Client
@EnableConfigurationProperties(OAuth2SsoProperties.class)
@Import({ ResourceServerTokenServicesConfiguration.class })
@Deprecated
public @interface EnableOAuth2Sso {

}
