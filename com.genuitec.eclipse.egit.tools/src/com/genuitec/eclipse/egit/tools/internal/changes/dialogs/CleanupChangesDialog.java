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
package com.genuitec.eclipse.egit.tools.internal.changes.dialogs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.dialogs.CheckedTreeSelectionDialog;

import com.genuitec.eclipse.egit.tools.EGitToolsPlugin;

public class CleanupChangesDialog {

	private Shell shell;
	
	public CleanupChangesDialog(Shell shell) {
		this.shell = shell;
	}
	
	public List<String> select(Repository repo) {
		CheckedTreeSelectionDialog dialog = new CheckedTreeSelectionDialog(
				shell, 
				new BranchesLabelProvider(),
				new BranchesContentProvider()) {
			@Override
			public void create() {
				super.create();
				getTreeViewer().expandAll();
			}
		};
		dialog.setContainerMode(true);
		dialog.setMessage("Choose change branches to remove from local repository:");
		dialog.setTitle("Changes cleanup");
		dialog.setInput(repo);
		dialog.setInitialSelection(ResourcesPlugin.getWorkspace().getRoot().getProjects());
		if (dialog.open() == Dialog.OK && dialog.getResult() != null) {
			ArrayList<String> changes = new ArrayList<String>();
			for (Object o: dialog.getResult()) {
				if (o instanceof Change) {
					changes.add(((Change)o).getFullName());
				}
			}
			return changes;
		}
		return Collections.emptyList();
	}
	
	private static class StableBranch {
		
		private String name;
		private Collection<Change> children;
		
		public StableBranch(String name) {
			this.name = name;
			this.children = new ArrayList<Change>();
		}
		
		public void addChange(String name) {
			children.add(new Change(this, name));
		}
		
		public Object[] getChildren() {
			return children.toArray();
		}
		
	}
	
	private static class Change {
		
		private String name;
		private StableBranch parent;
		
		public Change(StableBranch parent, String name) {
			this.name = name;
			this.parent = parent;
		}
		
		public String getFullName() {
			return parent.name + "/" + name;
		}
	}
	
	private static class BranchesLabelProvider extends LabelProvider {

		private static Image IMG_STABLE = EGitToolsPlugin.getDefault().getImage(EGitToolsPlugin.IMG_BRANCH);
		private static Image IMG_CHANGE = EGitToolsPlugin.getDefault().getImage(EGitToolsPlugin.IMG_GERRIT_CHANGE);
		
		@Override
		public Image getImage(Object element) {
			if (element instanceof StableBranch) {
				return IMG_STABLE;
			} else if (element instanceof Change) {
				return IMG_CHANGE;
			}
			return null;
		}
		
		@Override
		public String getText(Object element) {
			if (element instanceof StableBranch) {
				return ((StableBranch) element).name;
			} else if (element instanceof Change) {
				return ((Change) element).name;
			}
			return null;
		}
		
	}
	
	private static class BranchesContentProvider implements ITreeContentProvider {

		private Repository repo;
		private Object[] children;
		
		@Override
		public void dispose() {
		}

		@Override
		public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {

		}

		@Override
		public Object[] getElements(Object inputElement) {
			Repository repo = (Repository) inputElement;
			Map<String, StableBranch> branches = new TreeMap<String, StableBranch>();
			try {

				String curBranch = repo.getFullBranch();
				if (curBranch.startsWith("refs/heads/changes/")) {
					curBranch = curBranch.substring("refs/heads/changes/".length());
				}
				for (String branch: repo.getRefDatabase().getRefs("refs/heads/changes/").keySet()) {
					if (!curBranch.equals(branch)) {
						IPath branchPath = new Path(branch);
						if (branchPath.segmentCount() > 1) {
							String stableName = branchPath.segment(0);
							String changeName;
							if (stableName.equals("features") && branchPath.segmentCount() > 3) {
								stableName = branchPath.uptoSegment(3).toString();
								changeName = branchPath.removeFirstSegments(3).toString();
							} else {
								changeName = branchPath.removeFirstSegments(1).toString();
							}
							StableBranch sb = branches.get(stableName);
							if (sb == null) {
								sb = new StableBranch(stableName);
								branches.put(stableName, sb);
							}
							sb.addChange(changeName);
						}
					}
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			return branches.values().toArray();
		}

		@Override
		public Object[] getChildren(Object element) {
			if (element instanceof StableBranch) {
				return ((StableBranch) element).getChildren();
			}
			return null;
		}

		@Override
		public Object getParent(Object element) {
			if (element instanceof Change) {
				return ((Change) element).parent;
			}
			return null;
		}

		@Override
		public boolean hasChildren(Object element) {
			return element instanceof StableBranch;
		}
		
	}
	
}
