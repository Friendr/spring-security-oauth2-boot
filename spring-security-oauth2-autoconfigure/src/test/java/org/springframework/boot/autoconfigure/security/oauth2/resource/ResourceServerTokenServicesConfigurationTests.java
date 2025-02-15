/*
 * Copyright 2012-2019 the original author or authors.
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

package org.springframework.boot.autoconfigure.security.oauth2.resource;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.OAuth2ClientProperties;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2RestOperationsConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.boot.web.servlet.server.MockServletWebServerFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.OAuth2RestTemplate;
import org.springframework.security.oauth2.client.token.grant.code.AuthorizationCodeResourceDetails;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.common.exceptions.InvalidTokenException;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.token.DefaultTokenServices;
import org.springframework.security.oauth2.provider.token.RemoteTokenServices;
import org.springframework.security.oauth2.provider.token.ResourceServerTokenServices;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.security.oauth2.provider.token.store.JwtAccessTokenConverter;
import org.springframework.security.oauth2.provider.token.store.JwtTokenStore;
import org.springframework.security.oauth2.provider.token.store.jwk.JwkTokenStore;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Tests for {@link ResourceServerTokenServicesConfiguration}.
 *
 * @author Dave Syer
 * @author Madhura Bhave
 * @author Eddú Meléndez
 */
public class ResourceServerTokenServicesConfigurationTests {

	private static String PUBLIC_KEY = "-----BEGIN PUBLIC KEY-----\n"
			+ "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAnGp/Q5lh0P8nPL21oMMrt2RrkT9"
			+ "AW5jgYwLfSUnJVc9G6uR3cXRRDCjHqWU5WYwivcF180A6CWp/ireQFFBNowgc5XaA0kPpzE"
			+ "tgsA5YsNX7iSnUibB004iBTfU9hZ2Rbsc8cWqynT0RyN4TP1RYVSeVKvMQk4GT1r7JCEC+T"
			+ "Nu1ELmbNwMQyzKjsfBXyIOCFU/E94ktvsTZUHF4Oq44DBylCDsS1k7/sfZC2G5EU7Oz0mhG"
			+ "8+Uz6MSEQHtoIi6mc8u64Rwi3Z3tscuWG2ShtsUFuNSAFNkY7LkLn+/hxLCu2bNISMaESa8"
			+ "dG22CIMuIeRLVcAmEWEWH5EEforTg+QIDAQAB\n-----END PUBLIC KEY-----";

	private ConfigurableApplicationContext context;

	private ConfigurableEnvironment environment = new StandardEnvironment();

	@Rule
	public ExpectedException thrown = ExpectedException.none();

	@After
	public void close() {
		if (this.context != null) {
			this.context.close();
		}
	}

	@Test
	public void useRemoteTokenServices() {
		TestPropertyValues.of("security.oauth2.resource.tokenInfoUri:https://example.com").applyTo(this.environment);
		this.context = new SpringApplicationBuilder(ResourceConfiguration.class).environment(this.environment)
				.web(WebApplicationType.NONE).run();
		RemoteTokenServices services = this.context.getBean(RemoteTokenServices.class);
		assertThat(services).isNotNull();
	}

	@Test
	public void overrideRemoteTokenServices() {
		TestPropertyValues.of("security.oauth2.resource.tokenInfoUri:https://example.com").applyTo(this.environment);
		this.context = new SpringApplicationBuilder(CustomRemoteTokenService.class, ResourceConfiguration.class)
				.environment(this.environment).web(WebApplicationType.NONE).run();
		CustomRemoteTokenService services = this.context.getBean(CustomRemoteTokenService.class);
		assertThat(services).isNotNull();
		this.thrown.expect(NoSuchBeanDefinitionException.class);
		this.context.getBean(RemoteTokenServices.class);
	}

	@Test
	public void switchToUserInfo() {
		TestPropertyValues.of("security.oauth2.resource.userInfoUri:https://example.com").applyTo(this.environment);
		this.context = new SpringApplicationBuilder(ResourceConfiguration.class).environment(this.environment)
				.web(WebApplicationType.NONE).run();
		UserInfoTokenServices services = this.context.getBean(UserInfoTokenServices.class);
		assertThat(services).isNotNull();
	}

	@Test
	public void userInfoWithAuthorities() {
		TestPropertyValues.of("security.oauth2.resource.userInfoUri:https://example.com").applyTo(this.environment);
		this.context = new SpringApplicationBuilder(AuthoritiesConfiguration.class).environment(this.environment)
				.web(WebApplicationType.NONE).run();
		UserInfoTokenServices services = this.context.getBean(UserInfoTokenServices.class);
		assertThat(services).isNotNull();
		assertThat(services).extracting("authoritiesExtractor")
				.isSameAs(this.context.getBean(AuthoritiesExtractor.class));
	}

	@Test
	public void userInfoWithPrincipal() {
		TestPropertyValues.of("security.oauth2.resource.userInfoUri:https://example.com").applyTo(this.environment);
		this.context = new SpringApplicationBuilder(PrincipalConfiguration.class).environment(this.environment)
				.web(WebApplicationType.NONE).run();
		UserInfoTokenServices services = this.context.getBean(UserInfoTokenServices.class);
		assertThat(services).isNotNull();
		assertThat(services).extracting("principalExtractor").isSameAs(this.context.getBean(PrincipalExtractor.class));
	}

	@Test
	public void userInfoWithClient() {
		TestPropertyValues.of("security.oauth2.client.client-id=acme",
				"security.oauth2.resource.userInfoUri:https://example.com", "server.port=-1", "debug=true")
				.applyTo(this.environment);
		this.context = new SpringApplicationBuilder(ResourceNoClientConfiguration.class).environment(this.environment)
				.web(WebApplicationType.SERVLET).run();
		BeanDefinition bean = ((BeanDefinitionRegistry) this.context)
				.getBeanDefinition("scopedTarget.oauth2ClientContext");
		assertThat(bean.getScope()).isEqualTo("request");
	}

	@Test
	public void preferUserInfo() {
		TestPropertyValues.of("security.oauth2.resource.userInfoUri:https://example.com",
				"security.oauth2.resource.tokenInfoUri:https://example.com",
				"security.oauth2.resource.preferTokenInfo:false").applyTo(this.environment);
		this.context = new SpringApplicationBuilder(ResourceConfiguration.class).environment(this.environment)
				.web(WebApplicationType.NONE).run();
		UserInfoTokenServices services = this.context.getBean(UserInfoTokenServices.class);
		assertThat(services).isNotNull();
	}

	@Test
	public void userInfoWithCustomizer() {
		TestPropertyValues.of("security.oauth2.resource.userInfoUri:https://example.com",
				"security.oauth2.resource.tokenInfoUri:https://example.com",
				"security.oauth2.resource.preferTokenInfo:false").applyTo(this.environment);
		this.context = new SpringApplicationBuilder(ResourceConfiguration.class, Customizer.class)
				.environment(this.environment).web(WebApplicationType.NONE).run();
		UserInfoTokenServices services = this.context.getBean(UserInfoTokenServices.class);
		assertThat(services).isNotNull();
	}

	@Test
	public void switchToJwt() {
		TestPropertyValues.of("security.oauth2.resource.jwt.keyValue=FOOBAR").applyTo(this.environment);
		this.context = new SpringApplicationBuilder(ResourceConfiguration.class).environment(this.environment)
				.web(WebApplicationType.NONE).run();
		DefaultTokenServices services = this.context.getBean(DefaultTokenServices.class);
		assertThat(services).isNotNull();
		this.thrown.expect(NoSuchBeanDefinitionException.class);
		this.context.getBean(RemoteTokenServices.class);
	}

	@Test
	public void asymmetricJwt() {
		TestPropertyValues.of("security.oauth2.resource.jwt.keyValue=" + PUBLIC_KEY).applyTo(this.environment);
		this.context = new SpringApplicationBuilder(ResourceConfiguration.class).environment(this.environment)
				.web(WebApplicationType.NONE).run();
		DefaultTokenServices services = this.context.getBean(DefaultTokenServices.class);
		assertThat(services).isNotNull();
	}

	@Test
	public void jwkConfiguration() throws Exception {
		TestPropertyValues.of("security.oauth2.resource.jwk.key-set-uri=https://idp.example.com/token_keys")
				.applyTo(this.environment);
		this.context = new SpringApplicationBuilder(ResourceConfiguration.class).environment(this.environment)
				.web(WebApplicationType.NONE).run();
		DefaultTokenServices services = this.context.getBean(DefaultTokenServices.class);
		assertThat(services).isNotNull();
		this.thrown.expect(NoSuchBeanDefinitionException.class);
		this.context.getBean(RemoteTokenServices.class);
	}

	@Test
	public void customUserInfoRestTemplateFactory() {
		TestPropertyValues.of("security.oauth2.resource.userInfoUri:https://example.com").applyTo(this.environment);
		this.context = new SpringApplicationBuilder(CustomUserInfoRestTemplateFactory.class,
				ResourceConfiguration.class).environment(this.environment).web(WebApplicationType.NONE).run();
		assertThat(this.context.getBeansOfType(UserInfoRestTemplateFactory.class)).hasSize(1);
		assertThat(this.context.getBean(UserInfoRestTemplateFactory.class))
				.isInstanceOf(CustomUserInfoRestTemplateFactory.class);
	}

	@Test
	public void jwtAccessTokenConverterIsConfiguredWhenKeyUriIsProvided() {
		TestPropertyValues.of("security.oauth2.resource.jwt.key-uri=http://localhost:12345/banana")
				.applyTo(this.environment);
		this.context = new SpringApplicationBuilder(ResourceConfiguration.class,
				JwtAccessTokenConverterRestTemplateCustomizerConfiguration.class).environment(this.environment)
						.web(WebApplicationType.NONE).run();
		assertThat(this.context.getBeansOfType(JwtAccessTokenConverter.class)).hasSize(1);
	}

	@Test
	public void jwkTokenStoreShouldBeConditionalOnMissingBean() throws Exception {
		TestPropertyValues.of("security.oauth2.resource.jwk.key-set-uri=https://idp.example.com/token_keys")
				.applyTo(this.environment);
		this.context = new SpringApplicationBuilder(JwkTokenStoreConfiguration.class, ResourceConfiguration.class)
				.environment(this.environment).web(WebApplicationType.NONE).run();
		assertThat(this.context.getBeansOfType(JwkTokenStore.class)).hasSize(1);
	}

	@Test
	public void jwtTokenStoreShouldBeConditionalOnMissingBean() throws Exception {
		TestPropertyValues.of("security.oauth2.resource.jwt.keyValue=" + PUBLIC_KEY).applyTo(this.environment);
		this.context = new SpringApplicationBuilder(JwtTokenStoreConfiguration.class, ResourceConfiguration.class)
				.environment(this.environment).web(WebApplicationType.NONE).run();
		assertThat(this.context.getBeansOfType(JwtTokenStore.class)).hasSize(1);
	}

	@Test
	public void jwtAccessTokenConverterForKeyValueShouldBeConditionalOnMissingBean() throws Exception {
		TestPropertyValues.of("security.oauth2.resource.jwt.keyValue=" + PUBLIC_KEY).applyTo(this.environment);
		this.context = new SpringApplicationBuilder(JwtTokenStoreConfiguration.class, ResourceConfiguration.class)
				.environment(this.environment).web(WebApplicationType.NONE).run();
		assertThat(this.context.getBeansOfType(JwtAccessTokenConverter.class)).hasSize(1);
	}

	@Test
	public void jwtAccessTokenConverterForKeyStoreShouldBeConditionalOnMissingBean() throws Exception {
		TestPropertyValues.of(
				"security.oauth2.resource.jwt.key-store=classpath:"
						+ "org/springframework/boot/autoconfigure/security/oauth2/resource/keystore.jks",
				"security.oauth2.resource.jwt.key-store-password=changeme",
				"security.oauth2.resource.jwt.key-alias=jwt").applyTo(this.environment);
		this.context = new SpringApplicationBuilder(JwtTokenStoreConfiguration.class, ResourceConfiguration.class)
				.environment(this.environment).web(WebApplicationType.NONE).run();
		assertThat(this.context.getBeansOfType(JwtAccessTokenConverter.class)).hasSize(1);
	}

	@Test
	public void configureWhenKeyStoreIsProvidedThenExposesJwtTokenStore() {
		TestPropertyValues.of(
				"security.oauth2.resource.jwt.key-store=classpath:"
						+ "org/springframework/boot/autoconfigure/security/oauth2/resource/keystore.jks",
				"security.oauth2.resource.jwt.key-store-password=changeme",
				"security.oauth2.resource.jwt.key-alias=jwt").applyTo(this.environment);
		this.context = new SpringApplicationBuilder(ResourceConfiguration.class).environment(this.environment)
				.web(WebApplicationType.NONE).run();
		assertThat(this.context.getBeansOfType(TokenStore.class)).hasSize(1);
		assertThat(this.context.getBean(TokenStore.class)).isInstanceOf(JwtTokenStore.class);
	}

	@Test
	public void configureWhenKeyStoreIsProvidedThenExposesJwtAccessTokenConverter() {
		TestPropertyValues.of(
				"security.oauth2.resource.jwt.key-store=classpath:"
						+ "org/springframework/boot/autoconfigure/security/oauth2/resource/keystore.jks",
				"security.oauth2.resource.jwt.key-store-password=changeme",
				"security.oauth2.resource.jwt.key-alias=jwt").applyTo(this.environment);
		this.context = new SpringApplicationBuilder(ResourceConfiguration.class).environment(this.environment)
				.web(WebApplicationType.NONE).run();
		assertThat(this.context.getBeansOfType(JwtAccessTokenConverter.class)).hasSize(1);
	}

	@Test
	public void configureWhenKeyStoreIsProvidedWithKeyPasswordThenExposesJwtAccessTokenConverter() {
		TestPropertyValues
				.of("security.oauth2.resource.jwt.key-store=classpath:"
						+ "org/springframework/boot/autoconfigure/security/oauth2/resource/keyhaspassword.jks",
						"security.oauth2.resource.jwt.key-store-password=changeme",
						"security.oauth2.resource.jwt.key-alias=jwt",
						"security.oauth2.resource.jwt.key-password=password")
				.applyTo(this.environment);
		this.context = new SpringApplicationBuilder(ResourceConfiguration.class).environment(this.environment)
				.web(WebApplicationType.NONE).run();
		assertThat(this.context.getBeansOfType(JwtAccessTokenConverter.class)).hasSize(1);
	}

	@Test
	public void configureWhenKeyStoreIsProvidedButNoAliasThenThrowsException() {
		TestPropertyValues.of(
				"security.oauth2.resource.jwt.key-store=classpath:"
						+ "org/springframework/boot/autoconfigure/security/oauth2/resource/keystore.jks",
				"security.oauth2.resource.jwt.key-store-password=changeme").applyTo(this.environment);

		assertThatCode(() -> new SpringApplicationBuilder(ResourceConfiguration.class).environment(this.environment)
				.web(WebApplicationType.NONE).run()).isInstanceOf(UnsatisfiedDependencyException.class);
	}

	@Test
	public void configureWhenKeyStoreIsProvidedButNoPasswordThenThrowsException() {
		TestPropertyValues.of(
				"security.oauth2.resource.jwt.key-store=classpath:"
						+ "org/springframework/boot/autoconfigure/security/oauth2/resource/keystore.jks",
				"security.oauth2.resource.jwt.key-alias=jwt").applyTo(this.environment);

		assertThatCode(() -> new SpringApplicationBuilder(ResourceConfiguration.class).environment(this.environment)
				.web(WebApplicationType.NONE).run());
	}

	@Configuration
	@Import({ ResourceServerTokenServicesConfiguration.class, ResourceServerPropertiesConfiguration.class,
			PropertyPlaceholderAutoConfiguration.class })
	@EnableConfigurationProperties(OAuth2ClientProperties.class)
	protected static class ResourceConfiguration {

	}

	@Configuration
	protected static class AuthoritiesConfiguration extends ResourceConfiguration {

		@Bean
		AuthoritiesExtractor authoritiesExtractor() {
			return (map) -> AuthorityUtils.commaSeparatedStringToAuthorityList("ROLE_ADMIN");
		}

	}

	@Configuration
	protected static class PrincipalConfiguration extends ResourceConfiguration {

		@Bean
		PrincipalExtractor principalExtractor() {
			return (map) -> "boot";
		}

	}

	@Import({ OAuth2RestOperationsConfiguration.class })
	protected static class ResourceNoClientConfiguration extends ResourceConfiguration {

		@Bean
		public MockServletWebServerFactory webServerFactory() {
			return new MockServletWebServerFactory();
		}

	}

	@Configuration
	protected static class ResourceServerPropertiesConfiguration {

		private OAuth2ClientProperties credentials;

		public ResourceServerPropertiesConfiguration(OAuth2ClientProperties credentials) {
			this.credentials = credentials;
		}

		@Bean
		public ResourceServerProperties resourceServerProperties() {
			return new ResourceServerProperties(this.credentials.getClientId(), this.credentials.getClientSecret());
		}

	}

	@Component
	protected static class Customizer implements UserInfoRestTemplateCustomizer {

		@Override
		public void customize(OAuth2RestTemplate template) {
			template.getInterceptors().add((request, body, execution) -> execution.execute(request, body));
		}

	}

	@Component
	protected static class CustomUserInfoRestTemplateFactory implements UserInfoRestTemplateFactory {

		private final OAuth2RestTemplate restTemplate = new OAuth2RestTemplate(new AuthorizationCodeResourceDetails());

		@Override
		public OAuth2RestTemplate getUserInfoRestTemplate() {
			return this.restTemplate;
		}

	}

	@Component
	protected static class CustomRemoteTokenService implements ResourceServerTokenServices {

		@Override
		public OAuth2Authentication loadAuthentication(String accessToken)
				throws AuthenticationException, InvalidTokenException {
			return null;
		}

		@Override
		public OAuth2AccessToken readAccessToken(String accessToken) {
			return null;
		}

	}

	@Configuration
	static class JwtAccessTokenConverterRestTemplateCustomizerConfiguration {

		@Bean
		public JwtAccessTokenConverterRestTemplateCustomizer restTemplateCustomizer() {
			return new MockRestCallCustomizer();
		}

	}

	@Configuration
	static class JwtTokenStoreConfiguration {

		@Bean
		public TokenStore tokenStore(JwtAccessTokenConverter jwtTokenEnhancer) {
			return new JwtTokenStore(jwtTokenEnhancer);
		}

	}

	@Configuration
	static class JwkTokenStoreConfiguration {

		@Bean
		public TokenStore tokenStore() {
			return new JwkTokenStore("https://idp.example.com/token_keys");
		}

	}

	private static class MockRestCallCustomizer implements JwtAccessTokenConverterRestTemplateCustomizer {

		@Override
		public void customize(RestTemplate template) {
			template.getInterceptors().add((request, body, execution) -> {
				String payload = "{\"value\":\"FOO\"}";
				MockClientHttpResponse response = new MockClientHttpResponse(payload.getBytes(), HttpStatus.OK);
				response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
				return response;
			});
		}

	}

}
