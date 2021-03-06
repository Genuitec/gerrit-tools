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

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.SubMonitor;
import org.w3c.dom.Element;

public interface IGpsRepositoryHandler {

	public String getType();
	
	public IProject importProject(SubMonitor monitor) throws CoreException;

	public void serialize(Element repoConf);

	public boolean requiresImport() throws CoreException;
	
}
