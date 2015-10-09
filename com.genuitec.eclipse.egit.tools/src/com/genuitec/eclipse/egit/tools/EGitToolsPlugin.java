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
package com.genuitec.eclipse.egit.tools;

import org.eclipse.jface.resource.ImageRegistry;
import org.osgi.framework.BundleContext;

import com.genuitec.eclipse.egit.tools.utils.AbstractGenuitecUIPlugin;

/**
 * The activator class controls the plug-in life cycle
 */
public class EGitToolsPlugin extends AbstractGenuitecUIPlugin {

	// The plug-in ID
	public static final String PLUGIN_ID = "com.genuitec.eclipse.egit"; //$NON-NLS-1$

	// The shared instance
	private static EGitToolsPlugin plugin;
	
	public static final String IMG_BRANCH = "icons/branch.gif"; //$NON-NLS-1$
	public static final String IMG_FEATURE_BRANCH = "icons/feature-branch.gif"; //$NON-NLS-1$
	public static final String IMG_FEATURE_BRANCH_OWNER = "icons/feature-branch-owner.gif"; //$NON-NLS-1$
	public static final String IMG_GERRIT_CHANGE = "icons/gerrit-change.gif"; //$NON-NLS-1$
	public static final String IMG_GERRIT_CLEANUP = "icons/gerrit-cleanup.gif"; //$NON-NLS-1$
	public static final String IMG_GIT_REPO = "icons/git-repo.gif"; //$NON-NLS-1$
	
	/**
	 * The constructor
	 */
	public EGitToolsPlugin() {
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.ui.plugin.AbstractUIPlugin#start(org.osgi.framework.BundleContext)
	 */
	public void start(BundleContext context) throws Exception {
		super.start(context);
		plugin = this;
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.ui.plugin.AbstractUIPlugin#stop(org.osgi.framework.BundleContext)
	 */
	public void stop(BundleContext context) throws Exception {
		plugin = null;
		super.stop(context);
	}

	/**
	 * Returns the shared instance
	 *
	 * @return the shared instance
	 */
	public static EGitToolsPlugin getDefault() {
		return plugin;
	}
	
	@Override
	protected void initializeImageRegistry(ImageRegistry reg) {
		addImagesToRegistry(reg,
				IMG_BRANCH, 
				IMG_FEATURE_BRANCH, 
				IMG_FEATURE_BRANCH_OWNER,
				IMG_GERRIT_CHANGE,
				IMG_GERRIT_CLEANUP,
				IMG_GIT_REPO);
	}

}
