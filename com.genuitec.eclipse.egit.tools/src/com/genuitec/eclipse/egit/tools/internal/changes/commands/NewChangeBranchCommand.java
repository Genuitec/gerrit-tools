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
package com.genuitec.eclipse.egit.tools.internal.changes.commands;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.handlers.HandlerUtil;

import com.genuitec.eclipse.egit.tools.internal.changes.dialogs.CreateChangeBranchDialog;
import com.genuitec.eclipse.egit.tools.utils.RepositoryUtils;

public class NewChangeBranchCommand extends AbstractHandler {
	
	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
        Shell shell = HandlerUtil.getActiveShell(event);
        
        Repository repository = RepositoryUtils.getRepository(HandlerUtil.getCurrentSelection(event));
        if (repository == null) {
        	return null;
        }

        //fetch remote branches
        RepositoryUtils.fetchOrigin(shell, repository);
        
        //configure branch creation
        CreateChangeBranchDialog createChangeBranchDialog = new CreateChangeBranchDialog(shell, repository);
        if (createChangeBranchDialog.open() != IDialogConstants.OK_ID) {
            return null;
        }
        //perform branch operation
        try {
            ProgressMonitorDialog progressDialog = new ProgressMonitorDialog(shell);
            final CreateChangeBranchOperation op = new CreateChangeBranchOperation(
                progressDialog.getShell(), event, repository, createChangeBranchDialog.getSettings());
            progressDialog.run(true, true, op);
        } catch (InterruptedException e) {
            //ignore
        } catch (Exception e) {
        	RepositoryUtils.handleException(e);
        }
        
        return null;
	}

    private static class CreateChangeBranchOperation implements IRunnableWithProgress {

    	private Repository repository;
    	private Map<String, Object> settings;
    	private ExecutionEvent event;
    	
		public CreateChangeBranchOperation(Shell shell, ExecutionEvent event, Repository repository,
				Map<String, Object> settings) {
			this.repository = repository;
			this.settings = settings;
			this.event = event;
		}

		public void run(IProgressMonitor monitor)
				throws InvocationTargetException, InterruptedException {
			String stableBranch = "refs/remotes/origin/" + settings.get(CreateChangeBranchDialog.PROP_STABLE_BRANCH);
			String simpleBranchName = "changes/" + settings.get(CreateChangeBranchDialog.PROP_STABLE_BRANCH)
					 + "/" + settings.get(CreateChangeBranchDialog.PROP_CHANGE_BRANCH_NAME);
			String changeBranch = "refs/heads/" + simpleBranchName;
			try {
				Ref stable = repository.getRef(stableBranch);
				AnyObjectId oid = stable.getLeaf().getObjectId();
				
				RefUpdate refUpdate = repository.updateRef(changeBranch);
				refUpdate.setNewObjectId(oid);
				
				Result res = refUpdate.update();
				switch (res) {
					case FAST_FORWARD: case NEW: case FORCED:
					case NO_CHANGE: case RENAMED:
						//we are fine;
						break;
					default:
						throw new RuntimeException("Cannot create change branch: " + res.toString());
				}
				
				//configure for pull
				StoredConfig config = repository.getConfig();
				config.setString("branch", simpleBranchName, "remote", "origin");  //$NON-NLS-1$//$NON-NLS-2$
				config.setString("branch", simpleBranchName, "merge", "refs/heads/" + settings.get(CreateChangeBranchDialog.PROP_STABLE_BRANCH));  //$NON-NLS-1$//$NON-NLS-2$
				config.setBoolean("branch", simpleBranchName, "rebase", true);  //$NON-NLS-1$//$NON-NLS-2$
				
				
				if ((Boolean)settings.get(CreateChangeBranchDialog.PROP_CHECKOUT)) {
					new SwitchToChangeBranchCommand().execute(event, repository, changeBranch);
				}
			} catch (Exception e) {
	        	RepositoryUtils.handleException(e);
			}
		}
		
    }
    
}
