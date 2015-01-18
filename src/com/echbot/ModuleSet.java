/*
 * echbot - an open-source IRC bot
 * Copyright (C) 2003  Christopher Pearson
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 * To contact the author, Chris Pearson, email chris@echbot.com
 */
package com.echbot;

import com.echbot.messages.InboundMessage;
import com.echbot.messages.MessageVisitor;
import org.apache.log4j.Logger;

import java.io.File;
import java.lang.reflect.Constructor;
import java.util.*;

/**
 * One ModuleSet per clone. Warning, each instance will remain in memory
 * forever as written now.
 * @author Chris Pearson
 * @version $Id: ModuleSet.java,v 1.15 2003/09/22 23:02:12 chris Exp $
 */
class ModuleSet
{
    private static final Logger log = Logger.getLogger(ModuleSet.class);
    private static final Map moduleClasses = new HashMap();
    private static final Set moduleSets = Collections.synchronizedSet(new HashSet());
    private static ModuleLoader loader = new ModuleLoader();
    private final Map modules = new HashMap();
    private final Clone clone;
    private final Map regMessages = new HashMap();
    private final Map regUserEvents = new HashMap();

    ModuleSet(Clone clone) {
        this.clone = clone;
        moduleSets.add(this);
        loadAllModules(clone.getConfig().get("modules"));
    }

    private static void loadAllModules(String moduleList) {
        if (moduleList == null) {
            log.warn("No modules given for loading");
            return;
        }
        synchronized (moduleClasses) {
            StringTokenizer tokens = new StringTokenizer(moduleList, ", ");
            while (tokens.hasMoreTokens()) {
                String nextModule = tokens.nextToken();
                loadModule(nextModule);
            }
        }
    }

    private static boolean loadModule(String name) {
        synchronized (moduleClasses) {
            // Only load modules if they haven't been loaded already
            if (!moduleClasses.containsKey(name)) {
                try {
                    String className = getModuleClassName(name);
                    Class loaded = Class.forName(className.toString(), true, loader);
                    if (!UserModule.class.isAssignableFrom(loaded)) {
                        log.info("Module " + name + " is invalid");
                    } else {
                        log.info("Loaded module " + name);
                        moduleClasses.put(name, loaded);
                        return true;
                    }
                } catch (ClassNotFoundException e) {
                    log.info("Couldn't load module " + name + " (" + e.getMessage() + ")");
                }
            }
        }
        return false;
    }

    private static String getModuleClassName(String moduleName) {
        StringBuffer className = new StringBuffer();
        className.append(ModuleLoader.packageName);
        className.append(moduleName).append('.');
        className.append(Character.toUpperCase(moduleName.charAt(0)));
        className.append(moduleName.substring(1)).append("Module");
        return className.toString();
    }

    void register(Class messageType, UserModule module) {
        synchronized (regMessages) {
            Set modules = (Set)regMessages.get(messageType);
            if (modules == null) {
                modules = new HashSet();
                regMessages.put(messageType, modules);
            }
            modules.add(module);
        }
    }

    void received(InboundMessage message) {
        synchronized (regMessages) {
            Set modules = (Set)regMessages.get(message.getClass());
            if (modules == null) return;
            for (Iterator i = modules.iterator(); i.hasNext();) {
                try {
                    MessageVisitor visitor = (MessageVisitor)i.next();
                    message.visit(visitor);
                } catch (Throwable e) {
                    log.error("Module error in received(" + message.getClass().getName() + ")", e);
                }
            }
        }
    }

    void registerForEvent(int id, UserModule module) {
        synchronized (regUserEvents) {
            Integer eventId = new Integer(id);
            Set modules = (Set)regUserEvents.get(eventId);
            if (modules == null) {
                modules = new HashSet();
                regUserEvents.put(eventId, modules);
            }
            modules.add(module);
        }
    }

    void triggerEvent(int id, Object attachment) {
        synchronized (regUserEvents) {
            Integer eventId = new Integer(id);
            Set modules = (Set)regUserEvents.get(eventId);
            if (modules == null) return;
            for (Iterator i = modules.iterator(); i.hasNext();) {
                UserModule module = (UserModule)i.next();
                try {
                    module.userEvent(id, attachment);
                } catch (Throwable e) {
                    log.error("Module error in userEvent(..)", e);
                }
            }
        }
    }

    public void instantiateModules(Map savedState) {
        synchronized (moduleClasses) {
            synchronized (modules) {
                for (Iterator i = moduleClasses.keySet().iterator(); i.hasNext();) {
                    String moduleName = (String)i.next();
                    instantiateModule(moduleName);
                }
                for (Iterator i = moduleClasses.keySet().iterator(); i.hasNext();) {
                    String moduleName = (String)i.next();
                    Object state = (savedState == null) ? null : savedState.get(moduleName);
                    initialiseModule(moduleName, state);
                }
            }
        }
    }

    private void instantiateModule(String moduleName) {
        synchronized (modules) {
            Class moduleClass = (Class)moduleClasses.get(moduleName);
            try {
                Constructor constructor = moduleClass.getConstructor(new Class[]{UserModuleInterface.class});
                UserModule newModule = (UserModule)constructor.newInstance(new Object[]{clone});
                modules.put(moduleName, newModule);
                log.debug("Instantiated " + moduleName + ", " + newModule);
            } catch (Throwable e) {
                log.info("Failed to instantiate " + moduleName, e);
            }
        }
    }

    private void initialiseModule(String moduleName, Object state) {
        synchronized (modules) {
            UserModule module = (UserModule)modules.get(moduleName);
            if (module != null) {
                try {
                    module.initialise(state);
                } catch (Throwable e) {
                    log.error("Module error in initialise(..)", e);
                }
            }
        }
    }

    static void reloadModules(String moduleList) {
        log.info("Reloading all modules");
        final Map savedState = new HashMap();
        synchronized (moduleClasses) {
            moduleClasses.clear();
            for (Iterator i = moduleSets.iterator(); i.hasNext();) {
                ModuleSet moduleSet = (ModuleSet)i.next();
                synchronized (moduleSet.regMessages) {
                    moduleSet.regMessages.clear();
                }
                synchronized (moduleSet.regUserEvents) {
                    moduleSet.regUserEvents.clear();
                }
                final Map moduleState = new HashMap();
                synchronized (moduleSet.modules) {
                    for (Iterator j = moduleSet.modules.keySet().iterator(); j.hasNext();) {
                        String className = (String)j.next();
                        UserModule module = (UserModule)moduleSet.modules.get(className);
                        try {
                            moduleState.put(className, module.getState());
                            module.cancelTimer();
                        } catch (Throwable e) {
                            log.error("Module error in getState()", e);
                        }
                    }
                    moduleSet.modules.clear();
                }
                savedState.put(moduleSet, moduleState);
            }
            Collection moduleFiles = loader.getModuleFiles();
            loader = new ModuleLoader();
            // Hopefully we've unloaded all the jars getting used
            System.gc();
            // Update module files
            for (Iterator i = moduleFiles.iterator(); i.hasNext();) {
                String filename = (String)i.next();
                File oldFile = new File(filename);
                if (oldFile.exists() && oldFile.canWrite()) {
                    File newFile = new File(filename + ".new");
                    if (newFile.exists() && newFile.canWrite()) {
                        oldFile.delete();
                        newFile.renameTo(oldFile);
                        log.info("Updated " + filename);
                    }
                }
            }
            // Now recreate all the modules
            loadAllModules(moduleList);
            for (Iterator i = moduleSets.iterator(); i.hasNext();) {
                ModuleSet moduleSet = (ModuleSet)i.next();
                moduleSet.instantiateModules((Map)savedState.get(moduleSet));
            }
        }
    }

    UserModule getModule(String name) {
        return (UserModule)modules.get(name);
    }

    public static void addModule(String name) {
        log.debug("Loading module: " + name);
        if (loadModule(name)) {
            for (Iterator i = moduleSets.iterator(); i.hasNext();) {
                ModuleSet set = (ModuleSet)i.next();
                set.instantiateModule(name);
                set.initialiseModule(name, null);
            }
        }
    }

    public static void removeModule(String name) {
        log.debug("Removing module: " + name);
        synchronized (moduleClasses) {
            if (!moduleClasses.containsKey(name)) return;
            moduleClasses.remove(name);
            for (Iterator i = moduleSets.iterator(); i.hasNext();) {
                ModuleSet set = (ModuleSet)i.next();
                synchronized (set.modules) {
                    Object module = set.modules.get(name);
                    set.modules.remove(name);
                    synchronized (set.regMessages) {
                        removeFromSetMap(set.regMessages, module);
                    }
                    synchronized (set.regUserEvents) {
                        removeFromSetMap(set.regUserEvents, module);
                    }
                }
            }
        }
    }

    private static final void removeFromSetMap(Map map, Object o) {
        synchronized (map) {
            for (Iterator i = map.values().iterator(); i.hasNext();) {
                Set set = (Set)i.next();
                set.remove(o);
                if (set.isEmpty()) i.remove();
            }
        }
    }

    public static void triggerGlobalEvent(int id, Object attachment) {
        for (Iterator i = moduleSets.iterator(); i.hasNext();) {
            ModuleSet moduleSet = (ModuleSet)i.next();
            moduleSet.triggerEvent(id, attachment);
        }
    }

    public void terminate() {
        synchronized (moduleSets) {
            moduleSets.remove(this);
        }
    }
}
