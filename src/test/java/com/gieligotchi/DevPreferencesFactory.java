package com.gieligotchi;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.prefs.PreferencesFactory;

/** Development-only in-memory preferences, keeping the isolated client out of the Windows registry. */
public final class DevPreferencesFactory implements PreferencesFactory
{
	private final Preferences users = new MemoryNode(null, "");
	private final Preferences system = new MemoryNode(null, "");
	@Override public Preferences userRoot() { return users; }
	@Override public Preferences systemRoot() { return system; }

	private static final class MemoryNode extends AbstractPreferences
	{
		private final Map<String, String> values = new ConcurrentHashMap<>();
		private final Map<String, MemoryNode> nodes = new ConcurrentHashMap<>();
		private MemoryNode(AbstractPreferences parent, String name) { super(parent, name); }
		@Override protected void putSpi(String key, String value) { values.put(key, value); }
		@Override protected String getSpi(String key) { return values.get(key); }
		@Override protected void removeSpi(String key) { values.remove(key); }
		@Override protected void removeNodeSpi() { values.clear(); nodes.clear(); }
		@Override protected String[] keysSpi() { return values.keySet().toArray(new String[0]); }
		@Override protected String[] childrenNamesSpi() { return nodes.keySet().toArray(new String[0]); }
		@Override protected AbstractPreferences childSpi(String name) { return nodes.computeIfAbsent(name, key -> new MemoryNode(this, key)); }
		@Override protected void syncSpi() throws BackingStoreException { }
		@Override protected void flushSpi() throws BackingStoreException { }
	}
}
