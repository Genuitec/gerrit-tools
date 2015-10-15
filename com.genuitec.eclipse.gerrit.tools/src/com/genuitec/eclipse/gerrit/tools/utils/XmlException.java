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

public class XmlException extends Exception {
	
	private static final long serialVersionUID = 1L;

	public XmlException(String message, Exception e, Object... params) {
		super(MessageFormat.format(message, params), e);
	}
	
	public XmlException(String message, Exception e) {
		super(message, e);
	}

	public XmlException(String message, Object... params) {
		super(MessageFormat.format(message, params));
	}
	
	public XmlException(String message) {
		super(message);
	}

}
