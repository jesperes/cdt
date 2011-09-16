package org.eclipse.cdt.msw.build;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.cdt.managedbuilder.core.IConfiguration;
import org.eclipse.cdt.managedbuilder.core.IManagedProject;
import org.eclipse.cdt.managedbuilder.core.ManagedBuilderCorePlugin;
import org.eclipse.cdt.managedbuilder.envvar.IBuildEnvironmentVariable;
import org.eclipse.cdt.managedbuilder.envvar.IConfigurationEnvironmentVariableSupplier;
import org.eclipse.cdt.managedbuilder.envvar.IEnvironmentVariableProvider;
import org.eclipse.cdt.managedbuilder.envvar.IProjectEnvironmentVariableSupplier;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;

/**
 * @author DSchaefer
 * 
 */
public class WinEnvironmentVariableSupplier implements
		IConfigurationEnvironmentVariableSupplier,
		IProjectEnvironmentVariableSupplier {

	private static final String WINDOWSSDKDIR = "WindowsSdkDir";
	private static final String VCINSTALLDIR = "VCINSTALLDIR";
	private static final String INCLUDE = "INCLUDE";
	private static final String LIBDIRS = "LIB";
	private static final String PATH = "PATH";
	private static final String LIBPATH = "LIBPATH";

	private static Map<String, IBuildEnvironmentVariable> envvars;
	private static String sdkDir;
	private static String vcDir;
	private static String includeDirs;
	private static String libDirs;
	private static String libPathDirs;
	private static String path;

	private static class WindowsBuildEnvironmentVariable implements
			IBuildEnvironmentVariable {

		private final String name;
		private final String value;
		private final int operation;

		public WindowsBuildEnvironmentVariable(String name, String value,
				int operation) {
			this.name = name;
			this.value = value;
			this.operation = operation;
		}

		public String getDelimiter() {
			return ";";
		}

		public String getName() {
			return name;
		}

		public String getValue() {
			return value;
		}

		public int getOperation() {
			return operation;
		}

	}

	static {
		initvars();
	}

	public IBuildEnvironmentVariable getVariable(String variableName,
			IManagedProject project, IEnvironmentVariableProvider provider) {
		return envvars.get(variableName);
	}

	public IBuildEnvironmentVariable getVariable(String variableName,
			IConfiguration configuration, IEnvironmentVariableProvider provider) {
		return envvars.get(variableName);
	}

	public IBuildEnvironmentVariable[] getVariables(IManagedProject project,
			IEnvironmentVariableProvider provider) {
		return envvars.values().toArray(
				new IBuildEnvironmentVariable[envvars.size()]);
	}

	public IBuildEnvironmentVariable[] getVariables(
			IConfiguration configuration, IEnvironmentVariableProvider provider) {
		return envvars.values().toArray(
				new IBuildEnvironmentVariable[envvars.size()]);
	}

	private static void addvar(IBuildEnvironmentVariable var) {
		envvars.put(var.getName(), var);
	}

	public static IPath[] getIncludePath() {
		String[] dirs = includeDirs.split(";");
		IPath[] paths = new IPath[dirs.length];
		for (int i = 0; i < dirs.length; i++) {
			paths[i] = new Path(dirs[i]);
		}

		return paths;
	}

	public static Map<String, String> getPreprocessorSymbolValues(
			String... values) {
		Map<String, String> map = new HashMap<String, String>();
		File tempc = null;
		File tempexe = null;
		try {
			tempc = File.createTempFile("temp", ".cpp");
			tempexe = File.createTempFile("temp", ".exe");

			String START_SYMBOLS = "START_SYMBOLS";
			FileWriter writer = new FileWriter(tempc);
			writer.write("#include <iostream>\n" + "int main() {\n");
			writer.write("    std::cout << \"" + START_SYMBOLS
					+ "\" << std::endl;");
			for (String v : values) {
				writer.write(String.format(
						"    std::cout << %s << std::endl;\n", v));
			}

			writer.write("}\n");
			writer.close();

			String cmdline = String.format(
					"call \"%%%s%%\"\\vsvars32.bat && cl %s /Fe%s && %s",
					guessComnToolsVariableName(), tempc, tempexe, tempexe);

			ProcessBuilder pb = new ProcessBuilder("cmd", "/c", cmdline);

			pb.redirectErrorStream(true);
			Process process = pb.start();

			BufferedReader reader = new BufferedReader(new InputStreamReader(
					process.getInputStream()));

			String s = null;
			while ((s = reader.readLine()) != null && !s.equals(START_SYMBOLS))
				// swallow everything up to "START_SYMBOLS"
				;

			// The rest are the preprocessor symbols
			for (String value : values) {
				map.put(value, reader.readLine());
			}

			reader.close();
		} catch (IOException e) {
			ManagedBuilderCorePlugin.log(e);
		} finally {
			if (tempc != null && tempc.exists())
				tempc.delete();
			if (tempexe != null && tempexe.exists())
				tempexe.delete();
		}

		return map;
	}

	private static synchronized void initvars() {
		if (envvars != null)
			return;

		envvars = new HashMap<String, IBuildEnvironmentVariable>();

		Map<String, String> vsvars32 = loadVsVars32(null, WINDOWSSDKDIR,
				VCINSTALLDIR, INCLUDE, PATH, LIBDIRS, LIBPATH);

		// The SDK Location
		sdkDir = vsvars32.get(WINDOWSSDKDIR);
		vcDir = vsvars32.get(VCINSTALLDIR);
		includeDirs = vsvars32.get(INCLUDE);
		libDirs = vsvars32.get(LIBDIRS);
		libPathDirs = vsvars32.get(LIBPATH);
		path = vsvars32.get(PATH);

		// Make sure we don't get junk from running vsvars32.bat
		if (!new File(sdkDir).exists())
			sdkDir = null;

		if (!new File(vcDir).exists())
			vcDir = null;

		if (sdkDir == null && vcDir == null) {
			return;
		}

		// Add "Include/gl" to include path
		StringBuilder builder = new StringBuilder();
		builder.append(includeDirs);
		// not necessary to add ; here?
		// builder.append(";");
		builder.append(sdkDir + "/gl");
		includeDirs = builder.toString();

		// INCLUDE
		addvar(new WindowsBuildEnvironmentVariable("INCLUDE", includeDirs,
				IBuildEnvironmentVariable.ENVVAR_PREPEND));

		// LIB
		addvar(new WindowsBuildEnvironmentVariable("LIB", libDirs,
				IBuildEnvironmentVariable.ENVVAR_PREPEND));

		// PATH
		addvar(new WindowsBuildEnvironmentVariable("PATH", path,
				IBuildEnvironmentVariable.ENVVAR_PREPEND));

		// LIBPATH
		addvar(new WindowsBuildEnvironmentVariable("LIBPATH", libPathDirs,
				IBuildEnvironmentVariable.ENVVAR_PREPEND));
	}

	/**
	 * Load variable values defined in vsvars32.bat.
	 * 
	 * @param comntoolsVar
	 *            The name of the VS**COMNTOOLS variable to invoke to setup the
	 *            environment. Can be null, in which case the method will check
	 *            which one is defined, starting with the newest versions first.
	 * @param vars
	 *            The variables to get the values for.
	 * @return
	 * @throws InterruptedException
	 */
	private static Map<String, String> loadVsVars32(String comntoolsVar,
			String... vars) {

		Map<String, String> varMap = new HashMap<String, String>();

		if (comntoolsVar == null) {
			comntoolsVar = guessComnToolsVariableName();
		}

		if (comntoolsVar == null)
			return varMap;

		File file = null;
		try {
			file = File.createTempFile("cdtmsw", ".bat");
			// file.deleteOnExit();
			System.err.println(file);

			FileWriter writer = new FileWriter(file);
			writer.write("@echo off\n");
			writer.write("call \"%" + comntoolsVar + "%\"\\vsvars32.bat\n");

			for (String v : vars) {
				// use xxx prefix to avoid problems if variable is unset
				writer.write(String.format("echo xxx%%%s%%\n", v));
			}
			writer.close();

			Process p = Runtime.getRuntime().exec("cmd /c " + file.toString());
			BufferedReader reader = new BufferedReader(new InputStreamReader(
					p.getInputStream()));

			reader.readLine(); // discard first line
			for (String s : vars) {
				varMap.put(s, reader.readLine().substring(3)); // skip leading
																// xxx
			}
			reader.close();

			return varMap;

		} catch (IOException e) {
			e.printStackTrace();
			return null;
		} finally {
			// if (file != null && file.exists())
			// file.delete();
		}
	}

	private static String guessComnToolsVariableName() {
		String[] versions = new String[] { "10", "90", "80" };

		for (String v : versions) {
			String varname = String.format("VS%sCOMNTOOLS", v);
			if (System.getenv(varname) != null)
				return varname;
		}

		return null;
	}

}