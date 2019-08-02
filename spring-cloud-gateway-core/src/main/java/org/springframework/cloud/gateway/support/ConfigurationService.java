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

package org.springframework.cloud.gateway.support;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.convert.ConversionService;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.util.Assert;
import org.springframework.validation.Validator;

public class ConfigurationService {

	private ApplicationEventPublisher publisher;

	private BeanFactory beanFactory;

	private ConversionService conversionService;

	private SpelExpressionParser parser = new SpelExpressionParser();

	private Validator validator;

	@Deprecated
	public ConfigurationService() {
	}

	public ConfigurationService(BeanFactory beanFactory,
			ConversionService conversionService, Validator validator) {
		this.beanFactory = beanFactory;
		this.conversionService = conversionService;
		this.validator = validator;
	}

	public ApplicationEventPublisher getPublisher() {
		return this.publisher;
	}

	@Deprecated
	public void setPublisher(ApplicationEventPublisher publisher) {
		this.publisher = publisher;
	}

	public BeanFactory getBeanFactory() {
		return this.beanFactory;
	}

	@Deprecated
	public void setBeanFactory(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	@Deprecated
	public void setConversionService(ConversionService conversionService) {
		this.conversionService = conversionService;
	}

	public void setParser(SpelExpressionParser parser) {
		this.parser = parser;
	}

	public Validator getValidator() {
		return this.validator;
	}

	@Deprecated
	public void setValidator(Validator validator) {
		this.validator = validator;
	}

	public <T, C extends Configurable<T> & ShortcutConfigurable> Builder<T, C> with(C configurable) {
		return new Builder<T, C>(this, configurable);
	}

	public class Builder<T, C extends Configurable<T> & ShortcutConfigurable> {

		private final ConfigurationService service;
		private final C configurable;
		private BiFunction<T, Map<String, Object>, ApplicationEvent> eventFunction;
		private String name;
		private boolean normalizeProperties = true;
		private Map<String, String> properties;

		public Builder(ConfigurationService service, C configurable) {
			this.service = service;
			this.configurable = configurable;
		}

		public Builder<T, C> name(String name) {
			this.name = name;
			return this;
		}

		public Builder<T, C> eventFunction(
				BiFunction<T, Map<String, Object>, ApplicationEvent> eventFunction) {
			this.eventFunction = eventFunction;
			return this;
		}

		public Builder<T, C> normalizeProperties(boolean normalizeProperties) {
			this.normalizeProperties = normalizeProperties;
			return this;
		}

		public Builder<T, C> properties(Map<String, String> properties) {
			this.properties = properties;
			return this;
		}

		public T bind() {
			Assert.notNull(this.configurable, "configurable may not be null");
			Assert.hasText(this.name, "name may not be empty");
			Assert.notNull(this.properties, "properties may not be null");

			Map<String, Object> normalizedProperties;
			if (this.normalizeProperties) {
				normalizedProperties = this.configurable.shortcutType().normalize(
						this.properties, this.configurable, this.service.parser,
						this.service.beanFactory);
			}
			else {
				normalizedProperties = new HashMap<>();
				this.properties.forEach(normalizedProperties::put);
			}

			T bound = ConfigurationUtils.bindOrCreate(this.configurable, normalizedProperties,
					this.configurable.shortcutFieldPrefix(), this.name,
					this.service.validator, this.service.conversionService);

			if (this.eventFunction != null && this.service.publisher != null) {
				ApplicationEvent applicationEvent = this.eventFunction
						.apply(bound, normalizedProperties);
				this.service.publisher.publishEvent(applicationEvent);
			}

			return bound;
		}
	}
}
