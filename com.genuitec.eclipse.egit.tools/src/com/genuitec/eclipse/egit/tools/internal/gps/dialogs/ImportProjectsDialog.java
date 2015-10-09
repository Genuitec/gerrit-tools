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
package com.genuitec.eclipse.egit.tools.internal.gps.dialogs;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jface.layout.TreeColumnLayout;
import org.eclipse.jface.viewers.BaseLabelProvider;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.EditingSupport;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.TextCellEditor;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.TreeViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TreeColumn;

import com.genuitec.eclipse.egit.tools.EGitToolsPlugin;
import com.genuitec.eclipse.egit.tools.dialogs.SettingsDialog;
import com.genuitec.eclipse.egit.tools.internal.gps.model.GpsFile;
import com.genuitec.eclipse.egit.tools.internal.gps.model.GpsGitRepositoriesConfig;
import com.genuitec.eclipse.egit.tools.internal.gps.model.IGpsRepositoriesConfig;
import com.genuitec.eclipse.egit.tools.internal.gps.model.IGpsRepositorySetup;
import com.genuitec.eclipse.egit.tools.internal.gps.model.IGpsRepositorySetup.State;

public class ImportProjectsDialog extends SettingsDialog {

    Map<String, Object> settings = new HashMap<String, Object>();
    GpsFile gpsFile;
    
    public ImportProjectsDialog(Shell parentShell, GpsFile gpsFile) {
        super(parentShell,"Configure project import");
        this.gpsFile = gpsFile;
    }

    @Override
    protected void createDialogContents(Composite parent) {
    	Composite contents = new Composite(parent, SWT.NONE);
    	contents.setLayout(new GridLayout());
    	contents.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    	
        //create git options
        createGitOptionsGroup(contents);
        
        //create repositories options
        createRepositoriesGroup(contents);
    }

    private void createGitOptionsGroup(Composite parent) {
        Group group = new Group(parent, SWT.NONE);
        group.setText("Git project's import");
        group.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        group.setLayout(new GridLayout(4, false));

        Label label = new Label(group, SWT.WRAP);
        label.setText("Choose additional operations, which are performed on Git repositories before projects are imported.");
        GridData gd = new GridData(SWT.FILL, SWT.CENTER, true, false, 4, 1);
        gd.widthHint = 350;
        label.setLayoutData(gd);
        
        createOptionCheckBox(group, "Configure 'Push to upstream'.",
            GpsGitRepositoriesConfig.PROP_CONFIGURE_PUSH_TO_UPSTREAM, true);
        createOptionCheckBox(group, "Reconfigure branches setup",
            GpsGitRepositoriesConfig.PROP_RECONFIGURE_BRANCH, true);
        createOptionCheckBox(group, "Automatically pull each of repositories",
            GpsGitRepositoriesConfig.PROP_AUTO_PULL, true);
        createOptionCheckBox(group,
            "Force checkout (WARNING: this will override any non committed changes in problematic repositories)",
            GpsGitRepositoriesConfig.PROP_FORCE_CHECKOUT, false);
    }
    
    private void createRepositoriesGroup(Composite parent) {
    	Group group = new Group(parent, SWT.NONE);
    	group.setText("Git repositories");
    	group.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    	group.setLayout(new GridLayout());
    	
    	Composite treeParent = new Composite(group, SWT.NONE);
    	GridData data = new GridData(SWT.FILL, SWT.FILL, true, true);
    	data.heightHint = 200;
    	treeParent.setLayoutData(data);
    	TreeColumnLayout treeLayout = new TreeColumnLayout();
    	treeParent.setLayout(treeLayout);
    	
    	TreeViewer viewer = new TreeViewer(treeParent);
    	viewer.getTree().setHeaderVisible(true);
    	
    	TreeColumn column = new TreeColumn(viewer.getTree(), SWT.NONE);
    	column.setText("Name");
    	treeLayout.setColumnData(column, new ColumnWeightData(30));

    	column = new TreeColumn(viewer.getTree(), SWT.NONE);
    	column.setText("State");
    	treeLayout.setColumnData(column, new ColumnWeightData(20));

    	TreeViewerColumn vc = new TreeViewerColumn(viewer, SWT.NONE);
    	column = vc.getColumn();
    	column.setText("User name");
    	treeLayout.setColumnData(column, new ColumnWeightData(20));
    	vc.setEditingSupport(new MyCellEditingSupport(viewer));
    	
    	viewer.setContentProvider(new ContentProvider());
    	viewer.setLabelProvider(new MyLabelProvider());
    	viewer.setInput(null);
    	for (IGpsRepositoriesConfig repo: gpsFile.getRepositoryConfigs()) {
    		if (repo.getType().equals("git")) { //$NON-NLS-1$
    			viewer.setInput(repo.getRepositoriesSetups());
    		}
    	}
    	
    }
    
    private static class MyLabelProvider extends BaseLabelProvider implements ITableLabelProvider {

		@Override
		public Image getColumnImage(Object element, int columnIndex) {
			return columnIndex == 0 ? EGitToolsPlugin.getDefault().getImage(EGitToolsPlugin.IMG_GIT_REPO) : null;
		}

		@Override
		public String getColumnText(Object element, int columnIndex) {
			IGpsRepositorySetup repo = (IGpsRepositorySetup) element;
			switch (columnIndex) {
				case 0: return repo.getName();
				case 1: return repo.getState().toString().toLowerCase();
				case 2: return repo.getState() == State.CLONE ? repo.getUserName() : "";
			}
			return null;
		}
    	
    }

    private static class ContentProvider implements ITreeContentProvider {

		@Override
		public void dispose() {
		}

		@Override
		public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
		}

		@Override
		public Object[] getElements(Object inputElement) {
			return (Object[])inputElement;
		}

		@Override
		public Object[] getChildren(Object parentElement) {
			return null;
		}

		@Override
		public Object getParent(Object element) {
			return null;
		}

		@Override
		public boolean hasChildren(Object element) {
			return false;
		}
    	
    }
    
    private static class MyCellEditingSupport extends EditingSupport {
    	
    	private CellEditor userNameEditor;
    	
    	public MyCellEditingSupport(TreeViewer viewer) {
			super(viewer);
			userNameEditor = new TextCellEditor(viewer.getTree());
		}
    	
    	@Override
    	protected boolean canEdit(Object element) {
			IGpsRepositorySetup repo = (IGpsRepositorySetup) element;
    		return repo.getState() == State.CLONE && repo.getUserName() != null;
    	}
    	
    	@Override
    	protected CellEditor getCellEditor(Object element) {
    		return userNameEditor;
    	}
    	
    	@Override
    	protected Object getValue(Object element) {
    		return ((IGpsRepositorySetup) element).getUserName();
    	}
    	
    	@Override
    	protected void setValue(Object element, Object value) {
    		((IGpsRepositorySetup) element).setUserName((String)value);
    		getViewer().refresh(element);
    	}
    	
    }
    
}
