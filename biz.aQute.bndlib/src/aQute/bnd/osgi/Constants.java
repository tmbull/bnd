package aQute.bnd.osgi;

import java.nio.charset.*;
import java.util.*;
import java.util.regex.*;

public interface Constants {
	/*
	 * Defined in OSGi
	 */
	/**
	 * @syntax Bundle-ActivationPolicy ::= policy ( ’;’ directive )* policy ::=
	 *         ’lazy’
	 */
	String							BND_ADDXMLTOTEST							= "Bnd-AddXMLToTest";
	String							BUNDLE_ACTIVATIONPOLICY						= "Bundle-ActivationPolicy";
	String							BUNDLE_ACTIVATOR							= "Bundle-Activator";
	String							BUNDLE_BLUEPRINT							= "Bundle-Blueprint";
	String							BUNDLE_CATEGORY								= "Bundle-Category";
	String							BUNDLE_CLASSPATH							= "Bundle-ClassPath";
	String							BUNDLE_CONTACTADDRESS						= "Bundle-ContactAddress";
	String							BUNDLE_COPYRIGHT							= "Bundle-Copyright";
	String							BUNDLE_DESCRIPTION							= "Bundle-Description";
	String							BUNDLE_DOCURL								= "Bundle-DocURL";
	String							BUNDLE_ICON									= "Bundle-Icon";
	String							BUNDLE_LICENSE								= "Bundle-License";
	String							BUNDLE_LOCALIZATION							= "Bundle-Localization";
	String							BUNDLE_MANIFESTVERSION						= "Bundle-ManifestVersion";
	String							BUNDLE_NAME									= "Bundle-Name";
	String							BUNDLE_NATIVECODE							= "Bundle-NativeCode";
	String							BUNDLE_REQUIREDEXECUTIONENVIRONMENT			= "Bundle-RequiredExecutionEnvironment";
	String							BUNDLE_SYMBOLICNAME							= "Bundle-SymbolicName";
	String							BUNDLE_UPDATELOCATION						= "Bundle-UpdateLocation";
	String							BUNDLE_VENDOR								= "Bundle-Vendor";
	String							BUNDLE_VERSION								= "Bundle-Version";
	String							BUNDLE_DEVELOPERS							= "Bundle-Developers";
	String							BUNDLE_CONTRIBUTORS							= "Bundle-Contributors";
	String							DYNAMICIMPORT_PACKAGE						= "DynamicImport-Package";
	String							EXPORT_PACKAGE								= "Export-Package";
	String							EXPORT_SERVICE								= "Export-Service";
	String							FRAGMENT_HOST								= "Fragment-Host";
	String							IMPORT_PACKAGE								= "Import-Package";
	String							IMPORT_SERVICE								= "Import-Service";
	String							META_PERSISTENCE							= "Meta-Persistence";
	String							PROVIDE_CAPABILITY							= "Provide-Capability";
	String							REQUIRE_BUNDLE								= "Require-Bundle";
	String							REQUIRE_CAPABILITY							= "Require-Capability";
	String							SERVICE_COMPONENT							= "Service-Component";

	String							PRIVATE_PACKAGE								= "Private-Package";
	String							IGNORE_PACKAGE								= "Ignore-Package";
	String							INCLUDE_RESOURCE							= "Include-Resource";
	String							CONDITIONAL_PACKAGE							= "Conditional-Package";
	String							BND_LASTMODIFIED							= "Bnd-LastModified";
	String							CREATED_BY									= "Created-By";
	String							TOOL										= "Tool";
	String							TESTCASES									= "Test-Cases";
	String							REPOSITORIES								= "Repositories";

	String							headers[]									= {
			BUNDLE_ACTIVATOR, BUNDLE_CONTACTADDRESS, BUNDLE_COPYRIGHT, BUNDLE_DESCRIPTION, BUNDLE_DOCURL,
			BUNDLE_LOCALIZATION, BUNDLE_NATIVECODE, BUNDLE_VENDOR, BUNDLE_VERSION, BUNDLE_LICENSE, BUNDLE_CLASSPATH,
			SERVICE_COMPONENT, EXPORT_PACKAGE, IMPORT_PACKAGE, BUNDLE_LOCALIZATION, BUNDLE_MANIFESTVERSION,
			BUNDLE_NAME, BUNDLE_NATIVECODE, BUNDLE_REQUIREDEXECUTIONENVIRONMENT, BUNDLE_SYMBOLICNAME, BUNDLE_VERSION,
			FRAGMENT_HOST, PRIVATE_PACKAGE, IGNORE_PACKAGE, INCLUDE_RESOURCE, REQUIRE_BUNDLE, IMPORT_SERVICE,
			EXPORT_SERVICE, CONDITIONAL_PACKAGE, BND_LASTMODIFIED, TESTCASES, REQUIRE_CAPABILITY, PROVIDE_CAPABILITY,
			BUNDLE_ICON, REPOSITORIES, META_PERSISTENCE, BUNDLE_DEVELOPERS, BUNDLE_CONTRIBUTORS
																				};

	String							BASELINE									= "-baseline";
	String							BASELINEREPO								= "-baselinerepo";

	String							BNDDRIVER									= "-bnd-driver";
	String							BNDDRIVER_BND								= "bnd";
	String							BNDDRIVER_GRADLE							= "gradle";
	String							BNDDRIVER_GRADLE_NATIVE						= "gradle_native";
	String							BNDDRIVER_ANT								= "ant";
	String							BNDDRIVER_ECLIPSE							= "eclipse";
	String							BNDDRIVER_MAVEN								= "maven";
	String							BNDDRIVER_INTELLIJ							= "intellij";
	String							BNDDRIVER_SBT								= "sbt";
	String							BNDDRIVER_OSMORC							= "osmorc";

	String							BUILDPATH									= "-buildpath";
	@Deprecated
	String							BUILDPACKAGES								= "-buildpackages";
	String							BUMPPOLICY									= "-bumppolicy";
	String							CONDUIT										= "-conduit";
	String							CONTRACT									= "-contract";
	@Deprecated
	String							CACHEDIR									= "-cachedir";
	String							CONDITIONALPACKAGE							= "-conditionalpackage";
	String							DIFFIGNORE									= "-diffignore";
	String							DEPENDSON									= "-dependson";
	String							DEPLOY										= "-deploy";
	String							DEPLOYREPO									= "-deployrepo";
	String							DIGESTS										= "-digests";
	String							DSANNOTATIONS								= "-dsannotations";
	String							DONOTCOPY									= "-donotcopy";
	@Deprecated
	String							DEBUG										= "-debug";
	@Deprecated
	String							EXPERIMENTS									= "-experiments";
	String							EXPORT_CONTENTS								= "-exportcontents";
	String							EXTENSION									= "-extension";
	String							EEPROFILE									= "-eeprofile";
	String							EXPORT										= "-export";
	String							FAIL_OK										= "-failok";
	String							FIXUPMESSAGES								= "-fixupmessages";

	String							GESTALT										= "-gestalt";
	String							GESTALT_INTERACTIVE							= "interactive";
	String							GESTALT_BATCH								= "batch";
	String							GESTALT_CI									= "ci";
	String							GESTALT_OFFLINE								= "offline";
	String							GESTALT_SHELL								= "shell";

	String							INCLUDE										= "-include";
	String							INCLUDERESOURCE								= "-includeresource";
	String							INVALIDFILENAMES							= "-invalidfilenames";
	String							JAVAAGENT									= "-javaagent";

	String							MAKE										= "-make";
	String							METATYPE									= "-metatype";
	String							METATYPE_ANNOTATIONS						= "-metatypeannotations";
	String							MANIFEST									= "-manifest";
	String							PROFILE										= "-profile";
	String							SAVEMANIFEST								= "-savemanifest";
	String							NAMESECTION									= "-namesection";
	String							NOBUILDINCACHE								= "-nobuildincache";
	String							NODEFAULTVERSION							= "-nodefaultversion";
	String							NOEXTRAHEADERS								= "-noextraheaders";
	String							NOJUNIT										= "-nojunit";
	String							NOJUNITOSGI									= "-nojunitosgi";
	String							NOEE										= "-noee";

	String							NOMANIFEST									= "-nomanifest";
	String							MANIFEST_NAME								= "-manifest-name";
	String							NOUSES										= "-nouses";
	String							NOBUNDLES									= "-nobundles";
	String							OUTPUTMASK									= "-outputmask";																																					// default
																																																													// ${@bsn}.jar
	String							PACKAGE										= "-package";
	String							PACKAGE_JPM									= "jpm";
	String							PEDANTIC									= "-pedantic";
	String							PLUGIN										= "-plugin";
	String							PLUGINPATH									= "-pluginpath";
	String							PLUGINPATH_URL_ATTR							= "url";
	String							PLUGINPATH_SHA1_ATTR						= "sha1";
	String							POM											= "-pom";
	String							PREPROCESSMATCHERS							= "-preprocessmatchers";
	String							PRIVATEPACKAGE								= "-privatepackage";
	String							RELEASEREPO									= "-releaserepo";
	String							REMOVEHEADERS								= "-removeheaders";
	String							RESOURCEONLY								= "-resourceonly";
	String							SIGNATURE_TEST								= "-signaturetest";
	String							SOURCES										= "-sources";
	String							SOURCEPATH									= "-sourcepath";
	String							STRICT										= "-strict";
	String							SUB											= "-sub";
	String							RUNNOREFERENCES								= "-runnoreferences";
	String							RUNPROPERTIES								= "-runproperties";
	String							RUNSYSTEMPACKAGES							= "-runsystempackages";
	String							RUNSYSTEMCAPABILITIES						= "-runsystemcapabilities";
	String							RUNBUNDLES									= "-runbundles";
	String							RUNREPOS									= "-runrepos";

	/**
	 * @deprecated This is for support of the legacy OBR requirement format, use
	 *             {@link #RUNREQUIRES} for new format.
	 */
	@Deprecated
	String							RUNREQUIRE									= "-runrequire";

	String							RUNBLACKLIST								= "-runblacklist";
	String							RUNREQUIRES									= "-runrequires";
	String							RUNEE										= "-runee";
	String							RUNPATH										= "-runpath";
	String							RUNSTORAGE									= "-runstorage";
	String							RUNBUILDS									= "-runbuilds";
	String							RUNVM										= "-runvm";
	String							RUNPROGRAMARGS								= "-runprogramargs";
	String							RUNTRACE									= "-runtrace";
	String							RUNFRAMEWORK								= "-runframework";
	String							RUNFW										= "-runfw";
	String							RUNTIMEOUT									= "-runtimeout";
	String							RUNJDB										= "-runjdb";
	String							RUNENV										= "-runenv";
	String							SYSTEMPROPERTIES							= "-systemproperties";
	String							SNAPSHOT									= "-snapshot";
	String							RUNFRAMEWORK_SERVICES						= "services";
	String							RUNFRAMEWORK_NONE							= "none";
	String							REPORTNEWER									= "-reportnewer";
	String							SIGN										= "-sign";
	String							TESTPACKAGES								= "-testpackages";
	String							TESTPATH									= "-testpath";
	String							TESTCONTINUOUS								= "-testcontinuous";
	String							TESTSOURCES									= "-testsources";
	String							TESTUNRESOLVED								= "-testunresolved";
	String							UNDERTEST									= "-undertest";
	String							UPTO										= "-upto";
	String							PROVIDER_POLICY								= "-provider-policy";
	String							CONSUMER_POLICY								= "-consumer-policy";
	String							WAB											= "-wab";
	String							WABLIB										= "-wablib";
	String							REQUIRE_BND									= "-require-bnd";

	// Deprecated
	String							CLASSPATH									= "-classpath";
	String							OUTPUT										= "-output";

	String							options[]									= {
			BASELINE, BUILDPATH, BUMPPOLICY, CONDUIT, CLASSPATH, CONSUMER_POLICY, DEPENDSON, DONOTCOPY,
			EXPORT_CONTENTS, FAIL_OK, INCLUDE, INCLUDERESOURCE, MAKE, MANIFEST, NOEXTRAHEADERS, NOUSES, NOBUNDLES,
			PEDANTIC, PLUGIN, POM, PROVIDER_POLICY, REMOVEHEADERS, RESOURCEONLY, SOURCES, SOURCEPATH, SOURCES,
			SOURCEPATH, SUB, RUNBUNDLES, RUNPATH, RUNSYSTEMPACKAGES, RUNSYSTEMCAPABILITIES, RUNPROPERTIES, REPORTNEWER,
			UNDERTEST, TESTPATH, TESTPACKAGES, NOMANIFEST, DEPLOYREPO, RELEASEREPO, SAVEMANIFEST, RUNVM,
			RUNPROGRAMARGS, WAB, WABLIB, RUNFRAMEWORK, RUNFW, RUNTRACE, RUNBLACKLIST, TESTCONTINUOUS, SNAPSHOT,
			NAMESECTION, DIGESTS, DSANNOTATIONS, BASELINE, BASELINEREPO, PROFILE, PACKAGE, RUNNOREFERENCES, JAVAAGENT,
			STRICT, DIFFIGNORE, CONTRACT, NOBUILDINCACHE, EXTENSION, NOJUNIT, NOJUNITOSGI, PREPROCESSMATCHERS, UPTO,
			INVALIDFILENAMES, FIXUPMESSAGES, PRIVATEPACKAGE, CONDITIONALPACKAGE, NOEE, OUTPUTMASK, TESTUNRESOLVED,
			RUNJDB, RUNENV, RUNEE, EEPROFILE, RUNREQUIRES, EXPORT, GESTALT, BNDDRIVER
																				};

	// Ignore bundle specific headers. These bundles do not make
	// a lot of sense to inherit
	String[]						BUNDLE_SPECIFIC_HEADERS						= new String[] {
			INCLUDE_RESOURCE, BUNDLE_ACTIVATOR, BUNDLE_CLASSPATH, BUNDLE_NAME, BUNDLE_NATIVECODE, BUNDLE_SYMBOLICNAME,
			IMPORT_PACKAGE, EXPORT_PACKAGE, DYNAMICIMPORT_PACKAGE, FRAGMENT_HOST, REQUIRE_BUNDLE, PRIVATE_PACKAGE,
			PRIVATEPACKAGE, EXPORT_CONTENTS, TESTCASES, NOMANIFEST, WAB, WABLIB, REQUIRE_CAPABILITY,
			PROVIDE_CAPABILITY, DSANNOTATIONS, SERVICE_COMPONENT, SIGNATURE_TEST
																				};

	char							DUPLICATE_MARKER							= '~';
	String							SPECIFICATION_VERSION						= "specification-version";
	String							SPLIT_PACKAGE_DIRECTIVE						= "-split-package:";
	String							EFFECTIVE_DIRECTIVE							= "effective:";
	String							IMPORT_DIRECTIVE							= "-import:";
	String							NO_IMPORT_DIRECTIVE							= "-noimport:";
	String							REMOVE_ATTRIBUTE_DIRECTIVE					= "-remove-attribute:";
	String							LIB_DIRECTIVE								= "lib:";
	String							NOANNOTATIONS								= "-noannotations";
	String							COMMAND_DIRECTIVE							= "command:";
	String							USES_DIRECTIVE								= "uses:";
	String							MANDATORY_DIRECTIVE							= "mandatory:";
	String							INCLUDE_DIRECTIVE							= "include:";
	String							PROVIDE_DIRECTIVE							= "provide:";
	String							EXCLUDE_DIRECTIVE							= "exclude:";
	String							FILTER_DIRECTIVE							= "filter:";
	String							PRESENCE_DIRECTIVE							= "presence:";
	String							PRIVATE_DIRECTIVE							= "private:";
	String							SINGLETON_DIRECTIVE							= "singleton:";
	String							EXTENSION_DIRECTIVE							= "extension:";
	String							VISIBILITY_DIRECTIVE						= "visibility:";
	String							FRAGMENT_ATTACHMENT_DIRECTIVE				= "fragment-attachment:";
	String							RESOLUTION_DIRECTIVE						= "resolution:";
	String							PATH_DIRECTIVE								= "path:";
	String							SIZE_ATTRIBUTE								= "size";
	String							LINK_ATTRIBUTE								= "link";
	String							LITERAL_ATTRIBUTE							= "literal";
	String							NAME_ATTRIBUTE								= "name";
	String							DESCRIPTION_ATTRIBUTE						= "description";
	String							OSNAME_ATTRIBUTE							= "osname";
	String							OSVERSION_ATTRIBUTE							= "osversion";
	String							PROCESSOR_ATTRIBUTE							= "processor";
	String							LANGUAGE_ATTRIBUTE							= "language";
	String							SELECTION_FILTER_ATTRIBUTE					= "selection-filter";
	String							BLUEPRINT_WAIT_FOR_DEPENDENCIES_ATTRIBUTE	= "blueprint.wait-for-dependencies";
	String							BLUEPRINT_TIMEOUT_ATTRIBUTE					= "blueprint.timeout";
	String							VERSION_ATTRIBUTE							= "version";
	String							BUNDLE_SYMBOLIC_NAME_ATTRIBUTE				= "bundle-symbolic-name";
	String							BUNDLE_VERSION_ATTRIBUTE					= "bundle-version";
	String							FROM_DIRECTIVE								= "from:";

	String							KEYSTORE_LOCATION_DIRECTIVE					= "keystore:";
	String							KEYSTORE_PROVIDER_DIRECTIVE					= "provider:";
	String							KEYSTORE_PASSWORD_DIRECTIVE					= "password:";
	String							SIGN_PASSWORD_DIRECTIVE						= "sign-password:";
	String							FIXUPMESSAGES_RESTRICT_DIRECTIVE			= "restrict:";
	String							FIXUPMESSAGES_REPLACE_DIRECTIVE				= "replace:";
	String							FIXUPMESSAGES_IS_DIRECTIVE					= "is:";
	String							FIXUPMESSAGES_IS_ERROR						= "error";
	String							FIXUPMESSAGES_IS_WARNING					= "warning";
	String							FIXUPMESSAGES_IS_IGNORE						= "ignore";
	String							EEPROFILE_AUTO_ATTRIBUTE					= "auto";
	String							NONE										= "none";

	String							directives[]								= {
			SPLIT_PACKAGE_DIRECTIVE, NO_IMPORT_DIRECTIVE, IMPORT_DIRECTIVE, RESOLUTION_DIRECTIVE, INCLUDE_DIRECTIVE,
			USES_DIRECTIVE, EXCLUDE_DIRECTIVE, KEYSTORE_LOCATION_DIRECTIVE, KEYSTORE_PROVIDER_DIRECTIVE,
			KEYSTORE_PASSWORD_DIRECTIVE, SIGN_PASSWORD_DIRECTIVE, COMMAND_DIRECTIVE, NOANNOTATIONS, LIB_DIRECTIVE,
			FROM_DIRECTIVE, PRIVATE_DIRECTIVE, LITERAL_ATTRIBUTE, EFFECTIVE_DIRECTIVE, FILTER_DIRECTIVE,
			FIXUPMESSAGES_RESTRICT_DIRECTIVE, FIXUPMESSAGES_REPLACE_DIRECTIVE, FIXUPMESSAGES_IS_DIRECTIVE,
			BNDDRIVER_GRADLE, BNDDRIVER_GRADLE_NATIVE, BNDDRIVER_ANT, BNDDRIVER_ECLIPSE, BNDDRIVER_MAVEN,
			BNDDRIVER_INTELLIJ, BNDDRIVER_SBT, BNDDRIVER_OSMORC

																				// TODO
																				};

	String							USES_USES									= "<<USES>>";
	String							CURRENT_USES								= "@uses";
	String							IMPORT_REFERENCE							= "reference";
	String							IMPORT_PRIVATE								= "private";
	String[]						importDirectives							= {
			IMPORT_REFERENCE, IMPORT_PRIVATE
																				};

	static final Pattern			VALID_PROPERTY_TYPES						= Pattern
																						.compile("(String|Long|Double|Float|Integer|Byte|Character|Boolean|Short)");

	String							DEFAULT_BND_EXTENSION						= ".bnd";
	String							DEFAULT_JAR_EXTENSION						= ".jar";
	String							DEFAULT_BAR_EXTENSION						= ".bar";
	String							DEFAULT_BNDRUN_EXTENSION					= ".bndrun";
	String[]						METAPACKAGES								= {
			"META-INF", "OSGI-INF", "OSGI-OPT"
																				};

	String							CURRENT_VERSION								= "@";
	String							CURRENT_PACKAGE								= "@package";

	String							BUILDFILES									= "buildfiles";

	String							EMPTY_HEADER								= "<<EMPTY>>";

	String							EMBEDDED_REPO								= "/embedded-repo.jar";
	String							LAUNCHER_PLUGIN								= "Launcher-Plugin";
	String							TESTER_PLUGIN								= "Tester-Plugin";

	String							DEFAULT_LAUNCHER_BSN						= "biz.aQute.launcher";
	String							DEFAULT_TESTER_BSN							= "biz.aQute.junit";

	String							DEFAULT_DO_NOT_COPY							= "CVS|\\.svn|\\.git|\\.DS_Store";

	Charset							DEFAULT_CHARSET								= Charset.forName("UTF8");
	String							VERSION_FILTER								= "version";
	String							PROVIDER_TYPE_DIRECTIVE						= "x-provider-type:";
	/**
	 * Component constants
	 */
	String							NAMESPACE_STEM								= "http://www.osgi.org/xmlns/scr";
	String							JIDENTIFIER									= "<<identifier>>";
	String							COMPONENT_NAME								= "name:";
	String							COMPONENT_FACTORY							= "factory:";
	String							COMPONENT_SERVICEFACTORY					= "servicefactory:";
	String							COMPONENT_IMMEDIATE							= "immediate:";
	String							COMPONENT_ENABLED							= "enabled:";
	String							COMPONENT_DYNAMIC							= "dynamic:";
	String							COMPONENT_MULTIPLE							= "multiple:";
	String							COMPONENT_GREEDY							= "greedy:";
	String							COMPONENT_PROVIDE							= "provide:";
	String							COMPONENT_OPTIONAL							= "optional:";
	String							COMPONENT_PROPERTIES						= "properties:";
	String							COMPONENT_IMPLEMENTATION					= "implementation:";
	String							COMPONENT_DESIGNATE							= "designate:";
	String							COMPONENT_DESIGNATEFACTORY					= "designateFactory:";
	String							COMPONENT_DESCRIPTORS						= ".descriptors:";

	// v1.1.0
	String							COMPONENT_VERSION							= "version:";
	String							COMPONENT_CONFIGURATION_POLICY				= "configuration-policy:";
	String							COMPONENT_MODIFIED							= "modified:";
	String							COMPONENT_ACTIVATE							= "activate:";
	String							COMPONENT_DEACTIVATE						= "deactivate:";

	String							COMPONENT_NAMESPACE							= "xmlns:";

	final static Map<String,String>	EMPTY										= Collections.emptyMap();

	String[]						componentDirectives							= new String[] {
			COMPONENT_FACTORY, COMPONENT_IMMEDIATE, COMPONENT_ENABLED, COMPONENT_DYNAMIC, COMPONENT_MULTIPLE,
			COMPONENT_PROVIDE, COMPONENT_OPTIONAL, COMPONENT_PROPERTIES, COMPONENT_IMPLEMENTATION,
			COMPONENT_SERVICEFACTORY, COMPONENT_VERSION, COMPONENT_CONFIGURATION_POLICY, COMPONENT_MODIFIED,
			COMPONENT_ACTIVATE, COMPONENT_DEACTIVATE, COMPONENT_NAME, COMPONENT_DESCRIPTORS, COMPONENT_DESIGNATE,
			COMPONENT_DESIGNATEFACTORY, COMPONENT_GREEDY, COMPONENT_NAMESPACE
																				};

	Set<String>						SET_COMPONENT_DIRECTIVES					= new HashSet<String>(
																						Arrays.asList(componentDirectives));

	Set<String>						SET_COMPONENT_DIRECTIVES_1_1				= //
																				new HashSet<String>(Arrays.asList(
																						COMPONENT_VERSION,
																						COMPONENT_CONFIGURATION_POLICY,
																						COMPONENT_MODIFIED,
																						COMPONENT_ACTIVATE,
																						COMPONENT_DEACTIVATE));

	Set<String>						SET_COMPONENT_DIRECTIVES_1_2				= new HashSet<String>(
																						Arrays.asList(COMPONENT_GREEDY));

	String							VERSION_ATTR_LATEST							= "latest";
	String							VERSION_ATTR_SNAPSHOT						= "snapshot";
	String							VERSION_ATTR_PROJECT						= "project";

	/**
	 * List of standard matchers for preprocessing
	 */
	String							DEFAULT_PREPROCESSS_MATCHERS				= "!*.(jpg|jpeg|jif|jfif|jp2|jpx|j2k|j2c|fpx|png|gif|swf|doc|pdf|tiff|tif|raw|bmp|ppm|pgm|pbm|pnm|pfm|webp|zip|jar|gz|tar|tgz|exe|com|bin|mp[0-9]|mpeg|mov|):i, *";

	/*
	 * Default properties as listed in defaults.bnd
	 */

	String							DEFAULT_PROP_SRC_DIR						= "src";
	String							DEFAULT_PROP_BIN_DIR						= "bin";
	String							DEFAULT_PROP_TESTSRC_DIR					= "testsrc";
	String							DEFAULT_PROP_TESTBIN_DIR					= "testbin";
	String							DEFAULT_PROP_TARGET_DIR						= "target-dir";

	/*
	 * Deprecated Section
	 */

	/**
	 * we use the eclipse setting in eclipse and the javac.source setting in
	 * offline build
	 */
	@Deprecated
	String							COMPILER_SOURCE								= "-source";

	/**
	 * we use the eclipse setting in eclipse and the javac.target setting in
	 * offline build
	 */
	@Deprecated
	String							COMPILER_TARGET								= "-target";
	@Deprecated
	// no references in bnd?
	String							RESOLVE										= "-resolve";
	@Deprecated
	String							TESTSUITES									= "Test-Suites";
	@Deprecated
	String							TESTREPORT									= "-testreport";
	@Deprecated
	String							VERBOSE										= "-verbose";
	@Deprecated
	String							RUNPATH_MAIN_DIRECTIVE						= "main:";
	@Deprecated
	String							RUNPATH_LAUNCHER_DIRECTIVE					= "launcher:";

}