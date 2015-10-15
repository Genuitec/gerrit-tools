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
package com.genuitec.eclipse.gerrit.tools.internal.changes.commands;

import java.io.IOException;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jgit.api.CheckoutResult;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.ui.handlers.HandlerUtil;

import com.genuitec.eclipse.gerrit.tools.utils.RepositoryUtils;
import com.genuitec.org.eclipse.egit.ui.internal.branch.BranchOperationUI;
import com.genuitec.org.eclipse.egit.ui.internal.branch.BranchOperationUI.DoneCallback;

public class SwitchToChangeBranchCommand extends AbstractHandler {

	public Object execute(final ExecutionEvent event) throws ExecutionException {
		final Repository repository = RepositoryUtils.getRepository(HandlerUtil.getCurrentSelection(event));
		if (repository == null) {
			return null;
		}
		final String branchRef = (String)event.getParameter("branch.ref"); //$NON-NLS-1$
		if (branchRef == null) {
			return null;
		}
		execute(event, repository, branchRef);
		return null;
	}
	
	public void execute (final ExecutionEvent event, final Repository repository, final String branchRef) {
		BranchOperationUI op = BranchOperationUI.checkout(repository, branchRef)
				.doneCallback( new DoneCallback() {
					
					public void done(CheckoutResult result) {

						if (result.getStatus() == CheckoutResult.Status.OK) {
							IPath path = new Path(branchRef);
							String stableBranch = path.segment(3);
							if (stableBranch.equals("features") && path.segmentCount() > 6) {
								stableBranch = path.removeFirstSegments(3).uptoSegment(3).toString();
							}
							String newUpstreamRef = branchRef + ":refs/for/" + stableBranch; //$NON-NLS-1$
							repository.getConfig().setString("remote", "origin", "push",  //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
									newUpstreamRef);
							try {
								repository.getConfig().save();
							} catch (IOException e) {
					        	RepositoryUtils.handleException(e);
							}
						}
					}
				});
		op.start();
	}
	
}
