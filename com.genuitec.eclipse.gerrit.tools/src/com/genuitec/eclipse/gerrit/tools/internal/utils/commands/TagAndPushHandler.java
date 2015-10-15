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
package com.genuitec.eclipse.gerrit.tools.internal.utils.commands;

import java.lang.reflect.InvocationTargetException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.egit.ui.internal.repository.tree.RepositoryNode;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.util.Policy;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.handlers.HandlerUtil;

import com.genuitec.eclipse.gerrit.tools.GerritToolsPlugin;
import com.genuitec.eclipse.gerrit.tools.internal.utils.dialogs.TagAndPushDialog;

@SuppressWarnings("restriction")
public class TagAndPushHandler extends AbstractHandler {

    public static final String PROP_TAG_NAME = "tag.name";
    public static final String PROP_TAG_MESSAGE = "tag.message";
    public static final String PROP_CREATE_TAG = "create.tag";
    public static final String PROP_PUSH_TAG = "push.tag";
    
    private static IWorkspace workspace = ResourcesPlugin.getWorkspace();
    
    public Object execute(ExecutionEvent event) throws ExecutionException {
        IStructuredSelection selection = (IStructuredSelection)HandlerUtil.getCurrentSelection(event);
        Shell shell = HandlerUtil.getActiveShell(event);
        
        List<Repository> repositories = new ArrayList<Repository>(); 
        
        //acquire the list of repositories
        for (Object o: selection.toArray()) {
            RepositoryNode node = (RepositoryNode)o;
            repositories.add(node.getRepository());
            assert node.getRepository() != null;
        }
        
        //configure tagging operation
        TagAndPushDialog tagAndPushConfigDialog = new TagAndPushDialog(shell, repositories);
        if (tagAndPushConfigDialog.open() != IDialogConstants.OK_ID) {
            return null;
        }
        //perform tagging operation
        try {
            ProgressMonitorDialog progressDialog = new ProgressMonitorDialog(shell);
            final TagAndPushOperation op = new TagAndPushOperation(
                progressDialog.getShell(), repositories, tagAndPushConfigDialog.getSettings());
            progressDialog.run(true, true, op);
            shell.getDisplay().asyncExec(new Runnable() {
                
                public void run() {
                    Policy.getStatusHandler().show(op.getResult(), "Results of the operation");
                }
            });
        } catch (InterruptedException e) {
            //ignore
        } catch (Exception e) {
            GerritToolsPlugin.getDefault().getLog().log(new Status(IStatus.ERROR, GerritToolsPlugin.PLUGIN_ID, e.getLocalizedMessage(), e));
            MessageDialog.openError(shell, "Import", e.getLocalizedMessage());
        }
        
        return null;
    }
    
    private static class TagAndPushOperation implements IRunnableWithProgress {

        @SuppressWarnings("unused")
        private Shell parentShell;
        private List<Repository> repositories;
        private Map<String, Object> properties;
        private MultiStatus result; 
        
        public TagAndPushOperation(Shell parentShell, List<Repository> repositories, Map<String, Object> properties) {
            this.parentShell = parentShell;
            this.repositories = repositories;
            this.properties = properties;
        }

        public IStatus getResult() {
            return result;
        }
        
        public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
            try {
                workspace.run(new IWorkspaceRunnable() {
                    public void run(IProgressMonitor monitor) throws CoreException {
                        performTagAndPush(monitor);
                    }
                }, monitor);
            } catch (Exception e) {
                throw new InvocationTargetException(e, e.getLocalizedMessage());
            }
        }
     
        private void performTagAndPush(IProgressMonitor monitor) throws CoreException {
            String tagName = (String)properties.get(PROP_TAG_NAME);
            result = new MultiStatus(GerritToolsPlugin.PLUGIN_ID, 0, "Tag \"" + tagName + "\" operation results", null);
            if ((Boolean)properties.get(PROP_CREATE_TAG)) {
                MultiStatus tagCreation = new MultiStatus(GerritToolsPlugin.PLUGIN_ID, 0, "Creation", null);
                for (Repository repo: repositories) {
                    String repoName = repo.getDirectory().getParentFile().getName();
                    if (!repo.getTags().containsKey(tagName)) {
                        try {
                            new Git(repo).tag().
                                setAnnotated(true).
                                setMessage((String)properties.get(PROP_TAG_MESSAGE)).
                                setName(tagName).
                                call();
                            tagCreation.add(new Status(IStatus.OK, GerritToolsPlugin.PLUGIN_ID, 
                                repoName + ": successfully created"));
                        } catch (Exception e) {
                            tagCreation.add(new Status(IStatus.ERROR, GerritToolsPlugin.PLUGIN_ID,
                                repoName + ": error (see Error Log for details)", e));
                        }
                    } else {
                        tagCreation.add(new Status(IStatus.INFO, "nothing", 
                            repoName + ": already exists"));
                    }
                }
                result.add(tagCreation);
            }
            
            if ((Boolean)properties.get(PROP_PUSH_TAG)) {
                MultiStatus tagPush = new MultiStatus(GerritToolsPlugin.PLUGIN_ID, 0, "Push", null);
                for (Repository repo: repositories) {
                    String repoName = repo.getDirectory().getParentFile().getName();
                    Ref tag = repo.getTags().get(tagName); 
                    if (tag != null) {
                        try {
                            Iterable<PushResult> results = new Git(repo).push().
                                setRefSpecs(new RefSpec("refs/tags/" + tagName + ":refs/tags/" + tagName)).
                                setRemote("origin").
                                call();
                            int status = IStatus.OK;
                            StringBuilder strBuilder = new StringBuilder();
                            strBuilder.append(repoName);
                            for (PushResult res: results) {
                            	for (RemoteRefUpdate updateRes: res.getRemoteUpdates()) {
                            		strBuilder.append(" (");
                            		strBuilder.append(updateRes.getRemoteName());
                            		strBuilder.append("): ");
                            		switch (updateRes.getStatus()) {
                            			case REJECTED_NODELETE:
                            				status = IStatus.ERROR;
                            				strBuilder.append("rejected - cannot delete");
                            				break;
                            			case REJECTED_NONFASTFORWARD:
                            				status = IStatus.ERROR;
                            				strBuilder.append("rejected - non fast-forward");
                            				break;
                            			case REJECTED_REMOTE_CHANGED:
                            				status = IStatus.ERROR;
                            				strBuilder.append("rejected - remote changed");
                            				break;
                            			case REJECTED_OTHER_REASON:
                            				status = IStatus.ERROR;
                            				strBuilder.append("rejected");
                            				break;
                            			case OK:
                            				strBuilder.append("successfully pushed");
                            				break;
                            			case UP_TO_DATE:
                            				strBuilder.append("up to date");
                            				break;
                            			case NON_EXISTING:
                            				status = IStatus.ERROR;
                            				strBuilder.append("reference does not exist");
                            				break;
                            			default:
                            				strBuilder.append(updateRes.getStatus().toString());
                            		}
                            		strBuilder.append(". "); //$NON-NLS-1$
                            		if (updateRes.getMessage() != null) {
                            			strBuilder.append(StringUtils.capitalize(updateRes.getMessage()));
                            		}
                            	}
                            }                            
                            tagPush.add(new Status(status, GerritToolsPlugin.PLUGIN_ID, 
                                strBuilder.toString()));
                        } catch (Exception e) {
                            tagPush.add(new Status(IStatus.ERROR, GerritToolsPlugin.PLUGIN_ID,
                                repoName + ": error (see Error Log for details)", e));
                        }
                    } else {
                        tagPush.add(new Status(IStatus.WARNING, "nothing", 
                            repoName + ": doesn't exist"));
                    }
                }
                result.add(tagPush);
            }
            
            GerritToolsPlugin.getDefault().getLog().log(result);
        }
        
    }

}
