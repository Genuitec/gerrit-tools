/*
 * Copyright 2015, Genuitec, LLC
 * All Rights Reserved.
 */
package com.genuitec.eclipse.gerrit.tools.internal.utils.commands;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.OperationCanceledException;

import com.genuitec.eclipse.gerrit.tools.utils.RepositoryUtils;

public abstract class SafeCommandHandler extends AbstractHandler {

	@Override
	public final Object execute(ExecutionEvent event) throws ExecutionException {
		try {
			return internalExecute(event);
		} catch (ExecutionException ex) {
			throw ex;
		} catch (OperationCanceledException ex) {
			return null;
		} catch (Throwable t) {
			RepositoryUtils.handleException(t);
			return null;
		}
	}
	
	protected abstract Object internalExecute(ExecutionEvent event) throws Exception;

}
