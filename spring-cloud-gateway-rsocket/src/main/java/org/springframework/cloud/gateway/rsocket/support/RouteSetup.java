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

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import org.springframework.core.ResolvableType;
import org.springframework.core.codec.AbstractDecoder;
import org.springframework.core.codec.AbstractEncoder;
import org.springframework.core.codec.DecodingException;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.NettyDataBufferFactory;
import org.springframework.core.style.ToStringCreator;
import org.springframework.util.Assert;
import org.springframework.util.MimeType;

public class RouteSetup extends TagsMetadata {

	/**
	 * Route Setup subtype.
	 */
	public static final String ROUTE_SETUP = "x.rsocket.routesetup.v0";

	/**
	 * Route Setup mime type.
	 */
	public static final MimeType ROUTE_SETUP_MIME_TYPE = new MimeType("message",
			ROUTE_SETUP);

	private final BigInteger id;
	private final String serviceName;

	public RouteSetup(long id, String serviceName, Map<Key, String> tags) {
		this(BigInteger.valueOf(id), serviceName, tags);
	}

	public RouteSetup(BigInteger id, String serviceName, Map<Key, String> tags) {
		super(tags);
		this.id = id;
		this.serviceName = serviceName;
	}

	public BigInteger getId() {
		return this.id;
	}

	public String getServiceName() {
		return this.serviceName;
	}

	public boolean matches(RouteSetup other) {
		return false; //metadata.matches(other.metadata);
	}

	public ByteBuf encode() {
		return RouteSetup.encode(this);
	}

	@Override
	public TagsMetadata getEnrichedTagsMetadata() {
		// @formatter:off
		TagsMetadata tagsMetadata = TagsMetadata.builder(this)
				.with(WellKnownKey.SERVICE_NAME, getServiceName())
				.with(WellKnownKey.ROUTE_ID, getId().toString())
				.build();
		// @formatter:on

		return tagsMetadata;
	}

	@Override
	public String toString() {
		// @formatter:off
		return new ToStringCreator(this)
				.append("id", id)
				.append("serviceName", serviceName)
				.append("tags", getTags())
				.toString();
		// @formatter:on
	}

	static ByteBuf encode(RouteSetup routeSetup) {
		return encode(ByteBufAllocator.DEFAULT, routeSetup);
	}

	static ByteBuf encode(ByteBufAllocator allocator, RouteSetup routeSetup) {
		Assert.notNull(routeSetup, "routeSetup may not be null");
		Assert.notNull(allocator, "allocator may not be null");
		ByteBuf byteBuf = allocator.buffer();

		byte[] idBytes = routeSetup.id.toByteArray();
		// truncate or pad to 16 bytes or 128 bits
		//byte[] normalizedBytes = Arrays.copyOf(idBytes, 16);
		byte[] normalizedBytes = new byte[16];
		// right shift
		int destPos = normalizedBytes.length - idBytes.length;
		System.arraycopy(idBytes, 0, normalizedBytes, destPos, idBytes.length);

		byteBuf.writeBytes(normalizedBytes);

		encodeString(byteBuf, routeSetup.getServiceName());

		return byteBuf;
	}

	static RouteSetup decode(ByteBuf byteBuf) {
		AtomicInteger offset = new AtomicInteger(0);

		byte[] idBytes = new byte[16];
		byteBuf.getBytes(offset.get(), idBytes, 0, 16);
		offset.getAndAdd(16);

		String serviceName = decodeString(byteBuf, offset);

		RouteSetup routeSetup = new RouteSetup(new BigInteger(idBytes), serviceName, new LinkedHashMap<>());

		return routeSetup;
	}

	public static class Encoder extends AbstractEncoder<RouteSetup> {

		public Encoder() {
			super(ROUTE_SETUP_MIME_TYPE);
		}

		@Override
		public Flux<DataBuffer> encode(Publisher<? extends RouteSetup> inputStream,
				DataBufferFactory bufferFactory, ResolvableType elementType,
				MimeType mimeType, Map<String, Object> hints) {
			throw new UnsupportedOperationException("stream encoding not supported.");
		}

		@Override
		public DataBuffer encodeValue(RouteSetup value, DataBufferFactory bufferFactory,
				ResolvableType valueType, MimeType mimeType, Map<String, Object> hints) {
			NettyDataBufferFactory factory = (NettyDataBufferFactory) bufferFactory;
			ByteBuf encoded = null; //FIXME: Metadata.encode(factory.getByteBufAllocator(), value.metadata);
			return factory.wrap(encoded);
		}

	}

	public static class Decoder extends AbstractDecoder<RouteSetup> {

		public Decoder() {
			super(ROUTE_SETUP_MIME_TYPE);
		}

		@Override
		public Flux<RouteSetup> decode(Publisher<DataBuffer> inputStream,
				ResolvableType elementType, MimeType mimeType,
				Map<String, Object> hints) {
			throw new UnsupportedOperationException("stream decoding not supported.");
		}

		@Override
		public RouteSetup decode(DataBuffer buffer, ResolvableType targetType,
				MimeType mimeType, Map<String, Object> hints) throws DecodingException {
			ByteBuf byteBuf = Metadata.asByteBuf(buffer);
			return null; //FIXME: new RouteSetup(Metadata.decodeMetadata(byteBuf));
		}

	}

}
