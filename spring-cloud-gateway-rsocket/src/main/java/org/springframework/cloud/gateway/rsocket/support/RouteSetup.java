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

import java.util.Map;

import io.netty.buffer.ByteBuf;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import org.springframework.core.ResolvableType;
import org.springframework.core.codec.AbstractDecoder;
import org.springframework.core.codec.AbstractEncoder;
import org.springframework.core.codec.DecodingException;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.NettyDataBufferFactory;
import org.springframework.util.MimeType;

public class RouteSetup {

	/**
	 * Route Setup subtype.
	 */
	public static final String ROUTE_SETUP = "x.rsocket.routesetup.v0";

	/**
	 * Route Setup mime type.
	 */
	public static final MimeType ROUTE_SETUP_MIME_TYPE = new MimeType("message",
			ROUTE_SETUP);

	private final Metadata metadata;

	//TODO: proposed id is 128 bit
	public RouteSetup(String id, String name, Map<String, String> properties) {
		properties.put("id", id);
		this.metadata = new Metadata(name, properties);
	}

	public RouteSetup(Metadata metadata) {
		this.metadata = metadata;
	}

	public String getId() {
		return get("id");
	}

	public String getName() {
		return metadata.getName();
	}

	public Map<String, String> getProperties() {
		return metadata.getProperties();
	}

	public String get(String key) {
		return metadata.get(key);
	}

	public String put(String key, String value) {
		return metadata.put(key, value);
	}

	@Override
	public String toString() {
		return metadata.toString();
	}

	public boolean matches(RouteSetup other) {
		return metadata.matches(other.metadata);
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
			ByteBuf encoded = Metadata.encode(factory.getByteBufAllocator(),
					value.metadata);
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
			return new RouteSetup(Metadata.decodeMetadata(byteBuf));
		}

	}

}
