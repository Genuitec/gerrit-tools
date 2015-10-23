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
package com.genuitec.eclipse.gerrit.tools.utils;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.egit.core.RepositoryCache;
import org.eclipse.egit.core.RepositoryUtil;
import org.eclipse.egit.ui.internal.credentials.EGitCredentialsProvider;
import org.eclipse.egit.ui.internal.fetch.FetchOperationUI;
import org.eclipse.egit.ui.internal.repository.tree.RepositoryTreeNode;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.StringUtils;
import org.eclipse.jgit.util.SystemReader;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;

import com.genuitec.eclipse.gerrit.tools.GerritToolsPlugin;

@SuppressWarnings("restriction")
public class RepositoryUtils {

	private static final RepositoryCache repositoryCache = org.eclipse.egit.core.Activator
			.getDefault().getRepositoryCache();
	private static final RepositoryUtil repositoryUtil = org.eclipse.egit.ui.Activator.getDefault()
			.getRepositoryUtil();

	public static List<Repository> getAllRepositories() {
		List<Repository> result = new ArrayList<Repository>();
		for (String repo: repositoryUtil.getConfiguredRepositories()) {
			File gitDir = new File(repo);
			if (gitDir.exists()) {
				try {
					Repository repository = repositoryCache.lookupRepository(gitDir);
					if (!repository.isBare()) {
						result.add(repository);
					}
				} catch (IOException e) {
					//ignore
				}
			}
		}
		return result;
	}
	
	public static Repository getRepositoryForName(String name) {
		for (String repo: repositoryUtil.getConfiguredRepositories()) {
			File gitDir = new File(repo);
			if (gitDir.exists()) {
				try {
					Repository repository = repositoryCache.lookupRepository(gitDir);
					if (!repository.isBare() && repository.getDirectory().getParentFile().getName().equals(name)) {
						return repository;
					}
				} catch (IOException e) {
					//ignore
				}
			}
		}
		return null;
	}
	
	public static String getUserId(List<Repository> repositories) {
		return getUserId(repositories != null && repositories.size() == 1 ? repositories.get(0) : null);
	}

	public static String getUserId(Repository repository) {
		StoredConfig config;
		if (repository == null) {
			if (StringUtils.isEmptyOrNull(SystemReader.getInstance().getenv(
					Constants.GIT_CONFIG_NOSYSTEM_KEY))) {
				config = SystemReader.getInstance().openSystemConfig(null,
						FS.DETECTED);
			} else {
				config = new FileBasedConfig(null, FS.DETECTED) {
					public void load() {
						// empty, do not load
					}

					public boolean isOutdated() {
						// regular class would bomb here
						return false;
					}
				};
			}
			try {
				config.load();
				config = SystemReader.getInstance().openUserConfig(config,
						FS.DETECTED);
				config.load();
			} catch (Exception ex) {
				throw new RuntimeException(ex);
			}
		} else {
			config = repository.getConfig();
		}
		String email = config.getString("user", null, "email"); //$NON-NLS-1$ //$NON-NLS-2$
		if (email != null) {
			int ind = email.indexOf('@');
			if (ind > 0) {
				return email.substring(0, ind);
			}
		}
		String username = SystemReader.getInstance().getProperty(Constants.OS_USER_NAME_KEY);
		if (username == null)
			username = Constants.UNKNOWN_USER_DEFAULT;
		return username;
	}
	
	public static Repository getRepository(ISelection selection) {
		if (selection instanceof IStructuredSelection) {
			if (((IStructuredSelection) selection).size() == 1) {
				Object el = ((IStructuredSelection) selection).getFirstElement();
				if (el instanceof RepositoryTreeNode) {
					return ((RepositoryTreeNode<?>) el).getRepository();
				}
			}
		}
		return null;
	}
	
	public static List<Repository> getRepositories(ISelection selection) {
		List<Repository> repos = new ArrayList<Repository>();
		if (selection instanceof IStructuredSelection) {
			for (Object el: ((IStructuredSelection) selection).toArray()) {
				if (el instanceof RepositoryTreeNode) {
					repos.add(((RepositoryTreeNode<?>) el).getRepository());
				}
			}
		}
		return repos;
	}
	
	public static void handleException(final Throwable ex) {
		Display displ = Display.findDisplay(Thread.currentThread());
		if (displ != null) {
			Throwable t = ex;
			GerritToolsPlugin.getDefault().log(t);
			if (t instanceof InvocationTargetException) {
				t = t.getCause();
			}
			MessageDialog.openError(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), 
					"Error", t.getMessage() + "\nSee Error Log for details");
		} else {
			Display.getDefault().asyncExec(new Runnable() {
				@Override
				public void run() {
					handleException(ex);
				}
			});
		}
	}
	
	public static void fetchOrigin(Shell shell, final Repository repository) {
		 //perform branch operation
        try {
            ProgressMonitorDialog progressDialog = new ProgressMonitorDialog(shell);
            progressDialog.run(true, true, new IRunnableWithProgress() {
				
				@Override
				public void run(IProgressMonitor monitor) throws InvocationTargetException,
						InterruptedException {
					try {
						monitor.beginTask("Fetching updates from origin", 100);
						FetchOperationUI fetchOp = new FetchOperationUI(repository, 
								new RemoteConfig(repository.getConfig(), "origin"), 3000, false); //$NON-NLS-1$
						fetchOp.setCredentialsProvider(new EGitCredentialsProvider());
						fetchOp.execute(new SubProgressMonitor(monitor, 100));
					} catch (Exception e) {
						throw new InvocationTargetException(e);
					}
				}
			});
        } catch (InterruptedException e) {
            //ignore
        } catch (Exception e) {
        	RepositoryUtils.handleException(e);
        }
	}
}
