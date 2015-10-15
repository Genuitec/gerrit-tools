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
package com.genuitec.eclipse.gerrit.tools.internal.gps.model;

import static com.genuitec.eclipse.gerrit.tools.utils.XMLUtils.*;

import java.text.MessageFormat;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.PlatformUI;
import org.w3c.dom.Element;

import com.genuitec.eclipse.gerrit.tools.GerritToolsPlugin;
import com.genuitec.eclipse.gerrit.tools.utils.XmlException;

public class GpsProject {

	private static final String ELEM_WORKING_SET = "working-set"; //$NON-NLS-1$
	private static final String ELEM_REPOSITORY = "repository"; //$NON-NLS-1$
	
	private static final String ATTR_NAME = "name"; //$NON-NLS-1$
	private static final String ATTR_TYPE = "type"; //$NON-NLS-1$
	
	private String name;
	private Set<String> workingSets = new TreeSet<String>();
	private IGpsRepositoryHandler repositoryHandler;
	
	private GpsProject(IProject project) throws GpsFileException {
		name = project.getName();
		for (IWorkingSet ws: PlatformUI.getWorkbench().getWorkingSetManager().getAllWorkingSets()) {
			if (ws.getId() != null &&  ws.getId().equals("org.eclipse.jdt.ui.JavaWorkingSetPage")) { //$NON-NLS-1$
				for (IAdaptable element: ws.getElements()) {
					if (project.equals(element.getAdapter(IProject.class))) {
						workingSets.add(ws.getName());
						break;
					}
				}
			}
		}
		try {
			repositoryHandler = GpsRepositoryFactory.createHandler(project, this);
		} catch (Throwable t) {
			throw new GpsFileException("Cannot serialize project {0}", t, project.getName());
		}
	}
	
	public GpsProject(Element element) throws XmlException {
		name = element.getAttribute(ATTR_NAME);
		if (name == null) {
			reportMissingAttribute(element, ATTR_NAME);
		}
		
		for (Element el: getChildElements(element.getChildNodes())) {
			if (el.getNodeName().equals(ELEM_WORKING_SET)) {
				if (getChildElements(el.getChildNodes()).size() > 0) {
					reportUnsupportedChildren(el);
				}
				String text = el.getTextContent();
				if (text != null) {
					workingSets.add(text.trim());
				} else {
					reportMissingContent(element);
				}
			} else if (el.getNodeName().equals(ELEM_REPOSITORY)) {
				if (repositoryHandler != null) {
					reportOneChildAllowed(element, ELEM_REPOSITORY);
				}
				String type = el.getAttribute(ATTR_TYPE);
				if (type == null) {
					reportMissingAttribute(el, ATTR_TYPE);
				}
				repositoryHandler = GpsRepositoryFactory.createHandler(el, type, this);
			} else {
				reportUnsupportedElement(el, element);
			}
		}
		if (repositoryHandler == null) {
			reportMissingElement(element, ELEM_REPOSITORY);
		}
	}
	
	void setName(String name) {
		this.name = name;
	}
	
	public String getName() {
		return name;
	}
	
	public Set<String> getWorkingSets() {
		return workingSets;
	}
	
	public IGpsRepositoryHandler getRepositoryHandler() {
		return repositoryHandler;
	}

	public void serialize(Element element) {
		element.setAttribute(ATTR_NAME, name);
		
		Element repoConf = element.getOwnerDocument().createElement(ELEM_REPOSITORY);
		element.appendChild(repoConf);
		
		repoConf.setAttribute(ATTR_TYPE, repositoryHandler.getType());
		repositoryHandler.serialize(repoConf);
		for (String workingSet: workingSets) {
			Element tmp = element.getOwnerDocument().createElement(ELEM_WORKING_SET);
			tmp.setTextContent(workingSet);
			element.appendChild(tmp);
		}
	}
	
	public static GpsProject createForProject(IProject project) throws CoreException {
		try {
			GpsProject result = new GpsProject(project);
			return result.repositoryHandler != null ? result : null;
		} catch (GpsFileException ex) {
			throw new CoreException(new Status(IStatus.ERROR, GerritToolsPlugin.PLUGIN_ID, MessageFormat.format(
					"Cannot export project {0}. See error log for details",
					project.getName()), ex));
		}
	}

}
