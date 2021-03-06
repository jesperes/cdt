/*
 * Copyright (c) 2014 QNX Software Systems and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.cdt.internal.qt.core.index;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.cdt.core.model.CoreModel;
import org.eclipse.cdt.core.settings.model.CProjectDescriptionEvent;
import org.eclipse.cdt.core.settings.model.ICDescriptionDelta;
import org.eclipse.cdt.core.settings.model.ICProjectDescriptionListener;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;

/**
 * Represents a management of QMakeProjectInfo instances and manages life-cycle of all QMakeProjectInfo instances.
 */
public class QMakeProjectInfoManager {

	private static final PDListener PD_LISTENER = new PDListener();
	private static final RCListener RC_LISTENER = new RCListener();

	// sync object for CACHE field
	private static final Object CACHE_SYNC = new Object();
	// a list of all QMakeProjectInfo instances
	private static Map<IProject,QMakeProjectInfo> CACHE;

	// called by QtPlugin activator to setup this class
	public static final void start() {
		synchronized (CACHE_SYNC) {
			CACHE = new HashMap<IProject,QMakeProjectInfo>();
		}
		CoreModel.getDefault().addCProjectDescriptionListener(PD_LISTENER, ICDescriptionDelta.ACTIVE_CFG);
		ResourcesPlugin.getWorkspace().addResourceChangeListener(RC_LISTENER, IResourceChangeEvent.POST_CHANGE | IResourceChangeEvent.PRE_CLOSE | IResourceChangeEvent.PRE_DELETE);
	}

	// called by QtPlugin activator to clean up this class
	public static final void stop() {
		ResourcesPlugin.getWorkspace().removeResourceChangeListener(RC_LISTENER);
		CoreModel.getDefault().removeCProjectDescriptionListener(PD_LISTENER);
		List<QMakeProjectInfo> infos;
		synchronized (CACHE_SYNC) {
			infos = new ArrayList<QMakeProjectInfo>(CACHE.values());
			CACHE = null;
		}
		for (QMakeProjectInfo info : infos) {
			if (info != null) {
				info.destroy();
			}
		}
	}

	/**
	 * Returns a QMakeProjectInfo for an active project configuration of a specified project.
	 *
	 * @param project the project
	 * @return the QMakeProjectInfo; or null if the project does not have QtNature
	 */
	public static QMakeProjectInfo getQMakeProjectInfoFor(IProject project) {
		return getQMakeProjectInfoFor(project, true);
	}
	
	private static QMakeProjectInfo getQMakeProjectInfoFor(IProject project, boolean create) {
		QMakeProjectInfo info;
		synchronized (CACHE_SYNC) {
			info = CACHE.get(project);
			if (info != null) {
				return info;
			}
			if (! create) {
				// do not create, just return null
				return null;
			}
			info = new QMakeProjectInfo(project);
			CACHE.put(project, info);
		}
		info.updateState();
		return info;
	}

	// removes the project from the CACHE
	private static void removeProjectFromCache(IResource project) {
		QMakeProjectInfo info;
		synchronized (CACHE_SYNC) {
			info = CACHE.remove(project);
		}
		if (info != null) {
			info.destroy();
		}
	}

	private static final class PDListener implements ICProjectDescriptionListener {

		// called on active project configuration change
		@Override
		public void handleEvent(CProjectDescriptionEvent event) {
			QMakeProjectInfo info = getQMakeProjectInfoFor(event.getProject(), false);
			if (info != null) {
				info.updateState();
			}
		}

	}

	/**
	 * Listens on Eclipse file system changes.
	 */
	private static final class RCListener implements IResourceChangeListener {

		@Override
		public void resourceChanged(IResourceChangeEvent event) {
			RDVisitor visitor = new RDVisitor();

			// collect project to delete and changed files
			switch (event.getType()) {
			case IResourceChangeEvent.PRE_CLOSE:
			case IResourceChangeEvent.PRE_DELETE:
				IResource project = event.getResource();
				if (project != null && project.getType() == IResource.PROJECT) {
					visitor.addProjectToDelete(project);
				}
				break;
			case IResourceChangeEvent.POST_CHANGE:
				IResourceDelta delta = event.getDelta();
				if (delta != null) {
					try {
						delta.accept(visitor);
					} catch (CoreException e) {
						// empty
					}
				}
				break;
			}

			// process collected data
			visitor.process();
		}

	}

	private static final class RDVisitor implements IResourceDeltaVisitor {

		private final Set<IResource> projectsToDelete = new HashSet<IResource>();
		private final Set<IResource> projectsToUpdate = new HashSet<IResource>();
		private final Set<IPath> changedFiles = new HashSet<IPath>();

		@Override
		public boolean visit(IResourceDelta delta) throws CoreException {
			IResource resource = delta.getResource();
			if (resource != null) {
				switch (resource.getType()) {
				case IResource.FILE:
					addChangedFile(resource);
					return false;
				case IResource.PROJECT:
					switch (delta.getKind()) {
					case IResourceDelta.CHANGED:
						if ((delta.getFlags() & IResourceDelta.DESCRIPTION) != 0) {
							addProjectToUpdate(resource);
						}
						return true;
					case IResourceDelta.REMOVED:
						addProjectToDelete(resource);
						return false;
					}
					break;
				}
			}
			return true;
		}

		private void addProjectToUpdate(IResource project) {
			projectsToUpdate.add(project);
		}

		private void addProjectToDelete(IResource project) {
			projectsToDelete.add(project);
		}

		private void addChangedFile(IResource file) {
			IPath fullPath = file.getFullPath();
			if (fullPath != null) {
				changedFiles.add(fullPath);
			}
		}

		public void process() {
			// removing projects from CACHE
			for(IResource project : projectsToDelete) {
				removeProjectFromCache(project);
			}

			List<QMakeProjectInfo> infos;
			synchronized (CACHE_SYNC) {
				infos = new ArrayList<QMakeProjectInfo>(CACHE.values());
			}
			for (QMakeProjectInfo info : infos) {
				// checking if any project description change or any of the changed files affect QMakeProjectInfo
				if (projectsToUpdate.contains(info.getProject()) || info.containsAnySensitiveFile(changedFiles)) {
					// if so then updating
					info.updateState();
				}
			}
		}

	}

}
