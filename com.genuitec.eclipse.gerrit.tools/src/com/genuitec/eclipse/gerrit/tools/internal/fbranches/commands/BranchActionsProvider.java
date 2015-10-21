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
package com.genuitec.eclipse.gerrit.tools.internal.fbranches.commands;

import static com.genuitec.eclipse.gerrit.tools.internal.fbranches.BranchingUtils.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.egit.ui.internal.DecorationOverlayDescriptor;
import org.eclipse.egit.ui.internal.UIIcons;
import org.eclipse.jface.action.ContributionItem;
import org.eclipse.jface.action.IContributionItem;
import org.eclipse.jface.action.Separator;
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
import com.genuitec.eclipse.gerrit.tools.internal.fbranches.BranchingUtils;
import com.genuitec.eclipse.gerrit.tools.utils.RepositoryUtils;

@SuppressWarnings({ "nls", "restriction" })
public class BranchActionsProvider extends CompoundContributionItem implements IWorkbenchContribution {

	private static final int MODE_ADD_SEPARATOR = 1 << 16;
	
	private IServiceLocator serviceLocator;
	
	@Override
	protected IContributionItem[] getContributionItems() {
		ISelectionService selectionService = (ISelectionService) serviceLocator.
				getService(ISelectionService.class);
		//We are handling following ids:
		//- switch.branch.feature.user
		//- switch.branch.feature.others
		//- switch.branch.stable
		//- merge.branch.stable
		//- delete.branch.feature
		List<Repository> repositories;
		String typeId = getId();
		if (typeId.startsWith("all.")) {
			repositories = null;
			typeId = typeId.substring(4);
		} else {
			repositories = RepositoryUtils.getRepositories(selectionService.getSelection());
			if (repositories.isEmpty()) {
				return new IContributionItem[0];
			}
		}
		String cmdId;
		int mode = 0;
		if (typeId.equals("switch.branch.feature.others")) {
			return getOthersFeatureBranchesItems(repositories, "com.genuitec.eclipse.gerrit.tools.switchToBranch");
		} else if (typeId.startsWith("switch.branch.")) {
			cmdId = "com.genuitec.eclipse.gerrit.tools.switchToBranch";
			if (typeId.equals("switch.branch.stable")) {
				mode |= MODE_STABLE;
			} else if (typeId.equals("switch.branch.feature.user")) {
				mode |= MODE_FEATURE_USER;
			} else {
				throw new RuntimeException("Unknown command type: " + typeId);
			}
		} else if (typeId.equals("merge.branch.stable")) { //$NON-NLS-1$
			cmdId = "com.genuitec.eclipse.gerrit.tools.mergeStableIntoCurrent";
			mode |= MODE_STABLE;
		} else if (typeId.equals("delete.branch.feature.others")) {
			return getOthersFeatureBranchesItems(repositories, "com.genuitec.eclipse.gerrit.tools.deleteFeatureBranch");
		} else if (typeId.equals("delete.branch.feature.user")) { //$NON-NLS-1$
			cmdId = "com.genuitec.eclipse.gerrit.tools.deleteFeatureBranch";
			mode |= MODE_FEATURE_USER;
		} else {
			throw new RuntimeException("Unknown command type: " + typeId);
		}
		return createPerBranchContributionItems(repositories, cmdId, mode, getUserId(repositories));
	}
	
	
	private IContributionItem[] createPerBranchContributionItems(List<Repository> repositories, 
			String cmdId, int mode, String user) {
		List<String> branches = BranchingUtils.getBranches(repositories, mode, user);
		List<IContributionItem> result = new ArrayList<IContributionItem>();
		String curBranch = null;
		try {
			curBranch = repositories != null && repositories.size() == 1 ? 
					repositories.get(0).getFullBranch() : null;
		} catch (IOException e) {
			//ignore
		}
		for (String brName: branches) {
			if (result.isEmpty() && ( (mode & MODE_ADD_SEPARATOR) != 0)) {
				result.add(new Separator());
			}
			int ind = brName.lastIndexOf('/');
			String branch = brName.substring(ind + 1);
			
			CommandContributionItemParameter parameter = new CommandContributionItemParameter(
					serviceLocator, cmdId + (repositories == null ? ".all." : ".") + brName, cmdId, SWT.PUSH);
			if (repositories == null) {
				Map<String, Object> map = new HashMap<String, Object>();
				parameter.parameters = map;
				map.put("branch.ref", brName);
				map.put("all.repos", "true");
			} else {
				parameter.parameters = Collections.singletonMap("branch.ref", brName);
			}
			parameter.label = branch;
			parameter.icon = GerritToolsPlugin.getDefault().getImageRegistry().getDescriptor(
					((mode & MODE_STABLE) != 0) ? GerritToolsPlugin.IMG_BRANCH : GerritToolsPlugin.IMG_FEATURE_BRANCH);
			if (brName.equals(curBranch)) {
				result.add(new CurrentBranchCommandContributionItem(parameter));
			} else {
				result.add(new CommandContributionItem(parameter));
			}
		}
		return result.toArray(new IContributionItem[result.size()]);
	}

	private String getUserId(List<Repository> repos) {
		String userId = RepositoryUtils.getUserId(repos);
		if (userId == null) {
			throw new RuntimeException("Cannot determine user id");
		}
		return userId;
	}

	private IContributionItem[] getOthersFeatureBranchesItems(List<Repository> repositories, String cmdId) {
		List<String> branches = BranchingUtils.getBranches(repositories, 
				BranchingUtils.MODE_FEATURE_OTHERS, getUserId(repositories));
		List<IContributionItem> result = new ArrayList<IContributionItem>();
		List<String> users = new ArrayList<String>();
		for (String branch: branches) {
			if (branch.startsWith("refs/heads/features/")) {
				branch = branch.substring("refs/heads/features/".length());
				int pos = branch.indexOf('/');
				if (pos > 0) {
					String user = branch.substring(0, pos);
					if (!users.contains(user)){
						users.add(user);
						if (result.isEmpty()) {
							result.add(new Separator());
						}
						result.add(new OthersBranchesActionProvider(user, repositories, cmdId));
					}
				}
			}
		}
		return result.toArray(new IContributionItem[result.size()]);
	}

	public void initialize(IServiceLocator serviceLocator) {
		this.serviceLocator = serviceLocator;
	}

	
	private class OthersBranchesActionProvider extends ContributionItem {

		private String userId;
		private List<Repository> repositories;
		private String cmdId;

		public OthersBranchesActionProvider(String userId, List<Repository> repositories, String cmdId) {
			this.userId = userId;
			this.repositories = repositories;
			this.cmdId = cmdId;
			setId(cmdId + "." + userId);
		}
		
		@Override
		public void fill(Menu menu, int index) {
			MenuItem item = new MenuItem(menu, SWT.CASCADE);
			item.setText(userId);
			item.setImage(GerritToolsPlugin.getDefault().getImageRegistry().get(GerritToolsPlugin.IMG_FEATURE_BRANCH_OWNER));
			Menu subMenu = new Menu(menu);
			item.setMenu(subMenu);
			for (IContributionItem sub: createPerBranchContributionItems(repositories, 
					cmdId, MODE_FEATURE_USER, userId)) {
				sub.fill(item.getMenu(), -1);
			}
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
