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

package org.springframework.cloud.gateway.rsocket.support;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.rsocket.metadata.WellKnownMimeType;
import io.rsocket.util.NumberUtils;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import org.springframework.core.ResolvableType;
import org.springframework.core.codec.AbstractDecoder;
import org.springframework.core.codec.AbstractEncoder;
import org.springframework.core.codec.DecodingException;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.NettyDataBuffer;
import org.springframework.core.io.buffer.NettyDataBufferFactory;
import org.springframework.core.style.ToStringCreator;
import org.springframework.util.Assert;
import org.springframework.util.MimeType;

public class Metadata {

	/**
	 * Composite Metadata MimeType.
	 */
	public static final MimeType COMPOSITE_MIME_TYPE = MimeType
			.valueOf(WellKnownMimeType.MESSAGE_RSOCKET_COMPOSITE_METADATA.toString());

	/**
	 * The logical name.
	 */
	private final String name;

	/**
	 * Keys and values associated with name.
	 */
	private final Map<String, String> properties;

	public Metadata(String name, Map<String, String> properties) {
		this.name = name;
		this.properties = properties;
	}

	public String getName() {
		return this.name;
	}

	public Map<String, String> getProperties() {
		return this.properties;
	}

	public String get(String key) {
		return this.properties.get(key);
	}

	public String put(String key, String value) {
		return this.properties.put(key, value);
	}

	@Override
	public String toString() {
		return new ToStringCreator(this).append("name", name)
				.append("properties", properties).toString();
	}

	public static Builder from(String name) {
		return new Builder(name);
	}

	static ByteBuf encode(Metadata metadata) {
		return encode(ByteBufAllocator.DEFAULT, metadata);
	}

	static ByteBuf encode(ByteBufAllocator allocator, Metadata metadata) {
		return encode(allocator, metadata.getName(), metadata.getProperties());
	}

	static ByteBuf encode(String name, Map<String, String> properties) {
		return encode(ByteBufAllocator.DEFAULT, name, properties);
	}

	static ByteBuf encode(ByteBufAllocator allocator, String name,
			Map<String, String> properties) {
		Assert.hasText(name, "name may not be empty");
		Assert.notNull(properties, "properties may not be null");
		Assert.notNull(allocator, "allocator may not be null");
		ByteBuf byteBuf = allocator.buffer();

		encodeString(byteBuf, name);

		properties.entrySet().stream().forEach(entry -> {
			encodeString(byteBuf, entry.getKey());
			encodeString(byteBuf, entry.getValue());
		});
		return byteBuf;
	}

	private static void encodeString(ByteBuf byteBuf, String s) {
		int length = NumberUtils.requireUnsignedByte(ByteBufUtil.utf8Bytes(s));
		byteBuf.writeByte(length);
		ByteBufUtil.reserveAndWriteUtf8(byteBuf, s, length);
	}

	public static Metadata decodeMetadata(ByteBuf byteBuf) {
		AtomicInteger offset = new AtomicInteger(0);

		String name = decodeString(byteBuf, offset);

		Map<String, String> properties = new LinkedHashMap<>();
		while (offset.get() < byteBuf.readableBytes()) { // TODO: What is the best
															// conditional here?
			String key = decodeString(byteBuf, offset);
			String value = null;
			if (offset.get() < byteBuf.readableBytes()) {
				value = decodeString(byteBuf, offset);
			}
			properties.put(key, value);
		}

		return new Metadata(name, properties);
	}

	private static String decodeString(ByteBuf byteBuf, AtomicInteger offset) {
		int length = byteBuf.getByte(offset.get());
		int index = offset.addAndGet(Byte.BYTES);
		String s = byteBuf.toString(index, length, StandardCharsets.UTF_8);
		offset.addAndGet(length);
		return s;
	}

	public boolean matches(Metadata other) {
		if (other == null) {
			return false;
		}
		if (other.getName() == null) {
			return false;
		}
		if (!getName().equalsIgnoreCase(other.getName())) {
			return false;
		}
		return matches(getProperties(), other.getProperties());
	}

	/**
	 * Matches leftMetadata to rightMetadata. rightMetadata must contain all key with
	 * equal values (ignoring case) of leftMetadata.
	 * @param leftMetadata first metadata to compare.
	 * @param rightMetadata second metadata to compare.
	 * @return true if all keys and values (case-insensitive) from leftMetadata are in
	 * rightMetadata.
	 */
	// TODO: find a way to make this more performant
	public static boolean matches(Map<String, String> leftMetadata,
			Map<String, String> rightMetadata) {
		if (leftMetadata == null || rightMetadata == null) {
			return false;
		}

		for (Map.Entry<String, String> entry : leftMetadata.entrySet()) {
			String enrichedValue = rightMetadata.get(entry.getKey());
			if (enrichedValue == null ||
			// TODO: regex and possibly SpEL?
					!enrichedValue.equalsIgnoreCase(entry.getValue())) {
				return false;
			}
		}

		// all entries in metadata exist and match corresponding entries in
		// enriched.metadata
		return true;
	}

	public static ByteBuf asByteBuf(DataBuffer buffer) {
		return buffer instanceof NettyDataBuffer
				? ((NettyDataBuffer) buffer).getNativeBuffer()
				: Unpooled.wrappedBuffer(buffer.asByteBuffer());
	}

	public static class Builder {

		private final Metadata metadata;

		public Builder(String name) {
			Assert.hasText(name, "Name must not be empty.");
			this.metadata = new Metadata(name, new LinkedHashMap<>());
		}

		public Builder with(String key, String value) {
			this.metadata.put(key, value);
			return this;
		}

		public Metadata build() {
			return this.metadata;
		}

		ByteBuf encode() {
			return Metadata.encode(build());
		}

	}

	public static class Encoder extends AbstractEncoder<Metadata> {

		public Encoder() {
			super(RouteSetup.ROUTE_SETUP_MIME_TYPE);
		}

		@Override
		public Flux<DataBuffer> encode(Publisher<? extends Metadata> inputStream,
				DataBufferFactory bufferFactory, ResolvableType elementType,
				MimeType mimeType, Map<String, Object> hints) {
			throw new UnsupportedOperationException("stream encoding not supported.");
		}

		@Override
		public DataBuffer encodeValue(Metadata value, DataBufferFactory bufferFactory,
				ResolvableType valueType, MimeType mimeType, Map<String, Object> hints) {
			NettyDataBufferFactory factory = (NettyDataBufferFactory) bufferFactory;
			ByteBuf encoded = Metadata.encode(factory.getByteBufAllocator(), value);
			return factory.wrap(encoded);
		}

	}

	public static class Decoder extends AbstractDecoder<Metadata> {

		public Decoder() {
			super(RouteSetup.ROUTE_SETUP_MIME_TYPE);
		}

		@Override
		public Flux<Metadata> decode(Publisher<DataBuffer> inputStream,
				ResolvableType elementType, MimeType mimeType,
				Map<String, Object> hints) {
			throw new UnsupportedOperationException("stream decoding not supported.");
		}

		@Override
		public Metadata decode(DataBuffer buffer, ResolvableType targetType,
				MimeType mimeType, Map<String, Object> hints) throws DecodingException {
			ByteBuf byteBuf = Metadata.asByteBuf(buffer);
			return Metadata.decodeMetadata(byteBuf);
		}

	}

}
