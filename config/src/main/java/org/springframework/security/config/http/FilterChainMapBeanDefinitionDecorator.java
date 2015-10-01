package org.springframework.security.config.http;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.BeanMetadataElement;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.ManagedList;
import org.springframework.beans.factory.support.ManagedMap;
import org.springframework.beans.factory.xml.BeanDefinitionDecorator;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.security.config.Elements;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.util.StringUtils;
import org.springframework.util.xml.DomUtils;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Sets the filter chain Map for a FilterChainProxy bean declaration.
 *
 * @author Luke Taylor
 */
public class FilterChainMapBeanDefinitionDecorator implements BeanDefinitionDecorator {

	@SuppressWarnings("unchecked")
	public BeanDefinitionHolder decorate(Node node, BeanDefinitionHolder holder,
			ParserContext parserContext) {
		BeanDefinition filterChainProxy = holder.getBeanDefinition();

		ManagedList<BeanMetadataElement> securityFilterChains = new ManagedList<BeanMetadataElement>();
		Element elt = (Element) node;

		MatcherType matcherType = MatcherType.fromElement(elt);

		List<Element> filterChainElts = DomUtils.getChildElementsByTagName(elt,
				Elements.FILTER_CHAIN);

		for (Element chain : filterChainElts) {
			String path = chain
					.getAttribute(HttpSecurityBeanDefinitionParser.ATT_PATH_PATTERN);
			String filters = chain
					.getAttribute(HttpSecurityBeanDefinitionParser.ATT_FILTERS);

			if (!StringUtils.hasText(path)) {
				parserContext.getReaderContext().error(
						"The attribute '"
								+ HttpSecurityBeanDefinitionParser.ATT_PATH_PATTERN
								+ "' must not be empty", elt);
			}

			if (!StringUtils.hasText(filters)) {
				parserContext.getReaderContext().error(
						"The attribute '" + HttpSecurityBeanDefinitionParser.ATT_FILTERS
								+ "'must not be empty", elt);
			}

			BeanDefinition matcher = matcherType.createMatcher(path, null);

			if (filters.equals(HttpSecurityBeanDefinitionParser.OPT_FILTERS_NONE)) {
				securityFilterChains.add(createSecurityFilterChain(matcher,
						new ManagedList(0)));
			}
			else {
				String[] filterBeanNames = StringUtils
						.tokenizeToStringArray(filters, ",");
				ManagedList filterChain = new ManagedList(filterBeanNames.length);

				for (String name : filterBeanNames) {
					filterChain.add(new RuntimeBeanReference(name));
				}

				securityFilterChains.add(createSecurityFilterChain(matcher, filterChain));
			}
		}

		filterChainProxy.getConstructorArgumentValues().addGenericArgumentValue(
				securityFilterChains);

		return holder;
	}

	private BeanDefinition createSecurityFilterChain(BeanDefinition matcher,
			ManagedList<?> filters) {
		BeanDefinitionBuilder sfc = BeanDefinitionBuilder
				.rootBeanDefinition(DefaultSecurityFilterChain.class);
		sfc.addConstructorArgValue(matcher);
		sfc.addConstructorArgValue(filters);
		return sfc.getBeanDefinition();
	}
}
