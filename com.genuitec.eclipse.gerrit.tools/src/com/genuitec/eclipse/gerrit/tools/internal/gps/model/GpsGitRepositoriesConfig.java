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
package com.genuitec.eclipse.gerrit.tools.internal.gps.model;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.egit.core.op.CloneOperation;
import org.eclipse.egit.ui.internal.credentials.EGitCredentialsProvider;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.genuitec.eclipse.gerrit.tools.GerritToolsPlugin;
import com.genuitec.eclipse.gerrit.tools.utils.RepositoryUtils;
import com.genuitec.eclipse.gerrit.tools.utils.XMLUtils;
import com.genuitec.eclipse.gerrit.tools.utils.XmlException;

@SuppressWarnings("restriction")
public class GpsGitRepositoriesConfig implements IGpsRepositoriesConfig {

    public static final String PROP_CONFIGURE_PUSH_TO_UPSTREAM = "configure.push.to.upstream"; //$NON-NLS-1$
    public static final String PROP_RECONFIGURE_BRANCH = "reconfigure.branch"; //$NON-NLS-1$
    public static final String PROP_FORCE_CHECKOUT = "force.checkout"; //$NON-NLS-1$
    public static final String PROP_AUTO_PULL = "auto.pull"; //$NON-NLS-1$
    
	private static final String ELEM_REPOSITORY = "repository"; //$NON-NLS-1$
	
	private static final String ATTR_NAME = "name"; //$NON-NLS-1$
	private static final String ATTR_BRANCH = "branch"; //$NON-NLS-1$
	private static final String ATTR_URL = "url"; //$NON-NLS-1$
	
	
	private Map<String, RepoSetup> repo2branch = new TreeMap<String, RepoSetup>();
	
	public GpsGitRepositoriesConfig(Element el) throws XmlException {
		load(el);
	}

	public GpsGitRepositoriesConfig(GpsFile gpsFile) throws CoreException {
		
		for (GpsProject project: gpsFile.getProjects()) {
			final IGpsRepositoryHandler handler = project.getRepositoryHandler();
			if (handler instanceof GpsGitRepositoryHandler) {
				final String repoName = ((GpsGitRepositoryHandler) handler).getRepositoryPath().segment(0);
				repo2branch.put(repoName, null);
			}
		}
		
		for (String repoName: repo2branch.keySet()) {
			//acquire project's repository
			Repository repository = RepositoryUtils.getRepositoryForName(repoName);
			if (repository == null ) {
				throw new CoreException(new Status(IStatus.ERROR, GerritToolsPlugin.PLUGIN_ID, MessageFormat.format(
						"Cannot store configuration for repository {0}. It is not configured.",
						repoName)));
			}
			try {
				repo2branch.put(repoName, new RepoSetup(repoName, repository.getFullBranch(), getRepoUrl(repository)));
			} catch (IOException e) {
				throw new CoreException(new Status(IStatus.ERROR, GerritToolsPlugin.PLUGIN_ID, MessageFormat.format(
						"Cannot store configuration for repository {0}:\n{1}",
						repoName, e.getMessage()), e));
			}
		}
		
	}

	private String getRepoUrl(Repository repository) {
		try {
			RemoteConfig config = new RemoteConfig(repository.getConfig(), "origin"); //$NON-NLS-1$
			for (URIish uri: config.getURIs()) {
				if (uri.getUser() != null) {
					return uri.setUser("user-name").toASCIIString(); //$NON-NLS-1$
				}
				return uri.toASCIIString();
			}
		} catch (Exception e) {
			GerritToolsPlugin.getDefault().log(e);
		}
		return null;
	}

	public String getType() {
		return "git"; //$NON-NLS-1$
	}
	
	private void load(Element element) throws XmlException {
		for (Element el: XMLUtils.getChildElements(element.getChildNodes())) {
			if (el.getNodeName().equals(ELEM_REPOSITORY)) {
				RepoSetup setup = new RepoSetup(el);
				repo2branch.put(setup.name, setup);
			}
		}
	}

	public void serialize(Element root) {
		Document document = root.getOwnerDocument();
		for (Entry<String, RepoSetup> entry: repo2branch.entrySet()) {
	    	Element repoElement = document.createElement(ELEM_REPOSITORY);
	    	entry.getValue().serialize(repoElement);
	    	root.appendChild(repoElement);
		}
	}

	public void performConfiguration(Map<String, Object> options, SubMonitor monitor) throws CoreException {
		monitor.beginTask("", repo2branch.size()*2);
		for (Entry<String, RepoSetup> entry: repo2branch.entrySet()) {
            if (monitor.isCanceled()) return;
            
            RepoSetup repo = entry.getValue();
            
			String repositoryName = repo.name;
			String repositoryBranch = repo.branch;
			boolean localBranch = repositoryBranch.startsWith("refs/heads/"); //$NON-NLS-1$
			String branchName = null;
			if (localBranch) {
			    branchName = repositoryBranch.substring(11);
			}
			switch (repo.state) {
				case LOCATED:
					org.eclipse.egit.ui.Activator.getDefault().getRepositoryUtil().addConfiguredRepository(
							new File(repo.location, ".git")); //$NON-NLS-1$
					break;
				case CLONE:
					monitor.setTaskName("Cloning repository " + repositoryName);
					monitor.subTask("");
					try {
						URIish uri = new URIish(repo.url);
						if (repo.userName != null) {
							uri = uri.setUser(repo.userName);
						} else {
							uri = uri.setUser(null);
						}
						CloneOperation co = new CloneOperation(uri, true, null, 
								repo.location, repositoryBranch, "origin", 5000); //$NON-NLS-1$
						co.setCredentialsProvider(new EGitCredentialsProvider());
						co.setCloneSubmodules(true);
						co.run(new SubProgressMonitor(monitor, 0, SubProgressMonitor.PREPEND_MAIN_LABEL_TO_SUBTASK));
						
						org.eclipse.egit.ui.Activator.getDefault().getRepositoryUtil().addConfiguredRepository(
								co.getGitDir());
				        
						break;
					} catch (Throwable e) {
						if (e instanceof InvocationTargetException) {
							e = e.getCause();
						}
						throw new CoreException(new Status(IStatus.ERROR, GerritToolsPlugin.PLUGIN_ID, 
								(e instanceof InterruptedException) ? "Operation cancelled" : e.getMessage(), e));
					}
				default:
			}
			
			monitor.setTaskName("Preparing repository " + repositoryName);
			
			Repository repository = RepositoryUtils.getRepositoryForName(repositoryName);
			if (repository == null ) {
				throw new CoreException(new Status(IStatus.ERROR, GerritToolsPlugin.PLUGIN_ID, MessageFormat.format(
						"Cannot continue. A required git repository named {0} is not configured.",
						repositoryName)));
			}
			
			monitor.subTask(MessageFormat.format("Checking out branch \"{0}\" of git repository \"{1}\"",
					repositoryBranch, repositoryName));
			
			if (repositoryBranch != null && repositoryBranch.length() > 0) {
                //checkout the branch
			    boolean newBranch = false;
				try {
				    Ref ref = repository.getRef(repositoryBranch);
				    if (localBranch && ref == null) {
				        String originBranch = "refs/remotes/origin/" + branchName; //$NON-NLS-1$
				        ref = repository.getRef(originBranch);
				        if (ref == null) {
			                try {
			                    new Git(repository).fetch().setRemote("origin").call(); //$NON-NLS-1$
			                } catch (Exception e) {
			                    throw new CoreException(new Status(IStatus.ERROR, GerritToolsPlugin.PLUGIN_ID, 
			                        MessageFormat.format("Cannot fetch from remote 'origin' of repository \"{0}\":\n{1}",
			                                repositoryName, e.getMessage(), 
			                                e)));
			                }
				        }
                        ref = repository.getRef(originBranch);
                        if (ref == null) {
                            throw new CoreException(new Status(IStatus.ERROR, GerritToolsPlugin.PLUGIN_ID, 
                                MessageFormat.format("Cannot find branch \"{1}\" in repository \"{0}\".",
                                        repositoryName, originBranch)));
                        }
				        //we need to create the local branch based on remote branch
				        new Git(repository).branchCreate().
				            setName(branchName).
				            setStartPoint(originBranch).
				            setUpstreamMode(SetupUpstreamMode.TRACK).
				            call();
				        newBranch = true;
				    }
		            if (monitor.isCanceled()) return;
                    
					try {
    				    new Git(repository).checkout().
    					        setName(repositoryBranch).
    					        call();
					} catch (Exception e) {
					    if (options.containsKey(PROP_FORCE_CHECKOUT) && (Boolean)options.get(PROP_FORCE_CHECKOUT)) {
					        //try to reset
					        new Git(repository).reset().
					                setMode(ResetType.HARD).
					                call();
				            
					        //and then checkout again
	                        new Git(repository).checkout().
	                                setName(repositoryBranch).
	                                call();
					    } else {
					        throw e;
					    }
					}
					
					int fileCount = repository.getDirectory().getParentFile().list().length;
					if (fileCount == 1) {
						//we need to hard reset the repository - there are no files in it
						new Git(repository).reset().
			                setMode(ResetType.HARD).
			                call();
					}
					
				} catch (Exception e) {
					throw new CoreException(new Status(IStatus.ERROR, GerritToolsPlugin.PLUGIN_ID, 
							MessageFormat.format("Cannot checkout branch \"{1}\" of repository \"{0}\":\n{2}",
									repositoryName, repositoryBranch, e.getMessage(), 
									e)));
				}
	            if (monitor.isCanceled()) return;
				
				if (localBranch) {
	                monitor.subTask(MessageFormat.format("Configuring branch \"{0}\" of git repository \"{1}\"",
	                    repositoryBranch, repositoryName));
    				try {
    	                StoredConfig config = repository.getConfig();
    	                
    	                if (options.get(PROP_CONFIGURE_PUSH_TO_UPSTREAM) != null && (Boolean)options.get(PROP_CONFIGURE_PUSH_TO_UPSTREAM)) {
    	                    //configure push to upstream
                            config.setString("remote", "origin", "push", repositoryBranch + ":refs/for/" + branchName); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
    	                }
    	                if (newBranch || (options.get(PROP_RECONFIGURE_BRANCH) != null && (Boolean)options.get(PROP_RECONFIGURE_BRANCH))) {
                            config.setString("branch", branchName, "remote", "origin"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                            config.setString("branch", branchName, "merge", repositoryBranch); //$NON-NLS-1$ //$NON-NLS-2$
                            config.setString("branch", branchName, "rebase", "true"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    	                }
                        config.save();
                    } catch (Exception e) {
                        throw new CoreException(new Status(IStatus.ERROR, GerritToolsPlugin.PLUGIN_ID, 
                            MessageFormat.format("Cannot configure branch \"{1}\" of repository \"{0}\":\n{2}",
                                    repositoryName, repositoryBranch, e.getMessage(), 
                                    e)));
                    }
				}
	            if (monitor.isCanceled()) return;
				
				if (options.containsKey(PROP_AUTO_PULL) && (Boolean)options.get(PROP_AUTO_PULL)) {
    				monitor.subTask(MessageFormat.format("Pulling branch \"{0}\" from git repository \"{1}\"",
                        repositoryBranch, repositoryName));
    				
    				try {
    				    new Git(repository).pull().call();
    				} catch (Exception e) {
                        throw new CoreException(new Status(IStatus.ERROR, GerritToolsPlugin.PLUGIN_ID, 
                            MessageFormat.format("Cannot pull branch \"{1}\" of repository \"{0}\":\n{2}",
                                    repositoryName, repositoryBranch, e.getMessage(), 
                                    e)));
                    }
				}
			}
			
			monitor.worked(1);
		}
		
	}
	
	@Override
	public IGpsRepositorySetup[] getRepositoriesSetups() {
		return repo2branch.values().toArray(
				new IGpsRepositorySetup[repo2branch.values().size()]);
	}

	
	private static class RepoSetup implements IGpsRepositorySetup {
		
		public final String name;
		public final String branch;
		public final String url;
		
		private State state;
		private String userName;
		private File location;
		private String error;
		
		public RepoSetup(String name, String branch, String url) {
			assert name != null && branch != null;
			this.name = name;
			this.branch = branch;
			this.url = url;
		}
		
		public RepoSetup(Element repositoryEl) throws XmlException {
			this.name = repositoryEl.getAttribute(ATTR_NAME);
			if (name == null || name.length() == 0) {
				XMLUtils.reportMissingAttribute(repositoryEl, ATTR_NAME);
			}
			this.branch = repositoryEl.getAttribute(ATTR_BRANCH);
			if (branch == null || branch.length() == 0) {
				XMLUtils.reportMissingAttribute(repositoryEl, ATTR_BRANCH);
			}
			this.url = repositoryEl.getAttribute(ATTR_URL);
			if (url == null || url.length() == 0) {
				XMLUtils.reportMissingAttribute(repositoryEl, ATTR_URL);
			}
			determineState();
		}
		
		public void serialize(Element repositoryEl) {
			repositoryEl.setAttribute(ATTR_NAME, name);
			repositoryEl.setAttribute(ATTR_BRANCH, branch);
			if (url != null) {
				repositoryEl.setAttribute(ATTR_URL, url);
			}
		}
		
		public void determineState() {
			URIish urish;
			try {
				if (url != null) {
					urish = new URIish(url);
				} else {
					urish = null;
				}
			} catch (URISyntaxException e) {
				throw new RuntimeException(e);
			}
			
			//see if the repo is already present
			Repository repo;
			if ((repo = RepositoryUtils.getRepositoryForName(name)) != null) {
				state = State.PRESENT;
				location = repo.getDirectory();
				return;
			}
			
			//locate the repository in the workspace folder
			File workspace = ResourcesPlugin.getWorkspace().getRoot().getLocation().toFile();
			
			if ((location = new File(workspace, name)).exists()) {
				if (new File(location, ".git/config").exists()) { //$NON-NLS-1$
					state = State.LOCATED;
					return;
				}
				throw new RuntimeException(
						MessageFormat.format("{0} does not appear to be a valid git repository.",
								location.toString()));
			}
			
			if (urish == null) {
				state = State.NOT_FOUND;
				return;
			}
			
			//we need to clone
			if (urish.getUser() != null) {
				userName = RepositoryUtils.getUserId((Repository)null);
				if (userName == null) {
					userName = "user-name";
				}
			} else {
				userName = null;
			}			
			state = State.CLONE;
		}
		
		@Override
		public String getName() {
			return name;
		}
		
		@Override
		public State getState() {
			return state;
		}
		
		@Override
		public String getUserName() {
			return userName;
		}

		@Override
		public void setUserName(String name) {
			this.userName = name;
		}
		
	}

}
