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

import java.io.IOException;
import java.text.MessageFormat;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.egit.core.op.ConnectProviderOperation;
import org.eclipse.egit.core.project.RepositoryMapping;
import org.eclipse.jgit.lib.Repository;
import org.w3c.dom.Element;

import com.genuitec.eclipse.gerrit.tools.GerritToolsPlugin;
import com.genuitec.eclipse.gerrit.tools.utils.RepositoryUtils;
import com.genuitec.eclipse.gerrit.tools.utils.XmlException;

@SuppressWarnings("restriction")
public class GpsGitRepositoryHandler implements IGpsRepositoryHandler {
	
	private IPath repositoryPath;
	private GpsProject parent;
	private Repository repository;
	
	public GpsGitRepositoryHandler(IProject project, GpsProject parent) throws 
			Exception {
		this.parent = parent;
		RepositoryMapping mapping = RepositoryMapping.getMapping(project);
		if (mapping.getRepository().isBare()) {
			throw new GpsFileException("Cannot export projects from bare repositories!");
		}
		repositoryPath = new Path(mapping.getRepository().getDirectory().getParentFile().getName()).
				append(mapping.getRepoRelativePath(project));
	}
	
	public GpsGitRepositoryHandler(Element element, GpsProject parent) throws XmlException {
		this.parent = parent;		
		repositoryPath = new Path(readTextContents(element).trim());
	}
	
	private Repository getRepository() throws CoreException {
		if (repository == null) {
			//acquire project's repository
			repository = RepositoryUtils.getRepositoryForName(repositoryPath.segment(0));
			if (repository == null ) {
				throw new CoreException(new Status(IStatus.ERROR, GerritToolsPlugin.PLUGIN_ID, MessageFormat.format(
						"Cannot import project {0}. A repository named {1} is not configured.",
						parent.getName(),
						repositoryPath.segment(0))));
			}
		}
		return repository;
	}
	
	private IPath getProjectLocation() throws CoreException {		
		//locate the project
		return new Path(getRepository().getDirectory().getParent()).append(
				repositoryPath.removeFirstSegments(1));
	}
	
	public IProject importProject(SubMonitor monitor) throws CoreException {
		monitor.beginTask("", 3);
		
		IPath projectLocation = getProjectLocation();
		
		//create project
		final IProjectDescription projectDescription = ResourcesPlugin.getWorkspace()
				.loadProjectDescription(projectLocation
						.append(IProjectDescription.DESCRIPTION_FILE_NAME));
		final IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(parent.getName());
		project.create(projectDescription, monitor.newChild(1));
		if (monitor.isCanceled()) return null;
		
		//open the project
		project.open(monitor.newChild(1));
		if (monitor.isCanceled()) return null;
		
		//connect the project with repository
		final ConnectProviderOperation connectProviderOperation = new ConnectProviderOperation(
				project, getRepository().getDirectory());
		connectProviderOperation.execute(monitor.newChild(1));
		if (monitor.isCanceled()) return null;
		
		return project;
	}
	
	public IPath getRepositoryPath() {
		return repositoryPath;
	}
	
	public String getType() {
		return "git";
	}

	public void serialize(Element repoConf) {
		repoConf.setTextContent(repositoryPath.toString());
	}

	@Override
	public boolean requiresImport() throws CoreException {
		final IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(parent.getName());
		return !project.exists() || !project.getLocation().equals(getProjectLocation());
	}

}
