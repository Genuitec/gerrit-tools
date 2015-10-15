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
import java.util.Collections;

import org.eclipse.jface.action.ContributionItem;
import org.eclipse.jface.action.IContributionItem;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.ui.actions.CompoundContributionItem;
import org.eclipse.ui.menus.CommandContributionItem;
import org.eclipse.ui.menus.CommandContributionItemParameter;
import org.eclipse.ui.menus.IWorkbenchContribution;
import org.eclipse.ui.services.IServiceLocator;

import com.genuitec.eclipse.gerrit.tools.internal.gps.GpsQuickImportFiles;

public class QuickGpsImportCommandsProvider extends CompoundContributionItem implements IWorkbenchContribution {

	private IServiceLocator serviceLocator;
	
	@Override
	public void initialize(IServiceLocator serviceLocator) {
		this.serviceLocator = serviceLocator;
	}

	@Override
	protected IContributionItem[] getContributionItems() {
		File[] files = GpsQuickImportFiles.getGpsFiles();
		if (files.length == 0) {
			return new IContributionItem[] {
					new ContributionItem() {
						@Override
						public void fill(Menu menu, int index) {
							MenuItem item = new MenuItem(menu, 0);
							item.setText("No files available");
						}
					}
			};
		}
		
		IContributionItem[] result = new IContributionItem[files.length];
		for (int i = 0; i < result.length; i++) {
			CommandContributionItemParameter parameter = new CommandContributionItemParameter(
					serviceLocator, "com.genuitec.eclipse.egit.tools.quickImportProjects." + i,  //$NON-NLS-1$
					"com.genuitec.eclipse.egit.tools.importProjects", SWT.PUSH); //$NON-NLS-1$
			parameter.parameters = Collections.singletonMap("file", files[i].toString()); //$NON-NLS-1$
			parameter.label = files[i].getName();
			result[i] = new CommandContributionItem(parameter);
		}
		return result;
	}

}
