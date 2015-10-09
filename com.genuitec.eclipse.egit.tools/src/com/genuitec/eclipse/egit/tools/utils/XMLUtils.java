/**
 *  Copyright (c) 2015 Genuitec LLC.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *  Piotr Tomiak <piotr@genuitec.com> - initial API and implementation
 */
package com.genuitec.eclipse.egit.tools.utils;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class XMLUtils {

	private XMLUtils() {
	}

	public static List<Element> getChildElements(NodeList list) {
		List<Element> result = new ArrayList<Element>();
		for (int i = 0; i < list.getLength(); i++) {
			Node node = list.item(i);
			if (node instanceof Element) {
				Element el = (Element)node;
				result.add(el);
			}
		}
		return result;
	}
	
	public static String readTextContents(Element element) throws XmlException {
		if (getChildElements(element.getChildNodes()).size() > 0) {
			reportUnsupportedChildren(element);
		}
		String tmp = element.getTextContent();
		if (tmp == null) {
			reportMissingContent(element);
		}
		return tmp;
	}
	
	public static void reportMissingAttribute(Element el, String attrName) throws XmlException {
		throw new XmlException("Element <{0}> is missing required attribute {1}", el.getNodeName(), attrName);
	}
	
	public static void reportMissingElement(Element el, String elementName) throws XmlException {
		if (el.getAttribute("type") != null) {
			throw new XmlException("Element <{0}> of type {2} is missing required child element <{1}>", el.getNodeName(), elementName, el.getAttribute("type"));
		} else {
			throw new XmlException("Element <{0}> is missing required child element <{1}>", el.getNodeName(), elementName);
		}
	}
	
	public static void reportMissingContent(Element el) throws XmlException {
		throw new XmlException("Element <{0}> is missing required text content", el.getNodeName());
	}
	
	public static void reportUnsupportedElement(Element parent, Element child) throws XmlException {
		throw new XmlException("Element <{0}> cannot be a child of an element <{1}>", child.getNodeName(), parent.getNodeName());
	}
	
	public static void reportUnsupportedChildren(Element el) throws XmlException {
		throw new XmlException("Element <{0}> cannot have children", el);
	}
	
	public static void reportOneChildAllowed(Element parent, String child) throws XmlException {
		throw new XmlException("Each <{0}> element can have only one <{1}> element.", parent.getNodeName(), child);
	}
}
