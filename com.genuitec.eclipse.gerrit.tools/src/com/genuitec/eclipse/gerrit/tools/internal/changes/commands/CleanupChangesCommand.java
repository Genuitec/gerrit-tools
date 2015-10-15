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
package com.genuitec.eclipse.gerrit.tools.internal.changes.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.expressions.EvaluationResult;
import org.eclipse.core.expressions.Expression;
import org.eclipse.core.expressions.ExpressionInfo;
import org.eclipse.core.expressions.IEvaluationContext;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.ISources;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.internal.AbstractEvaluationHandler;
import org.eclipse.ui.internal.InternalHandlerUtil;

import com.genuitec.eclipse.gerrit.tools.internal.changes.dialogs.CleanupChangesDialog;
import com.genuitec.eclipse.gerrit.tools.internal.fbranches.BranchingUtils;
import com.genuitec.eclipse.gerrit.tools.utils.RepositoryUtils;

@SuppressWarnings("restriction")
public class CleanupChangesCommand extends AbstractEvaluationHandler {

	private Expression enabledWhen;
	
	public CleanupChangesCommand() {
		registerEnablement();
	}
	
	public Object execute(ExecutionEvent event) throws ExecutionException {
		Shell shell = HandlerUtil.getActiveShell(event);
		Repository repo = RepositoryUtils.getRepository(HandlerUtil.getCurrentSelection(event));
		if (repo == null) {
			return null;
		}
		
		CleanupChangesDialog dialog = new CleanupChangesDialog(shell);
		List<String> changesToRemove = dialog.select(repo);
		
		if (changesToRemove == null || changesToRemove.isEmpty()) {
			return null;
		}
		
		//perform branch operation
		List<Ref> toDelete = new ArrayList<Ref>();
		for (String change: changesToRemove) {
			Ref ref;
			try {
				ref = repo.getRef("refs/heads/changes/" + change); //$NON-NLS-1$
				if (ref == null) {
					throw new RuntimeException("Cannot acquire reference for change " +change);
				}
			} catch (Exception e) {
				RepositoryUtils.handleException(e);
				return null;
			}
			toDelete.add(ref);
		}
		BranchingUtils.deleteBranches(shell, repo, toDelete);
		
		return null;
	}
	
	@Override
	public void setEnabled(Object evaluationContext) {
		if (evaluationContext instanceof IEvaluationContext) {
			try {
				setEnabled(getEnabledWhenExpression().evaluate(
						(IEvaluationContext)evaluationContext)==EvaluationResult.TRUE);
			} catch (CoreException e) {
				//ignore
			}
		}
	}

	@Override
	protected Expression getEnabledWhenExpression() {
		if (enabledWhen == null) {
			enabledWhen = new Expression() {
				public EvaluationResult evaluate(IEvaluationContext context)
						throws CoreException {
					ISelection selection = InternalHandlerUtil
							.getCurrentSelection(context);
					Repository repo = RepositoryUtils.getRepository(selection);
					String curBranch = null;
					if (repo != null) {
						try {
							curBranch = repo.getFullBranch();
							if (curBranch.startsWith("refs/heads/changes/")) {
								curBranch = curBranch.substring("refs/heads/changes/".length());
							}
						} catch (IOException e) {
							//ignore
						}
						try {
							if (repo != null) {
								for (String branch: repo.getRefDatabase().getRefs("refs/heads/changes/").keySet()) {
									if (!branch.equals(curBranch) && branch.indexOf('/') > 0) {
										return EvaluationResult.TRUE;
									}
								}
							}
						} catch (IOException ex) {
							//ignore
						}
					}
					return EvaluationResult.FALSE;
				}

				/*
				 * (non-Javadoc)
				 * 
				 * @see org.eclipse.core.expressions.Expression#collectExpressionInfo(org.eclipse.core.expressions.ExpressionInfo)
				 */
				public void collectExpressionInfo(ExpressionInfo info) {
					info.addVariableNameAccess(ISources.ACTIVE_CURRENT_SELECTION_NAME);
				}
			};
		}
		return enabledWhen;
	}
    
}
