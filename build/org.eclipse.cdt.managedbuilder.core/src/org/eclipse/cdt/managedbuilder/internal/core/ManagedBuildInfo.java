package org.eclipse.cdt.managedbuilder.internal.core;

/**********************************************************************
 * Copyright (c) 2002,2003 Rational Software Corporation and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Common Public License v0.5
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v05.html
 * 
 * Contributors: 
 * IBM Rational Software - Initial API and implementation
 * **********************************************************************/

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.eclipse.cdt.managedbuilder.core.BuildException;
import org.eclipse.cdt.managedbuilder.core.IConfiguration;
import org.eclipse.cdt.managedbuilder.core.IManagedBuildInfo;
import org.eclipse.cdt.managedbuilder.core.IOption;
import org.eclipse.cdt.managedbuilder.core.ITarget;
import org.eclipse.cdt.managedbuilder.core.ITool;
import org.eclipse.cdt.core.CCProjectNature;
import org.eclipse.cdt.core.CProjectNature;
import org.eclipse.cdt.core.parser.IScannerInfo;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class ManagedBuildInfo implements IManagedBuildInfo, IScannerInfo {
	
	// Local variables
	private boolean isDirty;
	private IResource owner;
	private Map targetMap;
	private List targets;
	private Map defaultConfigurations;
	private ITarget defaultTarget;
	
	public ManagedBuildInfo(IResource owner) {
		targetMap = new HashMap();
		targets = new ArrayList();
		defaultConfigurations = new HashMap();
		this.owner = owner;
	}
	
	public ManagedBuildInfo(IResource owner, Element element) {
		this(owner);
		
		// The id of the default configuration
		String defaultTargetId = null;
		List configIds = new ArrayList();
		Node child = element.getFirstChild();
		while (child != null) {
			if (child.getNodeName().equals("target")) {
				new Target(this, (Element)child);
			} else if (child.getNodeName().equals("defaultConfig")) {
				// We may not have read the config in yet, so just cache it
				configIds.add(((Element)child).getAttribute("id"));
			} else if (child.getNodeName().equals("defaultTarget")) {
				defaultTargetId = ((Element)child).getAttribute("id");
			}
			child = child.getNextSibling();
		}
		// All the available targets have been read in
		defaultTarget = (ITarget) targetMap.get(defaultTargetId);
		// Now we have a misserable O(N^2) operation (oh well, the data sets are small)
		ListIterator stringIter = configIds.listIterator();
		while (stringIter.hasNext()){
			String confId = (String) stringIter.next();
			ListIterator targIter = targets.listIterator();
			while (targIter.hasNext()) {
				Target targ = (Target) targIter.next();
				IConfiguration conf = targ.getConfiguration(confId);
				if (conf != null) {
					defaultConfigurations.put(targ.getId(), conf);
					break;
				}				
			}
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.cdt.core.build.managed.IManagedBuildInfo#addTarget(org.eclipse.cdt.core.build.managed.ITarget)
	 */
	public void addTarget(ITarget target) {
		targetMap.put(target.getId(), target);
		targets.add(target);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.cdt.core.build.managed.IManagedBuildInfo#buildsFileType(java.lang.String)
	 */
	public boolean buildsFileType(String srcExt) {
		// Make sure the owner is treated as a project for the duration
		IProject project = (IProject)owner;

		// Check to see if there is a rule to build a file with this extension
		IConfiguration config = getDefaultConfiguration(getDefaultTarget());
		ITool[] tools = config.getTools();
		for (int index = 0; index < tools.length; index++) {
			ITool tool = tools[index];
			try {
				// Make sure the tool is right for the project
				switch (tool.getNatureFilter()) {
					case ITool.FILTER_C:
						if (project.hasNature(CProjectNature.C_NATURE_ID) && !project.hasNature(CCProjectNature.CC_NATURE_ID)) {
							return tool.buildsFileType(srcExt);
						}
						break;
					case ITool.FILTER_CC:
						if (project.hasNature(CCProjectNature.CC_NATURE_ID)) {
							return tool.buildsFileType(srcExt);
						}
						break;
					case ITool.FILTER_BOTH:
						return tool.buildsFileType(srcExt);
				}
			} catch (CoreException e) {
				continue;
			}
		}
		return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.cdt.core.build.managed.IManagedBuildInfo#getBuildArtifactName()
	 */
	public String getBuildArtifactName() {
		// Get the default target and use its value
		String name = getDefaultTarget().getArtifactName();
		return name == null ? new String() : name;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.cdt.core.build.managed.IManagedBuildInfo#getCleanCommand()
	 */
	public String getCleanCommand() {
		// Get from the model
		String command = new String();
		ITarget target = getDefaultTarget();
		command = target.getCleanCommand();
		return command;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.cdt.core.build.managed.IManagedBuildInfo#getConfigurationName()
	 */
	public String getConfigurationName() {
		// Return the human-readable name of the default configuration
		IConfiguration config = getDefaultConfiguration(getDefaultTarget());
		return config == null ? new String() : config.getName();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.cdt.core.build.managed.IManagedBuildInfo#getConfigurationNames()
	 */
	public String[] getConfigurationNames() {
		ArrayList configNames = new ArrayList();
		IConfiguration[] configs = getDefaultTarget().getConfigurations();
		for (int i = 0; i < configs.length; i++) {
			IConfiguration configuration = configs[i];
			configNames.add(configuration.getName());
		}
		configNames.trimToSize();
		return (String[])configNames.toArray(new String[configNames.size()]);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.cdt.core.build.managed.IManagedBuildInfo#getDefaultConfiguration()
	 */
	public IConfiguration getDefaultConfiguration(ITarget target) {
		// Get the default config associated with the defalt target
		IConfiguration config = (IConfiguration) defaultConfigurations.get(target.getId());

		// If null, look up the first configuration associated with the target
		if (config == null) {
			IConfiguration[] configs = getDefaultTarget().getConfigurations();
			if (configs.length > 0) {
				config = configs[0];
			}
		}
		return config;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.cdt.core.build.managed.IManagedBuildInfo#getDefaultTarget()
	 */
	public ITarget getDefaultTarget() {
		if (defaultTarget == null) {
			defaultTarget = (ITarget) targets.get(0);
		}
		return defaultTarget;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.cdt.core.build.managed.IScannerInfo#getDefinedSymbols()
	 */
	public Map getDefinedSymbols() {
		IProject project = (IProject)owner;
		// Return the defined symbols for the default configuration
		HashMap symbols = new HashMap();
		IConfiguration config = getDefaultConfiguration(getDefaultTarget());
		ITool[] tools = config.getTools();
		for (int i = 0; i < tools.length; i++) {
			ITool tool = tools[i];
			try {
				// Make sure the tool is right for the project
				switch (tool.getNatureFilter()) {
					case ITool.FILTER_C:
						if (!project.hasNature(CProjectNature.C_NATURE_ID) || project.hasNature(CCProjectNature.CC_NATURE_ID)) {
							continue;
						}
						break;
					case ITool.FILTER_CC:
						if (!project.hasNature(CCProjectNature.CC_NATURE_ID)) {
							continue;
						}
						break;
					case ITool.FILTER_BOTH:
						break;
				}
			} catch (CoreException e) {
				continue;
			}
			// Now extract the valid tool's options
			IOption[] opts = tool.getOptions();
			for (int j = 0; j < opts.length; j++) {
				IOption option = opts[j];
				if (option.getValueType() == IOption.PREPROCESSOR_SYMBOLS) {
					try {
						ArrayList symbolList = new ArrayList();
						symbolList.addAll(Arrays.asList(option.getBuiltIns()));
						symbolList.addAll(Arrays.asList(option.getDefinedSymbols()));
						Iterator iter = symbolList.listIterator();
						while (iter.hasNext()) {
							String symbol = (String) iter.next();
							if (symbol.length() == 0){
								continue;
							}
							String key = new String();
							String value = new String();
							int index = symbol.indexOf("=");
							if (index != -1) {
								key = symbol.substring(0, index).trim();
								value = symbol.substring(index + 1).trim();
							} else {
								key = symbol.trim();
							}
							symbols.put(key, value);
						}

					} catch (BuildException e) {
						// we should never get here
						continue;
					}
				}
			}
		}
		return symbols; 
	}

	/* (non-Javadoc)
	 * @see org.eclipse.cdt.core.build.managed.IManagedBuildInfo#getFlagsForSource(java.lang.String)
	 */
	public String getFlagsForSource(String extension) {
		IProject project = (IProject)owner;

		// Get all the tools for the current config
		IConfiguration config = getDefaultConfiguration(getDefaultTarget());
		ITool[] tools = config.getTools();
		for (int index = 0; index < tools.length; index++) {
			ITool tool = tools[index];
			if (tool.buildsFileType(extension)) {
				try {
					// Make sure the tool is right for the project
					switch (tool.getNatureFilter()) {
						case ITool.FILTER_C:
							if (project.hasNature(CProjectNature.C_NATURE_ID) && !project.hasNature(CCProjectNature.CC_NATURE_ID)) {
								return tool.getToolFlags();
							}
							break;
						case ITool.FILTER_CC:
							if (project.hasNature(CCProjectNature.CC_NATURE_ID)) {
								return tool.getToolFlags();
							}
							break;
						case ITool.FILTER_BOTH:
							return tool.getToolFlags();
					}
				} catch (CoreException e) {
					continue;
				} catch (BuildException e) {
					// Give it your best shot with the next tool
					continue;
				}
			}
		}
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.cdt.core.build.managed.IManagedBuildInfo#getToolFlags(java.lang.String)
	 */
	public String getFlagsForTarget(String extension) {
		IProject project = (IProject)owner;
		// Treat null extensions as an empty string
		String ext = extension == null ? new String() : extension;
		
		// Get all the tools for the current config
		IConfiguration config = getDefaultConfiguration(getDefaultTarget());
		ITool[] tools = config.getTools();
		for (int index = 0; index < tools.length; index++) {
			ITool tool = tools[index];
			if (tool.producesFileType(ext)) {
				try {
					// Make sure the tool is right for the project
					switch (tool.getNatureFilter()) {
						case ITool.FILTER_C:
							if (project.hasNature(CProjectNature.C_NATURE_ID) && !project.hasNature(CCProjectNature.CC_NATURE_ID)) {
								return tool.getToolFlags();
							}
							break;
						case ITool.FILTER_CC:
							if (project.hasNature(CCProjectNature.CC_NATURE_ID)) {
								return tool.getToolFlags();
							}
							break;
						case ITool.FILTER_BOTH:
							return tool.getToolFlags();
					}
				} catch (CoreException e) {
					continue;
				} catch (BuildException e) {
					// Give it your best shot with the next tool
					continue;
				}
			}
		}
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.cdt.core.build.managed.IScannerInfo#getIncludePaths()
	 */
	public String[] getIncludePaths() {
		IProject project = (IProject)owner;
		
		// Return the include paths for the default configuration
		ArrayList paths = new ArrayList();
		IConfiguration config = getDefaultConfiguration(getDefaultTarget());
		IPath root = owner.getLocation().addTrailingSeparator().append(config.getName());
		ITool[] tools = config.getTools();
		for (int i = 0; i < tools.length; i++) {
			ITool tool = tools[i];
			try {
				// Make sure the tool is right for the project
				switch (tool.getNatureFilter()) {
					case ITool.FILTER_C:
						if (!project.hasNature(CProjectNature.C_NATURE_ID) || project.hasNature(CCProjectNature.CC_NATURE_ID)) {
							continue;
						}
						break;
					case ITool.FILTER_CC:
						if (!project.hasNature(CCProjectNature.CC_NATURE_ID)) {
							continue;
						}
						break;
					case ITool.FILTER_BOTH:
						break;
				}
			} catch (CoreException e) {
				continue;
			}
			// The tool checks out for this project, get its options
			IOption[] opts = tool.getOptions();
			for (int j = 0; j < opts.length; j++) {
				IOption option = opts[j];
				if (option.getValueType() == IOption.INCLUDE_PATH) {
					try {
						// Get all the built-in paths from the option
						paths.addAll(Arrays.asList(option.getBuiltIns()));
						// Get all the user-defined paths from the option as absolute paths
						String[] userPaths = option.getIncludePaths();
						for (int index = 0; index < userPaths.length; ++index) {
							IPath userPath = new Path(userPaths[index]);
							if (userPath.isAbsolute()) {
								paths.add(userPath.toOSString());
							} else {
								IPath absPath = root.addTrailingSeparator().append(userPath);
								paths.add(absPath.makeAbsolute().toOSString());
							}
 						}
					} catch (BuildException e) {
						// we should never get here, but continue anyway
						continue;
					}
				}
			}
		}
		paths.trimToSize();
		return (String[])paths.toArray(new String[paths.size()]); 
	}

	/* (non-Javadoc)
	 * @see org.eclipse.cdt.core.build.managed.IManagedBuildInfo#getLibsForTarget(java.lang.String)
	 */
	public String[] getLibsForTarget(String extension) {
		IProject project = (IProject)owner;
		
		ArrayList libs = new ArrayList();
		// Get all the tools for the current config
		IConfiguration config = getDefaultConfiguration(getDefaultTarget());
		ITool[] tools = config.getTools();
		for (int index = 0; index < tools.length; index++) {
			ITool tool = tools[index];
			try {
				// Make sure the tool is right for the project
				switch (tool.getNatureFilter()) {
					case ITool.FILTER_C:
						if (!project.hasNature(CProjectNature.C_NATURE_ID) || project.hasNature(CCProjectNature.CC_NATURE_ID)) {
							continue;
						}
						break;
					case ITool.FILTER_CC:
						if (!project.hasNature(CCProjectNature.CC_NATURE_ID)) {
							continue;
						}
						break;
					case ITool.FILTER_BOTH:
						break;
				}
			} catch (CoreException e) {
				continue;
			}
			// The tool is OK for this project nature
			if (tool.producesFileType(extension)) {
				IOption[] opts = tool.getOptions();
				// Look for the lib option type
				for (int i = 0; i < opts.length; i++) {
					IOption option = opts[i];
					if (option.getValueType() == IOption.LIBRARIES) {
						try {
							String command = option.getCommand();
							String[] allLibs = option.getLibraries();
							for (int j = 0; j < allLibs.length; j++) {
								String string = allLibs[j];
								libs.add(command + string);
							}
						} catch (BuildException e) {
							continue;
						}
					}
				}
			}
		}
		libs.trimToSize();
		return (String[])libs.toArray(new String[libs.size()]);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.cdt.core.build.managed.IManagedBuildInfo#getMakeArguments()
	 */
	public String getMakeArguments() {
		String arguments = new String();		
		
		// The make command may or may not have any flags
		ITarget target = getDefaultTarget();
		String command = target.getMakeCommand();
		
		// If it does, the flags will be everything between the '-' and the next space
		int indexOfArgs = command.indexOf('-');
		if (indexOfArgs != - 1) {
			try {
				String argsAndTargs = command.substring(indexOfArgs);
				int indexOfTargs = argsAndTargs.indexOf(' ');
				arguments = (indexOfTargs != -1) ? 
							argsAndTargs.substring(0, indexOfTargs) : 
							argsAndTargs;
				// Make sure the arg list does not contain f or C
				
			} catch (IndexOutOfBoundsException e) {
			}
		}
		
		return arguments.trim();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.cdt.core.build.managed.IManagedBuildInfo#getMakeCommand()
	 */
	public String getMakeCommand() {
		String command = new String();
		ITarget target = getDefaultTarget();
		command = target.getMakeCommand();
		
		// There may actually be arguments, so just get everything up to the first '-'
		int indexOfArgs = command.indexOf('-');
		if (indexOfArgs != -1) {
			// Return ecverything up to the first argument as the command
			return command.substring(0, indexOfArgs).trim();
		} else {
			return command.trim();
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.cdt.core.build.managed.IManagedBuildInfo#getOutputExtension(java.lang.String)
	 */
	public String getOutputExtension(String resourceExtension) {
		IProject project = (IProject)owner;
		// Get all the tools for the current config
		IConfiguration config = getDefaultConfiguration(getDefaultTarget());
		ITool[] tools = config.getTools();
		for (int index = 0; index < tools.length; index++) {
			ITool tool = tools[index];
			try {
				// Make sure the tool is right for the project
				switch (tool.getNatureFilter()) {
					case ITool.FILTER_C:
						if (project.hasNature(CProjectNature.C_NATURE_ID) && !project.hasNature(CCProjectNature.CC_NATURE_ID)) {
							return tool.getOutputExtension(resourceExtension);
						}
						break;
					case ITool.FILTER_CC:
						if (project.hasNature(CCProjectNature.CC_NATURE_ID)) {
							return tool.getOutputExtension(resourceExtension);
						}
						break;
					case ITool.FILTER_BOTH:
						return tool.getOutputExtension(resourceExtension);
				}
			} catch (CoreException e) {
				continue;
			}
		}
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.cdt.core.build.managed.IManagedBuildInfo#getOutputFlag()
	 */
	public String getOutputFlag(String outputExt) {
		IProject project = (IProject)owner;
		// Treat null extension as an empty string
		String ext = outputExt == null ? new String() : outputExt;
		
		// Get all the tools for the current config
		String flags = new String();
		IConfiguration config = getDefaultConfiguration(getDefaultTarget());
		ITool[] tools = config.getTools();
		for (int index = 0; index < tools.length; index++) {
			ITool tool = tools[index];
			try {
				// Make sure the tool is right for the project
				switch (tool.getNatureFilter()) {
					case ITool.FILTER_C:
						if (!project.hasNature(CProjectNature.C_NATURE_ID) || project.hasNature(CCProjectNature.CC_NATURE_ID)) {
							continue;
						}
						break;
					case ITool.FILTER_CC:
						if (!project.hasNature(CCProjectNature.CC_NATURE_ID)) {
							continue;
						}
						break;
					case ITool.FILTER_BOTH:
						break;
				}
			} catch (CoreException e) {
				continue;
			}
			// It's OK
			if (tool.producesFileType(ext)) {
				flags = tool.getOutputFlag();
			}
		}
		return flags;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.cdt.core.build.managed.IManagedBuildInfo#getOutputPrefix(java.lang.String)
	 */
	public String getOutputPrefix(String outputExtension) {
		IProject project = (IProject)owner;
		// Treat null extensions as empty string
		String ext = outputExtension == null ? new String() : outputExtension;
		
		// Get all the tools for the current config
		String flags = new String();
		IConfiguration config = getDefaultConfiguration(getDefaultTarget());
		ITool[] tools = config.getTools();
		for (int index = 0; index < tools.length; index++) {
			ITool tool = tools[index];
			try {
				// Make sure the tool is right for the project
				switch (tool.getNatureFilter()) {
					case ITool.FILTER_C:
						if (!project.hasNature(CProjectNature.C_NATURE_ID) || project.hasNature(CCProjectNature.CC_NATURE_ID)) {
							continue;
						}
						break;
					case ITool.FILTER_CC:
						if (!project.hasNature(CCProjectNature.CC_NATURE_ID)) {
							continue;
						}
						break;
					case ITool.FILTER_BOTH:
						break;
				}
			} catch (CoreException e) {
				continue;
			}
			if (tool.producesFileType(ext)) {
				flags = tool.getOutputPrefix();
			}
		}
		return flags;
	}

	public IResource getOwner() {
		return owner;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.cdt.core.build.managed.IManagedBuildInfo#getTarget(org.eclipse.cdt.core.build.managed.IConfiguration)
	 */
	public ITarget getTarget(String id) {
		return (ITarget) targetMap.get(id);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.cdt.core.build.managed.IManagedBuildInfo#getTargets(org.eclipse.cdt.core.build.managed.IConfiguration)
	 */
	public List getTargets() {
		return targets;	
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.cdt.core.build.managed.IManagedBuildInfo#getToolForSource(java.lang.String)
	 */
	public String getToolForSource(String extension) {
		IProject project = (IProject)owner;

		// Get all the tools for the current config
		IConfiguration config = getDefaultConfiguration(getDefaultTarget());
		ITool[] tools = config.getTools();
		for (int index = 0; index < tools.length; index++) {
			ITool tool = tools[index];
			if (tool.buildsFileType(extension)) {
				try {
					// Make sure the tool is right for the project
					switch (tool.getNatureFilter()) {
						case ITool.FILTER_C:
							if (project.hasNature(CProjectNature.C_NATURE_ID) && !project.hasNature(CCProjectNature.CC_NATURE_ID)) {
								return tool.getToolCommand();
							}
							break;
						case ITool.FILTER_CC:
							if (project.hasNature(CCProjectNature.CC_NATURE_ID)) {
								return tool.getToolCommand();
							}
							break;
						case ITool.FILTER_BOTH:
							return tool.getToolCommand();
					}
				} catch (CoreException e) {
					continue;
				}
			}
		}
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.cdt.core.build.managed.IManagedBuildInfo#getToolInvocation(java.lang.String)
	 */
	public String getToolForTarget(String extension) {
		IProject project = (IProject)owner;

		// Treat a null argument as an empty string
		String ext = extension == null ? new String() : extension;
		// Get all the tools for the current config
		IConfiguration config = getDefaultConfiguration(getDefaultTarget());
		ITool[] tools = config.getTools();
		for (int index = 0; index < tools.length; index++) {
			ITool tool = tools[index];
			if (tool.producesFileType(ext)) {
				try {
					// Make sure the tool is right for the project
					switch (tool.getNatureFilter()) {
						case ITool.FILTER_C:
							if (project.hasNature(CProjectNature.C_NATURE_ID) && !project.hasNature(CCProjectNature.CC_NATURE_ID)) {
								return tool.getToolCommand();
							}
							break;
						case ITool.FILTER_CC:
							if (project.hasNature(CCProjectNature.CC_NATURE_ID)) {
								return tool.getToolCommand();
							}
							break;
						case ITool.FILTER_BOTH:
							return tool.getToolCommand();
					}
				} catch (CoreException e) {
					continue;
				}
			}
		}
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.cdt.managedbuilder.core.IManagedBuildInfo#getUserObjectsForTarget(java.lang.String)
	 */
	public String[] getUserObjectsForTarget(String extension) {
		IProject project = (IProject)owner;
		ArrayList objs = new ArrayList();
		// Get all the tools for the current config
		IConfiguration config = getDefaultConfiguration(getDefaultTarget());
		ITool[] tools = config.getTools();
		for (int index = 0; index < tools.length; index++) {
			ITool tool = tools[index];
			try {
				// Make sure the tool is right for the project
				switch (tool.getNatureFilter()) {
					case ITool.FILTER_C:
						if (!project.hasNature(CProjectNature.C_NATURE_ID) || project.hasNature(CCProjectNature.CC_NATURE_ID)) {
							continue;
						}
						break;
					case ITool.FILTER_CC:
						if (!project.hasNature(CCProjectNature.CC_NATURE_ID)) {
							continue;
						}
						break;
					case ITool.FILTER_BOTH:
						break;
				}
			} catch (CoreException e) {
				continue;
			}
			// The tool is OK for this project nature
			if (tool.producesFileType(extension)) {
				IOption[] opts = tool.getOptions();
				// Look for the user object option type
				for (int i = 0; i < opts.length; i++) {
					IOption option = opts[i];
					if (option.getValueType() == IOption.OBJECTS) {
						try {
							objs.addAll(Arrays.asList(option.getUserObjects()));
						} catch (BuildException e) {
							continue;
						}
					}
				}
			}
		}
		objs.trimToSize();
		return (String[])objs.toArray(new String[objs.size()]);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.cdt.managedbuilder.core.IManagedBuildInfo#isDirty()
	 */
	public boolean isDirty() {
		return isDirty;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.cdt.managedbuilder.core.IManagedBuildInfo#isHeaderFile(java.lang.String)
	 */
	public boolean isHeaderFile(String ext) {
		IProject project = (IProject)owner;

		// Check to see if there is a rule to build a file with this extension
		IConfiguration config = getDefaultConfiguration(getDefaultTarget());
		ITool[] tools = config.getTools();
		for (int index = 0; index < tools.length; index++) {
			ITool tool = tools[index];
			try {
				// Make sure the tool is right for the project
				switch (tool.getNatureFilter()) {
					case ITool.FILTER_C:
						if (project.hasNature(CProjectNature.C_NATURE_ID) && !project.hasNature(CCProjectNature.CC_NATURE_ID)) {
							return tool.isHeaderFile(ext);
						}
						break;
					case ITool.FILTER_CC:
						if (project.hasNature(CCProjectNature.CC_NATURE_ID)) {
							return tool.isHeaderFile(ext);
						}
						break;
					case ITool.FILTER_BOTH:
						return tool.isHeaderFile(ext);
				}
			} catch (CoreException e) {
				continue;
			}
		}
		return false;
	}

	/**
	 * Write the contents of the build model to the persistent store specified in the 
	 * argument.
	 * 
	 * @param doc
	 * @param element
	 */
	public void serialize(Document doc, Element element) {
		// Write out each target and their default config
		for (int i = 0; i < targets.size(); ++i) {
			Element targetElement = doc.createElement("target");
			element.appendChild(targetElement);
			((Target)targets.get(i)).serialize(doc, targetElement);
			IConfiguration config = getDefaultConfiguration((ITarget)targets.get(i));
			if (config != null) {
				Element configEl = doc.createElement("defaultConfig");
				element.appendChild(configEl);
				configEl.setAttribute("id", config.getId());
			}
		}
		// Persist the default target
		if (getDefaultTarget() != null){
			Element targEl = doc.createElement("defaultTarget");
			element.appendChild(targEl);
			targEl.setAttribute("id", getDefaultTarget().getId());
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.cdt.core.build.managed.IManagedBuildInfo#setDefaultConfiguration(org.eclipse.cdt.core.build.managed.IConfiguration)
	 */
	public void setDefaultConfiguration(IConfiguration configuration) {
		// Get the target associated with the argument
		ITarget target = configuration.getTarget();
		// Make sure it is the default
		setDefaultTarget(target);
		defaultConfigurations.put(target.getId(), configuration);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.cdt.core.build.managed.IManagedBuildInfo#setDefaultTarget(org.eclipse.cdt.core.build.managed.ITarget)
	 */
	public void setDefaultTarget(ITarget target) {
		if (defaultTarget != null && defaultTarget.getId().equals(target.getId())) {
			return;
		}
		defaultTarget = target;		
	}

	/* (non-Javadoc)
	 * @see org.eclipse.cdt.managedbuilder.core.IManagedBuildInfo#setDirty(boolean)
	 */
	public void setDirty(boolean isDirty) {
		this.isDirty = isDirty;
	}

	/**
	 * Sets the owner of the receiver to be the <code>IResource</code> specified
	 * in the argument.
	 * 
	 * @param resource
	 */
	public void updateOwner(IResource resource) {
		// Check to see if the owner is the same as the argument
		if (resource != null) {
			if (!owner.equals(resource)) {
				owner = resource;
				// Do the same for the targets
				Iterator iter = targets.listIterator();
				while(iter.hasNext()) {
					ITarget target = (ITarget) iter.next();
					target.updateOwner(resource);
				}
			}
		}
	}

}
