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
package com.genuitec.eclipse.egit.tools.internal.fbranches.commands;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.egit.core.op.PushOperationResult;
import org.eclipse.egit.core.op.PushOperationSpecification;
import org.eclipse.egit.ui.Activator;
import org.eclipse.egit.ui.internal.UIText;
import org.eclipse.egit.ui.internal.credentials.EGitCredentialsProvider;
import org.eclipse.egit.ui.internal.push.PushOperationUI;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.handlers.HandlerUtil;

import com.genuitec.eclipse.egit.tools.internal.fbranches.BranchingUtils;
import com.genuitec.eclipse.egit.tools.utils.RepositoryUtils;

@SuppressWarnings("restriction")
public class DeleteFeatureBranchCommand extends FeatureBranchCommand {

	@Override
	protected void execute(ExecutionEvent event, final List<Repository> repositories, String branchRef)
			throws ExecutionException {

		final Shell shell = HandlerUtil.getActiveShell(event);
		
		if (!MessageDialog.openQuestion(shell, "Delete branch", 
				"Delete feature branch: " + branchRef +" ?")) {
			//user cancelled
			return;
		}
		
		for (Repository repo: repositories) {
			try {
				if (//local
						repo.getRef(branchRef) != null ||
						//or remote
						repo.getRef("refs/remotes/origin/" + branchRef.substring("refs/heads/".length())) != null) {
					execute(shell, repo, branchRef);
				}
			} catch (IOException e) {
				throw new ExecutionException(e.getLocalizedMessage(), e);
			}
		}
		
	}
	
	protected void execute(Shell shell, final Repository repository, String branchRef)
			throws ExecutionException {
		Ref branch;
		try {
			branch = repository.getRef(branchRef);
		} catch (IOException e) {
			throw new ExecutionException(e.getLocalizedMessage(), e);
		}
		
		if (branch != null &&
				!BranchingUtils.deleteBranches(shell, repository, Collections.<Ref>singletonList(branch))) {
			//something went wrong
			return;
		}
		
		//branch removed locally - remove it remotely
		try {
			deleteBranchRemotely(repository, branchRef);
		} catch (Exception e) {
			Activator.handleError(
					UIText.RepositoriesView_BranchDeletionFailureMessage, e, true);
		}

		return;
	}

	private void deleteBranchRemotely(final Repository repository, final String branch) throws Exception {
		PushOperationSpecification spec = 
				BranchingUtils.setupPush(repository, ":" + branch ); //$NON-NLS-1$
		
		PushOperationUI op = new PushOperationUI(repository, spec, false) {
			
			@Override
			public PushOperationResult execute(IProgressMonitor monitor)
					throws CoreException {
				PushOperationResult result = super.execute(monitor);
				for (URIish uri: result.getURIs()) {
					if (result.getErrorMessage(uri) != null) {
						return result;
					}
				}
				try {
					//delete reference from local repo
					RefUpdate updateRef = repository.updateRef("refs/remotes/origin/" + branch.substring("refs/heads/".length())); //$NON-NLS-1$ //$NON-NLS-2$
					updateRef.setForceUpdate(true);
					updateRef.delete();
				} catch (Exception e) {
		        	RepositoryUtils.handleException(e);
				}
				return result;
			}
			
		};
		op.setCredentialsProvider(new EGitCredentialsProvider());
		op.start();
	}

}
