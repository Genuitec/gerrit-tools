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
package com.genuitec.eclipse.egit.tools.dialogs;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.fieldassist.ControlDecoration;
import org.eclipse.jface.fieldassist.FieldDecorationRegistry;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

public abstract class SettingsDialog extends Dialog {

    private String title;
    private Map<String, Object> settings = new HashMap<String, Object>();
    private Map<String, ControlDecoration> decorations = new HashMap<String, ControlDecoration>();

    private static final Image IMG_ERR = FieldDecorationRegistry.getDefault().getFieldDecoration(
    		FieldDecorationRegistry.DEC_ERROR).getImage();
    private static final Image IMG_WARNING = FieldDecorationRegistry.getDefault().getFieldDecoration(
    		FieldDecorationRegistry.DEC_WARNING).getImage();
    private static final Image IMG_INFO = FieldDecorationRegistry.getDefault().getFieldDecoration(
    		FieldDecorationRegistry.DEC_INFORMATION).getImage();
    
    public SettingsDialog(Shell parentShell, String title) {
        super(parentShell);
        setShellStyle(SWT.RESIZE | SWT.MAX | SWT.APPLICATION_MODAL);
        this.title = title;
    }
    
    protected void setSetting(String prop, String name) {
    	settings.put(prop, name);
    }
    
    public Object getSetting(String name) {
        return settings.get(name);
    }

    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText(title);
        shell.layout(true, true);
    }
    
    protected Control createDialogArea(Composite parent) {
        Composite outer = (Composite)super.createDialogArea(parent);
        GridLayout gl = new GridLayout(2, false);
        gl.marginHeight = 10;
        gl.marginWidth = 10;
        outer.setLayout(gl);

        createDialogContents(outer);
        
        return outer;
    }

    protected abstract void createDialogContents(Composite parent);
    
    protected void createButtonsForButtonBar(Composite parent) {
        parent.setLayoutData(new GridData(SWT.RIGHT, SWT.FILL, false, false));
        createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL, true);
        createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
        revalidate();
    }

    public Map<String, Object> getSettings() {
        return settings;
    }

    protected IStatus validate(String property, Object value) {
        return Status.OK_STATUS;
    }
    
    public final void revalidate() {
    	int maxSeverity = IStatus.OK;
    	for (Entry<String, Object> entry: settings.entrySet()) {
    		ControlDecoration dec = decorations.get(entry.getKey());
    		if (dec == null) {
    			continue;
    		}
        	IStatus st = validate(entry.getKey(), entry.getValue());
        	if (st != null) {
        		if (st.getSeverity() > maxSeverity) {
        			maxSeverity = st.getSeverity();
        		}
    			dec.setDescriptionText(st.getMessage());
        		if (st.getSeverity() >= IStatus.ERROR) {
        			dec.setImage(IMG_ERR);
        		} else if (st.getSeverity() >= IStatus.WARNING) {
        			dec.setImage(IMG_WARNING);
        		} else if (st.getSeverity() >= IStatus.INFO) {
        			dec.setImage(IMG_INFO);
        		}
        		if (st.getSeverity() >= IStatus.INFO) {
        			dec.show();
        		} else {
        			dec.hide();
        		}
        	} else {
        		dec.hide();
        	}
    	}
        getButton(IDialogConstants.OK_ID).setEnabled(maxSeverity < IStatus.ERROR);
    }
    
    protected final IStatus createErrorStatus(String message, Object... params) {
    	return new Status(IStatus.ERROR, "id", MessageFormat.format(message, params)); //$NON-NLS-1$
    }
    
    private void createDecoration(Control control, String property) {
    	ControlDecoration decoration = new ControlDecoration(control, SWT.LEFT | SWT.TOP, control.getParent());
    	((GridData)control.getLayoutData()).horizontalIndent = 8;
    	decorations.put(property, decoration);
    }
    
    protected Button createOptionCheckBox(final Composite parent, final String caption,
                    final String property, boolean defaultValue) {
        final Button btn = new Button(parent, SWT.CHECK + SWT.WRAP);
        GridData gd = new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1);
        gd.widthHint = 350;
        settings.put(property, defaultValue);
        btn.setLayoutData(gd);
        btn.setText(caption);
        btn.setSelection(defaultValue);
        btn.addSelectionListener(new SelectionListener() {
            
            public void widgetSelected(SelectionEvent e) {
                settings.put(property, btn.getSelection());
                revalidate();
            }
            
            public void widgetDefaultSelected(SelectionEvent e) { }
        });
        createDecoration(btn, property);
        return btn;
    }
    protected Text createOptionTextEditor(final Composite parent, final String caption,
            final String property, String defaultValue, boolean multiline) {
    	return createOptionTextEditor(parent, caption, property, defaultValue, multiline ? SWT.MULTI : 0);
    }
    
    protected Text createOptionTextEditor(final Composite parent, final String caption,
                    final String property, String defaultValue, int style) {
    	boolean multiline = (style & SWT.MULTI) != 0; 
        Label l = new Label(parent, SWT.NONE);
        l.setText(caption);
        l.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
        
        final Text text = new Text(parent, SWT.BORDER | style);
        GridData gd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        gd.widthHint = 350;
        if (multiline) {
            gd.heightHint = 100;
        }
        settings.put(property, defaultValue);
        text.setLayoutData(gd);
        text.setText(defaultValue);
        text.addModifyListener(new ModifyListener() {
            public void modifyText(ModifyEvent e) {
                settings.put(property, text.getText());
                revalidate();
            }
        });
        createDecoration(text, property);
        return text;
    }
    
    protected Combo createOptionComboEditor(final Composite parent, final String caption,
                    final String property, String defaultValue, String[] items, boolean readOnly) {
        Label l = new Label(parent, SWT.NONE);
        l.setText(caption);
        l.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
        
        final Combo combo = new Combo(parent, readOnly ? SWT.READ_ONLY : 0);
        GridData gd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        gd.widthHint = 350;
        combo.setLayoutData(gd);
        combo.setItems(items);
        combo.setText(defaultValue);
        settings.put(property, combo.getText());
        combo.addModifyListener(new ModifyListener() {
            public void modifyText(ModifyEvent e) {
                settings.put(property, combo.getText());
                revalidate();
            }
        });
        createDecoration(combo, property);
        return combo;
    }
}
