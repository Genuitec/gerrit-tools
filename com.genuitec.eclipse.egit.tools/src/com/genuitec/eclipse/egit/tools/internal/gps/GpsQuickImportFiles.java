/**
 *  Copyright (c) 2015 Genuitec LLC.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *  Piotr Tomiak <piotr@genuitec.com> - initial API and implementation
 */
package com.genuitec.eclipse.egit.tools.internal.gps;

import java.io.File;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang.StringUtils;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.osgi.service.prefs.BackingStoreException;

import com.genuitec.eclipse.egit.tools.EGitToolsPlugin;

public class GpsQuickImportFiles {

	private static final Set<File> files = new TreeSet<File>(new Comparator<File>() {
		@Override
		public int compare(File o1, File o2) {
			return o1.getName().compareToIgnoreCase(o2.getName());
		}
	});
	
	private static IEclipsePreferences prefs =
			InstanceScope.INSTANCE.getNode(EGitToolsPlugin.PLUGIN_ID);
	
	static {
		load();
	}
	
	public static void addGpsFile(File file) {
		assert file.getName().endsWith(".gps"); //$NON-NLS-1$
		synchronized(files) {
			files.add(file);
			store();
		}
	}
	
	public static File[] getGpsFiles() {
		synchronized (files) {
			return files.toArray(new File[files.size()]);
		}
	}
	
	private static void store() {
		prefs.put("quick-files", StringUtils.join(files, '\n')); //$NON-NLS-1$
		try {
			prefs.flush();
		} catch (BackingStoreException e) {
			EGitToolsPlugin.getDefault().log(e);
		}
	}
	
	private static void load() {
		synchronized (files) {
			for (String file: StringUtils.split(prefs.get("quick-files", ""), '\n')) { //$NON-NLS-1$ //$NON-NLS-2$
				File f = new File(file);
				if (f.exists()) {
					files.add(f);
				}
			}
		}
	}
	
}
