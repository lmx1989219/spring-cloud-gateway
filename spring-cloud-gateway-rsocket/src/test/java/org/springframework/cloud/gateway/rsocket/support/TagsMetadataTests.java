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

package org.springframework.cloud.gateway.rsocket.support;

import io.netty.buffer.ByteBuf;
import org.junit.Test;

import org.springframework.cloud.gateway.rsocket.support.TagsMetadata.Key;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.gateway.rsocket.support.WellKnownKey.ROUTE_ID;
import static org.springframework.cloud.gateway.rsocket.support.WellKnownKey.SERVICE_NAME;

public class TagsMetadataTests {

	@Test
	public void encodeAndDecodeWorksAllWellKnowKeys() {
		ByteBuf byteBuf = TagsMetadata.builder().with(ROUTE_ID, "routeId1111111")
				.with(SERVICE_NAME, "serviceName2222222").encode();
		TagsMetadata metadata = TagsMetadata.decode(byteBuf);
		assertThat(metadata).isNotNull();
		assertThat(metadata.getTags()).hasSize(2)
				.containsOnlyKeys(new Key(ROUTE_ID), new Key(SERVICE_NAME))
				.containsValues("routeId1111111", "serviceName2222222");
	}

	@Test
	public void encodeAndDecodeWorksAllStringKeys() {
		ByteBuf byteBuf = TagsMetadata.builder().with("mykey111111111", "myval1111111")
				.with("mykey2222222222", "myval2222222").encode();
		TagsMetadata metadata = TagsMetadata.decode(byteBuf);
		assertThat(metadata).isNotNull();
		assertThat(metadata.getTags()).hasSize(2)
				.containsOnlyKeys(new Key("mykey111111111"), new Key("mykey2222222222"))
				.containsValues("myval1111111", "myval2222222");
	}

	@Test
	public void encodeAndDecodeWorksMixedKeys() {
		ByteBuf byteBuf = TagsMetadata.builder().with(ROUTE_ID, "routeId1111111")
				.with("mykey2222222222", "myval2222222").encode();
		TagsMetadata metadata = TagsMetadata.decode(byteBuf);
		assertThat(metadata).isNotNull();
		assertThat(metadata.getTags()).hasSize(2)
				.containsOnlyKeys(new Key(ROUTE_ID), new Key("mykey2222222222"))
				.containsValues("routeId1111111", "myval2222222");
	}

	private Metadata assertMetadata(ByteBuf byteBuf, String name) {
		Metadata metadata = Metadata.decodeMetadata(byteBuf);
		assertThat(metadata).isNotNull();
		assertThat(metadata.getName()).isEqualTo(name);
		return metadata;
	}

	/*@Test
	public void nullMetadataDoesNotMatch() {
		assertThat(matches(null, new HashMap<>())).isFalse();

		assertThat(matches(new HashMap<>(), null)).isFalse();
	}

	@Test
	public void metadataSubsetMatches() {
		assertThat(matches(metadata(2), metadata(3))).isTrue();
	}

	@Test
	public void metadataEqualSetMatches() {
		assertThat(matches(metadata(3), metadata(3))).isTrue();
	}

	@Test
	public void metadataSuperSetDoesNotMatch() {
		assertThat(matches(metadata(3), metadata(2))).isFalse();
	}

	private Map<String, String> metadata(int size) {
		Assert.isTrue(size > 0, "size must be > 0");
		HashMap<String, String> metadata = new HashMap<>();
		IntStream.rangeClosed(1, size).forEach(i -> metadata.put("key" + i, "val" + i));
		return metadata;
	}*/

}
