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

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.team.core.RepositoryProvider;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.CVSTag;
import org.eclipse.team.internal.ccvs.core.CVSTeamProvider;
import org.eclipse.team.internal.ccvs.core.ICVSFolder;
import org.eclipse.team.internal.ccvs.core.ICVSRemoteFolder;
import org.eclipse.team.internal.ccvs.core.ICVSRepositoryLocation;
import org.eclipse.team.internal.ccvs.core.connection.CVSRepositoryLocation;
import org.eclipse.team.internal.ccvs.core.resources.CVSWorkspaceRoot;
import org.eclipse.team.internal.ccvs.core.resources.RemoteFolder;
import org.eclipse.team.internal.ccvs.core.syncinfo.FolderSyncInfo;
import org.eclipse.team.internal.ccvs.core.util.KnownRepositories;
import org.eclipse.team.internal.ccvs.ui.operations.CheckoutSingleProjectOperation;
import org.w3c.dom.Element;

import com.genuitec.eclipse.gerrit.tools.GerritToolsPlugin;
import com.genuitec.eclipse.gerrit.tools.utils.XmlException;

@SuppressWarnings("restriction")
public class GpsCvsRepositoryHandler implements IGpsRepositoryHandler {

	private static final String ELEM_TAG = "tag";
	private static final String ELEM_ADDRESS = "address";
	private static final String ELEM_LOCATION = "location";
	
	private GpsProject parent;
	private String tag;
	private String address;
	private String location;
	
	public GpsCvsRepositoryHandler(IProject project, GpsProject parent) throws Exception {
		this.parent = parent;
		CVSTeamProvider provider = (CVSTeamProvider) RepositoryProvider.getProvider(project);
		
		CVSWorkspaceRoot root = provider.getCVSWorkspaceRoot();
		CVSRepositoryLocation location = CVSRepositoryLocation.fromString(root.getRemoteLocation().getLocation(false));
		location.setUserMuteable(true);
		address = location.getLocation(false);
		
		ICVSFolder folder = root.getLocalRoot();
		FolderSyncInfo syncInfo = folder.getFolderSyncInfo();
		
		this.location = syncInfo.getRepository() /*module*/;

		/* CVS import handler does not allow us to import the project under different name */
		parent.setName( folder.getName() /* project name */ );
		
		CVSTag tag = syncInfo.getTag();
		if (tag != null) {
			if (tag.getType() != CVSTag.DATE) {
				this.tag = tag.getName();
			}
		}
		
	}
	
	public GpsCvsRepositoryHandler(Element element, GpsProject parent) throws XmlException {
		this.parent = parent;
		for (Element el: getChildElements(element.getChildNodes())) {
			if (el.getNodeName().equals(ELEM_TAG)) {
				if (tag != null) {
					reportOneChildAllowed(element, ELEM_TAG);
				}
				tag = readTextContents(el);
			} else if (el.getNodeName().equals(ELEM_ADDRESS)) {
				if (address != null) {
					reportOneChildAllowed(element, ELEM_ADDRESS);
				}
				address = readTextContents(el);
			} else if (el.getNodeName().equals(ELEM_LOCATION)) {
				if (location != null) {
					reportOneChildAllowed(element, ELEM_LOCATION);
				}
				location = readTextContents(el);
			} else {
				reportUnsupportedElement(element, el);
			}
		}
		if (address == null) {
			reportMissingElement(element, ELEM_ADDRESS);
		}
		if (location == null) {
			reportMissingElement(element, ELEM_LOCATION);
		}
	}

	public IProject importProject(SubMonitor monitor) throws CoreException {
		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(parent.getName());
		try {
			ICVSRepositoryLocation cvsLocation = getLocationFromString(address);
			
			CVSTag cvsTag = tag != null ? new CVSTag(tag, CVSTag.BRANCH) : null;
			
			ICVSRemoteFolder remote = new RemoteFolder(null, cvsLocation, location, cvsTag);
			new CheckoutSingleProjectOperation(null /* no part */, remote, project, null /* location */, true).
					run(monitor);
		} catch (CoreException ex) {
			throw ex;
		} catch (OperationCanceledException ex) {
			monitor.setCanceled(true);
			return null;
		} catch (Exception ex) {
			throw new CoreException(new Status(IStatus.ERROR, GerritToolsPlugin.PLUGIN_ID, ex.getLocalizedMessage(), ex));
		}
		return project;
	}
	

	private ICVSRepositoryLocation getLocationFromString(String repo) throws CVSException {
		// create the new location
		ICVSRepositoryLocation newLocation = CVSRepositoryLocation.fromString(repo);
		if (newLocation.getUsername() == null || newLocation.getUsername().length() == 0) {
			// look for an existing location that matched
			ICVSRepositoryLocation[] locations = KnownRepositories.getInstance().getRepositories();
			for (int i = 0; i < locations.length; i++) {
				ICVSRepositoryLocation location = locations[i];
				if (location.getMethod() == newLocation.getMethod()
					&& location.getHost().equals(newLocation.getHost())
					&& location.getPort() == newLocation.getPort()
					&& location.getRootDirectory().equals(newLocation.getRootDirectory()))
						return location;
			}
		}
		return newLocation;
	}
	
	public String getType() {
		return "cvs";
	}

	public void serialize(Element repoConf) {
		Element tmp = repoConf.getOwnerDocument().createElement(ELEM_ADDRESS);
		tmp.setTextContent(address);
		repoConf.appendChild(tmp);
		
		tmp = repoConf.getOwnerDocument().createElement(ELEM_LOCATION);
		tmp.setTextContent(location);
		repoConf.appendChild(tmp);
		
		if (tag != null) {
			tmp = repoConf.getOwnerDocument().createElement(ELEM_TAG);
			tmp.setTextContent(tag);
			repoConf.appendChild(tmp);
		}
	}

	@Override
	public boolean requiresImport() throws CoreException {
		//always import CVS projects
		return true;
	}
	
}
