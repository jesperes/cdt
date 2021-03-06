/*******************************************************************************
 * Copyright (c) 2012 Google, Inc and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Alex Ruiz (Google) - initial API and implementation
 *******************************************************************************/
package org.eclipse.cdt.codan.internal.core;

import org.eclipse.cdt.codan.core.model.CheckerLaunchMode;
import org.eclipse.cdt.codan.core.model.IChecker;
import org.eclipse.core.resources.IResource;

/**
 * Verifies that an <code>{@link IChecker}</code> can be invoked.
 */
public interface ICheckerEnablementVerifier {
	/**
	 * Indicates whether the given code checker can be invoked on the given resource in the given
	 * launch mode.
	 * @param checker the given code checker.
	 * @param resource the resource to be checked.
	 * @param mode the current launch mode.
	 * @return {@code true} if the given code checker can be invoked, {@code false} otherwise.
	 */
	public boolean isCheckerEnabled(IChecker checker, IResource resource, CheckerLaunchMode mode);
}
