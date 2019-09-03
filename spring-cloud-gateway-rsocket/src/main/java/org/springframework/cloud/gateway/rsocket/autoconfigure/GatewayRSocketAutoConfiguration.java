/*
 * Copyright 2018-2019 the original author or authors.
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

package org.springframework.cloud.gateway.rsocket.autoconfigure;

import java.util.List;

import io.micrometer.core.instrument.MeterRegistry;
import io.rsocket.RSocket;

import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.rsocket.RSocketServerAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.rsocket.messaging.RSocketStrategiesCustomizer;
import org.springframework.boot.rsocket.server.RSocketServerBootstrap;
import org.springframework.boot.rsocket.server.RSocketServerFactory;
import org.springframework.cloud.gateway.rsocket.core.GatewayRSocket;
import org.springframework.cloud.gateway.rsocket.core.GatewayServerRSocketFactoryCustomizer;
import org.springframework.cloud.gateway.rsocket.registry.Registry;
import org.springframework.cloud.gateway.rsocket.registry.RegistryRoutes;
import org.springframework.cloud.gateway.rsocket.registry.RegistrySocketAcceptorFilter;
import org.springframework.cloud.gateway.rsocket.route.Routes;
import org.springframework.cloud.gateway.rsocket.socketacceptor.GatewaySocketAcceptor;
import org.springframework.cloud.gateway.rsocket.socketacceptor.SocketAcceptorFilter;
import org.springframework.cloud.gateway.rsocket.socketacceptor.SocketAcceptorPredicate;
import org.springframework.cloud.gateway.rsocket.socketacceptor.SocketAcceptorPredicateFilter;
import org.springframework.cloud.gateway.rsocket.support.Forwarding;
import org.springframework.cloud.gateway.rsocket.support.RouteSetup;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.messaging.rsocket.DefaultMetadataExtractor;
import org.springframework.messaging.rsocket.MetadataExtractor;
import org.springframework.messaging.rsocket.RSocketStrategies;

import static org.springframework.cloud.gateway.rsocket.support.Forwarding.FORWARDING_MIME_TYPE;
import static org.springframework.cloud.gateway.rsocket.support.RouteSetup.ROUTE_SETUP_MIME_TYPE;

/**
 * @author Spencer Gibb
 */
@Configuration
@ConditionalOnProperty(name = "spring.cloud.gateway.rsocket.enabled",
		matchIfMissing = true)
@EnableConfigurationProperties
@ConditionalOnClass(RSocket.class)
@AutoConfigureBefore(RSocketServerAutoConfiguration.class)
public class GatewayRSocketAutoConfiguration {

	@Bean
	public Registry registry() {
		return new Registry();
	}

	// TODO: CompositeRoutes
	@Bean
	public RegistryRoutes registryRoutes(Registry registry) {
		RegistryRoutes registryRoutes = new RegistryRoutes();
		registry.addListener(registryRoutes);
		return registryRoutes;
	}

	@Bean
	public RegistrySocketAcceptorFilter registrySocketAcceptorFilter(Registry registry) {
		return new RegistrySocketAcceptorFilter(registry);
	}

	@Bean
	public GatewayRSocket.Factory gatewayRSocketFactory(Registry registry, Routes routes,
			MeterRegistry meterRegistry, GatewayRSocketProperties properties,
			RSocketStrategies rSocketStrategies) {
		return new GatewayRSocket.Factory(registry, routes, meterRegistry, properties,
				rSocketStrategies.metadataExtractor());
	}

	@Bean
	public GatewayRSocketProperties gatewayRSocketProperties(Environment env) {
		GatewayRSocketProperties properties = new GatewayRSocketProperties();
		if (env.containsProperty("spring.application.name")) {
			properties.setId(env.getProperty("spring.application.name")); // set default
																			// from env
		}
		return properties;
	}

	@Bean
	public SocketAcceptorPredicateFilter socketAcceptorPredicateFilter(
			List<SocketAcceptorPredicate> predicates) {
		return new SocketAcceptorPredicateFilter(predicates);
	}

	@Bean
	public GatewaySocketAcceptor socketAcceptor(GatewayRSocket.Factory rsocketFactory,
			List<SocketAcceptorFilter> filters, MeterRegistry meterRegistry,
			GatewayRSocketProperties properties, RSocketStrategies rSocketStrategies) {
		MetadataExtractor metadataExtractor = registerMimeTypes(rSocketStrategies);
		return new GatewaySocketAcceptor(rsocketFactory, filters, meterRegistry,
				properties, metadataExtractor);
	}

	public static MetadataExtractor registerMimeTypes(
			RSocketStrategies rSocketStrategies) {
		MetadataExtractor metadataExtractor = rSocketStrategies.metadataExtractor();
		// TODO: see if possible to make easier in framework.
		if (metadataExtractor instanceof DefaultMetadataExtractor) {
			DefaultMetadataExtractor extractor = (DefaultMetadataExtractor) metadataExtractor;
			extractor.metadataToExtract(FORWARDING_MIME_TYPE, Forwarding.class,
					"forwarding");
			extractor.metadataToExtract(ROUTE_SETUP_MIME_TYPE, RouteSetup.class,
					"routesetup");
		}
		return metadataExtractor;
	}

	@Bean
	public GatewayServerRSocketFactoryCustomizer gatewayServerRSocketFactoryCustomizer(
			GatewayRSocketProperties properties, MeterRegistry meterRegistry) {
		return new GatewayServerRSocketFactoryCustomizer(properties, meterRegistry);
	}

	@Bean
	public RSocketServerBootstrap gatewayRSocketServerBootstrap(
			RSocketServerFactory rSocketServerFactory,
			GatewaySocketAcceptor gatewaySocketAcceptor) {
		return new RSocketServerBootstrap(rSocketServerFactory, gatewaySocketAcceptor);
	}

	@Bean
	public RSocketStrategiesCustomizer gatewayRSocketStrategiesCustomizer() {
		return strategies -> {
			strategies.decoder(new Forwarding.Decoder(), new RouteSetup.Decoder())
					.encoder(new Forwarding.Encoder(), new RouteSetup.Encoder());
		};
	}

}
