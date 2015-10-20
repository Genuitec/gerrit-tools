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

import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.egit.core.EclipseGitProgressTransformer;
import org.eclipse.egit.ui.internal.credentials.EGitCredentialsProvider;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.TagOpt;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;

import com.genuitec.eclipse.gerrit.tools.internal.changes.dialogs.FetchChangeBranchDialog;
import com.genuitec.eclipse.gerrit.tools.utils.MessageLinkDialog;
import com.genuitec.eclipse.gerrit.tools.utils.RepositoryUtils;

@SuppressWarnings("restriction")
public class FetchChangeCommand extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
        Shell shell = HandlerUtil.getActiveShell(event);
        
        Repository repository = RepositoryUtils.getRepository(HandlerUtil.getCurrentSelection(event));
        if (repository == null) {
        	return null;
        }
        
        //configure branch creation
        try {
	        FetchChangeBranchDialog fetchChangeBranchDialog = new FetchChangeBranchDialog(shell, repository);
	        if (fetchChangeBranchDialog.open() != IDialogConstants.OK_ID) {
	            return null;
	        }
	        //perform branch operation
	        try {
	            ProgressMonitorDialog progressDialog = new ProgressMonitorDialog(shell);
	            final CreateChangeBranchOperation op = new CreateChangeBranchOperation(
	                progressDialog.getShell(), event, repository, fetchChangeBranchDialog.getSettings());
	            progressDialog.run(true, true, op);
	        } catch (InterruptedException e) {
	            //ignore
	        } catch (Exception e) {
	        	RepositoryUtils.handleException(e);
	        }
        } catch (NoClassDefFoundError err) {
        	MessageLinkDialog.openWarning(shell, 
        			"Mylyn for Gerrit is not installed", 
        			"To be able to fetch a change from Gerrit, please install Mylyn Gerrit connector. Detailed instructions can be found <a>here</a>.",
        			new MessageLinkDialog.IMessageLinkDialogListener() {
						@Override
						public void linkSelected(SelectionEvent e) {
							try {
								PlatformUI.getWorkbench().getBrowserSupport().getExternalBrowser().openURL(
										new URL("https://github.com/Genuitec/gerrit-tools/wiki/Fetch-from-Gerrit-setup"));
							} catch (Exception ex) {
								RepositoryUtils.handleException(ex);
							}
						}
					});
        }
        
        return null;
	}

    private static class CreateChangeBranchOperation implements IRunnableWithProgress {

    	private Repository repo;
    	private Map<String, Object> settings;
    	private ExecutionEvent event;
    	
		public CreateChangeBranchOperation(Shell shell, ExecutionEvent event, Repository repository,
				Map<String, Object> settings) {
			this.repo = repository;
			this.settings = settings;
			this.event = event;
		}

		public void run(IProgressMonitor monitor)
				throws InvocationTargetException, InterruptedException {
			final String changeBranch = "refs/heads/changes/" + settings.get(FetchChangeBranchDialog.PROP_BRANCH)
					 + "/" + settings.get(FetchChangeBranchDialog.PROP_CHANGE_ID) 
					 		+ "-" + convertToBranchName((String)settings.get(FetchChangeBranchDialog.PROP_CHANGE_TITLE)) 
					 + "/" + settings.get(FetchChangeBranchDialog.PROP_PATCHSET_ID);
			
			int changeId = Integer.parseInt((String)settings.get(FetchChangeBranchDialog.PROP_CHANGE_ID));
			int patchSetId = Integer.parseInt((String)settings.get(FetchChangeBranchDialog.PROP_PATCHSET_ID));
			try {
				List<URIish> urls = new RemoteConfig(repo.getConfig(), "origin").getURIs(); //$NON-NLS-1$
				if (urls.isEmpty()) {
					throw new InvocationTargetException(new RuntimeException("Repository is not configured for fetch"));
				}
				List<RefSpec> refSpecs = new ArrayList<RefSpec>();
				refSpecs.add(new RefSpec(
						String.format("+refs/changes/%02d/%d/%d", changeId % 100, changeId, patchSetId)
						+ ":" + changeBranch));
				
				URIish uri = urls.get(0);
				new Git(repo).fetch().
						setRemote(uri.toPrivateString()).
						setRefSpecs(refSpecs).
						setRemoveDeletedRefs(true).
						setTagOpt(TagOpt.NO_TAGS).
						setDryRun(false).
						setTimeout(3000).
						setCredentialsProvider(new EGitCredentialsProvider()).
						setProgressMonitor( new EclipseGitProgressTransformer(monitor)).
						call();
				
				if ((Boolean)settings.get(FetchChangeBranchDialog.PROP_CHECKOUT)) {
					Display.getDefault().asyncExec( new Runnable() {
						@Override
						public void run() {
							new SwitchToChangeBranchCommand().execute(event, repo, changeBranch);
						}
					});
				}
			} catch (Exception e) {
	        	RepositoryUtils.handleException(e);
			}
		}

		private String convertToBranchName(String name) {
			StringBuilder sb = new StringBuilder(name.length());
			for(int i = 0; i < name.length() && i < 30; i++) {
				char ch = name.charAt(i);
				if (ch < 40 
						|| ch == '~' || ch == '^' || ch == ':' || ch == 177 
						|| ch == '?' || ch == '*' || ch == '/' || ch == '\\'
						|| ch == '.' || ch == '@' || ch == ' ') {
					sb.append('_');
				} else if (ch == '[') {
					sb.append('(');
				} else if (ch == ']') {
					sb.append(')');
				} else {
					sb.append(ch);
				}
			}
			return sb.toString();
		}
		
    }
    
}
