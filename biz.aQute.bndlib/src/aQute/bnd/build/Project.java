package aQute.bnd.build;

import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.locks.*;
import java.util.jar.*;
import java.util.regex.*;

import aQute.bnd.header.*;
import aQute.bnd.help.*;
import aQute.bnd.maven.support.*;
import aQute.bnd.osgi.*;
import aQute.bnd.osgi.eclipse.*;
import aQute.bnd.service.*;
import aQute.bnd.service.RepositoryPlugin.PutResult;
import aQute.bnd.service.action.*;
import aQute.bnd.version.*;
import aQute.lib.collections.*;
import aQute.lib.converter.*;
import aQute.lib.io.*;
import aQute.lib.strings.*;
import aQute.lib.utf8properties.*;
import aQute.libg.command.*;
import aQute.libg.generics.*;
import aQute.libg.glob.*;
import aQute.libg.reporter.*;
import aQute.libg.sed.*;

/**
 * This class is NOT threadsafe
 */

public class Project extends Processor {

	final static Pattern		VERSION_ANNOTATION		= Pattern
																.compile("@\\s*(:?aQute\\.bnd\\.annotation\\.)?Version\\s*\\(\\s*(:?value\\s*=\\s*)?\"(\\d+(:?\\.\\d+(:?\\.\\d+(:?\\.[\\d\\w-_]+)?)?)?)\"\\s*\\)");
	final static String			DEFAULT_ACTIONS			= "build; label='Build', test; label='Test', run; label='Run', clean; label='Clean', release; label='Release', refreshAll; label=Refresh, deploy;label=Deploy";
	public final static String	BNDFILE					= "bnd.bnd";
	public final static String	BNDCNF					= "cnf";
	final Workspace				workspace;
	boolean						preparedPaths;
	final Collection<Project>	dependson				= new LinkedHashSet<Project>();
	final Collection<Container>	classpath				= new LinkedHashSet<Container>();
	final Collection<Container>	buildpath				= new LinkedHashSet<Container>();
	final Collection<Container>	testpath				= new LinkedHashSet<Container>();
	final Collection<Container>	runpath					= new LinkedHashSet<Container>();
	final Collection<Container>	runbundles				= new LinkedHashSet<Container>();
	final Collection<Container>	runfw					= new LinkedHashSet<Container>();
	File						runstorage;
	final Collection<File>		sourcepath				= new LinkedHashSet<File>();
	final Collection<File>		allsourcepath			= new LinkedHashSet<File>();
	final Collection<Container>	bootclasspath			= new LinkedHashSet<Container>();
	final Map<String, Version>	versionMap				= new LinkedHashMap<String, Version>();
	final Lock					lock					= new ReentrantLock(true);
	volatile String				lockingReason;
	volatile Thread				lockingThread;
	File						output;
	File						target;
	boolean						inPrepare;
	int							revision;
	File						files[];
	static List<Project>		trail					= new ArrayList<Project>();
	boolean						delayRunDependencies	= true;
	final ProjectMessages		msgs					= ReporterMessages.base(this, ProjectMessages.class);
	private Properties			ide;
	final Packages				exportedPackages		= new Packages();
	final Packages				importedPackages		= new Packages();
	final Packages				containedPackages		= new Packages();

	public Project(Workspace workspace, File projectDir, File buildFile) throws Exception {
		super(workspace);
		this.workspace = workspace;
		setFileMustExist(false);
		setProperties(buildFile);
		assert workspace != null;
		// For backward compatibility reasons, we also read
		readBuildProperties();
	}

	public Project(Workspace workspace, File buildDir) throws Exception {
		this(workspace, buildDir, new File(buildDir, BNDFILE));
	}

	private void readBuildProperties() throws Exception {
		try {
			File f = getFile("build.properties");
			if (f.isFile()) {
				Properties p = loadProperties(f);
				for (Enumeration< ? > e = p.propertyNames(); e.hasMoreElements();) {
					String key = (String) e.nextElement();
					String newkey = key;
					if (key.indexOf('$') >= 0) {
						newkey = getReplacer().process(key);
					}
					setProperty(newkey, p.getProperty(key));
				}
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static Project getUnparented(File propertiesFile) throws Exception {
		propertiesFile = propertiesFile.getAbsoluteFile();
		Workspace workspace = new Workspace(propertiesFile.getParentFile());
		Project project = new Project(workspace, propertiesFile.getParentFile());
		project.setProperties(propertiesFile);
		project.setFileMustExist(true);
		return project;
	}

	public synchronized boolean isValid() {
		return getBase().isDirectory() && getPropertiesFile().isFile();
	}

	/**
	 * Return a new builder that is nicely setup for this project. Please close
	 * this builder after use.
	 *
	 * @param parent
	 *            The project builder to use as parent, use this project if null
	 * @return
	 * @throws Exception
	 */
	public synchronized ProjectBuilder getBuilder(ProjectBuilder parent) throws Exception {

		ProjectBuilder builder;

		if (parent == null)
			builder = new ProjectBuilder(this);
		else
			builder = new ProjectBuilder(parent);

		builder.setBase(getBase());
		builder.setPedantic(isPedantic());
		builder.setTrace(isTrace());
		return builder;
	}

	public synchronized int getChanged() {
		return revision;
	}

	/*
	 * Indicate a change in the external world that affects our build. This will
	 * clear any cached results.
	 */
	public synchronized void setChanged() {
		// if (refresh()) {
		preparedPaths = false;
		files = null;
		revision++;
		// }
	}

	public Workspace getWorkspace() {
		return workspace;
	}

	@Override
	public String toString() {
		return getBase().getName();
	}

	/**
	 * Set up all the paths
	 */

	public synchronized void prepare() throws Exception {
		if (!isValid()) {
			warning("Invalid project attempts to prepare: %s", this);
			return;
		}

		if (inPrepare)
			throw new CircularDependencyException(trail.toString() + "," + this);

		trail.add(this);
		try {
			if (!preparedPaths) {
				inPrepare = true;
				try {
					dependson.clear();
					buildpath.clear();
					sourcepath.clear();
					allsourcepath.clear();
					bootclasspath.clear();

					// JIT
					testpath.clear();
					runpath.clear();
					runbundles.clear();
					runfw.clear();

					// We use a builder to construct all the properties for
					// use.
					setProperty("basedir", getBase().getAbsolutePath());

					// If a bnd.bnd file exists, we read it.
					// Otherwise, we just do the build properties.
					if (!getPropertiesFile().isFile() && new File(getBase(), ".classpath").isFile()) {
						// Get our Eclipse info, we might depend on other
						// projects
						// though ideally this should become empty and void
						doEclipseClasspath();
					}

					// Calculate our source directory

					File src = getSrc();
					if (src.isDirectory()) {
						sourcepath.add(src);
						allsourcepath.add(src);
					} else
						sourcepath.add(getBase());

					// Set default bin directory
					output = getSrcOutput().getAbsoluteFile();
					if (!output.exists()) {
						if (!output.mkdirs()) {
							throw new IOException("Could not create directory " + output);
						}
						getWorkspace().changedFile(output);
					}
					if (!output.isDirectory())
						msgs.NoOutputDirectory_(output);
					else {
						Container c = new Container(this, output);
						if (!buildpath.contains(c))
							buildpath.add(c);
					}

					// Where we store all our generated stuff.
					target = getTarget0();

					// Where the launched OSGi framework stores stuff
					String runStorageStr = getProperty(Constants.RUNSTORAGE);
					runstorage = runStorageStr != null ? getFile(runStorageStr) : null;

					// We might have some other projects we want build
					// before we do anything, but these projects are not in
					// our path. The -dependson allows you to build them before.
					// The values are possibly negated globbing patterns.

					// dependencies.add( getWorkspace().getProject("cnf"));

					String dp = getProperty(Constants.DEPENDSON);
					Set<String> requiredProjectNames = new LinkedHashSet<String>(new Parameters(dp).keySet());

					// Allow DependencyConstributors to modify
					// requiredProjectNames
					List<DependencyContributor> dcs = getPlugins(DependencyContributor.class);
					for (DependencyContributor dc : dcs)
						dc.addDependencies(this, requiredProjectNames);

					Instructions is = new Instructions(requiredProjectNames);

					Set<Instruction> unused = new HashSet<Instruction>();
					Collection<Project> projects = getWorkspace().getAllProjects();
					Collection<Project> dependencies = is.select(projects, unused, false);

					for (Instruction u : unused)
						msgs.MissingDependson_(u.getInput());

					// We have two paths that consists of repo files, projects,
					// or some other stuff. The doPath routine adds them to the
					// path and extracts the projects so we can build them
					// before.

					doPath(buildpath, dependencies, parseBuildpath(), bootclasspath, false, BUILDPATH);
					doPath(testpath, dependencies, parseTestpath(), bootclasspath, false, TESTPATH);
					if (!delayRunDependencies) {
						doPath(runfw, dependencies, parseRunFw(), null, false, RUNFW);
						doPath(runpath, dependencies, parseRunpath(), null, false, RUNPATH);
						doPath(runbundles, dependencies, parseRunbundles(), null, true, RUNBUNDLES);
					}

					// We now know all dependent projects. But we also depend
					// on whatever those projects depend on. This creates an
					// ordered list without any duplicates. This of course
					// assumes
					// that there is no circularity. However, this is checked
					// by the inPrepare flag, will throw an exception if we
					// are circular.

					Set<Project> done = new HashSet<Project>();
					done.add(this);
					allsourcepath.addAll(sourcepath);

					for (Project project : dependencies)
						project.traverse(dependson, done);

					for (Project project : dependson) {
						allsourcepath.addAll(project.getSourcePath());
					}
					//[cs] Testing this commented out. If bad issues, never setting this to true means that
					// TONS of extra preparing is done over and over again on the same projects.
					//if (isOk())
					preparedPaths = true;
				}
				finally {
					inPrepare = false;
				}
			}
		}
		finally {
			trail.remove(this);
		}
	}

	/*
	 *
	 */

	private File getTarget0() throws IOException {
		File target = getTargetDir();
		if (!target.exists()) {
			if (!target.mkdirs()) {
				throw new IOException("Could not create directory " + target);
			}
			getWorkspace().changedFile(target);
		}
		return target;
	}

	public File getSrc() {
		String deflt = Workspace.getDefaults().getProperty(Constants.DEFAULT_PROP_SRC_DIR);
		return getFile(getProperty(Constants.DEFAULT_PROP_SRC_DIR, deflt));
	}

	public File getSrcOutput() {
		String deflt = Workspace.getDefaults().getProperty(Constants.DEFAULT_PROP_BIN_DIR);
		return getFile(getProperty(Constants.DEFAULT_PROP_BIN_DIR, deflt));
	}

	public File getTestSrc() {
		String deflt = Workspace.getDefaults().getProperty(Constants.DEFAULT_PROP_TESTSRC_DIR);
		return getFile(getProperty(Constants.DEFAULT_PROP_TESTSRC_DIR, deflt));
	}

	public File getTestOutput() {
		String deflt = Workspace.getDefaults().getProperty(Constants.DEFAULT_PROP_TESTBIN_DIR);
		return getFile(getProperty(Constants.DEFAULT_PROP_TESTBIN_DIR, deflt));
	}

	public File getTargetDir() {
		String deflt = Workspace.getDefaults().getProperty(Constants.DEFAULT_PROP_TARGET_DIR);
		return getFile(getProperty(Constants.DEFAULT_PROP_TARGET_DIR, deflt));
	}

	private void traverse(Collection<Project> dependencies, Set<Project> visited) throws Exception {
		if (visited.contains(this))
			return;

		visited.add(this);

		for (Project project : getDependson())
			project.traverse(dependencies, visited);

		dependencies.add(this);
	}

	/**
	 * Iterate over the entries and place the projects on the projects list and
	 * all the files of the entries on the resultpath.
	 *
	 * @param resultpath
	 *            The list that gets all the files
	 * @param projects
	 *            The list that gets any projects that are entries
	 * @param entries
	 *            The input list of classpath entries
	 */
	private void doPath(Collection<Container> resultpath, Collection<Project> projects, Collection<Container> entries,
			Collection<Container> bootclasspath, boolean noproject, String name) {
		for (Container cpe : entries) {
			if (cpe.getError() != null)
				error(cpe.getError());
			else {
				if (cpe.getType() == Container.TYPE.PROJECT) {
					projects.add(cpe.getProject());

					if (noproject //
							&& since(About._2_3) //
							&& cpe.getAttributes() != null
							&& VERSION_ATTR_PROJECT.equals(cpe.getAttributes().get(VERSION_ATTRIBUTE))) {
						//
						// we're trying to put a project's output directory on
						// -runbundles list
						//
						error("%s is specified with version=project on %s. This version uses the project's output directory, which is not allowed since it must be an actual JAR file for this list.",
								cpe.getBundleSymbolicName(), name);
					}
				}
				if (bootclasspath != null
						&& (cpe.getBundleSymbolicName().startsWith("ee.") || cpe.getAttributes().containsKey("boot")))
					bootclasspath.add(cpe);
				else
					resultpath.add(cpe);
			}
		}
	}

	/**
	 * Parse the list of bundles that are a prerequisite to this project.
	 * Bundles are listed in repo specific names. So we just let our repo
	 * plugins iterate over the list of bundles and we get the highest version
	 * from them.
	 *
	 * @return
	 */

	private List<Container> parseBuildpath() throws Exception {
		List<Container> bundles = getBundles(Strategy.LOWEST, mergeProperties(Constants.BUILDPATH), Constants.BUILDPATH);
		return bundles;
	}

	private List<Container> parseRunpath() throws Exception {
		return getBundles(Strategy.HIGHEST, mergeProperties(Constants.RUNPATH), Constants.RUNPATH);
	}

	private List<Container> parseRunbundles() throws Exception {
		
		return getBundles(Strategy.HIGHEST, mergeProperties(Constants.RUNBUNDLES), Constants.RUNBUNDLES);
	}

	private List<Container> parseRunFw() throws Exception {
		return getBundles(Strategy.HIGHEST, getProperty(Constants.RUNFW), Constants.RUNFW);
	}

	private List<Container> parseTestpath() throws Exception {
		return getBundles(Strategy.HIGHEST, mergeProperties(Constants.TESTPATH), Constants.TESTPATH);
	}


	/**
	 * Analyze the header and return a list of files that should be on the
	 * build, test or some other path. The list is assumed to be a list of bsns
	 * with a version specification. The special case of version=project
	 * indicates there is a project in the same workspace. The path to the
	 * output directory is calculated. The default directory ${bin} can be
	 * overridden with the output attribute.
	 *
	 * @param strategy
	 *            STRATEGY_LOWEST or STRATEGY_HIGHEST
	 * @param spec
	 *            The header
	 * @return
	 */

	public List<Container> getBundles(Strategy strategyx, String spec, String source) throws Exception {
		List<Container> result = new ArrayList<Container>();
		Parameters bundles = new Parameters(spec);

		try {
			for (Iterator<Entry<String,Attrs>> i = bundles.entrySet().iterator(); i.hasNext();) {
				Entry<String,Attrs> entry = i.next();
				String bsn = removeDuplicateMarker(entry.getKey());
				Map<String,String> attrs = entry.getValue();

				Container found = null;

				String versionRange = attrs.get("version");

				if (versionRange != null) {
					if (versionRange.equals(VERSION_ATTR_LATEST) || versionRange.equals(VERSION_ATTR_SNAPSHOT)) {
						found = getBundle(bsn, versionRange, strategyx, attrs);
					}
				}
				if (found == null) {

					//
					// TODO This looks like a duplicate
					// of what is done in getBundle??
					//
					if (versionRange != null
							&& (versionRange.equals(VERSION_ATTR_PROJECT) || versionRange.equals(VERSION_ATTR_LATEST))) {

						//
						// Use the bin directory ...
						//
						Project project = getWorkspace().getProject(bsn);
						if (project != null && project.exists()) {
							File f = project.getOutput();
							found = new Container(project, bsn, versionRange, Container.TYPE.PROJECT, f, null, attrs,
									null);
						} else {
							msgs.NoSuchProject(bsn, spec);
							continue;
						}
					} else if (versionRange != null && versionRange.equals("file")) {
						File f = getFile(bsn);
						String error = null;
						if (!f.exists())
							error = "File does not exist: " + f.getAbsolutePath();
						if (f.getName().endsWith(".lib")) {
							found = new Container(this, bsn, "file", Container.TYPE.LIBRARY, f, error, attrs, null);
						} else {
							found = new Container(this, bsn, "file", Container.TYPE.EXTERNAL, f, error, attrs, null);
						}
					} else {
						found = getBundle(bsn, versionRange, strategyx, attrs);
					}
				}

				if (found != null) {
					List<Container> libs = found.getMembers();
					for (Container cc : libs) {
						if (result.contains(cc)) {
							if (isPedantic())
								warning("Multiple bundles with the same final URL: %s, dropped duplicate", cc);
						} else {
							if (cc.getError() != null) {
								warning("Cannot find %s", cc);
							}
							result.add(cc);
						}
					}
				} else {
					// Oops, not a bundle in sight :-(
					Container x = new Container(this, bsn, versionRange, Container.TYPE.ERROR, null, bsn + ";version="
							+ versionRange + " not found", attrs, null);
					result.add(x);
					warning("Can not find URL for bsn " + bsn);
				}
			}
		}
		catch (CircularDependencyException e) {
			String message = e.getMessage();
			if (source != null)
				message = String.format("%s (from property: %s)", message, source);
			msgs.CircularDependencyContext_Message_(getName(), message);
		}
		catch (Exception e) {
			msgs.Unexpected_Error_(spec, e);
		}
		return result;
	}

	/**
	 * Just calls a new method with a default parm.
	 *
	 * @throws Exception
	 */
	Collection<Container> getBundles(Strategy strategy, String spec) throws Exception {
		return getBundles(strategy, spec, null);
	}

	static void mergeNames(String names, Set<String> set) {
		StringTokenizer tokenizer = new StringTokenizer(names, ",");
		while (tokenizer.hasMoreTokens())
			set.add(tokenizer.nextToken().trim());
	}

	static String flatten(Set<String> names) {
		StringBuilder builder = new StringBuilder();
		boolean first = true;
		for (String name : names) {
			if (!first)
				builder.append(',');
			builder.append(name);
			first = false;
		}
		return builder.toString();
	}

	static void addToPackageList(Container container, String newPackageNames) {
		Set<String> merged = new HashSet<String>();

		String packageListStr = container.attributes.get("packages");
		if (packageListStr != null)
			mergeNames(packageListStr, merged);
		if (newPackageNames != null)
			mergeNames(newPackageNames, merged);

		container.putAttribute("packages", flatten(merged));
	}

	/**
	 * The user selected pom in a path. This will place the pom as well as its
	 * dependencies on the list
	 *
	 * @param strategyx
	 *            the strategy to use.
	 * @param result
	 *            The list of result containers
	 * @param attrs
	 *            The attributes
	 * @throws Exception
	 *             anything goes wrong
	 */
	public void doMavenPom(Strategy strategyx, List<Container> result, String action) throws Exception {
		File pomFile = getFile("pom.xml");
		if (!pomFile.isFile())
			msgs.MissingPom();
		else {
			ProjectPom pom = getWorkspace().getMaven().createProjectModel(pomFile);
			if (action == null)
				action = "compile";
			Pom.Scope act = Pom.Scope.valueOf(action);
			Set<Pom> dependencies = pom.getDependencies(act);
			for (Pom sub : dependencies) {
				File artifact = sub.getArtifact();
				Container container = new Container(artifact, null);
				result.add(container);
			}
		}
	}

	public Collection<Project> getDependson() throws Exception {
		prepare();
		return dependson;
	}

	public Collection<Container> getBuildpath() throws Exception {
		prepare();
		return buildpath;
	}

	public Collection<Container> getTestpath() throws Exception {
		prepare();
		justInTime(testpath, parseTestpath(), false, TESTPATH);
		return testpath;
	}

	/**
	 * Handle dependencies for paths that are calculated on demand.
	 *
	 * @param testpath2
	 * @param parseTestpath
	 */
	private void justInTime(Collection<Container> path, List<Container> entries, boolean noproject, String name) {
		if (delayRunDependencies && path.isEmpty())
			doPath(path, dependson, entries, null, noproject, name);
	}

	public Collection<Container> getRunpath() throws Exception {
		prepare();
		justInTime(runpath, parseRunpath(), false, RUNPATH);
		return runpath;
	}

	public Collection<Container> getRunbundles() throws Exception {
		prepare();
		justInTime(runbundles, parseRunbundles(), true, RUNBUNDLES);
		return runbundles;
	}

	/**
	 * Return the run framework
	 *
	 * @throws Exception
	 */
	public Collection<Container> getRunFw() throws Exception {
		prepare();
		justInTime(runfw, parseRunFw(), false, RUNFW);
		return runfw;
	}

	public File getRunStorage() throws Exception {
		prepare();
		return runstorage;
	}

	public boolean getRunBuilds() {
		boolean result;
		String runBuildsStr = getProperty(Constants.RUNBUILDS);
		if (runBuildsStr == null)
			result = !getPropertiesFile().getName().toLowerCase().endsWith(Constants.DEFAULT_BNDRUN_EXTENSION);
		else
			result = Boolean.parseBoolean(runBuildsStr);
		return result;
	}

	public Collection<File> getSourcePath() throws Exception {
		prepare();
		return sourcepath;
	}

	public Collection<File> getAllsourcepath() throws Exception {
		prepare();
		return allsourcepath;
	}

	public Collection<Container> getBootclasspath() throws Exception {
		prepare();
		return bootclasspath;
	}

	public File getOutput() throws Exception {
		prepare();
		return output;
	}

	private void doEclipseClasspath() throws Exception {
		EclipseClasspath eclipse = new EclipseClasspath(this, getWorkspace().getBase(), getBase());
		eclipse.setRecurse(false);

		// We get the file directories but in this case we need
		// to tell ant that the project names
		for (File dependent : eclipse.getDependents()) {
			Project required = workspace.getProject(dependent.getName());
			dependson.add(required);
		}
		for (File f : eclipse.getClasspath()) {
			buildpath.add(new Container(f, null));
		}
		for (File f : eclipse.getBootclasspath()) {
			bootclasspath.add(new Container(f, null));
		}
		sourcepath.addAll(eclipse.getSourcepath());
		allsourcepath.addAll(eclipse.getAllSources());
		output = eclipse.getOutput();
	}

	public String _p_dependson(String args[]) throws Exception {
		return list(args, toFiles(getDependson()));
	}

	private Collection< ? > toFiles(Collection<Project> projects) {
		List<File> files = new ArrayList<File>();
		for (Project p : projects) {
			files.add(p.getBase());
		}
		return files;
	}

	public String _p_buildpath(String args[]) throws Exception {
		return list(args, getBuildpath());
	}

	public String _p_testpath(String args[]) throws Exception {
		return list(args, getRunpath());
	}

	public String _p_sourcepath(String args[]) throws Exception {
		return list(args, getSourcePath());
	}

	public String _p_allsourcepath(String args[]) throws Exception {
		return list(args, getAllsourcepath());
	}

	public String _p_bootclasspath(String args[]) throws Exception {
		return list(args, getBootclasspath());
	}

	public String _p_output(String args[]) throws Exception {
		if (args.length != 1)
			throw new IllegalArgumentException("${output} should not have arguments");
		return getOutput().getAbsolutePath();
	}

	private String list(String[] args, Collection< ? > list) {
		if (args.length > 3)
			throw new IllegalArgumentException("${" + args[0]
					+ "[;<separator>]} can only take a separator as argument, has " + Arrays.toString(args));

		String separator = ",";

		if (args.length == 2) {
			separator = args[1];
		}

		return join(list, separator);
	}

	@Override
	protected Object[] getMacroDomains() {
		return new Object[] {
			workspace
		};
	}

	public File release(String jarName, InputStream jarStream) throws Exception {
		String name = getProperty(Constants.RELEASEREPO);
		return release(name, jarName, jarStream);
	}

	public URI releaseURI(String jarName, InputStream jarStream) throws Exception {
		String name = getProperty(Constants.RELEASEREPO);
		return releaseURI(name, jarName, jarStream);
	}

	/**
	 * Release
	 *
	 * @param name
	 *            The repository name
	 * @param jarName
	 * @param jarStream
	 * @return
	 * @throws Exception
	 */
	public File release(String name, String jarName, InputStream jarStream) throws Exception {
		URI uri = releaseURI(name, jarName, jarStream);
		if (uri != null && uri.getScheme().equals("file")) {
			return new File(uri);
		}
		return null;
	}

	public URI releaseURI(String name, String jarName, InputStream jarStream) throws Exception {

		// Blank repository name means no release
		if ("".equals(name)) {
			return null;
		}

		trace("release to %s", name);
		RepositoryPlugin repo = getReleaseRepo(name);

		if (repo == null) {
			if (name == null)
				msgs.NoNameForReleaseRepository();
			else
				msgs.ReleaseRepository_NotFoundIn_(name, getPlugins(RepositoryPlugin.class));
			return null;
		}

		try {
			PutResult r = repo.put(jarStream, new RepositoryPlugin.PutOptions());
			trace("Released %s to %s in repository %s", jarName, r.artifact, repo);
			return r.artifact;
		}
		catch (Exception e) {
			msgs.Release_Into_Exception_(jarName, repo, e);
			return null;
		}
	}

	RepositoryPlugin getReleaseRepo(String releaserepo) {
		String name = releaserepo == null ? name = getProperty(RELEASEREPO) : releaserepo;

		List<RepositoryPlugin> plugins = getPlugins(RepositoryPlugin.class);

		for (RepositoryPlugin plugin : plugins) {
			if (!plugin.canWrite())
				continue;

			if (name == null)
				return plugin;

			if (name.equals(plugin.getName()))
				return plugin;
		}
		return null;
	}

	public void release(boolean test) throws Exception {
		String name = getProperty(Constants.RELEASEREPO);
		release(name, test);
	}

	/**
	 * Release
	 *
	 * @param name
	 *            The respository name
	 * @param test
	 *            Run testcases
	 * @throws Exception
	 */
	public void release(String name, boolean test) throws Exception {
		trace("release");
		File[] jars = build(test);
		// If build fails jars will be null
		if (jars == null) {
			trace("no jars being build");
			return;
		}
		trace("build ", Arrays.toString(jars));
		for (File jar : jars) {
			release(name, jar.getName(), new BufferedInputStream(new FileInputStream(jar)));
		}
	}

	/**
	 * Get a bundle from one of the plugin repositories. If an exact version is
	 * required we just return the first repository found (in declaration order
	 * in the build.bnd file).
	 *
	 * @param bsn
	 *            The bundle symbolic name
	 * @param range
	 *            The version range
	 * @param lowest
	 *            set to LOWEST or HIGHEST
	 * @return the file object that points to the bundle or null if not found
	 * @throws Exception
	 *             when something goes wrong
	 */

	public Container getBundle(String bsn, String range, Strategy strategy, Map<String,String> attrs) throws Exception {

		if (range == null)
			range = "0";

		if (VERSION_ATTR_SNAPSHOT.equals(range) || VERSION_ATTR_PROJECT.equals(range)) {
			return getBundleFromProject(bsn, attrs);
		}

		Strategy useStrategy = strategy;

		if (VERSION_ATTR_LATEST.equals(range)) {
			Container c = getBundleFromProject(bsn, attrs);
			if (c != null)
				return c;

			useStrategy = Strategy.HIGHEST;
		}

		useStrategy = overrideStrategy(attrs, useStrategy);

		List<RepositoryPlugin> plugins = workspace.getRepositories();

		if (useStrategy == Strategy.EXACT) {
			if (!Verifier.isVersion(range))
				return new Container(this, bsn, range, Container.TYPE.ERROR, null, bsn + ";version=" + range
						+ " Invalid version", null, null);

			// For an exact range we just iterate over the repos
			// and return the first we find.
			Version version = new Version(range);
			for (RepositoryPlugin plugin : plugins) {
				DownloadBlocker blocker = new DownloadBlocker(this);
				File result = plugin.get(bsn, version, attrs, blocker);
				if (result != null)
					return toContainer(bsn, range, attrs, result, blocker);
			}
		} else {
			VersionRange versionRange = VERSION_ATTR_LATEST.equals(range) ? new VersionRange("0") : new VersionRange(
					range);

			// We have a range search. Gather all the versions in all the repos
			// and make a decision on that choice. If the same version is found
			// in
			// multiple repos we take the first

			SortedMap<Version,RepositoryPlugin> versions = new TreeMap<Version,RepositoryPlugin>();
			for (RepositoryPlugin plugin : plugins) {
				try {
					SortedSet<Version> vs = plugin.versions(bsn);
					if (vs != null) {
						for (Version v : vs) {
							if (!versions.containsKey(v) && versionRange.includes(v))
								versions.put(v, plugin);
						}
					}
				}
				catch (UnsupportedOperationException ose) {
					// We have a plugin that cannot list versions, try
					// if it has this specific version
					// The main reaosn for this code was the Maven Remote
					// Repository
					// To query, we must have a real version
					if (!versions.isEmpty() && Verifier.isVersion(range)) {
						Version version = new Version(range);
						DownloadBlocker blocker = new DownloadBlocker(this);
						File file = plugin.get(bsn, version, attrs, blocker);
						// and the entry must exist
						// if it does, return this as a result
						if (file != null)
							return toContainer(bsn, range, attrs, file, blocker);
					}
				}
			}

			//
			// We have to augment the list of returned versions
			// with info from the workspace. We use null as a marker
			// to indicate that it is a workspace project
			//

			SortedSet<Version> localVersions = getWorkspace().getWorkspaceRepository().versions(bsn);
			for (Version v : localVersions) {
				if (!versions.containsKey(v) && versionRange.includes(v))
					versions.put(v, null);
			}

			// Verify if we found any, if so, we use the strategy to pick
			// the first or last

			if (!versions.isEmpty()) {
				Version provider = null;

				switch (useStrategy) {
					case HIGHEST :
						provider = versions.lastKey();
						break;

					case LOWEST :
						provider = versions.firstKey();
						break;
					case EXACT :
						// TODO need to handle exact better
						break;
				}
				if (provider != null) {
					RepositoryPlugin repo = versions.get(provider);
					if ( repo == null) {
						// A null provider indicates that we have a local project
						return getBundleFromProject(bsn, attrs);
					}

					String version = provider.toString();
					DownloadBlocker blocker = new DownloadBlocker(this);
					File result = repo.get(bsn, provider, attrs, blocker);
					if (result != null)
						return toContainer(bsn, version, attrs, result, blocker);
				} else {
					msgs.FoundVersions_ForStrategy_ButNoProvider(versions, useStrategy);
				}
			}
		}

		//
		// If we get this far we ran into an error somewhere
		//
		// Check if we try to find a BSN that is in the workspace but marked
		// latest
		//

		if (!range.equals(VERSION_ATTR_LATEST)) {
			Container c = getBundleFromProject(bsn, attrs);
			return new Container(this, bsn, range, Container.TYPE.ERROR, null, bsn + ";version=" + range
					+ " Not found because latest was not specified."
					+ " It is, however, present in the workspace. Add '" + bsn
					+ ";version=(latest|snapshot)' to see the bundle in the workspace.", null, null);
		}

		return new Container(this, bsn, range, Container.TYPE.ERROR, null, bsn + ";version=" + range + " Not found in "
				+ plugins, null, null);

	}

	/**
	 * @param attrs
	 * @param useStrategy
	 * @return
	 */
	protected Strategy overrideStrategy(Map<String,String> attrs, Strategy useStrategy) {
		if (attrs != null) {
			String overrideStrategy = attrs.get("strategy");

			if (overrideStrategy != null) {
				if ("highest".equalsIgnoreCase(overrideStrategy))
					useStrategy = Strategy.HIGHEST;
				else if ("lowest".equalsIgnoreCase(overrideStrategy))
					useStrategy = Strategy.LOWEST;
				else if ("exact".equalsIgnoreCase(overrideStrategy))
					useStrategy = Strategy.EXACT;
			}
		}
		return useStrategy;
	}

	/**
	 * @param bsn
	 * @param range
	 * @param attrs
	 * @param result
	 * @return
	 */
	protected Container toContainer(String bsn, String range, Map<String,String> attrs, File result, DownloadBlocker db) {
		File f = result;
		if (f == null) {
			msgs.ConfusedNoContainerFile();
			f = new File("was null");
		}
		Container container;
		if (f.getName().endsWith("lib"))
			container = new Container(this, bsn, range, Container.TYPE.LIBRARY, f, null, attrs, db);
		else
			container = new Container(this, bsn, range, Container.TYPE.REPO, f, null, attrs, db);

		return container;
	}

	/**
	 * Look for the bundle in the workspace. The premise is that the bsn must
	 * start with the project name.
	 *
	 * @param bsn
	 *            The bsn
	 * @param attrs
	 *            Any attributes
	 * @return
	 * @throws Exception
	 */
	private Container getBundleFromProject(String bsn, Map<String,String> attrs) throws Exception {
		String pname = bsn;
		while (true) {
			Project p = getWorkspace().getProject(pname);
			if (p != null && p.isValid()) {
				Container c = p.getDeliverable(bsn, attrs);
				return c;
			}

			int n = pname.lastIndexOf('.');
			if (n <= 0)
				return null;
			pname = pname.substring(0, n);
		}
	}

	/**
	 * Deploy the file (which must be a bundle) into the repository.
	 *
	 * @param name
	 *            The repository name
	 * @param file
	 *            bundle
	 */
	public void deploy(String name, File file) throws Exception {
		List<RepositoryPlugin> plugins = getPlugins(RepositoryPlugin.class);

		RepositoryPlugin rp = null;
		for (RepositoryPlugin plugin : plugins) {
			if (!plugin.canWrite()) {
				continue;
			}
			if (name == null) {
				rp = plugin;
				break;
			} else if (name.equals(plugin.getName())) {
				rp = plugin;
				break;
			}
		}

		if (rp != null) {
			try {
				rp.put(new BufferedInputStream(new FileInputStream(file)), new RepositoryPlugin.PutOptions());
				return;
			}
			catch (Exception e) {
				msgs.DeployingFile_On_Exception_(file, rp.getName(), e);
			}
			return;
		}
		trace("No repo found " + file);
		throw new IllegalArgumentException("No repository found for " + file);
	}

	/**
	 * Deploy the file (which must be a bundle) into the repository.
	 *
	 * @param file
	 *            bundle
	 */
	public void deploy(File file) throws Exception {
		String name = getProperty(Constants.DEPLOYREPO);
		deploy(name, file);
	}

	/**
	 * Deploy the current project to a repository
	 *
	 * @throws Exception
	 */
	public void deploy() throws Exception {
		Parameters deploy = new Parameters(getProperty(DEPLOY));
		if (deploy.isEmpty()) {
			warning("Deploying but %s is not set to any repo", DEPLOY);
			return;
		}
		File[] outputs = getBuildFiles();
		for (File output : outputs) {
			for (Deploy d : getPlugins(Deploy.class)) {
				trace("Deploying %s to: %s", output.getName(), d);
				try {
					if (d.deploy(this, output.getName(), new BufferedInputStream(new FileInputStream(output))))
						trace("deployed %s successfully to %s", output, d);
				}
				catch (Exception e) {
					msgs.Deploying(e);
				}
			}
		}
	}

	/**
	 * Macro access to the repository ${repo;<bsn>[;<version>[;<low|high>]]}
	 */

	static String	_repoHelp	= "${repo ';'<bsn> [ ; <version> [; ('HIGHEST'|'LOWEST')]}";

	public String _repo(String args[]) throws Exception {
		if (args.length < 2) {
			msgs.RepoTooFewArguments(_repoHelp, args);
			return null;
		}

		String bsns = args[1];
		String version = null;
		Strategy strategy = Strategy.HIGHEST;

		if (args.length > 2) {
			version = args[2];
			if (args.length == 4) {
				if (args[3].equalsIgnoreCase("HIGHEST"))
					strategy = Strategy.HIGHEST;
				else if (args[3].equalsIgnoreCase("LOWEST"))
					strategy = Strategy.LOWEST;
				else if (args[3].equalsIgnoreCase("EXACT"))
					strategy = Strategy.EXACT;
				else
					msgs.InvalidStrategy(_repoHelp, args);
			}
		}

		Collection<String> parts = split(bsns);
		List<String> paths = new ArrayList<String>();

		for (String bsn : parts) {
			Container container = getBundle(bsn, version, strategy, null);
			if (container.getError() != null) {
				error("${repo} macro refers to an artifact %s-%s (%s) that has an error: %s", bsn, version, strategy,
						container.getError());
			} else
				add(paths, container);
		}
		return join(paths);
	}

	private void add(List<String> paths, Container container) throws Exception {
		if (container.getType() == Container.TYPE.LIBRARY) {
			List<Container> members = container.getMembers();
			for (Container sub : members) {
				add(paths, sub);
			}
		} else {
			if (container.getError() == null)
				paths.add(container.getFile().getAbsolutePath());
			else {
				paths.add("<<${repo} = " + container.getBundleSymbolicName() + "-" + container.getVersion() + " : "
						+ container.getError() + ">>");

				if (isPedantic()) {
					warning("Could not expand repo path request: %s ", container);
				}
			}

		}
	}

	public File getTarget() throws Exception {
		prepare();
		return target;
	}

	/**
	 * This is the external method that will pre-build any dependencies if it is
	 * out of date.
	 *
	 * @param underTest
	 * @return
	 * @throws Exception
	 */
	public File[] build(boolean underTest) throws Exception {
		if (isNoBundles())
			return null;

		if (getProperty("-nope") != null) {
			warning("Please replace -nope with %s", NOBUNDLES);
			return null;
		}

		if (isStale()) {
			trace("building " + this);
			files = buildLocal(underTest);
		}

		return files;
	}

	/**
	 * Return the files
	 */

	public File[] getFiles() {
		return files;
	}

	/**
	 * Check if this project needs building. This is defined as:
	 */
	public boolean isStale() throws Exception {
		if (workspace == null || workspace.isOffline()) {
			trace("working %s offline, so always stale", this);
			return true;
		}

		Set<Project> visited = new HashSet<Project>();
		return isStale(visited);
	}

	boolean isStale(Set<Project> visited) throws Exception {
		// When we do not generate anything ...
		if (isNoBundles())
			return false;

		if (visited.contains(this)) {
			msgs.CircularDependencyContext_Message_(this.getName(), visited.toString());
			return false;
		}

		visited.add(this);

		long buildTime = 0;

		files = getBuildFiles(false);
		if (files == null)
			return true;

		for (File f : files) {
			if (f.lastModified() < lastModified())
				return true;

			if (buildTime < f.lastModified())
				buildTime = f.lastModified();
		}

		for (Project dependency : getDependson()) {
			if (dependency == this)
				continue;

			if (dependency.isStale())
				return true;

			if (dependency.isNoBundles())
				continue;

			File[] deps = dependency.getBuildFiles();
			for (File f : deps) {
				if (f.lastModified() >= buildTime)
					return true;
			}
		}

		return false;
	}

	/**
	 * This method must only be called when it is sure that the project has been
	 * build before in the same session. It is a bit yucky, but ant creates
	 * different class spaces which makes it hard to detect we already build it.
	 * This method remembers the files in the appropriate instance vars.
	 *
	 * @return
	 */

	public File[] getBuildFiles() throws Exception {
		return getBuildFiles(true);
	}

	public File[] getBuildFiles(boolean buildIfAbsent) throws Exception {
		if (files != null)
			return files;

		File f = new File(getTarget(), BUILDFILES);
		if (f.isFile()) {
			BufferedReader rdr = IO.reader(f);
			try {
				List<File> files = newList();
				for (String s = rdr.readLine(); s != null; s = rdr.readLine()) {
					s = s.trim();
					File ff = new File(s);
					if (!ff.isFile()) {
						// Originally we warned the user
						// but lets just rebuild. That way
						// the error is not noticed but
						// it seems better to correct,
						// See #154
						rdr.close();
						f.delete();
						break;
					}
					files.add(ff);
				}
				return this.files = files.toArray(new File[files.size()]);
			}
			finally {
				rdr.close();
			}
		}
		if (buildIfAbsent)
			return files = buildLocal(false);
		return files = null;
	}

	/**
	 * Build without doing any dependency checking. Make sure any dependent
	 * projects are built first.
	 *
	 * @param underTest
	 * @return
	 * @throws Exception
	 */
	public File[] buildLocal(boolean underTest) throws Exception {
		if (isNoBundles())
			return null;

		File bfs = new File(getTarget(), BUILDFILES);
		bfs.delete();

		files = null;
		ProjectBuilder builder = getBuilder(null);
		try {
			if (underTest)
				builder.setProperty(Constants.UNDERTEST, "true");
			Jar jars[] = builder.builds();
			File[] files = new File[jars.length];

			getInfo(builder);

			if (isOk()) {
				this.files = files;

				for (int i = 0; i < jars.length; i++) {
					Jar jar = jars[i];
					File file = saveBuild(jar);
					if (file == null) {
						getInfo(builder);
						error("Could not save %s", jar.getName());
						return this.files = null;
					}
					this.files[i] = file;
				}

				// Write out the filenames in the buildfiles file
				// so we can get them later evenin another process
				Writer fw = IO.writer(bfs);
				try {
					for (File f : files) {
						fw.append(f.getAbsolutePath());
						fw.append("\n");
					}
				}
				finally {
					fw.close();
				}
				getWorkspace().changedFile(bfs);
				return files;
			}
			return null;
		}
		finally {
			builder.close();
		}
	}

	/**
	 * Answer if this project does not have any output
	 *
	 * @return
	 */
	public boolean isNoBundles() {
		return getProperty(NOBUNDLES) != null;
	}

	public File saveBuild(Jar jar) throws Exception {
		try {
			File f = getOutputFile(jar.getBsn(), jar.getVersion());
			String msg = "";
			if (!f.exists() || f.lastModified() < jar.lastModified()) {
				reportNewer(f.lastModified(), jar);
				f.delete();
				File fp = f.getParentFile();
				if (!fp.isDirectory()) {
					if (!fp.exists() && !fp.mkdirs()) {
						throw new IOException("Could not create directory " + fp);
					}
				}
				jar.write(f);

				getWorkspace().changedFile(f);
			} else {
				msg = "(not modified since " + new Date(f.lastModified()) + ")";
			}
			trace(jar.getName() + " (" + f.getName() + ") " + jar.getResources().size() + " " + msg);
			return f;
		}
		finally {
			jar.close();
		}
	}

	/**
	 * Calculate the file for a JAR. The default name is bsn.jar, but this can
	 * be overridden with an
	 *
	 * @param jar
	 * @return
	 * @throws Exception
	 */
	public File getOutputFile(String bsn, String version) throws Exception {
		if (version == null)
			version = "0";
		Processor scoped = new Processor(this);
		try {
			scoped.setProperty("@bsn", bsn);
			scoped.setProperty("@version", version.toString());
			String path = scoped.getProperty(OUTPUTMASK, bsn + ".jar");
			return IO.getFile(getTarget(), path);
		}
		finally {
			scoped.close();
		}
	}

	public File getOutputFile(String bsn) throws Exception {
		return getOutputFile(bsn, "0.0.0");
	}

	private void reportNewer(long lastModified, Jar jar) {
		if (isTrue(getProperty(Constants.REPORTNEWER))) {
			StringBuilder sb = new StringBuilder();
			String del = "Newer than " + new Date(lastModified);
			for (Map.Entry<String,Resource> entry : jar.getResources().entrySet()) {
				if (entry.getValue().lastModified() > lastModified) {
					sb.append(del);
					del = ", \n     ";
					sb.append(entry.getKey());
				}
			}
			if (sb.length() > 0)
				warning(sb.toString());
		}
	}

	/**
	 * Refresh if we are based on stale data. This also implies our workspace.
	 */
	@Override
	public boolean refresh() {
		synchronized (versionMap) {
			versionMap.clear();
		}
		boolean changed = false;
		if (isCnf()) {
			changed = workspace.refresh();
		}
		return super.refresh() || changed;
	}

	public boolean isCnf() {
		try {
			return getBase().getCanonicalPath().equals(getWorkspace().buildDir.getCanonicalPath());
		}
		catch (IOException e) {
			return false;
		}
	}

	@Override
	public void propertiesChanged() {
		super.propertiesChanged();
		preparedPaths = false;
		files = null;

	}

	public String getName() {
		return getBase().getName();
	}

	public Map<String,Action> getActions() {
		Map<String,Action> all = newMap();
		Map<String,Action> actions = newMap();
		fillActions(all);
		getWorkspace().fillActions(all);

		for (Map.Entry<String,Action> action : all.entrySet()) {
			String key = getReplacer().process(action.getKey());
			if (key != null && key.trim().length() != 0)
				actions.put(key, action.getValue());
		}
		return actions;
	}

	public void fillActions(Map<String,Action> all) {
		List<NamedAction> plugins = getPlugins(NamedAction.class);
		for (NamedAction a : plugins)
			all.put(a.getName(), a);

		Parameters actions = new Parameters(getProperty("-actions", DEFAULT_ACTIONS));
		for (Entry<String,Attrs> entry : actions.entrySet()) {
			String key = Processor.removeDuplicateMarker(entry.getKey());
			Action action;

			if (entry.getValue().get("script") != null) {
				// TODO check for the type
				action = new ScriptAction(entry.getValue().get("type"), entry.getValue().get("script"));
			} else {
				action = new ReflectAction(key);
			}
			String label = entry.getValue().get("label");
			all.put(label.toLowerCase(), action);
		}
	}

	public void release() throws Exception {
		release(false);
	}

	@SuppressWarnings("resource")
	public void export(String runFilePath, boolean keep, File output) throws Exception {
		prepare();

		OutputStream outStream = null;
		try {
			Project packageProject;
			if (runFilePath == null || runFilePath.length() == 0 || ".".equals(runFilePath)) {
				packageProject = this;
			} else {
				File runFile = new File(getBase(), runFilePath);
				if (!runFile.isFile())
					throw new IOException(String.format("Run file %s does not exist (or is not a file).",
							runFile.getAbsolutePath()));
				packageProject = new Project(getWorkspace(), getBase(), runFile);
				packageProject.setParent(this);
			}

			packageProject.clear();
			ProjectLauncher launcher = packageProject.getProjectLauncher();
			launcher.setKeep(keep);
			Jar jar = launcher.executable();

			outStream = new FileOutputStream(output);
			jar.write(outStream);
		}
		finally {
			IO.close(outStream);
		}
	}

	/**
	 * Release.
	 *
	 * @param name
	 *            The repository name
	 * @throws Exception
	 */
	public void release(String name) throws Exception {
		release(name, false);
	}

	public void clean() throws Exception {
		clean(getTarget(), "target");
		clean(getSrcOutput(), "source output");
		clean(getTestOutput(), "test output");
		clean(getOutput(), "output");
	}

	void clean(File dir, String type) throws IOException {
		if ( !dir.exists())
			return;

		String basePath = getBase().getCanonicalPath();
		String dirPath = dir.getCanonicalPath();
		if ( ! dirPath.startsWith(basePath)) {
			trace("path outside the project dir %s", type);
			return;
		}

		if ( dirPath.length() == basePath.length())  {
			error("Trying to delete the project directory for %s", type);
			return;
		}

		IO.delete(dir);
		if ( dir.exists()) {
			error("Trying to delete %s (%s), but failed", dir, type);
			return;
		}

		dir.mkdirs();
	}

	public File[] build() throws Exception {
		return build(false);
	}

	public void run() throws Exception {
		ProjectLauncher pl = getProjectLauncher();
		pl.setTrace(isTrace() || isTrue(getProperty(RUNTRACE)));
		pl.launch();
	}

	public void runLocal() throws Exception {
		ProjectLauncher pl = getProjectLauncher();
		pl.setTrace(isTrace() || isTrue(getProperty(RUNTRACE)));
		pl.start(null);
	}

	public void test() throws Exception {
		test(null);
	}

	public void test(List<String> tests) throws Exception {

		String testcases = getProperties().getProperty(Constants.TESTCASES);
		if (testcases == null) {
			warning("No %s set", Constants.TESTCASES);
			return;
		}
		clear();

		ProjectTester tester = getProjectTester();
		if ( tests != null) {
			trace("Adding tests %s", tests);
			for ( String test : tests) {
				tester.addTest(test);
			}
		}
		tester.setContinuous(isTrue(getProperty(Constants.TESTCONTINUOUS)));
		tester.prepare();

		if (!isOk()) {
			return;
		}
		int errors = tester.test();
		if (errors == 0) {
			System.err.println("No Errors");
		} else {
			if (errors > 0) {
				System.err.println(errors + " Error(s)");

			} else
				System.err.println("Error " + errors);
		}
	}

	/**
	 * Run JUnit
	 * @throws Exception
	 */
	public void junit() throws Exception {
		JUnitLauncher launcher = new JUnitLauncher(this);
		launcher.launch();
	}

	/**
	 * This methods attempts to turn any jar into a valid jar. If this is a
	 * bundle with manifest, a manifest is added based on defaults. If it is a
	 * bundle, but not r4, we try to add the r4 headers.
	 *
	 * @param descriptor
	 * @param in
	 * @return
	 * @throws Exception
	 */
	public Jar getValidJar(File f) throws Exception {
		Jar jar = new Jar(f);
		return getValidJar(jar, f.getAbsolutePath());
	}

	public Jar getValidJar(URL url) throws Exception {
		InputStream in = url.openStream();
		try {
			Jar jar = new Jar(url.getFile().replace('/', '.'), in, System.currentTimeMillis());
			return getValidJar(jar, url.toString());
		}
		finally {
			in.close();
		}
	}

	public Jar getValidJar(Jar jar, String id) throws Exception {
		Manifest manifest = jar.getManifest();
		if (manifest == null) {
			trace("Wrapping with all defaults");
			Builder b = new Builder(this);
			this.addClose(b);
			b.addClasspath(jar);
			b.setProperty("Bnd-Message", "Wrapped from " + id + "because lacked manifest");
			b.setProperty(Constants.EXPORT_PACKAGE, "*");
			b.setProperty(Constants.IMPORT_PACKAGE, "*;resolution:=optional");
			jar = b.build();
		} else if (manifest.getMainAttributes().getValue(Constants.BUNDLE_MANIFESTVERSION) == null) {
			trace("Not a release 4 bundle, wrapping with manifest as source");
			Builder b = new Builder(this);
			this.addClose(b);
			b.addClasspath(jar);
			b.setProperty(Constants.PRIVATE_PACKAGE, "*");
			b.mergeManifest(manifest);
			String imprts = manifest.getMainAttributes().getValue(Constants.IMPORT_PACKAGE);
			if (imprts == null)
				imprts = "";
			else
				imprts += ",";
			imprts += "*;resolution=optional";

			b.setProperty(Constants.IMPORT_PACKAGE, imprts);
			b.setProperty("Bnd-Message", "Wrapped from " + id + "because had incomplete manifest");
			jar = b.build();
		}
		return jar;
	}

	public String _project(@SuppressWarnings("unused")
	String args[]) {
		return getBase().getAbsolutePath();
	}

	/**
	 * Bump the version of this project. First check the main bnd file. If this
	 * does not contain a version, check the include files. If they still do not
	 * contain a version, then check ALL the sub builders. If not, add a version
	 * to the main bnd file.
	 *
	 * @param mask
	 *            the mask for bumping, see {@link Macro#_version(String[])}
	 * @throws Exception
	 */
	public void bump(String mask) throws Exception {
		String pattern = "(" + Constants.BUNDLE_VERSION + "\\s*(:|=)\\s*)(([0-9]+(\\.[0-9]+(\\.[0-9]+)?)?))";
		String replace = "$1${version;" + mask + ";$3}";
		try {
			// First try our main bnd file
			if (replace(getPropertiesFile(), pattern, replace))
				return;

			trace("no version in bnd.bnd");

			// Try the included filed in reverse order (last has highest
			// priority)
			List<File> included = getIncluded();
			if (included != null) {
				List<File> copy = new ArrayList<File>(included);
				Collections.reverse(copy);

				for (File file : copy) {
					if (replace(file, pattern, replace)) {
						trace("replaced version in file %s", file);
						return;
					}
				}
			}
			trace("no version in included files");

			boolean found = false;

			// Replace in all sub builders.
			for (Builder sub : getSubBuilders()) {
				found |= replace(sub.getPropertiesFile(), pattern, replace);
			}

			if (!found) {
				trace("no version in sub builders, add it to bnd.bnd");
				String bndfile = IO.collect(getPropertiesFile());
				bndfile += "\n# Added by by bump\n" + Constants.BUNDLE_VERSION + ": 0.0.0\n";
				IO.store(bndfile, getPropertiesFile());
			}
		}
		finally {
			forceRefresh();
		}
	}

	boolean replace(File f, String pattern, String replacement) throws IOException {
		final Macro macro = getReplacer();
		Sed sed = new Sed(new Replacer() {
			public String process(String line) {
				return macro.process(line);
			}
		}, f);
		sed.replace(pattern, replacement);
		return sed.doIt() > 0;
	}

	public void bump() throws Exception {
		bump(getProperty(BUMPPOLICY, "=+0"));
	}

	public void action(String command) throws Exception {
		action(command, new Object[0]);
	}

	public void action(String command, Object... args) throws Exception {
		Map<String,Action> actions = getActions();

		Action a = actions.get(command);
		if (a == null)
			a = new ReflectAction(command);

		before(this, command);
		try {
			if ( args.length == 0)
				a.execute(this, command);
			else
				a.execute(this, args);
		}
		catch (Exception t) {
			after(this, command, t);
			throw t;
		}
	}

	/**
	 * Run all before command plugins
	 */
	void before(@SuppressWarnings("unused")
	Project p, String a) {
		List<CommandPlugin> testPlugins = getPlugins(CommandPlugin.class);
		for (CommandPlugin testPlugin : testPlugins) {
			testPlugin.before(this, a);
		}
	}

	/**
	 * Run all after command plugins
	 */
	void after(@SuppressWarnings("unused")
	Project p, String a, Throwable t) {
		List<CommandPlugin> testPlugins = getPlugins(CommandPlugin.class);
		for (int i = testPlugins.size() - 1; i >= 0; i--) {
			testPlugins.get(i).after(this, a, t);
		}
	}

	public String _findfile(String args[]) {
		File f = getFile(args[1]);
		List<String> files = new ArrayList<String>();
		tree(files, f, "", new Instruction(args[2]));
		return join(files);
	}

	void tree(List<String> list, File current, String path, Instruction instr) {
		if (path.length() > 0)
			path = path + "/";

		String subs[] = current.list();
		if (subs != null) {
			for (String sub : subs) {
				File f = new File(current, sub);
				if (f.isFile()) {
					if (instr.matches(sub) && !instr.isNegated())
						list.add(path + sub);
				} else
					tree(list, f, path + sub, instr);
			}
		}
	}

	public void refreshAll() {
		workspace.refresh();
		refresh();
	}

	@SuppressWarnings("unchecked")
	public void script(@SuppressWarnings("unused")
	String type, String script) throws Exception {
		script(type, script, new Object[0]);
	}

	@SuppressWarnings({
			"unchecked", "rawtypes"
	})
	public void script(String type, String script, Object... args) throws Exception {
		// TODO check tyiping
		List<Scripter> scripters = getPlugins(Scripter.class);
		if (scripters.isEmpty()) {
			msgs.NoScripters_(script);
			return;
		}

		Properties p = new UTF8Properties(getProperties());

		for (int i = 0; i < args.length; i++)
			p.setProperty("" + i, Converter.cnv(String.class, args[i]));
		scripters.get(0).eval((Map) p, new StringReader(script));
	}

	public String _repos(@SuppressWarnings("unused")
	String args[]) throws Exception {
		List<RepositoryPlugin> repos = getPlugins(RepositoryPlugin.class);
		List<String> names = new ArrayList<String>();
		for (RepositoryPlugin rp : repos)
			names.add(rp.getName());
		return join(names, ", ");
	}

	public String _help(String args[]) throws Exception {
		if (args.length == 1)
			return "Specify the option or header you want information for";

		Syntax syntax = Syntax.HELP.get(args[1]);
		if (syntax == null)
			return "No help for " + args[1];

		String what = null;
		if (args.length > 2)
			what = args[2];

		if (what == null || what.equals("lead"))
			return syntax.getLead();
		if (what.equals("example"))
			return syntax.getExample();
		if (what.equals("pattern"))
			return syntax.getPattern();
		if (what.equals("values"))
			return syntax.getValues();

		return "Invalid type specified for help: lead, example, pattern, values";
	}

	/**
	 * Returns containers for the deliverables of this project. The deliverables
	 * is the project builder for this project if no -sub is specified.
	 * Otherwise it contains all the sub bnd files.
	 *
	 * @return A collection of containers
	 * @throws Exception
	 */
	public Collection<Container> getDeliverables() throws Exception {
		List<Container> result = new ArrayList<Container>();
		Collection< ? extends Builder> builders = getSubBuilders();

		for (Builder builder : builders) {
			Container c = new Container(this, builder.getBsn(), builder.getVersion(), Container.TYPE.PROJECT,
					getOutputFile(builder.getBsn()), null, null, null);
			result.add(c);
		}
		return result;

	}

	/**
	 * Return the builder associated with the give bnd file or null. The bnd.bnd
	 * file can contain -sub option. This option allows specifying files in the
	 * same directory that should drive the generation of multiple deliverables.
	 * This method figures out if the bndFile is actually one of the bnd files
	 * of a deliverable.
	 *
	 * @param bndFile
	 *            A file pointing to a bnd file.
	 * @return null or the builder for a sub file.
	 * @throws Exception
	 */
	public Builder getSubBuilder(File bndFile) throws Exception {
		bndFile = bndFile.getCanonicalFile();

		// Verify that we are inside the project.
		File base = getBase().getCanonicalFile();
		if (!bndFile.getAbsolutePath().startsWith(base.getAbsolutePath()))
			return null;

		Collection< ? extends Builder> builders = getSubBuilders();
		for (Builder sub : builders) {
			File propertiesFile = sub.getPropertiesFile();
			if (propertiesFile != null) {
				if (propertiesFile.getCanonicalFile().equals(bndFile)) {
					// Found it!
					return sub;
				}
			}
		}
		return null;
	}

	/**
	 * Return a build that maps to the sub file.
	 *
	 * @param string
	 * @throws Exception
	 */
	public ProjectBuilder getSubBuilder(String string) throws Exception {
		Collection< ? extends Builder> builders = getSubBuilders();
		for (Builder b : builders) {
			if (b.getBsn().equals(string) || b.getBsn().endsWith("." + string))
				return (ProjectBuilder) b;
		}
		return null;
	}

	/**
	 * Answer the container associated with a given bsn.
	 *
	 * @param bndFile
	 *            A file pointing to a bnd file.
	 * @return null or the builder for a sub file.
	 * @throws Exception
	 */
	public Container getDeliverable(String bsn, @SuppressWarnings("unused")
	Map<String,String> attrs) throws Exception {
		Collection< ? extends Builder> builders = getSubBuilders();
		for (Builder sub : builders) {
			if (sub.getBsn().equals(bsn))
				return new Container(this, getOutputFile(bsn, sub.getVersion()));
		}
		return null;
	}

	/**
	 * Get a list of the sub builders. A bnd.bnd file can contain the -sub
	 * option. This will generate multiple deliverables. This method returns the
	 * builders for each sub file. If no -sub option is present, the list will
	 * contain a builder for the bnd.bnd file.
	 *
	 * @return A list of builders.
	 * @throws Exception
	 */
	public Collection< ? extends Builder> getSubBuilders() throws Exception {
		return getBuilder(null).getSubBuilders();
	}

	/**
	 * Calculate the classpath. We include our own runtime.jar which includes
	 * the test framework and we include the first of the test frameworks
	 * specified.
	 *
	 * @throws Exception
	 */
	Collection<File> toFile(Collection<Container> containers) throws Exception {
		ArrayList<File> files = new ArrayList<File>();
		for (Container container : containers) {
			container.contributeFiles(files, this);
		}
		return files;
	}

	public Collection<String> getRunVM() {
		Parameters hdr = getParameters(RUNVM);
		return hdr.keySet();
	}

	public Collection<String> getRunProgramArgs() {
		Parameters hdr = getParameters(RUNPROGRAMARGS);
		return hdr.keySet();
	}

	public Map<String,String> getRunProperties() {
		return OSGiHeader.parseProperties(getProperty(RUNPROPERTIES));
	}

	/**
	 * Get a launcher.
	 *
	 * @return
	 * @throws Exception
	 */
	public ProjectLauncher getProjectLauncher() throws Exception {
		return getHandler(ProjectLauncher.class, getRunpath(), LAUNCHER_PLUGIN, "biz.aQute.launcher");
	}

	public ProjectTester getProjectTester() throws Exception {
		return getHandler(ProjectTester.class, getTestpath(), TESTER_PLUGIN, "biz.aQute.junit");
	}

	private <T> T getHandler(Class<T> target, Collection<Container> containers, String header, String defaultHandler)
			throws Exception {
		Class< ? extends T> handlerClass = target;

		// Make sure we find at least one handler, but hope to find an earlier
		// one
		List<Container> withDefault = Create.list();
		withDefault.addAll(containers);
		withDefault.addAll(getBundles(Strategy.HIGHEST, defaultHandler, null));
		trace("candidates for tester %s", withDefault);

		for (Container c : withDefault) {
			Manifest manifest = c.getManifest();

			if (manifest != null) {
				String launcher = manifest.getMainAttributes().getValue(header);
				if (launcher != null) {
					Class< ? > clz = getClass(launcher, c.getFile());
					if (clz != null) {
						if (!target.isAssignableFrom(clz)) {
							msgs.IncompatibleHandler_For_(launcher, defaultHandler);
						} else {
							trace("found handler %s from %s", defaultHandler, c);
							handlerClass = clz.asSubclass(target);
							Constructor< ? extends T> constructor = handlerClass.getConstructor(Project.class);
							return constructor.newInstance(this);
						}
					}
				}
			}
		}

		throw new IllegalArgumentException("Default handler for " + header + " not found in " + defaultHandler);
	}

	/**
	 * Make this project delay the calculation of the run dependencies. The run
	 * dependencies calculation can be done in prepare or until the dependencies
	 * are actually needed.
	 */
	public void setDelayRunDependencies(boolean x) {
		delayRunDependencies = x;
	}

	/**
	 * Sets the package version on an exported package
	 *
	 * @param packageName
	 *            The package name
	 * @param version
	 *            The new package version
	 */
	public void setPackageInfo(String packageName, Version version) {
		try {
			Version current = getPackageInfoJavaVersion(packageName);
			boolean packageInfoJava = false;
			if (current != null) {
				updatePackageInfoJavaFile(packageName, version);
				packageInfoJava = true;
			}
			if (!packageInfoJava || getPackageInfoFile(packageName).exists()) {
				updatePackageInfoFile(packageName, version);
			}
		}
		catch (Exception e) {
			msgs.SettingPackageInfoException_(e);
		}
	}

	void updatePackageInfoJavaFile(String packageName, final Version newVersion) throws Exception {
		File file = getPackageInfoJavaFile(packageName);

		if (!file.exists()) {
			return;
		}

		// If package/classes are copied into the bundle through Private-Package
		// etc, there will be no source
		if (!file.getParentFile().exists()) {
			return;
		}

		Version oldVersion = getPackageInfo(packageName);

		if (newVersion.compareTo(oldVersion) == 0) {
			return;
		}

		Sed sed = new Sed(new Replacer() {
			public String process(String line) {
				Matcher m = VERSION_ANNOTATION.matcher(line);
				if (m.find()) {
					return line.substring(0, m.start(3)) + newVersion.toString() + line.substring(m.end(3));
				}
				return line;
			}
		}, file);

		sed.replace(VERSION_ANNOTATION.pattern(), "$0");
		sed.setBackup(false);
		sed.doIt();
	}

	void updatePackageInfoFile(String packageName, Version newVersion) throws Exception {

		File file = getPackageInfoFile(packageName);

		// If package/classes are copied into the bundle through Private-Package
		// etc, there will be no source
		if (!file.getParentFile().exists()) {
			return;
		}

		Version oldVersion = getPackageInfoVersion(packageName);
		if (oldVersion == null) {
			oldVersion = Version.emptyVersion;
		}

		if (newVersion.compareTo(oldVersion) == 0) {
			return;
		}
		PrintWriter pw = IO.writer(file);
		pw.println("version " + newVersion);
		pw.flush();
		pw.close();

		String path = packageName.replace('.', '/') + "/packageinfo";
		File binary = IO.getFile(getOutput(), path);
		File bp = binary.getParentFile();
		if (!bp.exists() && !bp.mkdirs()) {
			throw new IOException("Could not create directory " + bp);
		}
		IO.copy(file, binary);
	}

	File getPackageInfoFile(String packageName) {
		String path = packageName.replace('.', '/') + "/packageinfo";
		return IO.getFile(getSrc(), path);

	}

	File getPackageInfoJavaFile(String packageName) {
		String path = packageName.replace('.', '/') + "/package-info.java";
		return IO.getFile(getSrc(), path);

	}

	public Version getPackageInfo(String packageName) throws IOException {

		Version version = getPackageInfoJavaVersion(packageName);
		if (version != null) {
			return version;
		}

		version = getPackageInfoVersion(packageName);
		if (version != null) {
			return version;
		}

		return Version.emptyVersion;
	}

	Version getPackageInfoVersion(String packageName) throws IOException {
		File packageInfoFile = getPackageInfoFile(packageName);
		if (!packageInfoFile.exists()) {
			return null;
		}

		BufferedReader reader = IO.reader(packageInfoFile);
		try {
			String line;
			while ((line = reader.readLine()) != null) {
				line = line.trim();
				if (line.startsWith("version ")) {
					return Version.parseVersion(line.substring(8));
				}
			}
		}
		finally {
			IO.close(reader);
		}
		return null;
	}

	Version getPackageInfoJavaVersion(String packageName) throws IOException {
		File packageInfoJavaFile = getPackageInfoJavaFile(packageName);
		if (!packageInfoJavaFile.exists()) {
			return null;
		}
		BufferedReader reader = null;
		reader = IO.reader(packageInfoJavaFile);
		try {
			String line;
			while ((line = reader.readLine()) != null) {
				Matcher matcher = VERSION_ANNOTATION.matcher(line);
				if (matcher.find()) {
					return Version.parseVersion(matcher.group(3));
				}
			}
		}
		finally {
			IO.close(reader);
		}
		return null;
	}

	/**
	 * bnd maintains a class path that is set by the environment, i.e. bnd is
	 * not in charge of it.
	 */

	public void addClasspath(File f) {
		if (!f.isFile() && !f.isDirectory()) {
			msgs.AddingNonExistentFileToClassPath_(f);
		}
		Container container = new Container(f, null);
		classpath.add(container);
	}

	public void clearClasspath() {
		classpath.clear();
	}

	public Collection<Container> getClasspath() {
		return classpath;
	}

	/**
	 * Pack the project (could be a bndrun file) and save it on disk. Report
	 * errors if they happen.
	 */
	static List<String>	ignore	= new ExtList<String>(BUNDLE_SPECIFIC_HEADERS);

	public Jar pack(String profile) throws Exception {
		Collection< ? extends Builder> subBuilders = getSubBuilders();

		if (subBuilders.size() != 1) {
			error("Project has multiple bnd files, please select one of the bnd files");
			return null;
		}

		Builder b = subBuilders.iterator().next();

		ignore.remove(BUNDLE_SYMBOLICNAME);
		ignore.remove(BUNDLE_VERSION);
		ignore.add(SERVICE_COMPONENT);

		ProjectLauncher launcher = getProjectLauncher();
		launcher.getRunProperties().put("profile", profile); // TODO remove
		launcher.getRunProperties().put(PROFILE, profile);
		Jar jar = launcher.executable();
		Manifest m = jar.getManifest();
		Attributes main = m.getMainAttributes();
		for (String key : getPropertyKeys(true)) {
			if (Character.isUpperCase(key.charAt(0)) && !ignore.contains(key)) {
				main.putValue(key, getProperty(key));
			}
		}

		if (main.getValue(BUNDLE_SYMBOLICNAME) == null)
			main.putValue(BUNDLE_SYMBOLICNAME, b.getBsn());

		if (main.getValue(BUNDLE_SYMBOLICNAME) == null)
			main.putValue(BUNDLE_SYMBOLICNAME, getName());

		if (main.getValue(BUNDLE_VERSION) == null) {
			main.putValue(BUNDLE_VERSION, Version.LOWEST.toString());
			warning("No version set, uses 0.0.0");
		}

		jar.setManifest(m);
		jar.calcChecksums(new String[] {
				"SHA1", "MD5"
		});
		return jar;
	}

	/**
	 * Do a baseline for this project
	 *
	 * @throws Exception
	 */

	public void baseline() throws Exception {
		ProjectBuilder b = getBuilder(null);
		for (Builder pb : b.getSubBuilders()) {
			ProjectBuilder ppb = (ProjectBuilder) pb;
			Jar build = ppb.build();
			getInfo(ppb);
		}
		getInfo(b);
	}

	/**
	 * Method to verify that the paths are correct, ie no missing dependencies
	 *
	 * @param test
	 *            for test cases, also adds -testpath
	 * @throws Exception
	 */
	public void verifyDependencies(boolean test) throws Exception {
		verifyDependencies(RUNBUNDLES, getRunbundles());
		verifyDependencies(RUNPATH, getRunpath());
		if (test)
			verifyDependencies(TESTPATH, getTestpath());
		verifyDependencies(BUILDPATH, getBuildpath());
	}

	private void verifyDependencies(String title, Collection<Container> path) throws Exception {
		List<String> msgs = new ArrayList<String>();
		for (Container c : new ArrayList<Container>(path)) {
			for (Container cc : c.getMembers()) {
				if (cc.getError() != null)
					msgs.add(cc + " - " + cc.getError());
				else if (!cc.getFile().isFile() && !cc.getFile().equals(cc.getProject().getOutput())
						&& !cc.getFile().equals(cc.getProject().getTestOutput()))
					msgs.add(cc + " file does not exists: " + cc.getFile());
			}
		}
		if (msgs.isEmpty())
			return;

		error("%s: has errors: %s", title, Strings.join(msgs));
	}

	/**
	 * Report detailed info from this project
	 *
	 * @throws Exception
	 */

	public void report(Map<String,Object> table) throws Exception {
		super.report(table);
		report(table, true);
	}

	protected void report(Map<String,Object> table, boolean isProject) throws Exception {
		if (isProject) {
			table.put("Target", getTarget());
			table.put("Source", getSrc());
			table.put("Output", getOutput());
			File[] buildFiles = getBuildFiles();
			if (buildFiles != null)
				table.put("BuildFiles", Arrays.asList(buildFiles));
			table.put("Classpath", getClasspath());
			table.put("Actions", getActions());
			table.put("AllSourcePath", getAllsourcepath());
			table.put("BootClassPath", getBootclasspath());
			table.put("BuildPath", getBuildpath());
			table.put("Deliverables", getDeliverables());
			table.put("DependsOn", getDependson());
			table.put("SourcePath", getSourcePath());
		}
		table.put("RunPath", getRunpath());
		table.put("TestPath", getTestpath());
		table.put("RunProgramArgs", getRunProgramArgs());
		table.put("RunVM", getRunVM());
		table.put("Runfw", getRunFw());
		table.put("Runbundles", getRunbundles());
	}

	// TODO test format parametsr

	public void compile(boolean test) throws Exception {

		Command javac = getCommonJavac(false);
		javac.add("-d", getOutput().getAbsolutePath());

		StringBuilder buildpath = new StringBuilder();

		String buildpathDel = "";
		Collection<Container> bp = Container.flatten(getBuildpath());
		trace("buildpath %s", getBuildpath());
		for (Container c : bp) {
			buildpath.append(buildpathDel).append(c.getFile().getAbsolutePath());
			buildpathDel = File.pathSeparator;
		}

		if (buildpath.length() != 0) {
			javac.add("-classpath", buildpath.toString());
		}

		List<File> sp = new ArrayList<File>(getAllsourcepath());
		StringBuilder sourcepath = new StringBuilder();
		String sourcepathDel = "";

		for (File sourceDir : sp) {
			sourcepath.append(sourcepathDel).append(sourceDir.getAbsolutePath());
			sourcepathDel = File.pathSeparator;
		}

		javac.add("-sourcepath", sourcepath.toString());

		Glob javaFiles = new Glob("*.java");
		List<File> files = javaFiles.getFiles(getSrc(), true, false);

		for (File file : files) {
			javac.add(file.getAbsolutePath());
		}

		if (files.isEmpty()) {
			trace("Not compiled, no source files");
		} else
			compile(javac, "src");

		if (test) {
			javac = getCommonJavac(true);
			javac.add("-d", getTestOutput().getAbsolutePath());

			Collection<Container> tp = Container.flatten(getTestpath());
			for (Container c : tp) {
				buildpath.append(buildpathDel).append(c.getFile().getAbsolutePath());
				buildpathDel = File.pathSeparator;
			}
			if (buildpath.length() != 0) {
				javac.add("-classpath", buildpath.toString());
			}

			sourcepath.append(sourcepathDel).append(getTestSrc().getAbsolutePath());
			javac.add("-sourcepath", sourcepath.toString());

			javaFiles.getFiles(getTestSrc(), files, true, false);
			for (File file : files) {
				javac.add(file.getAbsolutePath());
			}
			if (files.isEmpty()) {
				trace("Not compiled for test, no test src files");
			} else
				compile(javac, "test");
		}
	}

	private void compile(Command javac, String what) throws Exception {
		trace("compile %s %s", what, javac);

		StringBuilder stdout = new StringBuilder();
		StringBuilder stderr = new StringBuilder();

		int n = javac.execute(stdout, stderr);
		trace("javac stdout: ", stdout);
		trace("javac stderr: ", stderr);

		if (n != 0) {
			error("javac failed %s", stderr);
		}
	}

	private Command getCommonJavac(boolean test) throws Exception {
		Command javac = new Command();
		javac.add(getProperty("javac", "javac"));
		String target = getProperty("javac.target", "1.6");
		String source = getProperty("javac.source", "1.6");
		String debug = getProperty("javac.debug");
		if ("on".equalsIgnoreCase(debug) || "true".equalsIgnoreCase(debug))
			debug = "vars,source,lines";

		Parameters options = new Parameters(getProperty("java.options"));

		boolean deprecation = isTrue(getProperty("java.deprecation"));

		javac.add("-encoding", "UTF-8");

		javac.add("-source", source);

		javac.add("-target", target);

		if (deprecation)
			javac.add("-deprecation");

		if (test || debug == null) {
			javac.add("-g:source,lines,vars" + debug);
		} else {
			javac.add("-g:" + debug);
		}

		for (String option : options.keySet())
			javac.add(option);

		StringBuilder bootclasspath = new StringBuilder();
		String bootclasspathDel = "-Xbootclasspath/p:";

		Collection<Container> bcp = Container.flatten(getBootclasspath());
		for (Container c : bcp) {
			bootclasspath.append(bootclasspathDel).append(c.getFile().getAbsolutePath());
			bootclasspathDel = File.pathSeparator;
		}

		if (bootclasspath.length() != 0) {
			javac.add(bootclasspath.toString());
		}
		return javac;
	}

	public String _ide(String[] args) throws IOException {
		if (args.length < 2) {
			error("The ${ide;<>} macro needs an argument");
			return null;
		}
		if (ide == null) {
			ide = new UTF8Properties();
			File file = getFile(".settings/org.eclipse.jdt.core.prefs");
			if (!file.isFile()) {
				error("The ${ide;<>} macro requires a .settings/org.eclipse.jdt.core.prefs file in the project");
				return null;
			}
			FileInputStream in = new FileInputStream(file);
			ide.load(in);
		}
		
		String deflt = args.length > 2 ? args[2] : null;
		if ("javac.target".equals(args[1])) {
			return ide.getProperty("org.eclipse.jdt.core.compiler.codegen.targetPlatform", deflt);
		}
		if ("javac.source".equals(args[1])) {
			return ide.getProperty("org.eclipse.jdt.core.compiler.source", deflt);
		}
		return null;
	}

	public Map<String, Version> getVersions() throws Exception {
		synchronized (versionMap) {
			if (versionMap.isEmpty()) {
				for (Builder builder : getSubBuilders()) {
					String v = builder.getVersion();
					if (v == null)
						v = "0";
					else {
						v = Analyzer.cleanupVersion(v);
						if (!Verifier.isVersion(v))
							continue; // skip
					}

					Version version = new Version(v);
					versionMap.put(builder.getBsn(), version);
				}
			}
			return new LinkedHashMap<String, Version>(versionMap);
		}
	}

	public Collection<String> getBsns() throws Exception {
		return new ArrayList<String>(getVersions().keySet());
	}

	public Version getVersion(String bsn) throws Exception {
		Version version = getVersions().get(bsn);
		if (version == null) {
			throw new IllegalArgumentException("Bsn " + bsn + " does not exist in project " + getName());
		}
		return version;
	}

	/**
	 * Get the exported packages form all builders calculated from the last
	 * build
	 */

	public Packages getExports() {
		return exportedPackages;
	}

	/**
	 * Get the imported packages from all builders calculated from the last
	 * build
	 */

	public Packages getImports() {
		return importedPackages;
	}

	/**
	 * Get the contained packages calculated from all builders from the last
	 * build
	 */

	public Packages getContained() {
		return containedPackages;
	}

	public void remove() throws Exception {
		getWorkspace().removeProject(this);
		IO.delete(getBase());
	}
}
