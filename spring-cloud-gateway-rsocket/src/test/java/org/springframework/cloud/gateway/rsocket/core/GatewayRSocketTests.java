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

package org.springframework.cloud.gateway.rsocket.core;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.util.DefaultPayload;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Before;
import org.junit.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoProcessor;
import reactor.test.StepVerifier;

import org.springframework.cloud.gateway.rsocket.autoconfigure.GatewayRSocketProperties;
import org.springframework.cloud.gateway.rsocket.registry.LoadBalancedRSocket;
import org.springframework.cloud.gateway.rsocket.registry.LoadBalancedRSocket.EnrichedRSocket;
import org.springframework.cloud.gateway.rsocket.registry.Registry;
import org.springframework.cloud.gateway.rsocket.route.Route;
import org.springframework.cloud.gateway.rsocket.route.Routes;
import org.springframework.cloud.gateway.rsocket.support.Forwarding;
import org.springframework.cloud.gateway.rsocket.support.Metadata;
import org.springframework.cloud.gateway.rsocket.support.RouteSetup;
import org.springframework.cloud.gateway.rsocket.test.MetadataEncoder;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.messaging.rsocket.DefaultMetadataExtractor;
import org.springframework.messaging.rsocket.MetadataExtractor;
import org.springframework.messaging.rsocket.PayloadUtils;
import org.springframework.messaging.rsocket.RSocketStrategies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.gateway.rsocket.support.Forwarding.FORWARDING_MIME_TYPE;
import static org.springframework.messaging.rsocket.MetadataExtractor.COMPOSITE_METADATA;

/**
 * @author Rossen Stoyanchev
 */
public class GatewayRSocketTests {

	private static Log logger = LogFactory.getLog(GatewayRSocketTests.class);

	private Registry registry;

	private Payload incomingPayload;

	private final RSocketStrategies rSocketStrategies = RSocketStrategies.builder()
			.decoder(new Forwarding.Decoder()).encoder(new Forwarding.Encoder()).build();

	private DefaultMetadataExtractor metadataExtractor = new DefaultMetadataExtractor(
			rSocketStrategies.decoders());

	// TODO: add tests for metrics and other request types

	@Before
	public void init() {
		registry = mock(Registry.class);

		this.metadataExtractor.metadataToExtract(FORWARDING_MIME_TYPE, Forwarding.class,
				"forwarding");

		MetadataEncoder encoder = new MetadataEncoder(COMPOSITE_METADATA,
				this.rSocketStrategies);
		DataBuffer dataBuffer = encoder.metadata(
				new Forwarding(Metadata.from("mock").with("id", "mock1").build()),
				FORWARDING_MIME_TYPE).encode();
		DataBuffer data = MetadataEncoder.emptyDataBuffer(rSocketStrategies);
		incomingPayload = PayloadUtils.createPayload(data, dataBuffer);

		RSocket rSocket = mock(RSocket.class);
		LoadBalancedRSocket loadBalancedRSocket = mock(LoadBalancedRSocket.class);
		when(registry.getRegistered(any(Forwarding.class)))
				.thenReturn(loadBalancedRSocket);

		Mono<EnrichedRSocket> mono = Mono
				.just(new EnrichedRSocket(rSocket, getMetadata()));
		when(loadBalancedRSocket.choose()).thenReturn(mono);

		when(rSocket.requestResponse(any(Payload.class)))
				.thenReturn(Mono.just(DefaultPayload.create("response")));
	}

	@Test
	public void multipleFilters() {
		TestFilter filter1 = new TestFilter();
		TestFilter filter2 = new TestFilter();
		TestFilter filter3 = new TestFilter();

		Payload payload = new TestGatewayRSocket(registry,
				new TestRoutes(filter1, filter2, filter3), metadataExtractor)
						.requestResponse(incomingPayload).block(Duration.ZERO);

		assertThat(filter1.invoked()).isTrue();
		assertThat(filter2.invoked()).isTrue();
		assertThat(filter3.invoked()).isTrue();
		assertThat(payload).isNotNull();
	}

	@Test
	public void zeroFilters() {
		Payload payload = new TestGatewayRSocket(registry, new TestRoutes(),
				metadataExtractor).requestResponse(incomingPayload).block(Duration.ZERO);

		assertThat(payload).isNotNull();
	}

	@Test
	public void shortcircuitFilter() {

		TestFilter filter1 = new TestFilter();
		ShortcircuitingFilter filter2 = new ShortcircuitingFilter();
		TestFilter filter3 = new TestFilter();

		TestGatewayRSocket gatewayRSocket = new TestGatewayRSocket(registry,
				new TestRoutes(filter1, filter2, filter3), metadataExtractor);
		Mono<Payload> response = gatewayRSocket.requestResponse(incomingPayload);

		// a false filter will create a pending rsocket that blocks forever
		// this tweaks the rsocket to compelte.
		gatewayRSocket.processor.onNext(null);

		StepVerifier.withVirtualTime(() -> response).expectSubscription()
				.verifyComplete();

		assertThat(filter1.invoked()).isTrue();
		assertThat(filter2.invoked()).isTrue();
		assertThat(filter3.invoked()).isFalse();
	}

	@Test
	public void asyncFilter() {

		AsyncFilter filter = new AsyncFilter();

		Payload payload = new TestGatewayRSocket(registry, new TestRoutes(filter),
				metadataExtractor).requestResponse(incomingPayload)
						.block(Duration.ofSeconds(5));

		assertThat(filter.invoked()).isTrue();
		assertThat(payload).isNotNull();
	}

	// TODO: add exception handlers?
	@Test(expected = IllegalStateException.class)
	public void handleErrorFromFilter() {

		ExceptionFilter filter = new ExceptionFilter();

		new TestGatewayRSocket(registry, new TestRoutes(filter), metadataExtractor)
				.requestResponse(incomingPayload).block(Duration.ofSeconds(5));

		// assertNull(socket);
	}

	private static RouteSetup getMetadata() {
		return new RouteSetup(Metadata.from("service").with("id", "service1").build());
	}

	private static class TestGatewayRSocket extends GatewayRSocket {

		private final MonoProcessor<RSocket> processor = MonoProcessor.create();

		TestGatewayRSocket(Registry registry, Routes routes,
				MetadataExtractor metadataExtractor) {
			super(registry, routes, new SimpleMeterRegistry(),
					new GatewayRSocketProperties(), metadataExtractor, getMetadata());
		}

		@Override
		PendingRequestRSocket constructPendingRSocket(GatewayExchange exchange) {
			Function<Registry.RegisteredEvent, Mono<Route>> routeFinder = registeredEvent -> getRouteMono(
					registeredEvent, exchange);
			return new PendingRequestRSocket(getMetadataExtractor(), routeFinder, map -> {
				Tags tags = exchange.getTags().and("responder.id", map.get("id"));
				exchange.setTags(tags);
			}, processor);
		}

		public MonoProcessor<RSocket> getProcessor() {
			return processor;
		}

	}

	private static class TestRoutes implements Routes {

		private final Route route;

		private List<GatewayFilter> filters;

		TestRoutes() {
			this(Collections.emptyList());
		}

		TestRoutes(GatewayFilter... filters) {
			this(Arrays.asList(filters));
		}

		TestRoutes(List<GatewayFilter> filters) {
			this.filters = filters;
			route = Route.builder().id("route1")
					.routingMetadata(new RouteSetup(Metadata.from("mock").build()))
					.predicate(exchange -> Mono.just(true)).filters(filters).build();
		}

		@Override
		public Flux<Route> getRoutes() {
			return Flux.just(route);
		}

	}

	private static class TestFilter implements GatewayFilter {

		private volatile boolean invoked;

		public boolean invoked() {
			return this.invoked;
		}

		@Override
		public Mono<Success> filter(GatewayExchange exchange, GatewayFilterChain chain) {
			this.invoked = true;
			return doFilter(exchange, chain);
		}

		public Mono<Success> doFilter(GatewayExchange exchange,
				GatewayFilterChain chain) {
			return chain.filter(exchange);
		}

	}

	private static class ShortcircuitingFilter extends TestFilter {

		@Override
		public Mono<Success> doFilter(GatewayExchange exchange,
				GatewayFilterChain chain) {
			return Mono.empty();
		}

	}

	private static class AsyncFilter extends TestFilter {

		@Override
		public Mono<Success> doFilter(GatewayExchange exchange,
				GatewayFilterChain chain) {
			return doAsyncWork().flatMap(asyncResult -> {
				logger.debug("Async result: " + asyncResult);
				return chain.filter(exchange);
			});
		}

		private Mono<String> doAsyncWork() {
			return Mono.delay(Duration.ofMillis(100L)).map(l -> "123");
		}

	}

	private static class ExceptionFilter implements GatewayFilter {

		@Override
		public Mono<Success> filter(GatewayExchange exchange, GatewayFilterChain chain) {
			return Mono.error(new IllegalStateException("boo"));
		}

	}

	/*
	 * private static class TestExceptionHandler implements WebExceptionHandler {
	 *
	 * private Throwable ex;
	 *
	 * @Override public Mono<Void> handle(GatewayExchange exchange, Throwable ex) {
	 * this.ex = ex; return Mono.error(ex); } }
	 */

}
