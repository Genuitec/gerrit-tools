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
package com.genuitec.eclipse.egit.tools.internal.gps.commands;

import java.io.FileInputStream;
import java.lang.reflect.InvocationTargetException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.egit.core.GitProvider;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.team.core.RepositoryProviderType;
import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.IWorkingSetManager;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;

import com.genuitec.eclipse.egit.tools.EGitToolsPlugin;
import com.genuitec.eclipse.egit.tools.internal.gps.dialogs.ImportProjectsDialog;
import com.genuitec.eclipse.egit.tools.internal.gps.dialogs.ObsoleteProjectsDialog;
import com.genuitec.eclipse.egit.tools.internal.gps.model.GpsFile;
import com.genuitec.eclipse.egit.tools.internal.gps.model.GpsProject;
import com.genuitec.eclipse.egit.tools.internal.gps.model.IGpsRepositoriesConfig;

@SuppressWarnings("restriction")
public class ImportProjectsHandler extends AbstractHandler {

	private static IWorkspace workspace = ResourcesPlugin.getWorkspace();
	private static IWorkingSetManager workingSetManager = PlatformUI.getWorkbench().getWorkingSetManager();
	
	public ImportProjectsHandler() {
		
	}

	public Object execute(ExecutionEvent event) throws ExecutionException {
		Shell shell = HandlerUtil.getActiveShell(event);
		if (shell == null) {
			shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
		}
		
		String fileName = event.getParameter("file"); //$NON-NLS-1$
		if (fileName == null) {
			//first open the file
			FileDialog fd = new FileDialog(shell, SWT.OPEN);
	        fd.setText("Import Genuitec Project Set file");
	        fd.setFilterPath(ResourcesPlugin.getWorkspace().getRoot().getLocation().toOSString());
	        String[] filterExt = { "*.gps" }; //$NON-NLS-1$
	        fd.setFilterExtensions(filterExt);
	        fileName = fd.open();
		}
        
        if (fileName != null) {
	        GpsFile gpsFile = new GpsFile();
	        try {
				gpsFile.loadFromStream(new FileInputStream(fileName));
				
				ImportProjectsDialog importDialog = new ImportProjectsDialog(shell, gpsFile);
				if (importDialog.open() != IDialogConstants.OK_ID) {
				    return null;
				}
				
				ProgressMonitorDialog progressDialog = new ProgressMonitorDialog(shell);
				progressDialog.run(true, true, new ImportOperation(gpsFile, importDialog.getSettings(), progressDialog));
			} catch (InterruptedException e) {
				//ignore
			} catch (Exception e) {
				EGitToolsPlugin.getDefault().getLog().log(new Status(IStatus.ERROR, EGitToolsPlugin.PLUGIN_ID, e.getLocalizedMessage(), e));
				MessageDialog.openError(shell, "Import", e.getLocalizedMessage());
			}
        }
		return null;
	}
	
	private static class ImportOperation implements IRunnableWithProgress {

		private GpsFile file;
		private ProgressMonitorDialog progressDialog;
		private int dialogResult;
		private Map<String, Object> options;
		
		public ImportOperation(GpsFile file, Map<String, Object> options, ProgressMonitorDialog progressDialog) {
			this.file = file;
			this.progressDialog = progressDialog;
			this.options = options;
		}
		
		public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
			try {
				workspace.run(new IWorkspaceRunnable() {
					public void run(IProgressMonitor monitor) throws CoreException {
						performImport(monitor);
					}
				}, monitor);
			} catch (Exception e) {
				throw new InvocationTargetException(e, e.getLocalizedMessage());
			}
		}
		
		private void performImport(IProgressMonitor m) throws CoreException {
			SubMonitor monitor = SubMonitor.convert(m, 1000);
			
			monitor.setTaskName("Initializing Git Team provider...");
			RepositoryProviderType.getProviderType(GitProvider.class.getName());
			
			HashSet<String> projectSet = new HashSet<String>();
			for (IProject prj: Arrays.asList(workspace.getRoot().getProjects())) {
				projectSet.add(prj.getName());
			}

			SubMonitor repoSetup = monitor.newChild(600, 0);
			repoSetup.beginTask("Preparing git repositories...", file.getRepositoryConfigs().size()*20);
			for (IGpsRepositoriesConfig config: file.getRepositoryConfigs()) {
				config.performConfiguration(options, repoSetup.newChild(20, SubMonitor.SUPPRESS_BEGINTASK));
			}
			
			//initialize set to detect obsolete projects
			final Set<IProject> obsoleteProjects = new HashSet<IProject>();
			for (IProject project: ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
				if (project.isAccessible()) {
					obsoleteProjects.add(project);
				}
			}
			
			//first pass is to check if everything is ok
			boolean replaceAll = false;
			boolean skipAll = false;
			monitor = monitor.newChild(file.getProjects().size()*10, 0);
			for (GpsProject gpsProject: file.getProjects()) {
				
				if (monitor.isCanceled()) return;
				SubMonitor projectMonitor = monitor.newChild(10, 0);
				projectMonitor.beginTask("Importing project " + gpsProject.getName(), 2);
				//check if any project already exists in the workspace
				boolean skipImport = false;
				
				IProject project = workspace.getRoot().getProject(gpsProject.getName());
				
				obsoleteProjects.remove(project);
				
				boolean existingProject = projectSet.contains(gpsProject.getName());
				
				skipImport = existingProject && !gpsProject.getRepositoryHandler().requiresImport();
						
				if (!skipImport && existingProject) {
					skipImport = skipAll;
					if (!skipAll && !replaceAll) {
						openDialog("Do you want to replace project {0}", gpsProject.getName());
						replaceAll = dialogResult == 1;
						skipAll = dialogResult == 3;
						if (dialogResult == 4) {
							return;
						}
						if (dialogResult > 1) { //do not replace
							skipImport = true;
						}
					}
					if (!skipImport) {
						project.delete(false, true, projectMonitor.newChild(1));
					}
				}
				if (projectMonitor.isCanceled()) return;

				//import the project into workspace
				if (!skipImport) {
					project = gpsProject.getRepositoryHandler().importProject(projectMonitor.newChild(1));
					if (projectMonitor.isCanceled()) return;
				}
				
				//add project to working sets
				if (project.isAccessible()) {
					List<IWorkingSet> sets = new ArrayList<IWorkingSet>();
					for (String workingSet: gpsProject.getWorkingSets()) {
						IWorkingSet set = workingSetManager.getWorkingSet(workingSet);
						if (set == null) {
							set = workingSetManager.createWorkingSet(workingSet, new IAdaptable[0]);
							set.setId("org.eclipse.jdt.ui.JavaWorkingSetPage");
							workingSetManager.addWorkingSet(set);
						}
						sets.add(set);
					}
					if (sets.size() > 0) {
						workingSetManager.addToWorkingSets(project, sets.toArray(new IWorkingSet[sets.size()]));
					}
				}
				projectMonitor.done();
				
			}
			
			if (!obsoleteProjects.isEmpty()) {
				progressDialog.getShell().getDisplay().syncExec(
					new Runnable() {
						@Override
						public void run() {
							List<IProject> toDelete = new ObsoleteProjectsDialog(
									progressDialog.getShell()).select(obsoleteProjects);
							for (IProject project: toDelete) {
								try {
									project.delete(false, true, null);
								} catch (CoreException e) {
									EGitToolsPlugin.getDefault().log(e);
								}
							}
						}
					});
			}
			
		}
		
		private void openDialog(final String message, final Object... params) {
			progressDialog.getShell().getDisplay().syncExec(new Runnable() {

				public void run() {
					MessageDialog dialog = new MessageDialog(
							progressDialog.getShell(), 
							"Import", 
							null, MessageFormat.format(message, params), 
							MessageDialog.QUESTION, 
							new String[] {"Yes", "Yes to all", "No", "No to all", "Cancel"}, 
							4);
					dialogResult = dialog.open();
				}
				
			});
			
		}
		
	}
	

}
