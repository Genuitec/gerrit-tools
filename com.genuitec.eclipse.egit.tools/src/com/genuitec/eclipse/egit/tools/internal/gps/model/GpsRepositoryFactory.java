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

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.egit.core.GitProvider;
import org.eclipse.team.core.RepositoryProvider;
import org.w3c.dom.Element;

import com.genuitec.eclipse.egit.tools.utils.XmlException;

@SuppressWarnings("restriction")
public class GpsRepositoryFactory {

	public static IGpsRepositoryHandler createHandler(IProject project, GpsProject gpsProject) 
			throws Exception {
		RepositoryProvider provider = RepositoryProvider.getProvider(project);
		if ("org.eclipse.team.internal.ccvs.core.CVSTeamProvider".equals( //$NON-NLS-1$
				provider.getClass().getName())) {
			return new GpsCvsRepositoryHandler(project, gpsProject);
		} else if (provider instanceof GitProvider) {
			return new GpsGitRepositoryHandler(project, gpsProject);
		}
		return null;
	}
	
	public static IGpsRepositoryHandler createHandler(Element el, String type, GpsProject project) throws XmlException {
		if (type.equals("git")) {
			return new GpsGitRepositoryHandler(el, project);
		} else if (type.equals("cvs")) {
			return new GpsCvsRepositoryHandler(el, project);
		} else {
			throw new XmlException("Cannot handle repositories of type {0}", type);
		}
	}

	public static IGpsRepositoriesConfig[] createRepositoryConfigs(GpsFile gpsFile) throws CoreException {
		return new IGpsRepositoriesConfig[] { new GpsGitRepositoriesConfig(gpsFile) };
	}
	
	public static IGpsRepositoriesConfig createRepositoryConfig(Element el, String type) throws XmlException, GpsFileException {
		if (type.equals("git")) {
			return new GpsGitRepositoriesConfig(el);
		} else {
			throw new GpsFileException("Cannot handle configuration of repositories of type {0}", type);
		}
	}
	
}
