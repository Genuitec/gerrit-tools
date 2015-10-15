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
package com.genuitec.eclipse.gerrit.tools.dialogs;

import java.io.IOException;
import java.util.List;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;

import com.genuitec.eclipse.gerrit.tools.GerritToolsPlugin;
import com.genuitec.eclipse.gerrit.tools.internal.fbranches.BranchingUtils;

public class StableBranchSelectionDialog extends SettingsDialog {

    public static final String PROP_STABLE_BRANCH = "stable.branch"; //$NON-NLS-1$
    
	protected final List<Repository> repositories;
	
	public StableBranchSelectionDialog(Shell parentShell, List<Repository> repositories, String title) {
		super(parentShell, title);
		this.repositories = repositories;
	}

	@Override
	protected IStatus validate(String property, Object value) {
		if (property.equals(PROP_STABLE_BRANCH)) {
			String branchName = (String)value;
			if (branchName.length() < 1) {
				return createErrorStatus("Select an existing stable branch");
			}

			for (Repository repository: repositories) {
				try {
					if (repository.getRef("refs/heads/" + branchName) == null){
						return createErrorStatus("Repository {0} does not have stable branch {1}. Choose a different stable branch.", 
								repository.getDirectory().getParentFile().getName(), branchName);
					}
				} catch (IOException e) {
					GerritToolsPlugin.getDefault().log(e);
					return createErrorStatus("Error: {0}", e.getLocalizedMessage());
				}
			}
		}
		return super.validate(property, value);
	}
	
	@Override
	protected void createDialogContents(Composite parent) {
		List<String> stableBranches = BranchingUtils.getBranches(
				repositories, BranchingUtils.MODE_STABLE, null);
		for (int i = 0; i < stableBranches.size(); i++) {
			stableBranches.set(i, stableBranches.get(i).substring("refs/heads/".length())); //$NON-NLS-1$
		}
		createOptionComboEditor(parent, "Stable branch", 
				PROP_STABLE_BRANCH, "master", 
				stableBranches.toArray(new String[stableBranches.size()]), true);
	}
    
}
