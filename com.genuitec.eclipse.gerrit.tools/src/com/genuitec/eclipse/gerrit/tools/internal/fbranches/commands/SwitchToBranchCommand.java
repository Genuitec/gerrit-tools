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

import java.io.IOException;
import java.text.MessageFormat;
import java.util.List;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jgit.api.CheckoutResult;
import org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

import com.genuitec.eclipse.gerrit.tools.utils.RepositoryUtils;
import com.genuitec.org.eclipse.egit.ui.internal.branch.BranchOperationUI;
import com.genuitec.org.eclipse.egit.ui.internal.branch.BranchOperationUI.DoneCallback;

public class SwitchToBranchCommand extends FeatureBranchCommand {

	@Override
	protected void execute(ExecutionEvent event, final List<Repository> repositories, final String branchRef)
			throws ExecutionException {

		String branchName = branchRef.substring("refs/heads/".length()); //$NON-NLS-1$
		String remoteBranchRef = "refs/remotes/origin/" + branchName; //$NON-NLS-1$
		
		for (Repository repo: repositories) {
			try {
				Ref remoteRef = repo.getRef(remoteBranchRef);
				if (remoteRef != null) {
					execute(event, repo, branchRef);
				}
			} catch (IOException e) {
				throw new ExecutionException(e.getMessage(), e);
			}
		}
	}
	
	protected void execute(ExecutionEvent event, final Repository repository, final String branchRef)
			throws ExecutionException {		
		//check if local branch exists
		final String branchName = branchRef.substring("refs/heads/".length()); //$NON-NLS-1$
		try {
			if (repository.getRef(branchRef) == null) {
				//create local branch
				String remoteBranchRef = "refs/remotes/origin/" + branchName; //$NON-NLS-1$
				Ref remoteRef = repository.getRef(remoteBranchRef);
				if (remoteRef == null) {
					throw new RuntimeException(MessageFormat.format(
							"Remote branch {0} doesn't exist",
							remoteBranchRef));
				}
				new Git(repository).branchCreate().
					setName(branchName).
					setStartPoint(remoteBranchRef).
					setUpstreamMode(SetupUpstreamMode.TRACK).
					call();
				repository.getConfig().setBoolean("branch", branchName, "rebase", true);  //$NON-NLS-1$//$NON-NLS-2$
			}
		} catch (Exception e1) {
        	RepositoryUtils.handleException(e1);
			return;
		}
		
		BranchOperationUI op = BranchOperationUI.checkout(repository, branchRef)
				.doneCallback( new DoneCallback() {
					
					public void done(CheckoutResult result) {

						if (result.getStatus() == CheckoutResult.Status.OK) {
							String newUpstreamRef = branchRef + ":refs/for/" + branchName; //$NON-NLS-1$
							repository.getConfig().setString("remote", "origin", "push",  //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
									newUpstreamRef);
							//ensure in rebase mode
							repository.getConfig().setBoolean("branch", branchName, "rebase", true);  //$NON-NLS-1$//$NON-NLS-2$
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
