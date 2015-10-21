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
package com.genuitec.eclipse.gerrit.tools.internal.fbranches.commands;

import java.util.List;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.ui.handlers.HandlerUtil;

import com.genuitec.eclipse.gerrit.tools.internal.utils.commands.SafeCommandHandler;
import com.genuitec.eclipse.gerrit.tools.utils.RepositoryUtils;

public abstract class FeatureBranchCommand extends SafeCommandHandler {

	protected final Object internalExecute(ExecutionEvent event) throws Exception {
		List<Repository> repos = null;
		if (!Boolean.parseBoolean(event.getParameter("all.repos"))) { //$NON-NLS-1$
			repos = RepositoryUtils.getRepositories(
							HandlerUtil.getCurrentSelection(event));
			assert repos != null && !repos.isEmpty();
		} else {
			repos = RepositoryUtils.getAllRepositories();
		}
		String branchRef = event.getParameter("branch.ref"); //$NON-NLS-1$
		execute(event, repos, branchRef);
		return null;
	}

	protected abstract void execute(ExecutionEvent event, 
			List<Repository> repositories, String branchRef) throws ExecutionException;
	
}
