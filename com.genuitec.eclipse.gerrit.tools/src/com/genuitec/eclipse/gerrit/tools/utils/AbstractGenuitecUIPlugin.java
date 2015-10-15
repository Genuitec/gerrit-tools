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

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.plugin.AbstractUIPlugin;

public class AbstractGenuitecUIPlugin extends AbstractUIPlugin {

	public static final int DEFAULT_LOG_SEVERITY = IStatus.ERROR;
	
	public Image getImage(String theKey) {	
		return getImageRegistry().get(theKey.toLowerCase());
	}
	
	public ImageDescriptor getImageDescriptor(String theKey) {	
		return getImageRegistry().getDescriptor(theKey.toLowerCase());
	}
	
	/**
	 * This method helps with adding images to bundle image registry.
	 * It registers images under keys equal to their location inside
	 * bundle.
	 * 
	 * @param reg image registry to which add images
	 * @param paths paths to images within current bundle
	 */
	protected void addImagesToRegistry(ImageRegistry reg, String... paths) {
		for (String path: paths) {
			reg.put(path.toLowerCase(), AbstractUIPlugin.imageDescriptorFromPlugin(
					getBundle().getSymbolicName(), path));
		}
	}
	
    public void log(String msg) {
        log(msg,DEFAULT_LOG_SEVERITY);
    }

	public void log(String message, String... params) {
		log(MessageFormat.format(message, (Object[]) params), null, DEFAULT_LOG_SEVERITY);
	}
    
    public void log(String msg, int theIStatusSeverity) {
        log(msg,null,theIStatusSeverity);
    }    

	public void log(Throwable ex) {
		log(ex.getMessage(), ex);
	}
	
    public void log(String msg, Throwable ex) {
        log(msg,ex,DEFAULT_LOG_SEVERITY);
    }
    
	public void log(String message, Throwable t, String... params) {
		log(MessageFormat.format(message, (Object[]) params), t, DEFAULT_LOG_SEVERITY);
	}
    
	/**
	 * Write an informational message to the plugin log file.
	 */
    public void log(String msg, Throwable ex, int theIStatusSeverity) {
        int severity = theIStatusSeverity;
        if (severity < IStatus.OK || severity > IStatus.CANCEL) {
            severity = DEFAULT_LOG_SEVERITY;
        }
        log(new Status(
                severity,
                getBundle().getSymbolicName(),
                msg != null ? msg : "<null>", //$NON-NLS-1$
                ex));
    }
    
    public void log(IStatus theStatus) {
    	getLog().log(theStatus);
    }
	
	
}
