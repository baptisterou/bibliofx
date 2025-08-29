package fr.cactusstudio.bibliofx;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import fr.cactusstudio.bibliofx.model.Book;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Dépôt de persistance des bibliothèques et des livres.
 * <p>
 * Les données sont stockées dans un fichier JSON dans le répertoire utilisateur (~/.bibliofx.json).
 * Cette classe gère la migration depuis un ancien format (liste simple) vers un format
 * multi-bibliothèques, ainsi que les opérations CRUD de base.
 */
public class LibraryRepository {
    /** Fichier de données JSON dans le répertoire utilisateur. */
    private final File dataFile;
    /** Instance Gson configurée (compact pour de meilleures perfs). */
    private final Gson gson;

    private static final String DEFAULT_LIBRARY = "Bibliothèque";

    // Cache mémoire des données pour éviter les relectures disque à chaque appel
    private volatile Data cached;
    private final Object lock = new Object();

    // Ecriture différée (debounce)
    private final java.util.concurrent.ScheduledExecutorService scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "bibliofx-repo-writer");
        t.setDaemon(true);
        return t;
    });
    private java.util.concurrent.ScheduledFuture<?> pendingWrite;
    private static final long DEBOUNCE_MS = 300;

    private static final Type BOOK_LIST_TYPE = new TypeToken<List<Book>>(){}.getType();

    /**
     * Construit le dépôt et initialise le fichier de données si nécessaire.
     */
    public LibraryRepository() {
        String home = System.getProperty("user.home");
        // Sur Windows, le répertoire utilisateur est valide, mais le fichier peut ne pas exister.
        // Utiliser un nom de fichier distinct pour éviter les problèmes de nom réservé et s'assurer de la création.
        this.dataFile = new File(home, ".bibliofx.json");
        this.gson = new GsonBuilder().create();
        ensureInitialized();
        // Charger une fois en cache
        this.cached = readDataFromDisk();
    }

    // Modèle persistant en JSON
    private static class Data {
        String current;
        Map<String, List<Book>> libraries;
    }

    /**
     * S'assure que le fichier de données existe et est au bon format, sinon le crée
     * ou migre depuis l'ancien format (liste de livres seule).
     */
    private void ensureInitialized() {
        if (!dataFile.exists()) {
            // S'assurer que le dossier parent existe (utile si user.home pointe vers un chemin non créé)
            File parent = dataFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            // Créer un fichier initial valide quel que soit l'OS (Windows/Linux/macOS)
            Data d = new Data();
            d.current = DEFAULT_LIBRARY;
            d.libraries = new LinkedHashMap<>();
            d.libraries.put(DEFAULT_LIBRARY, new ArrayList<>());
            writeData(d);
            return;
        }
        // Si le fichier existe mais est un tableau (ancien format), migrer
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(dataFile), StandardCharsets.UTF_8))) {
            JsonElement root = com.google.gson.JsonParser.parseReader(br);
            if (root.isJsonArray()) {
                List<Book> old = gson.fromJson(root, BOOK_LIST_TYPE);
                Data d = new Data();
                d.current = DEFAULT_LIBRARY;
                d.libraries = new LinkedHashMap<>();
                d.libraries.put(DEFAULT_LIBRARY, old != null ? old : new ArrayList<>());
                writeData(d);
            } else if (root.isJsonObject()) {
                // ok
            } else {
                // Inattendu -> réinitialisation
                Data d = new Data();
                d.current = DEFAULT_LIBRARY;
                d.libraries = new LinkedHashMap<>();
                d.libraries.put(DEFAULT_LIBRARY, new ArrayList<>());
                writeData(d);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Lit l'ensemble des données du fichier JSON.
     * @return l'objet Data désérialisé
     */
    // Lecture brute depuis le disque (utilisée pour l'init/recache)
    private Data readDataFromDisk() {
        ensureInitialized();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(dataFile), StandardCharsets.UTF_8))) {
            Data d = gson.fromJson(br, Data.class);
            if (d == null) {
                d = new Data();
                d.current = DEFAULT_LIBRARY;
                d.libraries = new LinkedHashMap<>();
                d.libraries.put(DEFAULT_LIBRARY, new ArrayList<>());
            }
            return d;
        } catch (IOException e) {
            e.printStackTrace();
            Data d = new Data();
            d.current = DEFAULT_LIBRARY;
            d.libraries = new LinkedHashMap<>();
            d.libraries.put(DEFAULT_LIBRARY, new ArrayList<>());
            return d;
        }
    }

    // Accès au modèle en cache
    private Data readData() {
        Data d = cached;
        if (d == null) {
            synchronized (lock) {
                if (cached == null) cached = readDataFromDisk();
                d = cached;
            }
        }
        return d;
    }

    /**
     * Écrit l'objet Data dans le fichier JSON.
     * @param d données à persister
     */
    private void writeData(Data d) {
        // Met à jour le cache et programme une écriture différée
        synchronized (lock) {
            this.cached = d;
            if (pendingWrite != null) {
                pendingWrite.cancel(false);
            }
            pendingWrite = scheduler.schedule(this::flushToDiskSafely, DEBOUNCE_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
        }
    }

    /** Force l'écriture immédiate sur disque (utilisé rarement). */
    private void flushToDiskSafely() {
        Data snapshot;
        synchronized (lock) {
            snapshot = this.cached;
        }
        if (snapshot == null) return;
        // Ecriture atomique via fichier temporaire
        File tmp = new File(dataFile.getParentFile() != null ? dataFile.getParentFile() : new File("."), dataFile.getName() + ".tmp");
        try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmp, false), StandardCharsets.UTF_8))) {
            gson.toJson(snapshot, bw);
            bw.flush();
            // sur certains OS, renameTo est atomique quand même volume
            if (!tmp.renameTo(dataFile)) {
                // fallback: copie par flux
                try (InputStream in = new FileInputStream(tmp); OutputStream out = new FileOutputStream(dataFile, false)) {
                    in.transferTo(out);
                    out.flush();
                }
                // supprimer tmp
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // API publique
    /** @return la liste des bibliothèques disponibles */
    public List<String> listLibraries() {
        Data d = readData();
        return new ArrayList<>(d.libraries.keySet());
    }

    /** @return le nom de la bibliothèque courante */
    public String getCurrentLibrary() {
        return readData().current;
    }

    /**
     * Définit la bibliothèque courante (si elle existe).
     * @param name nom de la bibliothèque
     */
    public void setCurrentLibrary(String name) {
        Data d = readData();
        if (!d.libraries.containsKey(name)) return;
        d.current = name;
        writeData(d);
    }

    /**
     * Charge les livres d'une bibliothèque donnée.
     * @param name nom de la bibliothèque
     * @return une copie de la liste des livres
     */
    public List<Book> load(String name) {
        Data d = readData();
        return new ArrayList<>(d.libraries.getOrDefault(name, new ArrayList<>()));
    }

    /**
     * Sauvegarde la liste de livres d'une bibliothèque donnée.
     * @param name nom de la bibliothèque
     * @param books livres à sauvegarder
     */
    public void save(String name, List<Book> books) {
        Data d = readData();
        if (!d.libraries.containsKey(name)) {
            d.libraries.put(name, new ArrayList<>());
        }
        d.libraries.put(name, new ArrayList<>(books));
        writeData(d);
    }

    // Méthodes rétro‑compatibles (opèrent sur la bibliothèque courante)
    /** @return les livres de la bibliothèque courante */
    public List<Book> load() {
        return load(getCurrentLibrary());
    }

    /**
     * Sauvegarde les livres dans la bibliothèque courante.
     * @param books livres à sauvegarder
     */
    public void save(List<Book> books) {
        save(getCurrentLibrary(), books);
    }

    /**
     * Crée une nouvelle bibliothèque et la définit comme courante.
     * @param name nom de la nouvelle bibliothèque
     * @return true si créée, false sinon
     */
    public boolean createLibrary(String name) {
        if (name == null || name.isBlank()) return false;
        Data d = readData();
        if (d.libraries.containsKey(name)) return false;
        d.libraries.put(name, new ArrayList<>());
        d.current = name;
        writeData(d);
        return true;
    }

    /**
     * Renomme une bibliothèque existante.
     * @param oldName ancien nom
     * @param newName nouveau nom
     * @return true si renommée, false sinon
     */
    public boolean renameLibrary(String oldName, String newName) {
        if (oldName == null || newName == null) return false;
        if (newName.isBlank()) return false;
        Data d = readData();
        if (!d.libraries.containsKey(oldName) || d.libraries.containsKey(newName)) return false;
        List<Book> data = d.libraries.remove(oldName);
        d.libraries.put(newName, data);
        if (Objects.equals(d.current, oldName)) d.current = newName;
        writeData(d);
        return true;
    }

    /**
     * Supprime une bibliothèque (au moins une bibliothèque doit rester).
     * @param name nom de la bibliothèque à supprimer
     * @return true si supprimée, false sinon
     */
    public boolean deleteLibrary(String name) {
        Data d = readData();
        if (!d.libraries.containsKey(name)) return false;
        if (d.libraries.size() <= 1) return false; // conserver au moins une
        d.libraries.remove(name);
        if (Objects.equals(d.current, name)) {
            // basculer sur la première restante
            String next = d.libraries.keySet().iterator().next();
            d.current = next;
        }
        writeData(d);
        return true;
    }
}