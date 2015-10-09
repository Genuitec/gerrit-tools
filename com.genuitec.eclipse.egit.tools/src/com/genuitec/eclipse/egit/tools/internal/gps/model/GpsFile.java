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
package com.genuitec.eclipse.egit.tools.internal.gps.model;

import static com.genuitec.eclipse.egit.tools.utils.XMLUtils.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.genuitec.eclipse.egit.tools.utils.XmlException;

public class GpsFile {

	private static final String ELEM_ROOT = "projectSet";
	private static final String ELEM_PROJECT = "project";
	private static final String ELEM_REPOSITORIES_CONFIG = "repositories-config";

	private static final String ATTR_TYPE = "type";
	
	List<GpsProject> projects = new ArrayList<GpsProject>();
	Map<String, IGpsRepositoriesConfig> configs = new TreeMap<String, IGpsRepositoriesConfig>();
	
	/**
	 * Load data from the stream and closes it.
	 * @param stream
	 * @throws GpsFileException
	 */
	public void loadFromStream(InputStream stream) throws GpsFileException {
		Document document = null;
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			document = builder.parse(stream);
			projects.clear();
			boolean rootFound = false;
			for (Element el: getChildElements(document.getChildNodes())) {
				if (el.getNodeName().equals(ELEM_ROOT)) {
					if (rootFound) {
						throw new GpsFileException("Only one {0} element allowed", ELEM_ROOT);
					}
					readContents(el);
					rootFound = true;
				} else {
					throw new GpsFileException("Unsupported element {0}. Only {1} can be a root element.", el.getNodeName(), ELEM_ROOT);
				}
			}
		} catch (GpsFileException e) {
			throw e;
		} catch (Exception e) {
			throw new GpsFileException("Error reading GPS file. See error log for details.", e);
		} finally {
			try {
				stream.close();
			} catch (IOException e) {
				//ignore
			}
		}
	}
	
	private void readContents(Element element) throws XmlException, GpsFileException {
		for (Element el: getChildElements(element.getChildNodes())) {
			if (el.getNodeName().equals(ELEM_PROJECT)) {
				projects.add(new GpsProject(el));
			} else if (el.getNodeName().equals(ELEM_REPOSITORIES_CONFIG)) {
				String type = el.getAttribute(ATTR_TYPE);
				if (type == null) {
					reportMissingAttribute(el, ATTR_TYPE);
				}
				if (configs.get(type) != null) {
					throw new GpsFileException("Duplicated configuration element for repository of type {0}", type);
				}
				configs.put(type, GpsRepositoryFactory.createRepositoryConfig(el, type));
			} else {
				reportUnsupportedElement(element, el);
			}
		}
	}
	
	public void saveToStream(OutputStream stream) throws GpsFileException {
		try {
		    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		    DocumentBuilder loader = factory.newDocumentBuilder();
		    Document document = loader.newDocument();
		    
		    //create root element
		    Element root = document.createElement(ELEM_ROOT);
		    document.appendChild(root);
		    
		    //serialize repositories configuration
		    createRepositoryConfigs();
		    for (IGpsRepositoriesConfig config: configs.values()) {
		    	Element prjElement = document.createElement(ELEM_REPOSITORIES_CONFIG);
		    	prjElement.setAttribute(ATTR_TYPE, config.getType());
		    	config.serialize(prjElement);
		    	root.appendChild(prjElement);
		    }
		    
		    //sort project entries in natural order
			Collections.sort(projects, new Comparator<GpsProject>() {
				@Override
				public int compare(GpsProject o1, GpsProject o2) {
					return o1.getName().compareToIgnoreCase(o2.getName());
				}
			});
			
		    //serialize projects
		    for (GpsProject project: projects) {
		    	Element prjElement = document.createElement(ELEM_PROJECT);
		    	project.serialize(prjElement);
		    	root.appendChild(prjElement);
		    }
		    
		    //output DOM to the file
		    DOMSource domSource = new DOMSource(document);
		    Transformer transformer = TransformerFactory.newInstance().newTransformer();
		    transformer.setOutputProperty(OutputKeys.METHOD, "xml");
		    transformer.setOutputProperty(OutputKeys.STANDALONE, "true");
		    transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
		    transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
		    transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		    StreamResult sr = new StreamResult(stream);
		    transformer.transform(domSource, sr);
		} catch (Exception e) {
			throw new GpsFileException("Error saving GPS file. See error log for details.", e);
		}
	}
	
	private void createRepositoryConfigs() throws CoreException {
		configs.clear();
		for (IGpsRepositoriesConfig config: GpsRepositoryFactory.createRepositoryConfigs(this)){
			configs.put(config.getType(), config);
		}
	}
	
	public List<GpsProject> getProjects() {
		return projects;
	}
	
	public Collection<IGpsRepositoriesConfig> getRepositoryConfigs() {
		return Collections.unmodifiableCollection(configs.values());
	}
	
}
