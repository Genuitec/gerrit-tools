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
package com.genuitec.eclipse.gerrit.tools.internal.changes.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.egit.ui.internal.DecorationOverlayDescriptor;
import org.eclipse.egit.ui.internal.UIIcons;
import org.eclipse.jface.action.ContributionItem;
import org.eclipse.jface.action.IContributionItem;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.CoolBar;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.ui.ISelectionService;
import org.eclipse.ui.actions.CompoundContributionItem;
import org.eclipse.ui.menus.CommandContributionItem;
import org.eclipse.ui.menus.CommandContributionItemParameter;
import org.eclipse.ui.menus.IWorkbenchContribution;
import org.eclipse.ui.services.IServiceLocator;

import com.genuitec.eclipse.gerrit.tools.GerritToolsPlugin;
import com.genuitec.eclipse.gerrit.tools.utils.RepositoryUtils;

@SuppressWarnings("restriction")
public class ChangesActionsProvider extends CompoundContributionItem implements IWorkbenchContribution {

	private IServiceLocator serviceLocator;
	
	public void initialize(IServiceLocator serviceLocator) {
		this.serviceLocator = serviceLocator;
	}

	@Override
	protected IContributionItem[] getContributionItems() {	
		ISelectionService selectionService = (ISelectionService) serviceLocator.
				getService(ISelectionService.class);
		Repository repository = RepositoryUtils.getRepository(selectionService.getSelection());
		if (repository == null) {
			return new IContributionItem[0];
		}
		
		Set<String> stables = new TreeSet<String>();
		try {
			for (String ref: repository.getRefDatabase().getRefs("refs/heads/changes/").keySet()) {
				IPath refPath = new Path(ref);
				if (refPath.segmentCount() > 1) {
					if (refPath.segment(0).equals("features") && refPath.segmentCount() > 3) {
						stables.add(refPath.uptoSegment(3).toString());
					} else {
						stables.add(refPath.segment(0));
					}
				}
			}
		} catch (IOException e) {
			//ignore
		}
		
		List<IContributionItem> result = new ArrayList<IContributionItem>();
		for (String stable: stables) {
			result.add(new ChangesOnStableActionProvider(repository, stable));
		}
		
		return result.toArray(new IContributionItem[result.size()]);
	}

	private class ChangesOnStableActionProvider extends ContributionItem {
		
		private Repository repository;
		private String stable;

		public ChangesOnStableActionProvider(Repository repo, String stable) {
			this.repository = repo;
			this.stable = stable;
		}
		
		@Override
		public void fill(Menu menu, int index) {
			MenuItem item = new MenuItem(menu, SWT.CASCADE);
			item.setText(stable);
			item.setImage(GerritToolsPlugin.getDefault().getImageRegistry().get(GerritToolsPlugin.IMG_BRANCH));
			Menu subMenu = new Menu(menu);
			item.setMenu(subMenu);
			for (IContributionItem sub: createContributions()) {
				sub.fill(item.getMenu(), -1);
			}
		}
		
		private IContributionItem[] createContributions() {
			List<String> changes = new ArrayList<String>();
			try {
				changes.addAll(repository.getRefDatabase().getRefs("refs/heads/changes/" + stable + "/").keySet()); //$NON-NLS-1$ //$NON-NLS-2$
			} catch (IOException e) {
				//ignore
			}
			String curBranch = null;
			try {
				curBranch = repository.getFullBranch();
			} catch (IOException e) {
				//ignore
			}
			Collections.sort(changes);
			List<IContributionItem> result = new ArrayList<IContributionItem>();
			for (String change: changes) {
				CommandContributionItemParameter parameter = new CommandContributionItemParameter(
						serviceLocator, 
						"com.genuitec.eclipse.egit.tools.switchToChangeBranch." + change, 
						"com.genuitec.eclipse.egit.tools.switchToChangeBranch", SWT.PUSH);
				String branchRef = "refs/heads/changes/" + stable + "/" + change;
				parameter.parameters = Collections.singletonMap("branch.ref", branchRef);
				parameter.label = change.replace('(', '[').replace(')', ']').replace('_', ' ');
				parameter.icon = GerritToolsPlugin.getDefault().getImageDescriptor(GerritToolsPlugin.IMG_GERRIT_CHANGE);
				if (branchRef.equals(curBranch)) {
					result.add(new CurrentBranchCommandContributionItem(parameter));
				} else {
					result.add(new CommandContributionItem(parameter));
				}
			}
			return result.toArray(new IContributionItem[result.size()]);
		}
		
		@Override
		public void fill(Composite parent) {
			throw new UnsupportedOperationException("Not implemented");
		}
		
		@Override
		public void fill(CoolBar parent, int index) {
			throw new UnsupportedOperationException("Not implemented");
		}
		
		@Override
		public void fill(ToolBar parent, int index) {
			throw new UnsupportedOperationException("Not implemented");
		}
	}
	
	private static class CurrentBranchCommandContributionItem extends CommandContributionItem {
		
		public CurrentBranchCommandContributionItem(
				CommandContributionItemParameter contributionParameters) {
			super(decorate(contributionParameters));
		}
		
		@Override
		public boolean isEnabled() {
			return false;
		}

		private static CommandContributionItemParameter decorate(
				CommandContributionItemParameter contributionParameters) {
			ImageDescriptor ovr = UIIcons.OVR_CHECKEDOUT;
			contributionParameters.icon = new DecorationOverlayDescriptor(
					contributionParameters.icon, ovr, 0);
			return contributionParameters;
		}
		
	}
}
