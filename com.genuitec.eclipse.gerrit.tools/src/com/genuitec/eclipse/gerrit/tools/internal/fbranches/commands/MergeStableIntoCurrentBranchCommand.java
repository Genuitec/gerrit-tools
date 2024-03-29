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
import java.lang.reflect.InvocationTargetException;
import java.text.MessageFormat;
import java.util.List;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.egit.core.internal.CoreText;
import org.eclipse.egit.core.op.MergeOperation;
import org.eclipse.egit.ui.internal.UIText;
import org.eclipse.egit.ui.internal.credentials.EGitCredentialsProvider;
import org.eclipse.egit.ui.internal.fetch.FetchOperationUI;
import org.eclipse.egit.ui.internal.merge.MergeResultDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeCommand.FastForwardMode;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryState;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.team.core.TeamException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;

import com.genuitec.eclipse.gerrit.tools.utils.RepositoryUtils;

@SuppressWarnings("restriction")
public class MergeStableIntoCurrentBranchCommand extends FeatureBranchCommand {

	@Override
	protected void execute(ExecutionEvent event, List<Repository> repositories, String branchRef)
			throws ExecutionException {
		
		//perform branch operation
		Shell shell = HandlerUtil.getActiveShell(event);
		for (Repository repository: repositories) {
	        try {
				if (!canMerge(repository)) {
					continue;
				}
	            ProgressMonitorDialog progressDialog = new ProgressMonitorDialog(shell);
	            final MergeStableIntoCurrentBranchOperation op = new MergeStableIntoCurrentBranchOperation(
	                event, branchRef, repository);
	            progressDialog.run(true, true, op);
	        } catch (InterruptedException e) {
	            //ignore
	        } catch (Exception e) {
	        	RepositoryUtils.handleException(e);
	        }
		}
        
	}

	private boolean canMerge(final Repository repository) {
		String message = null;
		Exception ex = null;
		try {
			Ref head = repository.findRef(Constants.HEAD);
			if (head == null || !head.isSymbolic())
				message = UIText.MergeAction_HeadIsNoBranch;
			else if (!repository.getRepositoryState().equals(
					RepositoryState.SAFE))
				message = NLS.bind(UIText.MergeAction_WrongRepositoryState,
						repository.getRepositoryState());
			else if (!head.getLeaf().getName().startsWith("refs/heads/features"))	 { //$NON-NLS-1$
				message = "Current branch is not a feature branch.";
			}
		} catch (IOException e) {
			message = e.getMessage();
			ex = e;
		}

		if (message != null)
			org.eclipse.egit.ui.Activator.handleError(message, ex, true);
		return (message == null);
	}
	
    private static class MergeStableIntoCurrentBranchOperation implements IRunnableWithProgress {

    	private Repository repository;
    	private String branchRef;
    	
		public MergeStableIntoCurrentBranchOperation(ExecutionEvent event, 
				String branchRef, Repository repository) {
			this.repository = repository;
			this.branchRef = branchRef;
		}

		public void run(IProgressMonitor monitor)
				throws InvocationTargetException, InterruptedException {
			try {
				SubMonitor mon = SubMonitor.convert(monitor, 100);
				mon.setTaskName("Fetch from upstream...");
				fetchOrigin(mon.newChild(50));

				mon.setTaskName(MessageFormat.format("Merging with {0}...", branchRef));
				performMerge(mon.newChild(50));
			} catch (Exception e) {
	        	RepositoryUtils.handleException(e);
			}
		}
		
		private void performMerge(SubMonitor monitor) {
			final String branchName = branchRef.substring("refs/heads/".length()); //$NON-NLS-1$
			final String refName = "refs/remotes/origin/" + branchName; //$NON-NLS-1$
			
			String featureBranchName;
			String userName = "anonymous";
			try {
				Ref head = repository.findRef(Constants.HEAD);
				featureBranchName = head.getLeaf().getName();
				IPath path = new Path(featureBranchName);
				if (path.segmentCount() > 4 && "features".equals(path.segment(2))) { //$NON-NLS-1$
					featureBranchName = path.removeFirstSegments(4).toString();
					userName = path.segment(3);
				}
			} catch (IOException e1) {
				throw new RuntimeException(e1);
			}
			final String commitMessage = MessageFormat.format("Merge \"{0}\" into feature branch \"{1}\" of user {2}.", 
					branchName, featureBranchName, userName);
			
			final MergeOperation op = new MergeOperation(
					repository, refName);
			op.setFastForwardMode(FastForwardMode.FF);
			
			String jobname = NLS.bind(UIText.MergeAction_JobNameMerge, refName);
			Job job = new WorkspaceJob(jobname) {

				@Override
				public IStatus runInWorkspace(IProgressMonitor monitor) {
					try {
						op.execute(monitor);
						
						//Add Gerrit change-id to the commit
						try {
							new Git(repository)
								.commit()
								.setAmend(true)
								.setMessage(commitMessage)
								.setInsertChangeId(true)
								.call();
						} catch (Exception e) {
							throw new TeamException(
									CoreText.MergeOperation_InternalError, e);
						}
						
					} catch (final CoreException e) {
						return e.getStatus();
					}
					return Status.OK_STATUS;
				}
			};
			job.setUser(true);
			job.setRule(op.getSchedulingRule());
			job.addJobChangeListener(new JobChangeAdapter() {
				@Override
				public void done(IJobChangeEvent jobEvent) {
					IStatus result = jobEvent.getJob().getResult();
					if (result.getSeverity() == IStatus.CANCEL)
						Display.getDefault().asyncExec(new Runnable() {
							public void run() {
								// don't use getShell(event) here since
								// the active shell has changed since the
								// execution has been triggered.
								Shell shell = PlatformUI.getWorkbench()
										.getActiveWorkbenchWindow().getShell();
								MessageDialog.openInformation(shell,
										UIText.MergeAction_MergeCanceledTitle,
										UIText.MergeAction_MergeCanceledMessage);
							}
						});
					else if (!result.isOK())
						org.eclipse.egit.ui.Activator.handleError(result.getMessage(), result
								.getException(), true);
					else
						Display.getDefault().asyncExec(new Runnable() {
							public void run() {
								Shell shell = PlatformUI.getWorkbench()
										.getActiveWorkbenchWindow().getShell();
								MergeResultDialog.getDialog(shell, repository, op.getResult()).open();
							}
						});
				}
			});
			job.schedule();
		}
		
		private void fetchOrigin(IProgressMonitor monitor) throws Exception {
			FetchOperationUI fetchOp = new FetchOperationUI(repository, 
					new RemoteConfig(repository.getConfig(), "origin"), false); //$NON-NLS-1$
			fetchOp.setCredentialsProvider(new EGitCredentialsProvider());
			fetchOp.execute(monitor);
		}		
		
    }
}
