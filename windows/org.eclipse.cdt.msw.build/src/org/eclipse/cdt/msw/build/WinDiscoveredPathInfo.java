package org.eclipse.cdt.msw.build;


import java.util.HashMap;
import java.util.Map;

import org.eclipse.cdt.make.core.scannerconfig.IDiscoveredPathManager.IDiscoveredPathInfo;
import org.eclipse.cdt.make.core.scannerconfig.IDiscoveredPathManager.IDiscoveredScannerInfoSerializable;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IPath;

/**
 * @author Doug Schaefer
 *
 */
public class WinDiscoveredPathInfo implements IDiscoveredPathInfo {

	private final IPath[] paths;
	private final Map<String, String> symbols = new HashMap<String, String>();
	
	public WinDiscoveredPathInfo() {
		// Include paths
		paths = WinEnvironmentVariableSupplier.getIncludePath();

		// We may want to add some more symbols here, but these are the
		// important ones.
		symbols.putAll(WinEnvironmentVariableSupplier
				.getPreprocessorSymbolValues("_MSC_VER", "_M_IX86", "_WIN32"));

		// Microsoft specific modifiers that can be ignored
		symbols.put("__cdecl", "");
		symbols.put("__fastcall", "");
		symbols.put("__restrict", "");
		symbols.put("__sptr", "");
		symbols.put("__stdcall", "");
		symbols.put("__unaligned", "");
		symbols.put("__uptr", "");
		symbols.put("__w64", "");
	}
	
	public IPath[] getIncludePaths() {
		return paths;
	}

	public IProject getProject() {
		return null;
	}

	public IDiscoveredScannerInfoSerializable getSerializable() {
		return null;
	}

	public Map<String, String> getSymbols() {
		return symbols;
	}
}
