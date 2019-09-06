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

package org.springframework.cloud.gateway.rsocket.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import io.rsocket.RSocket;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.roaringbitmap.IntConsumer;
import org.roaringbitmap.RoaringBitmap;
import reactor.core.Disposable;
import reactor.core.publisher.DirectProcessor;
import reactor.core.publisher.FluxSink;

import org.springframework.cloud.gateway.rsocket.support.Forwarding;
import org.springframework.cloud.gateway.rsocket.support.RouteSetup;
import org.springframework.cloud.gateway.rsocket.support.TagsMetadata;
import org.springframework.core.style.ToStringCreator;
import org.springframework.util.Assert;

/**
 * The RoutingTable handles all RSocket connections that have been made that have associated
 * announcement metadata. RSocket connections can then be found based on routing metadata.
 * When a new RSocket is registered, a RegisteredEvent is pushed onto a DirectProcessor
 * that is acting as an event bus for registered Consumers.
 */
public class RoutingTable {

	private static final Log log = LogFactory.getLog(RoutingTable.class);

	AtomicInteger internalRouteId = new AtomicInteger();
	final Map<Integer, String> internalRouteIdToRouteId = new ConcurrentHashMap<>();
	final Map<RegistryKey, RoaringBitmap> tagsToBitmaps = new ConcurrentHashMap<>();
	final Map<String, RSocket> routeIdToRSocket = new ConcurrentHashMap<>();

	private final DirectProcessor<RegisteredEvent> registeredEvents = DirectProcessor
			.create();

	private final FluxSink<RegisteredEvent> registeredEventsSink = registeredEvents
			.sink(FluxSink.OverflowStrategy.DROP);

	public RoutingTable() {
	}

	// TODO: Mono<Void>?
	public void register(TagsMetadata tagsMetadata, RSocket rsocket) {
		Assert.notNull(tagsMetadata, "tagsMetadata may not be null");
		Assert.notNull(rsocket, "RSocket may not be null");

		if (log.isDebugEnabled()) {
			log.debug("Registering RSocket: " + tagsMetadata);
		}

		//TODO: only register new route if timestamp is newer
		String routeId = tagsMetadata.getRouteId();

		if (routeIdToRSocket.containsKey(routeId)) {
			throw new IllegalStateException("Route Id already registered: " + routeId);
		}

		int internalId = internalRouteId.incrementAndGet();
		routeIdToRSocket.put(routeId, rsocket);
		internalRouteIdToRouteId.put(internalId, routeId);

		tagsMetadata.getTags().forEach((key, value) -> {
			// TODO: deal with string keys?
			RoaringBitmap bitmap = tagsToBitmaps
					.computeIfAbsent(new RegistryKey(key, value), k -> new RoaringBitmap());
			bitmap.add(internalId);
		});

		// FIXME: new routing table with Roaring Bitmap
		//LoadBalancedRSocket composite = rsockets.computeIfAbsent(routeSetup.getName(),
		//		s -> new LoadBalancedRSocket(routeSetup.getName()));
		//composite.addRSocket(rsocket, routeSetup);
		registeredEventsSink.next(new RegisteredEvent(tagsMetadata, rsocket));
	}

	public void deregister(RouteSetup metadata) {
		Assert.notNull(metadata, "metadata may not be null");
		if (log.isDebugEnabled()) {
			log.debug("Deregistering RSocket: " + metadata);
		}
		LoadBalancedRSocket loadBalanced = null; //FIXME: this.rsockets.get(metadata.getName());
		if (loadBalanced != null) {
			loadBalanced.remove(metadata);
		}
	}

	public List<RSocket> find(TagsMetadata tagsMetadata) {

		RoaringBitmap found = new RoaringBitmap();
		AtomicBoolean first = new AtomicBoolean(true);
		tagsMetadata.getTags().forEach((key, value) -> {
			RegistryKey registryKey = new RegistryKey(key, value);
			if (tagsToBitmaps.containsKey(registryKey)) {
				RoaringBitmap search = tagsToBitmaps.get(registryKey);
				if (first.get()) {
					// initiliaze found bitmap with current search
					found.or(search);
					first.compareAndSet(true, false);
				} else {
					found.and(search);
				}
			}
		});
		ArrayList<RSocket> rSockets = new ArrayList<>();
		found.forEach(new IntConsumer() {
			@Override
			public void accept(int internalId) {
				String routeId = internalRouteIdToRouteId.get(internalId);
				RSocket rSocket = routeIdToRSocket.get(routeId);
				rSockets.add(rSocket);
			}
		});
		return rSockets;
	}

	@Deprecated
	public LoadBalancedRSocket getRegistered(Forwarding metadata) {
		// FIXME: match registered by all tags
		return null; //rsockets.get(metadata.getName());
	}

	public Disposable addListener(Consumer<RegisteredEvent> consumer) {
		return this.registeredEvents.subscribe(consumer);
	}

	public static class RegisteredEvent {

		private final TagsMetadata routingMetadata;

		private final RSocket rSocket;

		public RegisteredEvent(TagsMetadata routingMetadata, RSocket rSocket) {
			Assert.notNull(routingMetadata, "routingMetadata may not be null");
			Assert.notNull(rSocket, "RSocket may not be null");
			this.routingMetadata = routingMetadata;
			this.rSocket = rSocket;
		}

		public TagsMetadata getRoutingMetadata() {
			return routingMetadata;
		}

		public RSocket getRSocket() {
			return rSocket;
		}

	}

	static class RegistryKey {
		final TagsMetadata.Key key;
		final String value;

		public RegistryKey(TagsMetadata.Key key, String value) {
			//TODO: Assert non null
			this.key = key;
			this.value = value.toLowerCase();
		}

		public TagsMetadata.Key getKey() {
			return this.key;
		}

		public String getValue() {
			return this.value;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			RegistryKey that = (RegistryKey) o;
			return Objects.equals(this.key, that.key) &&
					Objects.equals(this.value, that.value);
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.key, this.value);
		}

		@Override
		public String toString() {
			return new ToStringCreator(this)
					.append("key", key)
					.append("value", value)
					.toString();

		}
	}

}
