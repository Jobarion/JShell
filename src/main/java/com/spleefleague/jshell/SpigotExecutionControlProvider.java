package com.spleefleague.jshell;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import jdk.jshell.execution.DirectExecutionControl;
import jdk.jshell.execution.LoaderDelegate;
import jdk.jshell.spi.ExecutionControl;
import jdk.jshell.spi.ExecutionControl.ClassBytecodes;
import jdk.jshell.spi.ExecutionControl.ClassInstallException;
import jdk.jshell.spi.ExecutionControl.EngineTerminationException;
import jdk.jshell.spi.ExecutionControl.InternalException;
import jdk.jshell.spi.ExecutionControlProvider;
import jdk.jshell.spi.ExecutionEnv;

/**
 *
 * @author balsfull
 */
public class SpigotExecutionControlProvider implements ExecutionControlProvider {

    @Override
    public String name() {
        return "direct";
    }

    @Override
    public ExecutionControl generate(ExecutionEnv env, Map<String, String> parameters) throws Throwable {
        return new DirectExecutionControl(new SpigotLoaderDelegate());
    }

    private static class SpigotLoaderDelegate implements LoaderDelegate {

        private final RemoteClassLoader loader;
        private final Map<String, Class<?>> klasses = new TreeMap<>();

        @Override
        public void classesRedefined(ClassBytecodes[] cbcs) {
            try {
                load(cbcs);
            } catch (ClassInstallException | EngineTerminationException ex) {
                Logger.getLogger(SpigotExecutionControlProvider.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        class RemoteClassLoader extends URLClassLoader {

            private final Map<String, byte[]> classObjects = new TreeMap<>();

            RemoteClassLoader() {
                super(new URL[0]);
            }

            void delare(String name, byte[] bytes) {
                classObjects.put(name, bytes);
            }

            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                byte[] b = classObjects.get(name);
                if (b == null) {
                    Class<?> c = null;
                    //System.out.println("findClass " + name + " (" + (b == null) + ")");
                    try {
                        c = Class.forName(name);
                    } catch(ClassNotFoundException e) {
                    }
                    if(c == null) {
                        return super.findClass(name);
                    }
                    return c;
                }
                return super.defineClass(name, b, 0, b.length, (CodeSource) null);
            }

            @Override
            public void addURL(URL url) {
                super.addURL(url);
            }

        }

        public SpigotLoaderDelegate() {
            this.loader = new RemoteClassLoader();
            Thread.currentThread().setContextClassLoader(loader);
        }

        @Override
        public void load(ClassBytecodes[] cbcs)
                throws ClassInstallException, EngineTerminationException {
            boolean[] loaded = new boolean[cbcs.length];
            try {
                for (ClassBytecodes cbc : cbcs) {
                    loader.delare(cbc.name(), cbc.bytecodes());
                }
                for (int i = 0; i < cbcs.length; ++i) {
                    ClassBytecodes cbc = cbcs[i];
                    Class<?> klass = loader.loadClass(cbc.name());
                    klasses.put(cbc.name(), klass);
                    loaded[i] = true;
                    // Get class loaded to the point of, at least, preparation
                    klass.getDeclaredMethods();
                }
            } catch (Throwable ex) {
                throw new ClassInstallException("load: " + ex.getMessage(), loaded);
            }
        }

        @Override
        public void addToClasspath(String cp)
                throws EngineTerminationException, InternalException {
            try {
                for (String path : cp.split(File.pathSeparator)) {
                    loader.addURL(new File(path).toURI().toURL());
                }
            } catch (Exception ex) {
                throw new InternalException(ex.toString());
            }
        }

        @Override
        public Class<?> findClass(String name) throws ClassNotFoundException {
            Class<?> klass = klasses.get(name);
            if (klass == null) {
                throw new ClassNotFoundException(name + " not found");
            } else {
                return klass;
            }
        }
    }
}
