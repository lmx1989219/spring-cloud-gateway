/*
 * Copyright 2013-2019 the original author or authors.
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

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.rsocket.AbstractRSocket;
import io.rsocket.RSocket;
import org.junit.Test;
import org.roaringbitmap.RoaringBitmap;

import org.springframework.cloud.gateway.rsocket.support.TagsMetadata;
import org.springframework.cloud.gateway.rsocket.support.WellKnownKey;
import org.springframework.core.style.ToStringCreator;

import static org.assertj.core.api.Assertions.assertThat;

public class RoutingTableTests {

	@Test
	public void testIndexesCreatedAndSearchWorks() {
		RoutingTable routingTable = new RoutingTable();
		// @formatter:off
		TagsMetadata setupTags1 = TagsMetadata.builder()
				.with(WellKnownKey.ROUTE_ID, "1111")
				.with(WellKnownKey.SERVICE_NAME, "serviceA")
				.with(WellKnownKey.CLUSTER_NAME, "clusterA")
				.build();
		TagsMetadata setupTags2 = TagsMetadata.builder()
				.with(WellKnownKey.ROUTE_ID, "2222")
				.with(WellKnownKey.SERVICE_NAME, "serviceA")
				.with(WellKnownKey.CLUSTER_NAME, "clusterB")
				.build();
		TagsMetadata setupTags3 = TagsMetadata.builder()
				.with(WellKnownKey.ROUTE_ID, "3333")
				.with(WellKnownKey.SERVICE_NAME, "serviceB")
				.with(WellKnownKey.REGION, "region1")
				.build();
		TagsMetadata setupTags4 = TagsMetadata.builder()
				.with(WellKnownKey.ROUTE_ID, "4444")
				.with(WellKnownKey.SERVICE_NAME, "serviceB")
				.with(WellKnownKey.CLUSTER_NAME, "clusterB")
				.with(WellKnownKey.ZONE, "zone1")
				.build();
		TagsMetadata setupTags5 = TagsMetadata.builder()
				.with(WellKnownKey.ROUTE_ID, "5555")
				.with(WellKnownKey.SERVICE_NAME, "serviceA")
				.with(WellKnownKey.CLUSTER_NAME, "clusterB")
				.build();
		// @formatter:on

		AtomicInteger internalRouteId = routingTable.internalRouteId;
		RSocket rSocket1 = assertRegister(routingTable, setupTags1, internalRouteId.get() + 1);
		internalRouteId.set(99);
		RSocket rSocket2 = assertRegister(routingTable, setupTags2, internalRouteId.get() + 1);
		internalRouteId.set(999);
		RSocket rSocket3 = assertRegister(routingTable, setupTags3, internalRouteId.get() + 1);
		internalRouteId.set(9999);
		RSocket rSocket4 = assertRegister(routingTable, setupTags4, internalRouteId.get() + 1);
		internalRouteId.set(99999);
		RSocket rSocket5 = assertRegister(routingTable, setupTags5, internalRouteId.get() + 1);

		// @formatter:off
		TagsMetadata searchTags1 = TagsMetadata.builder()
				.with(WellKnownKey.SERVICE_NAME, "serviceA")
				.with(WellKnownKey.CLUSTER_NAME, "clusterB")
				.build();
		// @formatter:on

		List<RSocket> results1 = routingTable.find(searchTags1);
		assertThat(results1).containsOnly(rSocket2, rSocket5);

		// @formatter:off
		TagsMetadata searchTags2 = TagsMetadata.builder()
				.with(WellKnownKey.ROUTE_ID, "3333")
				.build();
		// @formatter:on

		List<RSocket> results2 = routingTable.find(searchTags2);
		assertThat(results2).containsOnly(rSocket3);

		// @formatter:off
		TagsMetadata searchTags3 = TagsMetadata.builder()
				.with(WellKnownKey.ZONE, "zone1")
				.with(WellKnownKey.SERVICE_NAME, "serviceB")
				.with(WellKnownKey.CLUSTER_NAME, "clusterB")
				.build();
		// @formatter:on

		List<RSocket> results3 = routingTable.find(searchTags3);
		assertThat(results3).containsOnly(rSocket4);

	}

	private RSocket assertRegister(RoutingTable routingTable, TagsMetadata tagsMetadata, int internalId) {
		String routeId = tagsMetadata.getRouteId();
		RSocket rsocket = new TestRSocket(routeId);
		routingTable.register(tagsMetadata, rsocket);

		assertThat(routingTable.internalRouteId).hasValue(internalId);
		assertThat(routingTable.internalRouteIdToRouteId).containsEntry(internalId, routeId);
		assertThat(routingTable.routeIdToRSocket).containsKey(routeId);
		tagsMetadata.getTags().forEach((key, value) -> {
			RoutingTable.RegistryKey registryKey = new RoutingTable.RegistryKey(key, value);
			assertThat(routingTable.tagsToBitmaps).containsKey(registryKey);
			RoaringBitmap bitmap = routingTable.tagsToBitmaps.get(registryKey);
			assertThat(bitmap.contains(internalId));
		});

		return rsocket;
	}

	static class TestRSocket extends AbstractRSocket {

		final String routeId;

		public TestRSocket(String routeId) {
			this.routeId = routeId;
		}

		@Override
		public String toString() {
			return new ToStringCreator(this)
					.append("routeId", routeId)
					.toString();

		}
	}
}
