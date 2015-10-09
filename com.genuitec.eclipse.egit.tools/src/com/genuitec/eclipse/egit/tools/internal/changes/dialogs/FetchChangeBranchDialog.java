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
package com.genuitec.eclipse.egit.tools.internal.changes.dialogs;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.mylyn.internal.gerrit.core.client.GerritClient;
import org.eclipse.mylyn.internal.gerrit.core.client.GerritException;
import org.eclipse.mylyn.internal.gerrit.core.client.data.GerritQueryResult;
import org.eclipse.mylyn.internal.gerrit.core.client.rest.ChangeInfo;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import com.genuitec.eclipse.egit.tools.EGitToolsPlugin;
import com.genuitec.eclipse.egit.tools.dialogs.SettingsDialog;
import com.genuitec.eclipse.egit.tools.utils.GerritUtils;

@SuppressWarnings("restriction")
public class FetchChangeBranchDialog extends SettingsDialog {

    public static final String PROP_BRANCH = "branch"; //$NON-NLS-1$
    public static final String PROP_CHANGE_ID = "change.id"; //$NON-NLS-1$
    public static final String PROP_CHANGE_TITLE = "change.title"; //$NON-NLS-1$
    public static final String PROP_PATCHSET_ID = "patchset.id"; //$NON-NLS-1$
    public static final String PROP_CHECKOUT = "checkout"; //$NON-NLS-1$
    
    private static final String[] ITEMS_LOADING = new String[] { "<loading>" };

	protected final Repository repository;
	private Combo change;
	private Text branch;
	private String projectCondition;
	private Combo patchset;
	private GerritClient client;
	private List<GerritQueryResult> changes;
	private Map<Integer, ChangeInfo> details;
	private PatchSetListInit patchSetInitJob = new PatchSetListInit();
	
	public FetchChangeBranchDialog(Shell shell, Repository repository) {
		super(shell, "Fetch change from Gerrit");
		this.repository = repository;
		this.projectCondition = " project:" + GerritUtils.getGerritProjectName(repository); //$NON-NLS-1$
		this.details = new HashMap<Integer, ChangeInfo>();
		client = GerritUtils.getGerritClient(repository, SubMonitor.convert(null));
		try {
			changes = client.getRestClient().executeQuery(SubMonitor.convert(null), "status:open" + projectCondition); //$NON-NLS-1$
		} catch (GerritException e) {
			MessageDialog.openError(shell, "Error", "Cannot fetch list of changes from Gerrit. See Error Log for details");
			throw new RuntimeException("Cannot fetch list of changes from Gerrit", e);
		}
		for (GerritQueryResult res: changes) {
			new DetailsFetchJob(res.getNumber()).schedule();
		}
	}
	
	@Override
	protected IStatus validate(String property, Object value) {
		if (property.equals(PROP_CHANGE_ID)) {
			if (value == null) {
				return createErrorStatus("Choose a change");
			}
		}
		if (property.equals(PROP_BRANCH)) {
			if (value == null || ((String)value).isEmpty()) {
				return createErrorStatus("Provide an existing change id");
			}
		}
		if (property.equals(PROP_PATCHSET_ID)) {
			if (value == null || ((String)value).isEmpty()) {
				return createErrorStatus("Provide patchset nr");
			}
			try {
				Integer.parseInt((String)value);
			} catch (Exception e) {
				return createErrorStatus("Patchset nr must be an integer value");
			}
		}
		return super.validate(property, value);
	}
	
	@Override
	protected void createDialogContents(Composite parent) {
		change = createOptionComboEditor(parent, "Change id:", 
				PROP_CHANGE_ID, "", getGerritChanges(), true); //$NON-NLS-1$
		change.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				setFinishEnabled(false);
				updateChangeId();
				if (change.getSelectionIndex() >= 0) {
					GerritQueryResult res = changes.get(change.getSelectionIndex());
					setSetting(PROP_CHANGE_TITLE, res.getSubject());
					String b = res.getBranch();
					branch.setText(b);
					setSetting(PROP_BRANCH, b);
					patchSetInitJob.init(res);
				}
			}
		});
		
		branch = createOptionTextEditor(parent, "Branch:", PROP_BRANCH, "", SWT.READ_ONLY); //$NON-NLS-2$
		branch.setBackground(branch.getParent().getBackground());
		branch.setForeground(branch.getParent().getForeground());
		
		patchset = createOptionComboEditor(parent, "Patchset nr:", PROP_PATCHSET_ID, "1",  //$NON-NLS-2$
				new String[] { "1" }, false); //$NON-NLS-1$
		createOptionCheckBox(parent, "Checkout new branch", PROP_CHECKOUT, true);
	}
	
	private void setFinishEnabled(boolean enabled) {
		Control button = getButton(IDialogConstants.OK_ID);
		if (button != null) {
			button.setEnabled(enabled);
		}
	}
	
	private void updateChangeId() {
		String change = ((String) getSetting(PROP_CHANGE_ID)).trim();
		int id = 0;
		for (int i = 0; i < change.length(); i++) {
			char ch = change.charAt(i);
			if ('0' <= ch && ch <='9') {
				id = id*10 + ch - '0';
			} else {
				break;
			}
		}
		setSetting(PROP_CHANGE_ID, Integer.toString(id));
	}

	private String[] getGerritChanges() {
		String[] res = new String[changes.size()];
		for (int i = 0; i < res.length; i++) {
			GerritQueryResult qr = changes.get(i);
			res[i] = MessageFormat.format("{0}: {1} [{2}]", //$NON-NLS-1$
					Integer.toString(qr.getNumber()), qr.getSubject(), qr.getOwner().getName());
		}
		return res;
	}
	
	@Override
	protected void cancelPressed() {
		patchSetInitJob.cancel();
		super.cancelPressed();
	}

	private class PatchSetListInit extends Job {
		
		private GerritQueryResult res;
		
		public PatchSetListInit() {
			super("PatchSet list init job"); //$NON-NLS-1$
			setSystem(true);
		}
		
		public void init(GerritQueryResult res) {
			synchronized (details) {
				ChangeInfo detail = details.get(res.getNumber());
				if (detail != null) {
					fillCombo(detail);
					return;
				}
			}
			patchset.setItems(ITEMS_LOADING);
			patchset.select(0);
			setFinishEnabled(false);
			this.res = res;
			schedule();
		}

		@Override
		protected IStatus run(IProgressMonitor monitor) {
			synchronized (details) {
				int i = 0;
				while(i++ < 10) {
					if (details.containsKey(res.getNumber())) {
						final ChangeInfo detail = details.get(res.getNumber());
						if (detail != null) {
							Display.getDefault().asyncExec(new Runnable() {
								@Override
								public void run() {
									fillCombo(detail);	
								}
							});
							return Status.OK_STATUS;
						}
						break;
					} else {
						try {
							details.wait(500);
						} catch (InterruptedException e) {
							break;
						}
					}
				}
			}
			Display.getDefault().asyncExec(new Runnable() {
				@Override
				public void run() {
					patchset.setItems(new String[] {"Error loading patchsets. See Error Log for details."});
					patchset.select(0);
					setFinishEnabled(false);
				}
			});
			return Status.OK_STATUS;
		}
		
		private void fillCombo(ChangeInfo detail) {
			int max = detail.getCurrentPatchSetId() != null ? 
					detail.getCurrentPatchSetId().get() : 1;
			patchset.setItems(new String[] { Integer.toString(max) });
			patchset.select(0);
			setFinishEnabled(true);
		}
		
	}
	
	private class DetailsFetchJob extends Job {

		private int reviewId;
		
		public DetailsFetchJob(int reviewId) {
			super(MessageFormat.format("Fetching details of change {0}", reviewId));
			this.reviewId = reviewId;
		}
		
		@Override
		protected IStatus run(IProgressMonitor monitor) {
			try {
				ChangeInfo ci = client.getChangeInfo(reviewId, monitor);
				synchronized (details) {
					details.put(reviewId, ci);
					details.notifyAll();
				}
			} catch (GerritException e) {
				synchronized (details) {
					details.put(reviewId, null);
					details.notifyAll();
				}
				EGitToolsPlugin.getDefault().log(e);
			}
			return Status.OK_STATUS;
		}
		
	}
	
}
