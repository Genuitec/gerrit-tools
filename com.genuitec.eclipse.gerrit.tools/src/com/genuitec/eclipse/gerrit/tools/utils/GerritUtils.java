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

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.resource.StringConverter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.mylyn.commons.net.AuthenticationCredentials;
import org.eclipse.mylyn.commons.net.AuthenticationType;
import org.eclipse.mylyn.internal.gerrit.core.GerritConnector;
import org.eclipse.mylyn.internal.gerrit.core.client.GerritClient;
import org.eclipse.mylyn.internal.tasks.core.IRepositoryConstants;
import org.eclipse.mylyn.internal.tasks.core.TaskRepositoryManager;
import org.eclipse.mylyn.internal.tasks.ui.TasksUiPlugin;
import org.eclipse.mylyn.reviews.core.spi.ReviewsConnector;
import org.eclipse.mylyn.tasks.core.TaskRepository;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.PlatformUI;

import com.genuitec.eclipse.gerrit.tools.GerritToolsPlugin;

@SuppressWarnings("restriction")
public class GerritUtils {
	
	private static String GERRIT_REPO_KIND = "org.eclipse.mylyn.gerrit"; //$NON-NLS-1$

	public static String getGerritProjectName(Repository repository) {
		try {
			RemoteConfig config = new RemoteConfig(repository.getConfig(),
					"origin"); //$NON-NLS-1$
			
			List<URIish> urls = new ArrayList<URIish>(config.getPushURIs());
			urls.addAll(config.getURIs());
			
			for (URIish uri: urls) {
				if (uri.getPort() == 29418) { //Gerrit refspec
					String path = uri.getPath();
					while (path.startsWith("/")) { //$NON-NLS-1$
						path = path.substring(1);
					}
					return path;
				}
				break;
			}
		} catch (Exception e) {
			GerritToolsPlugin.getDefault().log(e);
		}
		return null;
	}
	
	public static String getGerritURL(Repository repository) {
		try {
			RemoteConfig config = new RemoteConfig(repository.getConfig(),
					"origin"); //$NON-NLS-1$
			
			List<URIish> urls = new ArrayList<URIish>(config.getPushURIs());
			urls.addAll(config.getURIs());
			
			for (URIish uri: urls) {
				if (uri.getPort() == 29418) { //Gerrit refspec
					return "https://" + uri.getHost(); //$NON-NLS-1$
				}
				break;
			}
		} catch (Exception e) {
			GerritToolsPlugin.getDefault().log(e);
		}
		return null;
	}
	
	//, final String defaultUser
	
	public static GerritClient getGerritClient(Repository gitRepository, SubMonitor monitor) {
		
		final String gerritURL = getGerritURL(gitRepository);
		if (gerritURL == null) {
			throw new RuntimeException(MessageFormat.format("Cannot detect Gerrit URL of repository {0}", 
					gitRepository.getDirectory().toString()));
		}
		final String defaultUser = RepositoryUtils.getUserId(gitRepository);

		monitor.beginTask("", 1);
		monitor.subTask("locating Gerrit configuration");
		
		final TaskRepositoryManager[] repoManager = new TaskRepositoryManager[1];
		
		if (Job.getJobManager().currentJob() != null) {
			Job mylynInitJob = new Job("Initialize Mylyn Task Repository Manager") {
				@Override
				protected IStatus run(IProgressMonitor monitor) {
					repoManager[0] = TasksUiPlugin.getRepositoryManager();
					return Status.OK_STATUS;
				}
			};
			mylynInitJob.schedule();
			while (mylynInitJob.getState() != Job.NONE) {
				Job.getJobManager().currentJob().yieldRule(monitor.newChild(0));
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					//ignore
				}
			}
		}
		
		if (repoManager[0] == null) {
			repoManager[0] = TasksUiPlugin.getRepositoryManager();
		}
		
		Set<TaskRepository> repos = repoManager[0].getRepositories(GERRIT_REPO_KIND);
		
		ReviewsConnector connector = (ReviewsConnector) TasksUiPlugin.getConnector(GERRIT_REPO_KIND);
		
		GerritClient client = null;
		
		for (TaskRepository repo: repos) {
			if (repo.getUrl().equals(gerritURL)) {
				client = (GerritClient) connector.getReviewClient(repo);
				break;
			}
		}
		
		if (client == null) {				
			TaskRepository repository = new TaskRepository(GERRIT_REPO_KIND, gerritURL);
			repository.setVersion(TaskRepository.NO_VERSION_SPECIFIED);
			repository.setRepositoryLabel(gerritURL);
			repository.setProperty(IRepositoryConstants.PROPERTY_CATEGORY, TaskRepository.CATEGORY_REVIEW);
			repository.removeProperty(GerritConnector.KEY_REPOSITORY_ACCOUNT_ID);
			repository.removeProperty(GerritConnector.KEY_REPOSITORY_AUTH);
			
			//Authentication
			final Display display = Display.getDefault();
			final String[] password = new String[2];
			display.syncExec(new Runnable() {

				@Override
				public void run() {
					PasswordDialog dialog = new PasswordDialog(
							PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), 
							"Password required", 
							MessageFormat.format(
									"Provide login and password to access {0}", gerritURL), defaultUser);
					if (dialog.open() == Dialog.OK) {
						password[0] = dialog.getLogin();
						password[1] = dialog.getPassword();
					}
				}
				
			});
			if (password[0] == null) {
				throw new OperationCanceledException();
			}
			AuthenticationCredentials credentials = new AuthenticationCredentials(password[0], password[1]);
			repository.setCredentials(AuthenticationType.REPOSITORY, credentials, true);
			repository.setCredentials(AuthenticationType.HTTP, credentials, true);
			
			//Proxy
			repository.setDefaultProxyEnabled(true);
			repository.setProperty(TaskRepository.PROXY_HOSTNAME, ""); //$NON-NLS-1$
			repository.setProperty(TaskRepository.PROXY_PORT, ""); //$NON-NLS-1$
			credentials = new AuthenticationCredentials("", ""); //$NON-NLS-1$ //$NON-NLS-2$
			repository.setCredentials(AuthenticationType.PROXY, credentials, true);
			
			//OpenID
			repository.setProperty(GerritConnector.KEY_REPOSITORY_OPEN_ID_ENABLED, "false"); //$NON-NLS-1$
			repository.setProperty(GerritConnector.KEY_REPOSITORY_OPEN_ID_PROVIDER, ""); //$NON-NLS-1$
			
			//add repo
			repoManager[0].addRepository(repository);
			
			//get client
			client = (GerritClient) connector.getReviewClient(repository);
			
		}
		return client;
	}
	
	private static class PasswordDialog extends Dialog {

	    private String title;
	    private String message;
	    private String login = "";//$NON-NLS-1$
	    private String password = "";//$NON-NLS-1$

	    private Button okButton;
	    private Text loginText;
	    private Text passwordText;
	    private Text errorMessageText;
	    private String errorMessage;

	    /**
	     * Creates an input dialog with OK and Cancel buttons. Note that the dialog
	     * will have no visual representation (no widgets) until it is told to open.
	     * <p>
	     * Note that the <code>open</code> method blocks for input dialogs.
	     * </p>
	     * 
	     * @param parentShell
	     *            the parent shell, or <code>null</code> to create a top-level
	     *            shell
	     * @param dialogTitle
	     *            the dialog title, or <code>null</code> if none
	     * @param dialogMessage
	     *            the dialog message, or <code>null</code> if none
	     * @param initialValue
	     *            the initial input value, or <code>null</code> if none
	     *            (equivalent to the empty string)
	     * @param validator
	     *            an input validator, or <code>null</code> if none
	     */
	    public PasswordDialog(Shell parentShell, String dialogTitle,
	            String dialogMessage, String initialLogin) {
	        super(parentShell);
	        this.title = dialogTitle;
	        message = dialogMessage;
	        if (initialLogin == null) {
				login = "";//$NON-NLS-1$
			} else {
				login = initialLogin;
			}
	    }

	    /*
	     * (non-Javadoc) Method declared on Dialog.
	     */
	    protected void buttonPressed(int buttonId) {
	        if (buttonId == IDialogConstants.OK_ID) {
	            login = loginText.getText();
	            password = passwordText.getText();
	        } else {
	            login = null;
	            password = null;
	        }
	        super.buttonPressed(buttonId);
	    }

	    /*
	     * (non-Javadoc)
	     * 
	     * @see org.eclipse.jface.window.Window#configureShell(org.eclipse.swt.widgets.Shell)
	     */
	    protected void configureShell(Shell shell) {
	        super.configureShell(shell);
	        if (title != null) {
				shell.setText(title);
			}
	    }

	    /*
	     * (non-Javadoc)
	     * 
	     * @see org.eclipse.jface.dialogs.Dialog#createButtonsForButtonBar(org.eclipse.swt.widgets.Composite)
	     */
	    protected void createButtonsForButtonBar(Composite parent) {
	        // create OK and Cancel buttons by default
	        okButton = createButton(parent, IDialogConstants.OK_ID,
	                IDialogConstants.OK_LABEL, true);
	        createButton(parent, IDialogConstants.CANCEL_ID,
	                IDialogConstants.CANCEL_LABEL, false);
	        //do this here because setting the text will set enablement on the ok
	        // button
	        passwordText.setFocus();
	        if (login != null) {
	            loginText.setText(login);
	        }
	    }

	    /*
	     * (non-Javadoc) Method declared on Dialog.
	     */
	    protected Control createDialogArea(Composite parent) {
	        // create composite
	        Composite composite = (Composite) super.createDialogArea(parent);
	        ((GridLayout)composite.getLayout()).numColumns = 2;
	        ((GridLayout)composite.getLayout()).makeColumnsEqualWidth = false;
	        
	        // create message
	        if (message != null) {
	            Label label = new Label(composite, SWT.WRAP);
	            label.setText(message);
	            GridData data = new GridData(GridData.GRAB_HORIZONTAL
	                    | GridData.GRAB_VERTICAL | GridData.HORIZONTAL_ALIGN_FILL
	                    | GridData.VERTICAL_ALIGN_CENTER);
	            data.widthHint = convertHorizontalDLUsToPixels(IDialogConstants.MINIMUM_MESSAGE_AREA_WIDTH);
	            data.horizontalSpan = 2;
	            label.setLayoutData(data);
	            label.setFont(parent.getFont());
	        }
	        new Label(composite, SWT.NONE).setText("Login:");
	        loginText = new Text(composite, getInputTextStyle());
	        loginText.setLayoutData(new GridData(GridData.GRAB_HORIZONTAL
	                | GridData.HORIZONTAL_ALIGN_FILL));
	        loginText.addModifyListener(new ModifyListener() {
	            public void modifyText(ModifyEvent e) {
	                validateInput();
	            }
	        });
	        new Label(composite, SWT.NONE).setText("Password:");
	        passwordText = new Text(composite, getInputTextStyle() | SWT.PASSWORD);
	        passwordText.setLayoutData(new GridData(GridData.GRAB_HORIZONTAL
	                | GridData.HORIZONTAL_ALIGN_FILL));
	        passwordText.addModifyListener(new ModifyListener() {
	            public void modifyText(ModifyEvent e) {
	                validateInput();
	            }
	        });
	        errorMessageText = new Text(composite, SWT.READ_ONLY | SWT.WRAP);
	        errorMessageText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 2,1));
	        errorMessageText.setBackground(errorMessageText.getDisplay()
	                .getSystemColor(SWT.COLOR_WIDGET_BACKGROUND));
	        // Set the error message text
	        // See https://bugs.eclipse.org/bugs/show_bug.cgi?id=66292
	        setErrorMessage(errorMessage);

	        applyDialogFont(composite);
	        return composite;
	    }

	    public String getLogin() {
	        return login;
	    }
	    
	    public String getPassword() {
	        return password;
	    }

	    /**
	     * Validates the input.
	     * <p>
	     * The default implementation of this framework method delegates the request
	     * to the supplied input validator object; if it finds the input invalid,
	     * the error message is displayed in the dialog's message line. This hook
	     * method is called whenever the text changes in the input field.
	     * </p>
	     */
	    protected void validateInput() {
	        String errorMessage = null;
	        if (loginText.getText().trim().isEmpty()) {
	        	errorMessage = "Provide login";
	        }
	        if (passwordText.getText().trim().isEmpty()) {
	        	errorMessage = "Provide password";
	        }
	        // Bug 16256: important not to treat "" (blank error) the same as null
	        // (no error)
	        setErrorMessage(errorMessage);
	    }

	    /**
	     * Sets or clears the error message.
	     * If not <code>null</code>, the OK button is disabled.
	     * 
	     * @param errorMessage
	     *            the error message, or <code>null</code> to clear
	     * @since 3.0
	     */
	    public void setErrorMessage(String errorMessage) {
	    	this.errorMessage = errorMessage;
	    	if (errorMessageText != null && !errorMessageText.isDisposed()) {
	    		errorMessageText.setText(errorMessage == null ? " \n " : errorMessage); //$NON-NLS-1$
	    		// Disable the error message text control if there is no error, or
	    		// no error text (empty or whitespace only).  Hide it also to avoid
	    		// color change.
	    		// See https://bugs.eclipse.org/bugs/show_bug.cgi?id=130281
	    		boolean hasError = errorMessage != null && (StringConverter.removeWhiteSpaces(errorMessage)).length() > 0;
	    		errorMessageText.setEnabled(hasError);
	    		errorMessageText.setVisible(hasError);
	    		errorMessageText.getParent().update();
	    		// Access the ok button by id, in case clients have overridden button creation.
	    		// See https://bugs.eclipse.org/bugs/show_bug.cgi?id=113643
	    		Control button = getButton(IDialogConstants.OK_ID);
	    		if (button != null) {
	    			button.setEnabled(errorMessage == null);
	    		}
	    	}
	    }
	    
		/**
		 * Returns the style bits that should be used for the input text field.
		 * Defaults to a single line entry. Subclasses may override.
		 * 
		 * @return the integer style bits that should be used when creating the
		 *         input text
		 * 
		 * @since 3.4
		 */
		protected int getInputTextStyle() {
			return SWT.SINGLE | SWT.BORDER;
		}
	}
	
}
