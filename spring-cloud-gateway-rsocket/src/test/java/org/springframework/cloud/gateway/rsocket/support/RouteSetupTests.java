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

import io.netty.buffer.ByteBuf;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RouteSetupTests {

	public static final BigInteger MAX_BIGINT = new BigInteger("170141183460469231731687303715884105727");
	public static final BigInteger TWO_BYTE_BIGINT = new BigInteger("128");

	@Test
	public void bigIntegerTest() {
		byte[] bytes = MAX_BIGINT.toByteArray();
		System.out.println("max bytes: " + bytes.length);

		bytes = BigInteger.ONE.toByteArray();
		System.out.println("min bytes: " + bytes.length);

		BigInteger bigInteger = TWO_BYTE_BIGINT;
		bytes = bigInteger.toByteArray();
		System.out.println("16 bytes: " + bytes.length);
	}

	@Test
	public void encodeAndDecodeWorksMaxBigint() {
		ByteBuf byteBuf = createRouteSetup(MAX_BIGINT);
		assertRouteSetup(byteBuf, MAX_BIGINT);
		/*assertThat(metadata.getTags()).hasSize(2)
				.containsOnlyKeys(new Key(ROUTE_ID), new Key(SERVICE_NAME))
				.containsValues("routeId1111111", "serviceName2222222");*/
	}

	@Test
	public void encodeAndDecodeWorksMinBigint() {
		ByteBuf byteBuf = createRouteSetup(BigInteger.ONE);
		assertRouteSetup(byteBuf, BigInteger.ONE);
	}

	@Test
	public void encodeAndDecodeWorksTwoBytes() {
		ByteBuf byteBuf = createRouteSetup(TWO_BYTE_BIGINT);
		assertRouteSetup(byteBuf, TWO_BYTE_BIGINT);
	}

	ByteBuf createRouteSetup(BigInteger one) {
		return new RouteSetup(one, "myservice11111111",
				new LinkedHashMap<>()).encode();
	}

	void assertRouteSetup(ByteBuf byteBuf, BigInteger one) {
		RouteSetup routeSetup = RouteSetup.decode(byteBuf);
		assertThat(routeSetup).isNotNull();
		assertThat(routeSetup.getId()).isEqualTo(one);
		assertThat(routeSetup.getServiceName()).isEqualTo("myservice11111111");
	}

}
