import * as FS from 'fs';
import * as Path from 'path';
import * as ChildProcess from 'child_process';

'use strict';

/**
 * Modules that a plain JRE typically does not provide, but that clients of this
 * package rely on:
 *
 * - `jdk.attach`: attach to locally running JVMs (`com.sun.tools.attach`)
 * - `jdk.management`: platform management extensions used along with the attach API
 */
export const REQUIRED_JDK_MODULES = [ 'jdk.attach', 'jdk.management' ];

export interface JVM {
    /**
     * 8 = Java 1.8.x, 9 = Java 9.x, etc
     */
    getMajorVersion() : number

    /**
     * Path to the Java executable
     */
    getJavaExecutable() : string

    /**
     * Path to the corresponding 'java home' for the executable.
     */
    getJavaHome() : string

    /**
     * Detect whether this JVM is a JDK, i.e. whether it provides the JDK-only
     * modules the language server needs (see REQUIRED_JDK_MODULES).
     */
    isJdk() : boolean

    /**
     * The JDK-only modules from REQUIRED_JDK_MODULES that this JVM does not provide.
     * An empty array means the JVM is usable as a JDK.
     */
    getMissingJdkModules() : string[]

    /**
     * Launch an executable jar with this jvm.
     */
    jarLaunch(jar: string, vmargs?: string[], execFileOptions?: ChildProcess.ExecFileOptions, pargs?: string[]) : ChildProcess.ChildProcess

    mainClassLaunch(mainClass: string, classpath: string[], jvmArgs: string[], execFileOptions?: ChildProcess.ExecFileOptions): ChildProcess.ChildProcess
}

/**
 * Find a JVM by looking in the JAVA_HOME and PATH environment variables.
 *
 * Optionally, a specific javaHome can be passed in. This shortcuts the
 * search logic and uses that javaHome as is.
 *
 * The returned JVM may or may not be a JDK. Methods are provided to obtain corresponding
 * toolsjar and to check whether the JVM is a JDK.
 */
export function findJvm(javaHome: string, log?: (msg: string) => void) : Promise<JVM | null> {
    if (!log) {
        log = console.log;
    }
    try {
        let javaExe = findJavaExe(javaHome, log);
        if (javaExe) {
            return getJavaInfo(javaExe).then(javaProps => new JVMImpl(
                javaProps.get("java.home"),
                javaExe,
                getMajorVersion(javaProps)
            ));
        }
        return Promise.resolve(null);
    } catch (e) {
        return Promise.reject(e);
    }
}

/**
 * Like findJvm, but additionally, if the found JVM is not a JDK tries to
 * find a companion JDK that may be installed alongside it.
 */
export function findJdk(javaHome: string, log?: (msg: string) => void) : Promise<JVM | null> {
    if (!log) {
        log = console.log;
    }
    return findJvm(javaHome, log).then(jvm => {
        if(!jvm) {
            return null;
        }
        if (!jvm.isJdk()) {
            log(`found JVM at location "${jvm.getJavaHome()}" is not a JDK`);

            //Try to find a 'sibling' JDK.
            //Mainly for windows where it is common to have side-by-side install of a jre and jdk, instead of a
            //nested jre install inside of a jdk.

            //E.g.
            //C:\ProgramFiles\Java\jdk1.8.0_161
            //C:\ProgramFiles\Java\jre1.8.0_161

            let javaExe = jvm.getJavaExecutable();
            // javaExe example: C:\ProgramFiles\Java\jre1.8.0_161\bin\java.exe
            let jhome = jvm.getJavaHome();
            let basename : string = Path.basename(jhome);
            let altBasename : string = basename.replace("jre", "jdk");
            if (altBasename!==basename) {
                let altHome = Path.join(Path.dirname(jhome), altBasename);
                log(`Trying to find JDK corresping to currently found JRE. Checking for JDK at ${altHome}`);
                if (FS.existsSync(altHome)) {
                    let altExe = Path.resolve(altHome, "bin", correctBinname("java"));
                    log("altExe = " + altExe);
                    return new JVMImpl(altHome, altExe, jvm.getMajorVersion());
                }
            }
        }
        return jvm;
    });
}

/**
 * Find a 'java' exe by looking in the JAVA_HOME and PATH environment variables.
 * <p>
 * Optionally, a specific javaHome can be passed in. This shortcuts the
 * search logic and uses that javaHome as is, not looking anywhere else.
 */
function findJavaExe(javaHome: string, log?: (msg: string) => void) : string | null {
    if (!log) {
        log = console.log;
    }
    //Try java home first
    if (!javaHome) {
        log('No user specified java-home setting. Looking for JAVA_HOME env variable...');
        javaHome = process.env["JAVA_HOME"];
    }
    if (javaHome) {
        //Resolve symlinks
        javaHome = FS.realpathSync(javaHome);
    } else {
        log('JAVA_HOME environment variable not set');
    }
    let binName = correctBinname("java");
    if (javaHome) {
        let javaExe = Path.resolve(javaHome, "bin", binName);
        if (FS.existsSync(javaExe)) {
            return javaExe;
        }
        // Fall through invalid JVM specified by JAVA_HOME varable
        log(`JAVA_HOME env variable points to location that does NOT exist: ${javaHome}`)
    }

    log('Looking for the path to JVM inside PATH env variable');
    for (let searchPath of process.env['PATH'].split(Path.delimiter)) {
        let javaExe = Path.resolve(searchPath, binName);
        if (FS.existsSync(javaExe)) {
            //Resolve symlinks
            return FS.realpathSync(javaExe);
        }
    }
    log('No valid JVM found on the system!!!');
    return null;
}

type Getter<T> = () => T;

function memoize<T>(getter : Getter<T>) : Getter<T> {
    let computed : boolean = false;
    let value : T | null = null;
    return () => {
        if (!computed) {
            value = getter();
            computed = true;
        }
        return value;
    };
}

class JVMImpl implements JVM {
    javaHome : string
    javaExe : string
    version : number
    private listModules : Getter<string> = memoize(() =>
        ChildProcess.execFileSync(this.getJavaExecutable(), ['--list-modules'], {windowsHide: true, encoding: 'utf8'})
    );
    constructor(javaHome : string, javaExe : string, version : number) {
        this.javaHome = javaHome;
        this.javaExe = javaExe;
        this.version = version;
    }

    getJavaHome() : string {
        return this.javaHome;
    }

    getMajorVersion() {
        return this.version;
    }
    getJavaExecutable(): string {
        return this.javaExe;
    }
    isJdk(): boolean {
        return this.getMissingJdkModules().length === 0;
    }
    getMissingJdkModules(): string[] {
        const modules = this.listModules();
        return REQUIRED_JDK_MODULES.filter(module =>
            modules.search(new RegExp(`^${module.replace(/\./g, '\\.')}@`, 'm')) < 0
        );
    }
    jarLaunch(jar: string, vmargs?: string[], execFileOptions?: ChildProcess.ExecFileOptions, pargs?: string[]): ChildProcess.ChildProcess {
        const args: string[] = [];
        if (vmargs) {
            args.push(...vmargs);
        }
        args.push("-jar", jar);
        if (pargs) {
            args.push(...pargs);
        }
        // explicit 'null' callback: the (file, args, options) overload without a callback
        // only exists for options that pin down the encoding
        return ChildProcess.execFile(this.getJavaExecutable(), args, execFileOptions, null);
    }

    mainClassLaunch(mainClass: string, classpath: string[], jvmArgs: string[], execFileOptions?: ChildProcess.ExecFileOptions): ChildProcess.ChildProcess {
        const args: string[] = [];

        // Classpath
        args.push('-cp');
        let classpathStr = classpath.join(Path.delimiter);
        args.push(classpathStr);

        // JVM Arguments
        args.push(...jvmArgs);

        // Main class
        args.push(mainClass);

        // explicit 'null' callback: the (file, args, options) overload without a callback
        // only exists for options that pin down the encoding
        return ChildProcess.execFile(this.getJavaExecutable(), args, execFileOptions, null);
    }
}

function getJavaInfo(javaExe : string) : Promise<Map<string, string>> {
    //console.log("Fetching java properties for "+javaExe);
    return new Promise((resolve, reject) => {
        ChildProcess.execFile(javaExe, ['-XshowSettings:properties'], {}, (error, stdout, stderr) => {
            let lines = stderr.split(/\r?\n/);
            let propNames = [ 'java.version', 'java.home' ];
            let props = new Map<string,string>();
            for (var l of lines) {
                //console.log("Line: "+l);
                for (var p of propNames) {
                    let offset = l.indexOf(p);
                    if (offset>=0) {
                        //Make sure it looks like a proper 'assignment' to the property and not an
                        // accidental match.
                        //console.log("Propname found: "+p);
                        let assign = " " +p + " = ";
                        offset = l.indexOf(assign);
                        if (offset>=0) {
                            //console.log("Assignment found: "+p);
                            offset = offset + assign.length;
                            let value = l.substring(offset);
                            //console.log("value = "+value);
                            props.set(p, value);
                            if (props.size >= propNames.length) {
                                //We found everything we care about, so we can stop now.
                                //console.log("result = ", props);
                                return resolve(props);
                            }
                        }
                    }
                }
            }
            //Not found everything we expected.
            return reject("Unexpected output from `java -XshowSettings:properties`. Didn't find all expected properties: "+propNames);
        });
    });
}

function getMajorVersion(javaProperties : Map<string,string>) : number {
    let versionString = javaProperties.get('java.version');
    let pieces = versionString.split(".");
    let major = parseInt(pieces[0]);
    return major==1 ? parseInt(pieces[1]) : major;
}

function correctBinname(binname: string) {
    if (process.platform === 'win32')
        return binname + '.exe';
    else
        return binname;
}
