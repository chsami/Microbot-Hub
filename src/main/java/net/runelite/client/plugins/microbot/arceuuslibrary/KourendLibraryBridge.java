package net.runelite.client.plugins.microbot.arceuuslibrary;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.kourendlibrary.KourendLibraryPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Reflection-only adapter around the upstream Kourend Library plugin.
 * The {@code Library}, {@code Bookcase}, {@code Book}, and {@code SolvedState}
 * types are package-private in the upstream plugin, so we cannot reference
 * them directly. This bridge resolves them at runtime and exposes typed
 * snapshots (POJOs in this package) to the rest of the Arceuus Library plugin.
 */
@Slf4j
public final class KourendLibraryBridge
{
    public enum Solved { NO_DATA, INCOMPLETE, COMPLETE }

    @Value
    public static class BookSnapshot
    {
        String enumName;
        int itemId;
        String shortName;
        String fullName;
    }

    @Value
    public static class BookcaseSnapshot
    {
        WorldPoint location;
        BookSnapshot known;          // null when no confirmed book
        Set<BookSnapshot> possible;  // populated when state != NO_DATA
        /**
         * True after the upstream plugin has observed a player search at this bookcase.
         * Empty searched bookcases have {@code bookSet=true} with {@code known=null} —
         * we must not retry them.
         */
        boolean bookSet;
    }

    private final PluginManager pluginManager;

    private Class<?> libraryClass;
    private Class<?> bookcaseClass;
    private Class<?> bookClass;
    private Class<?> solvedStateClass;

    private Field pluginLibraryField;
    private Method libGetState;
    private Method libGetCustomerBook;
    private Method libGetCustomerId;
    private Method libGetBookcases;

    private Method bookcaseGetLocation;
    private Method bookcaseGetBook;
    private Method bookcaseGetPossibleBooks;
    private Method bookcaseIsBookSet;

    private Method bookGetItem;
    private Method bookGetShortName;
    private Method bookGetName;
    private Method bookByIdStatic;
    private Method enumName;

    private boolean wired;

    public KourendLibraryBridge(PluginManager pluginManager)
    {
        this.pluginManager = pluginManager;
    }

    /**
     * Resolve all reflective handles. Idempotent. Returns true if the upstream
     * plugin is loaded and reflection succeeded.
     */
    public synchronized boolean wire()
    {
        if (wired) return true;
        try
        {
            ClassLoader cl = KourendLibraryPlugin.class.getClassLoader();
            String pkg = "net.runelite.client.plugins.kourendlibrary.";
            libraryClass = Class.forName(pkg + "Library", true, cl);
            bookcaseClass = Class.forName(pkg + "Bookcase", true, cl);
            bookClass = Class.forName(pkg + "Book", true, cl);
            solvedStateClass = Class.forName(pkg + "SolvedState", true, cl);

            pluginLibraryField = KourendLibraryPlugin.class.getDeclaredField("library");
            pluginLibraryField.setAccessible(true);

            libGetState = libraryClass.getDeclaredMethod("getState");
            libGetCustomerBook = libraryClass.getDeclaredMethod("getCustomerBook");
            libGetCustomerId = libraryClass.getDeclaredMethod("getCustomerId");
            libGetBookcases = libraryClass.getDeclaredMethod("getBookcases");
            libGetState.setAccessible(true);
            libGetCustomerBook.setAccessible(true);
            libGetCustomerId.setAccessible(true);
            libGetBookcases.setAccessible(true);

            bookcaseGetLocation = bookcaseClass.getDeclaredMethod("getLocation");
            bookcaseGetBook = bookcaseClass.getDeclaredMethod("getBook");
            bookcaseGetPossibleBooks = bookcaseClass.getDeclaredMethod("getPossibleBooks");
            bookcaseIsBookSet = bookcaseClass.getDeclaredMethod("isBookSet");
            bookcaseGetLocation.setAccessible(true);
            bookcaseGetBook.setAccessible(true);
            bookcaseGetPossibleBooks.setAccessible(true);
            bookcaseIsBookSet.setAccessible(true);

            bookGetItem = bookClass.getDeclaredMethod("getItem");
            bookGetShortName = bookClass.getDeclaredMethod("getShortName");
            bookGetName = bookClass.getDeclaredMethod("getName");
            bookByIdStatic = bookClass.getDeclaredMethod("byId", int.class);
            bookGetItem.setAccessible(true);
            bookGetShortName.setAccessible(true);
            bookGetName.setAccessible(true);
            bookByIdStatic.setAccessible(true);
            enumName = Enum.class.getDeclaredMethod("name");

            wired = true;
            return true;
        }
        catch (ReflectiveOperationException e)
        {
            log.warn("Failed to wire KourendLibraryBridge: {}", e.toString());
            return false;
        }
    }

    public Optional<KourendLibraryPlugin> findUpstreamPlugin()
    {
        for (Plugin p : pluginManager.getPlugins())
        {
            if (p instanceof KourendLibraryPlugin) return Optional.of((KourendLibraryPlugin) p);
        }
        return Optional.empty();
    }

    public boolean isUpstreamRunning()
    {
        return findUpstreamPlugin().map(pluginManager::isPluginEnabled).orElse(false);
    }

    /**
     * Ensure the upstream plugin is enabled and started. Returns true on success.
     */
    public boolean ensureUpstreamEnabled()
    {
        Optional<KourendLibraryPlugin> opt = findUpstreamPlugin();
        if (!opt.isPresent())
        {
            log.warn("Kourend Library plugin not present in PluginManager");
            return false;
        }
        KourendLibraryPlugin upstream = opt.get();
        if (pluginManager.isPluginEnabled(upstream)) return true;
        try
        {
            pluginManager.setPluginEnabled(upstream, true);
            pluginManager.startPlugin(upstream);
            return true;
        }
        catch (Exception e)
        {
            log.warn("Failed to start Kourend Library plugin: {}", e.toString());
            return false;
        }
    }

    private Object library()
    {
        Optional<KourendLibraryPlugin> opt = findUpstreamPlugin();
        if (!opt.isPresent()) return null;
        try
        {
            return pluginLibraryField.get(opt.get());
        }
        catch (IllegalAccessException e)
        {
            log.warn("Could not access Library field: {}", e.toString());
            return null;
        }
    }

    public Solved getState()
    {
        if (!wire()) return Solved.NO_DATA;
        Object lib = library();
        if (lib == null) return Solved.NO_DATA;
        try
        {
            Object state = libGetState.invoke(lib);
            if (state == null) return Solved.NO_DATA;
            return Solved.valueOf((String) enumName.invoke(state));
        }
        catch (ReflectiveOperationException e)
        {
            return Solved.NO_DATA;
        }
    }

    public Optional<BookSnapshot> getCustomerBook()
    {
        if (!wire()) return Optional.empty();
        Object lib = library();
        if (lib == null) return Optional.empty();
        try
        {
            Object book = libGetCustomerBook.invoke(lib);
            return Optional.ofNullable(toBook(book));
        }
        catch (ReflectiveOperationException e)
        {
            return Optional.empty();
        }
    }

    public int getCustomerId()
    {
        if (!wire()) return -1;
        Object lib = library();
        if (lib == null) return -1;
        try
        {
            Object id = libGetCustomerId.invoke(lib);
            return id instanceof Integer ? (Integer) id : -1;
        }
        catch (ReflectiveOperationException e)
        {
            return -1;
        }
    }

    public List<BookcaseSnapshot> getBookcases()
    {
        if (!wire()) return Collections.emptyList();
        Object lib = library();
        if (lib == null) return Collections.emptyList();
        try
        {
            @SuppressWarnings("unchecked")
            List<Object> raw = (List<Object>) libGetBookcases.invoke(lib);
            List<BookcaseSnapshot> out = new ArrayList<>(raw.size());
            for (Object bc : raw)
            {
                BookcaseSnapshot snap = toBookcase(bc);
                if (snap != null) out.add(snap);
            }
            return out;
        }
        catch (ReflectiveOperationException e)
        {
            return Collections.emptyList();
        }
    }

    private BookcaseSnapshot toBookcase(Object bc)
    {
        if (bc == null) return null;
        try
        {
            WorldPoint loc = (WorldPoint) bookcaseGetLocation.invoke(bc);
            BookSnapshot known = toBook(bookcaseGetBook.invoke(bc));
            boolean bookSet = (Boolean) bookcaseIsBookSet.invoke(bc);
            @SuppressWarnings("unchecked")
            Set<Object> rawPossible = (Set<Object>) bookcaseGetPossibleBooks.invoke(bc);
            Set<BookSnapshot> possible = new HashSet<>();
            if (rawPossible != null)
            {
                for (Object b : rawPossible)
                {
                    BookSnapshot bs = toBook(b);
                    if (bs != null) possible.add(bs);
                }
            }
            return new BookcaseSnapshot(loc, known, possible, bookSet);
        }
        catch (ReflectiveOperationException e)
        {
            return null;
        }
    }

    /**
     * Fast path for the hot poll loop: checks {@code Bookcase.isBookSet()} for the bookcase
     * at {@code loc} without allocating snapshot objects for the entire bookcase list.
     * Returns true when the upstream Library has observed our search outcome (this is the
     * same condition under which the {@code KourendLibraryOverlay} drops the white-square
     * highlight from a tile).
     */
    public boolean isBookcaseSearchedAt(WorldPoint loc)
    {
        if (!wire() || loc == null) return false;
        Object lib = library();
        if (lib == null) return false;
        try
        {
            @SuppressWarnings("unchecked")
            List<Object> raw = (List<Object>) libGetBookcases.invoke(lib);
            for (Object bc : raw)
            {
                WorldPoint bcLoc = (WorldPoint) bookcaseGetLocation.invoke(bc);
                if (loc.equals(bcLoc)) return (Boolean) bookcaseIsBookSet.invoke(bc);
            }
        }
        catch (ReflectiveOperationException ignored) {}
        return false;
    }

    /** Resolve a library {@link BookSnapshot} from an inventory item id, or null if not a library book. */
    public BookSnapshot bookForItemId(int itemId)
    {
        if (!wire()) return null;
        try
        {
            return toBook(bookByIdStatic.invoke(null, itemId));
        }
        catch (ReflectiveOperationException e)
        {
            return null;
        }
    }

    private BookSnapshot toBook(Object book)
    {
        if (book == null) return null;
        try
        {
            String name = (String) enumName.invoke(book);
            int id = (Integer) bookGetItem.invoke(book);
            String shortName = (String) bookGetShortName.invoke(book);
            String full = (String) bookGetName.invoke(book);
            return new BookSnapshot(name, id, shortName, full);
        }
        catch (ReflectiveOperationException e)
        {
            return null;
        }
    }
}
