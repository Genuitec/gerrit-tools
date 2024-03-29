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
package com.genuitec.eclipse.gerrit.tools.internal.changes.dialogs;

import java.io.IOException;
import java.util.Arrays;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;

import com.genuitec.eclipse.gerrit.tools.dialogs.StableBranchSelectionDialog;

public class CreateChangeBranchDialog extends StableBranchSelectionDialog {

    public static final String PROP_CHANGE_BRANCH_NAME = "branch.name"; //$NON-NLS-1$
    public static final String PROP_CHECKOUT = "checkout"; //$NON-NLS-1$
	
	public CreateChangeBranchDialog(Shell parentShell, Repository repository) {
		super(parentShell, Arrays.asList(repository), "Create new change branch"); //$NON-NLS-1$
	}

	@Override
	protected IStatus validate(String property, Object value) {
		if (property.equals(PROP_CHANGE_BRANCH_NAME)) {
			String branchName = (String)value;
			String refName = "refs/heads/change/" + getSetting(PROP_STABLE_BRANCH) + "/" + branchName; //$NON-NLS-1$ //$NON-NLS-2$
			if (branchName.length() < 4) {
				return createErrorStatus("Branch name must have at least 4 characters");
			} else if (branchName.endsWith("/") || branchName.startsWith("/")) { //$NON-NLS-1$ //$NON-NLS-2$
				return createErrorStatus("Branch name cannot start or end with ''/''");
			} else if (!Repository.isValidRefName(refName)) {
				return createErrorStatus("Branch name {0} is not allowed", branchName);
			} else
				try {
					if (repositories.get(0).findRef(refName) != null){
						return createErrorStatus("You already have a change branch with this name. Choose a different name.");
					}
				} catch (IOException e) {
					return createErrorStatus("Error: {0}", e.getLocalizedMessage());
				}
		}
		return super.validate(property, value);
	}
	
	@Override
	protected void createDialogContents(Composite parent) {
		super.createDialogContents(parent);
		createOptionTextEditor(parent, "Change branch name:", 
				PROP_CHANGE_BRANCH_NAME, "", false).setFocus(); //$NON-NLS-1$
		createOptionCheckBox(parent, "Checkout new branch", 
				PROP_CHECKOUT, true);
	}
    
}
