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
package com.genuitec.eclipse.egit.tools.internal.fbranches;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.egit.core.op.DeleteBranchOperation;
import org.eclipse.egit.core.op.PushOperationSpecification;
import org.eclipse.egit.ui.Activator;
import org.eclipse.egit.ui.internal.UIText;
import org.eclipse.egit.ui.internal.dialogs.UnmergedBranchDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.window.Window;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.swt.widgets.Shell;

import com.genuitec.eclipse.egit.tools.EGitToolsPlugin;
import com.genuitec.eclipse.egit.tools.utils.RepositoryUtils;

@SuppressWarnings("restriction")
public final class BranchingUtils {

	public static final int MODE_STABLE = 1 << 0;
	public static final int MODE_FEATURE_USER = 1 << 1;
	public static final int MODE_FEATURE_OTHERS = 1 << 2;
	public static final int MODE_FEATURE = MODE_FEATURE_USER + MODE_FEATURE_OTHERS;

	public static List<String> getBranches(List<Repository> repositories, int mode, String user) {
		List<String> result = new ArrayList<String>();
		for (Repository repo: repositories) {
			for (String branch: getBranches(repo, mode, user)) {
				if (!result.contains(branch)) {
					result.add(branch);
				}
			}
		}
		return result;
	}
	
	public static List<String> getBranches(Repository repository, int mode, String user) {
		if (repository == null) {
			return getBranches(RepositoryUtils.getAllRepositories(), mode, user);
		}
		List<String> result = new ArrayList<String>();
		try {
			Map<String, Ref> remoteBranches = repository.getRefDatabase().getRefs("refs/remotes/origin/");
			if ((mode & MODE_STABLE) != 0) {
				for (String branch: remoteBranches.keySet()) {
					if (branch.indexOf('/') < 0) {
						result.add("refs/heads/" + branch);
					}
				}
			}
			if ((mode & MODE_FEATURE) != 0) {
				String prefix = "features/" + user + "/";
				boolean userBranchMode = (mode & MODE_FEATURE_USER) != 0;
				boolean othersBranchMode = (mode & MODE_FEATURE_OTHERS) != 0;
				for (String branch: remoteBranches.keySet()) {
					boolean userPrefixMatched = branch.startsWith(prefix);
					if ( (userPrefixMatched && userBranchMode) ||
							(!userPrefixMatched && othersBranchMode)) {
						result.add("refs/heads/" + branch);
					}
				}
			}
		} catch (Exception e) {
			EGitToolsPlugin.getDefault().getLog().log(
					new Status(IStatus.ERROR, EGitToolsPlugin.PLUGIN_ID, "Cannot get list of branches", e));
		}
		return result;
	}
	
	public static PushOperationSpecification setupPush(Repository repository, String refSpec) throws IOException, URISyntaxException {
		PushOperationSpecification spec = new PushOperationSpecification();
		Collection<RemoteRefUpdate> updates = Transport
				.findRemoteRefUpdatesFor(repository, 
						Collections.singletonList(new RefSpec(refSpec)),
						null);
		
		RemoteConfig config = new RemoteConfig(repository.getConfig(),
				"origin");
		
		for (URIish uri: config.getPushURIs()) {
			spec.addURIRefUpdates(uri, updates);
			break;
		}
		if (spec.getURIsNumber() == 0) {
			for (URIish uri: config.getURIs()) {
				spec.addURIRefUpdates(uri, updates);
				break;
			}
		}
		if (spec.getURIsNumber() == 0) {
			throw new RuntimeException("Cannot find URI for push");
		}
		return spec;
	}
	
	public static boolean deleteBranches(Shell shell, final Repository repository, final List<Ref> toDelete) {
		final List<Ref> unmerged = new ArrayList<Ref>();
		try {
			new ProgressMonitorDialog(shell).run(true, false,
					new IRunnableWithProgress() {
						public void run(IProgressMonitor monitor)
								throws InvocationTargetException,
								InterruptedException {
							unmerged.addAll(deleteBranches(repository, toDelete, 
									false, monitor));
						}
					});
		} catch (InvocationTargetException e1) {
        	RepositoryUtils.handleException(e1);
			return false;
		} catch (InterruptedException e1) {
			// ignore
			return false;
		}
		if (!unmerged.isEmpty()) {
			MessageDialog messageDialog = new UnmergedBranchDialog<Ref>(
						shell, unmerged);
			if (messageDialog.open() != Window.OK)
				return false;
			try {
				new ProgressMonitorDialog(shell).run(true, false,
						new IRunnableWithProgress() {
							public void run(IProgressMonitor monitor)
									throws InvocationTargetException,
									InterruptedException {
								deleteBranches(repository, toDelete, 
										true, monitor);
							}
						});
			} catch (InvocationTargetException e1) {
	        	RepositoryUtils.handleException(e1);
				return false;
			} catch (InterruptedException e1) {
				// ignore
				return false;
			}
		}
		return true;
	}


	private static List<Ref> deleteBranches(final Repository repository, 
			final List<Ref> nodes, final boolean forceDeletionOfUnmergedBranches,
			IProgressMonitor progressMonitor) throws InvocationTargetException {
		final List<Ref> unmergedNodes = new ArrayList<Ref>();
		try {
			ResourcesPlugin.getWorkspace().run(new IWorkspaceRunnable() {

				public void run(IProgressMonitor monitor) throws CoreException {
					monitor.beginTask(UIText.DeleteBranchCommand_DeletingBranchesProgress, nodes.size());
					for (Ref refNode : nodes) {
						int result = deleteBranch(repository, 
								refNode, forceDeletionOfUnmergedBranches);
						if (result == DeleteBranchOperation.REJECTED_CURRENT) {
							throw new CoreException(
									Activator
									.createErrorStatus(
											UIText.DeleteBranchCommand_CannotDeleteCheckedOutBranch,
											null));
						} else if (result == DeleteBranchOperation.REJECTED_UNMERGED) {
							unmergedNodes.add(refNode);
						} else
							monitor.worked(1);
					}
				}
			}, progressMonitor);

		} catch (CoreException ex) {
			throw new InvocationTargetException(ex);
		} finally {
			progressMonitor.done();
		}
		return unmergedNodes;
	}

	private static int deleteBranch(final Repository repo, final Ref ref, boolean force)
			throws CoreException {
		DeleteBranchOperation dbop = new DeleteBranchOperation(repo, ref, force);
		dbop.execute(null);
		return dbop.getStatus();
	}
	
	private BranchingUtils() {}
	
}
