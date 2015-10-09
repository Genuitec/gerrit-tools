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
package com.genuitec.eclipse.egit.tools.internal.fbranches.dialogs;

import java.io.IOException;
import java.util.List;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;

import com.genuitec.eclipse.egit.tools.EGitToolsPlugin;
import com.genuitec.eclipse.egit.tools.dialogs.StableBranchSelectionDialog;
import com.genuitec.eclipse.egit.tools.utils.RepositoryUtils;

public class CreateFeatureBranchDialog extends StableBranchSelectionDialog {
	
    public static final String PROP_BRANCH_NAME = "branch.name"; //$NON-NLS-1$
    public static final String PROP_CHECKOUT = "checkout"; //$NON-NLS-1$

	private String userId;
	
	public CreateFeatureBranchDialog(Shell parentShell, List<Repository> repositories) {
		super(parentShell, repositories, "Create new feature branch"); //$NON-NLS-1$
		this.userId = RepositoryUtils.getUserId(repositories);
	}

	@Override
	protected IStatus validate(String property, Object value) {
		if (property.equals(PROP_BRANCH_NAME)) {
			String branchName = (String)value;
			if (branchName.length() < 4) {
				return createErrorStatus("Branch name must have at least 4 characters");
			} else
				try {
					for (Repository repository: repositories) {
						if (repository.getRef("refs/heads/features/" + userId + "/" + branchName) != null){
							return createErrorStatus("You already have a feature branch with this name in repository {0}. Choose a different name", 
									repository.getDirectory().getParentFile().getName());
						}
					}
				} catch (IOException e) {
					EGitToolsPlugin.getDefault().log(e);
					return createErrorStatus("Error: {0}", e.getLocalizedMessage());
				}
		}
		return super.validate(property, value);
	}
	
	@Override
	protected void createDialogContents(Composite parent) {
		super.createDialogContents(parent);
		createOptionTextEditor(parent, "Feature branch name:", 
				PROP_BRANCH_NAME, "", false).setFocus(); //$NON-NLS-1$
		createOptionCheckBox(parent, "Checkout new branch", 
				PROP_CHECKOUT, true);
	}
    
}
