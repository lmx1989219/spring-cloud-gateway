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
import org.springframework.core.style.ToStringCreator;
import org.springframework.util.MimeType;

// TODO: currently an ENVELOPE frame in RSocket extension, also discarding metadata
public class Forwarding extends TagsMetadata {

	/**
	 * Forwarding subtype.
	 */
	public static final String FORWARDING = "x.rsocket.forwarding.v0";

	/**
	 * Forwarding mimetype.
	 */
	public static final MimeType FORWARDING_MIME_TYPE = new MimeType("message",
			FORWARDING);

	private final BigInteger originRouteId;

	public Forwarding(long originRouteId, Map<TagsMetadata.Key, String> tags) {
		this(BigInteger.valueOf(originRouteId), tags);
	}

	public Forwarding(BigInteger originRouteId, Map<TagsMetadata.Key, String> tags) {
		super(tags);
		this.originRouteId = originRouteId;
	}

	public BigInteger getOriginRouteId() {
		return this.originRouteId;
	}

	@Override
	public String toString() {
		// @formatter:off
		return new ToStringCreator(this)
				.append("originRouteId", originRouteId)
				.append("tags", getTags())
				.toString();
		// @formatter:on
	}

	public static class Encoder extends AbstractEncoder<Forwarding> {

		public Encoder() {
			super(Forwarding.FORWARDING_MIME_TYPE);
		}

		@Override
		public Flux<DataBuffer> encode(Publisher<? extends Forwarding> inputStream,
				DataBufferFactory bufferFactory, ResolvableType elementType,
				MimeType mimeType, Map<String, Object> hints) {
			throw new UnsupportedOperationException("stream encoding not supported.");
		}

		@Override
		public DataBuffer encodeValue(Forwarding value, DataBufferFactory bufferFactory,
				ResolvableType valueType, MimeType mimeType, Map<String, Object> hints) {
			NettyDataBufferFactory factory = (NettyDataBufferFactory) bufferFactory;
			ByteBuf encoded = null; //FIXME: Metadata.encode(factory.getByteBufAllocator(), value.metadata);
			return factory.wrap(encoded);
		}

	}

	public static class Decoder extends AbstractDecoder<Forwarding> {

		public Decoder() {
			super(Forwarding.FORWARDING_MIME_TYPE);
		}

		@Override
		public Flux<Forwarding> decode(Publisher<DataBuffer> inputStream,
				ResolvableType elementType, MimeType mimeType,
				Map<String, Object> hints) {
			throw new UnsupportedOperationException("stream decoding not supported.");
		}

		@Override
		public Forwarding decode(DataBuffer buffer, ResolvableType targetType,
				MimeType mimeType, Map<String, Object> hints) throws DecodingException {
			ByteBuf byteBuf = Metadata.asByteBuf(buffer);
			return null; //FIXME: new Forwarding(Metadata.decodeMetadata(byteBuf));
		}

	}

}
