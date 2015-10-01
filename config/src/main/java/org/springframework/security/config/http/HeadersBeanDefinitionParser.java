/*
 * Copyright 2002-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.security.config.http;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.BeanMetadataElement;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.ManagedList;
import org.springframework.beans.factory.xml.BeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.security.web.header.HeaderWriterFilter;
import org.springframework.security.web.header.writers.CacheControlHeadersWriter;
import org.springframework.security.web.header.writers.HstsHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.header.writers.XContentTypeOptionsHeaderWriter;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;
import org.springframework.security.web.header.writers.frameoptions.RegExpAllowFromStrategy;
import org.springframework.security.web.header.writers.frameoptions.StaticAllowFromStrategy;
import org.springframework.security.web.header.writers.frameoptions.WhiteListedAllowFromStrategy;
import org.springframework.security.web.header.writers.frameoptions.XFrameOptionsHeaderWriter;
import org.springframework.util.StringUtils;
import org.springframework.util.xml.DomUtils;
import org.w3c.dom.Element;

/**
 * Parser for the {@code HeadersFilter}.
 *
 * @author Marten Deinum
 * @since 3.2
 */
public class HeadersBeanDefinitionParser implements BeanDefinitionParser {
	private static final String ATT_DISABLED = "disabled";

	private static final String ATT_ENABLED = "enabled";
	private static final String ATT_BLOCK = "block";

	private static final String ATT_POLICY = "policy";
	private static final String ATT_STRATEGY = "strategy";
	private static final String ATT_FROM_PARAMETER = "from-parameter";

	private static final String ATT_NAME = "name";
	private static final String ATT_VALUE = "value";
	private static final String ATT_REF = "ref";

	private static final String ATT_INCLUDE_SUBDOMAINS = "include-subdomains";
	private static final String ATT_MAX_AGE_SECONDS = "max-age-seconds";
	private static final String ATT_REQUEST_MATCHER_REF = "request-matcher-ref";

	private static final String CACHE_CONTROL_ELEMENT = "cache-control";

	private static final String HSTS_ELEMENT = "hsts";

	private static final String XSS_ELEMENT = "xss-protection";
	private static final String CONTENT_TYPE_ELEMENT = "content-type-options";
	private static final String FRAME_OPTIONS_ELEMENT = "frame-options";
	private static final String GENERIC_HEADER_ELEMENT = "header";

	private static final String ALLOW_FROM = "ALLOW-FROM";

	private ManagedList<BeanMetadataElement> headerWriters;

	public BeanDefinition parse(Element element, ParserContext parserContext) {
		headerWriters = new ManagedList<BeanMetadataElement>();
		BeanDefinitionBuilder builder = BeanDefinitionBuilder
				.rootBeanDefinition(HeaderWriterFilter.class);

		boolean disabled = element != null
				&& "true".equals(element.getAttribute("disabled"));
		boolean defaultsDisabled = element != null
				&& "true".equals(element.getAttribute("defaults-disabled"));

		boolean addIfNotPresent = element == null || !disabled && !defaultsDisabled;

		parseCacheControlElement(addIfNotPresent, element);
		parseHstsElement(addIfNotPresent, element, parserContext);
		parseXssElement(addIfNotPresent, element, parserContext);
		parseFrameOptionsElement(addIfNotPresent, element, parserContext);
		parseContentTypeOptionsElement(addIfNotPresent, element);

		parseHeaderElements(element);

		if (disabled) {
			if (!headerWriters.isEmpty()) {
				parserContext
						.getReaderContext()
						.error("Cannot specify <headers disabled=\"true\"> with child elements.",
								element);
			}
			return null;
		}

		builder.addConstructorArgValue(headerWriters);
		return builder.getBeanDefinition();
	}

	private void parseCacheControlElement(boolean addIfNotPresent, Element element) {
		Element cacheControlElement = element == null ? null : DomUtils
				.getChildElementByTagName(element, CACHE_CONTROL_ELEMENT);
		boolean disabled = "true".equals(getAttribute(cacheControlElement, ATT_DISABLED,
				"false"));
		if (disabled) {
			return;
		}
		if (addIfNotPresent || cacheControlElement != null) {
			addCacheControl();
		}
	}

	private void addCacheControl() {
		BeanDefinitionBuilder headersWriter = BeanDefinitionBuilder
				.genericBeanDefinition(CacheControlHeadersWriter.class);
		headerWriters.add(headersWriter.getBeanDefinition());
	}

	private void parseHstsElement(boolean addIfNotPresent, Element element,
			ParserContext context) {
		Element hstsElement = element == null ? null : DomUtils.getChildElementByTagName(
				element, HSTS_ELEMENT);
		if (addIfNotPresent || hstsElement != null) {
			addHsts(addIfNotPresent, hstsElement, context);
		}
	}

	private void addHsts(boolean addIfNotPresent, Element hstsElement,
			ParserContext context) {
		BeanDefinitionBuilder headersWriter = BeanDefinitionBuilder
				.genericBeanDefinition(HstsHeaderWriter.class);
		if (hstsElement != null) {
			boolean disabled = "true".equals(getAttribute(hstsElement, ATT_DISABLED,
					"false"));
			String includeSubDomains = hstsElement.getAttribute(ATT_INCLUDE_SUBDOMAINS);
			if (StringUtils.hasText(includeSubDomains)) {
				if (disabled) {
					attrNotAllowed(context, ATT_INCLUDE_SUBDOMAINS, ATT_DISABLED,
							hstsElement);
				}
				headersWriter.addPropertyValue("includeSubDomains", includeSubDomains);
			}
			String maxAgeSeconds = hstsElement.getAttribute(ATT_MAX_AGE_SECONDS);
			if (StringUtils.hasText(maxAgeSeconds)) {
				if (disabled) {
					attrNotAllowed(context, ATT_MAX_AGE_SECONDS, ATT_DISABLED,
							hstsElement);
				}
				headersWriter.addPropertyValue("maxAgeInSeconds", maxAgeSeconds);
			}
			String requestMatcherRef = hstsElement.getAttribute(ATT_REQUEST_MATCHER_REF);
			if (StringUtils.hasText(requestMatcherRef)) {
				if (disabled) {
					attrNotAllowed(context, ATT_REQUEST_MATCHER_REF, ATT_DISABLED,
							hstsElement);
				}
				headersWriter.addPropertyReference("requestMatcher", requestMatcherRef);
			}

			if (disabled == true) {
				return;
			}
		}
		if (addIfNotPresent || hstsElement != null) {
			headerWriters.add(headersWriter.getBeanDefinition());
		}
	}

	private void attrNotAllowed(ParserContext context, String attrName,
			String otherAttrName, Element element) {
		context.getReaderContext().error(
				"Only one of '" + attrName + "' or '" + otherAttrName + "' can be set.",
				element);
	}

	private void parseHeaderElements(Element element) {
		List<Element> headerElts = element == null ? Collections.<Element> emptyList()
				: DomUtils.getChildElementsByTagName(element, GENERIC_HEADER_ELEMENT);
		for (Element headerElt : headerElts) {
			String headerFactoryRef = headerElt.getAttribute(ATT_REF);
			if (StringUtils.hasText(headerFactoryRef)) {
				headerWriters.add(new RuntimeBeanReference(headerFactoryRef));
			}
			else {
				BeanDefinitionBuilder builder = BeanDefinitionBuilder
						.genericBeanDefinition(StaticHeadersWriter.class);
				builder.addConstructorArgValue(headerElt.getAttribute(ATT_NAME));
				builder.addConstructorArgValue(headerElt.getAttribute(ATT_VALUE));
				headerWriters.add(builder.getBeanDefinition());
			}
		}
	}

	private void parseContentTypeOptionsElement(boolean addIfNotPresent, Element element) {
		Element contentTypeElt = element == null ? null : DomUtils
				.getChildElementByTagName(element, CONTENT_TYPE_ELEMENT);
		boolean disabled = "true".equals(getAttribute(contentTypeElt, ATT_DISABLED,
				"false"));
		if (disabled) {
			return;
		}
		if (addIfNotPresent || contentTypeElt != null) {
			addContentTypeOptions();
		}
	}

	private void addContentTypeOptions() {
		BeanDefinitionBuilder builder = BeanDefinitionBuilder
				.genericBeanDefinition(XContentTypeOptionsHeaderWriter.class);
		headerWriters.add(builder.getBeanDefinition());
	}

	private void parseFrameOptionsElement(boolean addIfNotPresent, Element element,
			ParserContext parserContext) {
		BeanDefinitionBuilder builder = BeanDefinitionBuilder
				.genericBeanDefinition(XFrameOptionsHeaderWriter.class);

		Element frameElt = element == null ? null : DomUtils.getChildElementByTagName(
				element, FRAME_OPTIONS_ELEMENT);
		if (frameElt != null) {
			String header = getAttribute(frameElt, ATT_POLICY, null);
			boolean disabled = "true"
					.equals(getAttribute(frameElt, ATT_DISABLED, "false"));

			if (disabled && header != null) {
				this.attrNotAllowed(parserContext, ATT_DISABLED, ATT_POLICY, frameElt);
			}
			if (!StringUtils.hasText(header)) {
				header = "DENY";
			}

			if (ALLOW_FROM.equals(header)) {
				String strategyRef = getAttribute(frameElt, ATT_REF, null);
				String strategy = getAttribute(frameElt, ATT_STRATEGY, null);

				if (StringUtils.hasText(strategy) && StringUtils.hasText(strategyRef)) {
					parserContext.getReaderContext().error(
							"Only one of 'strategy' or 'strategy-ref' can be set.",
							frameElt);
				}
				else if (strategyRef != null) {
					builder.addConstructorArgReference(strategyRef);
				}
				else if (strategy != null) {
					String value = getAttribute(frameElt, ATT_VALUE, null);
					if (!StringUtils.hasText(value)) {
						parserContext.getReaderContext().error(
								"Strategy requires a 'value' to be set.", frameElt);
					}
					// static, whitelist, regexp
					if ("static".equals(strategy)) {
						try {
							builder.addConstructorArgValue(new StaticAllowFromStrategy(
									new URI(value)));
						}
						catch (URISyntaxException e) {
							parserContext.getReaderContext().error(
									"'value' attribute doesn't represent a valid URI.",
									frameElt, e);
						}
					}
					else {
						BeanDefinitionBuilder allowFromStrategy;
						if ("whitelist".equals(strategy)) {
							allowFromStrategy = BeanDefinitionBuilder
									.rootBeanDefinition(WhiteListedAllowFromStrategy.class);
							allowFromStrategy.addConstructorArgValue(StringUtils
									.commaDelimitedListToSet(value));
						}
						else {
							allowFromStrategy = BeanDefinitionBuilder
									.rootBeanDefinition(RegExpAllowFromStrategy.class);
							allowFromStrategy.addConstructorArgValue(value);
						}
						String fromParameter = getAttribute(frameElt, ATT_FROM_PARAMETER,
								"from");
						allowFromStrategy.addPropertyValue("allowFromParameterName",
								fromParameter);
						builder.addConstructorArgValue(allowFromStrategy
								.getBeanDefinition());
					}
				}
				else {
					parserContext.getReaderContext()
							.error("One of 'strategy' and 'strategy-ref' must be set.",
									frameElt);
				}
			}
			else {
				builder.addConstructorArgValue(header);
			}

			if (disabled) {
				return;
			}
		}

		if (addIfNotPresent || frameElt != null) {
			headerWriters.add(builder.getBeanDefinition());
		}
	}

	private void parseXssElement(boolean addIfNotPresent, Element element,
			ParserContext parserContext) {
		Element xssElt = element == null ? null : DomUtils.getChildElementByTagName(
				element, XSS_ELEMENT);
		BeanDefinitionBuilder builder = BeanDefinitionBuilder
				.genericBeanDefinition(XXssProtectionHeaderWriter.class);
		if (xssElt != null) {
			boolean disabled = "true".equals(getAttribute(xssElt, ATT_DISABLED, "false"));

			String enabled = xssElt.getAttribute(ATT_ENABLED);
			if (StringUtils.hasText(enabled)) {
				if (disabled) {
					attrNotAllowed(parserContext, ATT_ENABLED, ATT_DISABLED, xssElt);
				}
				builder.addPropertyValue("enabled", enabled);
			}

			String block = xssElt.getAttribute(ATT_BLOCK);
			if (StringUtils.hasText(block)) {
				if (disabled) {
					attrNotAllowed(parserContext, ATT_BLOCK, ATT_DISABLED, xssElt);
				}
				builder.addPropertyValue("block", block);
			}

			if (disabled) {
				return;
			}
		}
		if (addIfNotPresent || xssElt != null) {
			headerWriters.add(builder.getBeanDefinition());
		}
	}

	private String getAttribute(Element element, String name, String defaultValue) {
		if (element == null) {
			return defaultValue;
		}
		String value = element.getAttribute(name);
		if (StringUtils.hasText(value)) {
			return value;
		}
		else {
			return defaultValue;
		}
	}
}
