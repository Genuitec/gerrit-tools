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
package com.genuitec.eclipse.gerrit.tools.internal.utils.dialogs;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.egit.ui.internal.UIIcons;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;

import com.genuitec.eclipse.gerrit.tools.dialogs.SettingsDialog;
import com.genuitec.eclipse.gerrit.tools.internal.utils.commands.TagAndPushHandler;

@SuppressWarnings("restriction")
public class TagAndPushDialog extends SettingsDialog {    

    List<Repository> repos = new ArrayList<Repository>();
    
    public TagAndPushDialog(Shell parentShell, List<Repository> repositories) {
        super(parentShell, "Create and Push Tag");
        this.repos = repositories;
    }

    @Override
    protected IStatus validate(String property, Object value) {
    	if (property.equals(TagAndPushHandler.PROP_CREATE_TAG) ||
    			property.equals(TagAndPushHandler.PROP_PUSH_TAG)) {
    		if (!(Boolean)getSetting(TagAndPushHandler.PROP_CREATE_TAG) &&
                    !(Boolean)getSetting(TagAndPushHandler.PROP_PUSH_TAG)) {
    			return createErrorStatus("You must create or push tag(s)"); 
    		}
    	} else if (property.equals(TagAndPushHandler.PROP_TAG_NAME)) {
    		if (((String)value).length() < 2) {
    			return createErrorStatus("Tag name must be longer then 1 character");
    		}
    	} else if (property.equals(TagAndPushHandler.PROP_TAG_MESSAGE)) {

    		if (((String)value).length() <= 3) {
    			return createErrorStatus("Tag message must be longer then 3 characters");
    		}
    	}
    	return Status.OK_STATUS;
    }
    
    @Override
    protected void createDialogContents(Composite parent) {
        createOptionTextEditor(parent, "Tag name:", TagAndPushHandler.PROP_TAG_NAME, "", false);
        createOptionTextEditor(parent, "Tag message:", TagAndPushHandler.PROP_TAG_MESSAGE, "", false);
        createOptionCheckBox(parent, "Create tag", TagAndPushHandler.PROP_CREATE_TAG, true);
        createOptionCheckBox(parent, "Push tag", TagAndPushHandler.PROP_PUSH_TAG, true);
        
        createRepositoriesList(parent);
    }

    private void createRepositoriesList(Composite parent) {
        Label l = new Label(parent, SWT.NONE);
        l.setText("List of repositories:");
        l.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, false, 2, 1));
        
        TableViewer list = new TableViewer(parent, SWT.V_SCROLL + SWT.H_SCROLL + SWT.BORDER);
        list.setLabelProvider(new LabelProvider() {
            private Image img = UIIcons.REPOSITORY.createImage();
            
            @Override
            public Image getImage(Object element) {
                return img;
            }
            
            @Override
            public void dispose() {
                img.dispose();
            }
            
            @Override
            public String getText(Object element) {
                return ((Repository)element).getDirectory().getParentFile().getName();
            }
        });
        list.setContentProvider(new ArrayContentProvider());
        list.setInput(repos);
        Table lst = list.getTable();
        GridData gd = new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1);
        gd.heightHint = 200;
        lst.setLayoutData(gd);
    }
    
}
