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
package com.genuitec.eclipse.gerrit.tools.internal.gps.dialogs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.IWorkingSetManager;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.CheckedTreeSelectionDialog;
import org.eclipse.ui.model.WorkbenchLabelProvider;

public class ExportProjectsDialog {
	
	private static final Comparator<IProject> PROJECT_COMPARATOR = new Comparator<IProject>() {
		public int compare(IProject o1, IProject o2) {
			return o1.getName().compareToIgnoreCase(o2.getName());
		}
	};
	
	private Shell shell;
	
	public ExportProjectsDialog(Shell shell) {
		this.shell = shell;
	}
	
	public List<IProject> select() {
		CheckedTreeSelectionDialog dialog = new CheckedTreeSelectionDialog(
				shell, 
				new WorkbenchLabelProvider(),
				new ProjectSelectionContentProvider()) {
			@Override
			protected Label createMessageArea(Composite composite) {
				Label label = new Label(composite, SWT.WRAP);
				if (getMessage() != null) {
					label.setText(getMessage());
				}
				label.setFont(composite.getFont());
				GridData gd = new GridData(SWT.FILL, SWT.FILL, true, false);
				gd.widthHint = 300;
				label.setLayoutData(gd);
				return label;
			}
		};
		dialog.setContainerMode(true);
		dialog.setMessage("Choose projects to export");
		dialog.setTitle("Project selection");
		dialog.setInput(ResourcesPlugin.getWorkspace());
		dialog.setInitialSelection(ResourcesPlugin.getWorkspace().getRoot().getProjects());
		if (dialog.open() == Dialog.OK && dialog.getResult() != null) {
			ArrayList<IProject> projects = new ArrayList<IProject>();
			for (Object o: dialog.getResult()) {
				if (o instanceof IProject) {
					projects.add((IProject)o);
				}
			}
			return projects;
		}
		return Collections.emptyList();
	}
	
	private class ProjectSelectionContentProvider implements ITreeContentProvider {
		
		public void dispose() {
			//nothing to do
		}

		public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
			
		}

		public Object[] getElements(Object inputElement) {
			IWorkingSetManager wsm = PlatformUI.getWorkbench().
					getWorkingSetManager();
			
			Set<IProject> noWorkingSetProjects = new TreeSet<IProject>(PROJECT_COMPARATOR);
			for (IProject project: ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
				if (project.isAccessible()) {
					noWorkingSetProjects.add(project);
				}
			}			
			
			List<Object> result = new ArrayList<Object>();
			for (IWorkingSet ws: wsm.getWorkingSets()) {
				try {
					boolean added = false;
					for (IAdaptable a: ws.getElements()) {
						IProject project = (IProject) a.getAdapter(IProject.class);
						if (project != null && project.isAccessible()) {
							noWorkingSetProjects.remove(project);
							if (!added) {
								result.add(ws);
								added = true;
							}
						}
					}
				} catch (Exception e) {
					//ignore
				}
			}
			result.addAll(noWorkingSetProjects);
			return result.toArray();
		}

		public Object[] getChildren(Object parentElement) {
			if (parentElement instanceof IWorkingSet) {
				Set<IProject> result = new TreeSet<IProject>(PROJECT_COMPARATOR);
				for (IAdaptable element: ((IWorkingSet) parentElement).getElements()) {
					IProject project = (IProject) element.getAdapter(IProject.class);
					if (project != null && project.isAccessible()) {
						result.add(project);
					}
				}
				return result.toArray();
			}
			return null;
		}

		public Object getParent(Object element) {
			return null;
		}

		public boolean hasChildren(Object element) {
			return element instanceof IWorkingSet;
		}
		
	}
	
}
