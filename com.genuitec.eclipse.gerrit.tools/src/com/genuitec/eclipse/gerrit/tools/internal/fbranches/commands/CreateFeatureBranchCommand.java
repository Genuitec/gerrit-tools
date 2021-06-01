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

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.egit.core.op.PushOperationResult;
import org.eclipse.egit.core.op.PushOperationSpecification;
import org.eclipse.egit.ui.internal.credentials.EGitCredentialsProvider;
import org.eclipse.egit.ui.internal.fetch.FetchOperationUI;
import org.eclipse.egit.ui.internal.push.PushOperationUI;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.handlers.HandlerUtil;

import com.genuitec.eclipse.gerrit.tools.GerritToolsPlugin;
import com.genuitec.eclipse.gerrit.tools.internal.fbranches.BranchingUtils;
import com.genuitec.eclipse.gerrit.tools.internal.fbranches.dialogs.CreateFeatureBranchDialog;
import com.genuitec.eclipse.gerrit.tools.utils.RepositoryUtils;

@SuppressWarnings("restriction")
public class CreateFeatureBranchCommand extends FeatureBranchCommand {
    
	@Override
	protected void execute(ExecutionEvent event, List<Repository> repositories, String branchRef)
			throws ExecutionException {
        Shell shell = HandlerUtil.getActiveShell(event);
        
        //fetch remote branches
        for (Repository repository: repositories) {
        	RepositoryUtils.fetchOrigin(shell, repository);
        }
        
        //configure branch creation
        CreateFeatureBranchDialog createFeatureBranchDialog = new CreateFeatureBranchDialog(shell, repositories);
        if (createFeatureBranchDialog.open() != IDialogConstants.OK_ID) {
            return;
        }
        
        String userId = RepositoryUtils.getUserId(repositories);
        
        //perform branch operation
        for (Repository repository: repositories) {
	        try {
	            ProgressMonitorDialog progressDialog = new ProgressMonitorDialog(shell);
	            final CreateBranchOperation op = new CreateBranchOperation(
	                progressDialog.getShell(), event, repository, userId, createFeatureBranchDialog.getSettings());
	            progressDialog.run(true, true, op);
	        } catch (InterruptedException e) {
	            //ignore
	        } catch (Exception e) {
	        	RepositoryUtils.handleException(e);
	        }
        }
        
        return;
	}

    private static class CreateBranchOperation implements IRunnableWithProgress {

    	private Repository repository;
    	private Map<String, Object> settings;
    	private ExecutionEvent event;
    	private String userId;
    	
		public CreateBranchOperation(Shell shell, ExecutionEvent event, Repository repository,
				String userId, Map<String, Object> settings) {
			this.repository = repository;
			this.settings = settings;
			this.event = event;
			this.userId = userId;
		}

		public void run(IProgressMonitor monitor)
				throws InvocationTargetException, InterruptedException {
			String stableBranch = "refs/remotes/origin/" + settings.get(CreateFeatureBranchDialog.PROP_STABLE_BRANCH);
			String featureBranch = "refs/heads/features/" + 
					userId + "/" + settings.get(CreateFeatureBranchDialog.PROP_BRANCH_NAME);
			try {
				createBranchRemotely(monitor, stableBranch, featureBranch);
				if ((Boolean)settings.get(CreateFeatureBranchDialog.PROP_CHECKOUT)) {
					new SwitchToBranchCommand().execute(event, repository, featureBranch);
				}
			} catch (Exception e) {
	        	RepositoryUtils.handleException(e);
			}
		}
		
		private void createBranchRemotely(IProgressMonitor monitor, 
				String stableBranch, String featureBranch) throws Exception {
			PushOperationSpecification spec = 
					BranchingUtils.setupPush(repository, stableBranch + ":" + featureBranch ); //$NON-NLS-1$
			
			PushOperationUI op = new PushOperationUI(repository, spec, false);
			op.setCredentialsProvider(new EGitCredentialsProvider());
			PushOperationResult result = op.execute(monitor);
			for (URIish uri: result.getURIs()) {
				String msg = result.getErrorMessage(uri);
				if (msg != null && !msg.isEmpty()) {
					throw new CoreException(new Status(IStatus.ERROR, GerritToolsPlugin.PLUGIN_ID, msg));
				}
			}
			
			FetchOperationUI fetchOp = new FetchOperationUI(repository, new RemoteConfig(repository.getConfig(), "origin"), false);
			fetchOp.setCredentialsProvider(new EGitCredentialsProvider());
			fetchOp.execute(monitor);
		}
    }
	
}
