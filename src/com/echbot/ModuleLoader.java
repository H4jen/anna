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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author Chris Pearson
 * @version $Id: ModuleLoader.java,v 1.3 2003/07/27 10:43:49 chris Exp $
 */
class ModuleLoader extends ClassLoader
{
    public static final String packageName = "com.echbot.modules.";
    private static final int packageLength = packageName.length();
    private final Map moduleFiles = new HashMap();

    protected Class findClass(String name) throws ClassNotFoundException {
        if (name.startsWith(packageName) && (name.length() > packageLength)) {
            final int packageEnd = name.indexOf('.', packageLength);
            if (packageEnd != -1) {
                final String jarName = name.substring(packageLength, packageEnd);
                if (moduleFiles.containsKey(jarName)) {
                    return loadJarClass(name, (String)moduleFiles.get(jarName));
                } else {
                    final String filename = "modules/" + jarName + ".jar";
                    final File jarFile = new File(filename);
                    if (!jarFile.exists()) {
                        throw new ClassNotFoundException("Couldn't find " + filename);
                    } else {
                        moduleFiles.put(jarName, jarFile.getPath());
                        return loadJarClass(name, jarFile.getPath());
                    }
                }
            }
        }
        throw new ClassNotFoundException(name + " is not a module class");
    }

    private Class loadJarClass(String className, String filename) throws ClassNotFoundException {
        ZipFile zipFile = null;
        BufferedInputStream bis = null;
        byte[] res = null;
        try {
            zipFile = new ZipFile(filename);
            ZipEntry zipEntry = zipFile.getEntry(className.replace('.', '/') + ".class");
            res = new byte[(int)zipEntry.getSize()];
            bis = new BufferedInputStream(zipFile.getInputStream(zipEntry));
            bis.read(res, 0, res.length);
        } catch (Exception ex) {
        } finally {
            if (bis != null) {
                try {
                    bis.close();
                } catch (IOException ioex) {
                }
            }
            if (zipFile != null) {
                try {
                    zipFile.close();
                } catch (IOException ioex) {
                }
            }
        }
        if (res == null) throw new ClassNotFoundException("Couldn't load " + className + " from " + filename);
        Class clazz = defineClass(className, res, 0, res.length);
        if (clazz == null) throw new ClassFormatError();
        return clazz;
    }

    public Collection getModuleFiles() {
        return moduleFiles.values();
    }
}