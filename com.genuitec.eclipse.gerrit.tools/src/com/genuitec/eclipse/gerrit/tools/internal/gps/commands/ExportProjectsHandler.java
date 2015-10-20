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
package com.genuitec.eclipse.gerrit.tools.internal.gps.commands;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.handlers.HandlerUtil;

import com.genuitec.eclipse.gerrit.tools.GerritToolsPlugin;
import com.genuitec.eclipse.gerrit.tools.internal.gps.dialogs.ExportProjectsDialog;
import com.genuitec.eclipse.gerrit.tools.internal.gps.model.GpsFile;
import com.genuitec.eclipse.gerrit.tools.internal.gps.model.GpsProject;

public class ExportProjectsHandler extends AbstractHandler {
	
	private static IWorkspace workspace = ResourcesPlugin.getWorkspace();
	
	public Object execute(ExecutionEvent event) throws ExecutionException {
		Shell shell = HandlerUtil.getActiveShell(event);
		
		//first open the file
		FileDialog fd = new FileDialog(shell, SWT.SAVE);
        fd.setText("Export Gerrit Project Set file");
        fd.setFilterPath(ResourcesPlugin.getWorkspace().getRoot().getLocation().toOSString());
        String[] filterExt = { "*.gps" };
        fd.setOverwrite(true);
        fd.setFilterExtensions(filterExt);
        String selected = fd.open();
        
        if (selected != null) {
	        GpsFile gpsFile = new GpsFile();
	        List<IProject> projects = new ExportProjectsDialog(shell).select();
	        if (projects == null || projects.isEmpty()) {
	        	return null;
	        }
	        try {
				ProgressMonitorDialog progressDialog = new ProgressMonitorDialog(shell);
				progressDialog.run(true, true, new ExportOperation(gpsFile, progressDialog, projects));
				File f = new File(selected);
				if (f.isFile()) {
					f.delete();
				}
				FileOutputStream fos = new FileOutputStream(selected, false);
				try {
					gpsFile.saveToStream(fos);
				} finally {
					fos.close();
				}
			} catch (InterruptedException e) {
				//ignore
			} catch (Exception e) {
				GerritToolsPlugin.getDefault().getLog().log(new Status(IStatus.ERROR, GerritToolsPlugin.PLUGIN_ID, e.getLocalizedMessage(), e));
				MessageDialog.openError(shell, "Import", e.getLocalizedMessage());
			}
        }
		return null;
	}

	private static class ExportOperation implements IRunnableWithProgress {

		private GpsFile file;
		@SuppressWarnings("unused")
		private ProgressMonitorDialog progressDialog;
		private List<IProject> projects;
		
		public ExportOperation(GpsFile file, ProgressMonitorDialog progressDialog, 
				List<IProject> projects) {
			this.file = file;
			this.progressDialog = progressDialog;
			this.projects = projects;
		}
		
		public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
			try {
				workspace.run(new IWorkspaceRunnable() {
					public void run(IProgressMonitor monitor) throws CoreException {
						performExport(monitor);
					}
				}, monitor);
			} catch (Exception e) {
				throw new InvocationTargetException(e, e.getLocalizedMessage());
			}
		}
		
		private void performExport(IProgressMonitor monitor) throws CoreException {
			monitor.beginTask("Exporting projects...", projects.size());
			monitor.setTaskName("Exporting projects...");
			for (IProject project: projects) {
				if (monitor.isCanceled()) return;
				GpsProject gp = GpsProject.createForProject(project);
				if (gp != null) {
					file.getProjects().add(gp);
				}
				monitor.worked(1);
			}
			monitor.done();
		}
		
	}
	
}
