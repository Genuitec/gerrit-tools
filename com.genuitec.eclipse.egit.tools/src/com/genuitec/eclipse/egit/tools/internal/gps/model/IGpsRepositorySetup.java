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
package com.genuitec.eclipse.egit.tools.internal.gps.model;

public interface IGpsRepositorySetup {

	String getName();
	
	State getState();
	
	String getUserName();
	
	void setUserName(String name);
	
	public enum State {
		
		PRESENT,
		LOCATED,
		CLONE,
		NOT_FOUND
		
	}
	
}
