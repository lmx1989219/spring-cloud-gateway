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

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.context.properties.bind.BindHandler;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.handler.IgnoreTopLevelConverterNotFoundBindHandler;
import org.springframework.boot.context.properties.bind.validation.ValidationBindHandler;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.convert.ConversionService;
import org.springframework.validation.Validator;

public abstract class ConfigurationUtils {

	public static void bind(Object o, Map<String, Object> properties,
			String configurationPropertyName, String bindingName, Validator validator) {
		bind(o, properties, configurationPropertyName, bindingName, validator, null);
	}

	public static void bind(Object o, Map<String, Object> properties,
			String configurationPropertyName, String bindingName, Validator validator,
			ConversionService conversionService) {
		Object toBind = getTargetObject(o);
		Bindable<?> bindable = Bindable.ofInstance(toBind);

		bindOrCreate(bindable, properties, configurationPropertyName, validator,
				conversionService);
	}

	public static <T> T bindOrCreate(Configurable<T> configurable,
			Map<String, Object> properties, String configurationPropertyName,
			String bindingName, Validator validator,
			ConversionService conversionService) {
		return bindOrCreate(configurable.getConfigClass(), properties,
				configurationPropertyName, bindingName, validator, conversionService);
	}

	public static <T> T bindOrCreate(Class<T> targetClass, Map<String, Object> properties,
			String configurationPropertyName, String bindingName, Validator validator,
			ConversionService conversionService) {
		Bindable<T> bindable = Bindable.of(targetClass);
		return bindOrCreate(bindable, properties, configurationPropertyName, validator,
				conversionService);
	}

	static <T> T bindOrCreate(Bindable<T> bindable, Map<String, Object> properties,
			String configurationPropertyName, Validator validator,
			ConversionService conversionService) {
		// see ConfigurationPropertiesBinder from spring boot for this definition.
		BindHandler handler = new IgnoreTopLevelConverterNotFoundBindHandler();

		if (validator != null) { // TODO: list of validators?
			handler = new ValidationBindHandler(handler, validator);
		}

		List<ConfigurationPropertySource> propertySources = Collections
				.singletonList(new MapConfigurationPropertySource(properties));

		return new Binder(propertySources, null, conversionService)
				.bindOrCreate(configurationPropertyName, bindable, handler);
	}

	@SuppressWarnings("unchecked")
	public static <T> T getTargetObject(Object candidate) {
		try {
			if (AopUtils.isAopProxy(candidate) && (candidate instanceof Advised)) {
				return (T) ((Advised) candidate).getTargetSource().getTarget();
			}
		}
		catch (Exception ex) {
			throw new IllegalStateException("Failed to unwrap proxied object", ex);
		}
		return (T) candidate;
	}

}
