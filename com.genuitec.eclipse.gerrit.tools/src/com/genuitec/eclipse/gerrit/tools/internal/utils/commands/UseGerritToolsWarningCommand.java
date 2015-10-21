/*
 * Copyright 2015, Genuitec, LLC
 * All Rights Reserved.
 */
package com.genuitec.eclipse.gerrit.tools.internal.utils.commands;

import java.net.URL;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.egit.ui.internal.commands.shared.FetchChangeFromGerritCommand;
import org.eclipse.egit.ui.internal.commands.shared.PushHeadToGerritCommand;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.MessageDialogWithToggle;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;

import com.genuitec.eclipse.gerrit.tools.GerritToolsPlugin;

@SuppressWarnings("restriction")
public class UseGerritToolsWarningCommand extends SafeCommandHandler {

	private static final String PREF_SHOW_USE_GERRIT_TOOLS_INFO = "showUseGerritToolsInfo"; //$NON-NLS-1$
	
	@Override
	protected Object internalExecute(ExecutionEvent event) throws Exception {
		Shell shell = HandlerUtil.getActiveShell(event);
		
		AbstractHandler original;
		String id = event.getCommand().getId();
		if ("org.eclipse.egit.ui.PushHeadToGerrit".equals(id)) { //$NON-NLS-1$
			original = new PushHeadToGerritCommand();
		} else if ("org.eclipse.egit.ui.FetchGerritChange".equals(id)) { //$NON-NLS-1$
			original = new FetchChangeFromGerritCommand();
		} else {
			return null;
		}

		IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(GerritToolsPlugin.PLUGIN_ID);
		if (!prefs.getBoolean(PREF_SHOW_USE_GERRIT_TOOLS_INFO, true)) {
			return original.execute(event);
		}
		
		MessageDialogWithToggle dialog = new MessageDialogWithToggle(
				shell, "Use Gerrit Tools", null, null, MessageDialog.INFORMATION, 
				new String[] { IDialogConstants.YES_LABEL, IDialogConstants.NO_LABEL }, 
				0, "Do not show again", false) {

			protected Control createMessageArea(Composite theParent) {
				super.createMessageArea(theParent);
				Link link = new Link(theParent, SWT.WRAP);

				GridDataFactory
						.fillDefaults()
						.align(SWT.FILL, SWT.BEGINNING)
						.grab(true, false)
						.hint(convertHorizontalDLUsToPixels(IDialogConstants.MINIMUM_MESSAGE_AREA_WIDTH),
								SWT.DEFAULT).applyTo(link);
		        
				link.setText("Gerrit Tools provides an improved experience when pushing and fetching from Gerrit. <a>Learn more about using Gerrit Tools.</a>\n\nContinue to the standard EGit action?");
				
				link.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent e) {
						try {
							PlatformUI.getWorkbench().getBrowserSupport().getExternalBrowser().openURL(
									new URL("https://github.com/Genuitec/gerrit-tools/wiki")); //$NON-NLS-1$
						} catch (Exception e1) {
							throw new RuntimeException(e1);
						}
					}});
				return theParent;
			}
			
		};
		
		try {
			if (dialog.open() == IDialogConstants.YES_ID) {
				return original.execute(event);
			}
		} finally {
			prefs.putBoolean(PREF_SHOW_USE_GERRIT_TOOLS_INFO, !dialog.getToggleState());
		}
		return null;
	}

}
