package fr.cactusstudio.bibliofx;

import fr.cactusstudio.bibliofx.model.Book;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
// import removed: import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.Region;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

import java.io.IOException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Contrôleur principal de l'application (hello-view.fxml).
 * <p>
 * Gère la table des livres, les filtres (recherche, genre, disponibilité),
 * l'ouverture des boîtes de dialogue d'ajout/édition, et la gestion
 * multi-bibliothèques via {@link LibraryRepository}.
 */
public class Controller {
    @FXML private TextField searchField;
    @FXML private ComboBox<String> genreFilter;
    @FXML private CheckBox availableFilter;
    @FXML private ComboBox<String> readingStatusFilter;
    @FXML private ComboBox<String> libraryCombo;
    @FXML private TableView<Book> table;
    @FXML private TableColumn<Book, String> titleCol;
    @FXML private TableColumn<Book, String> authorCol;
    @FXML private TableColumn<Book, Integer> yearCol;
    @FXML private TableColumn<Book, String> genreCol;
    @FXML private TableColumn<Book, Boolean> availableCol;
    @FXML private TableColumn<Book, String> readingStatusCol;
    @FXML private TableColumn<Book, String> addedCol;

    // Included controller for book.fxml
    @FXML private BookDetailController bookDetailController; // populated via fx:include + fx:id convention

    private final ObservableList<Book> master = FXCollections.observableArrayList();
    private FilteredList<Book> filtered;
    private final LibraryRepository repository = new LibraryRepository();
    private String currentLibrary;

    /**
     * Initialisation de la vue et des composants JavaFX après le chargement du FXML.
     * Configure les colonnes, charge les données, met en place les filtres et les tris.
     */
    @FXML
    private void initialize() {
        // Libraries UI
        currentLibrary = repository.getCurrentLibrary();
        libraryCombo.setItems(FXCollections.observableArrayList(repository.listLibraries()));
        libraryCombo.getSelectionModel().select(currentLibrary);
        libraryCombo.valueProperty().addListener((obs, oldName, newName) -> onSwitchLibrary(oldName, newName));


        List<Book> loaded = repository.load(currentLibrary);

        long nowInit = System.currentTimeMillis();
        for (Book b : loaded) {
            if (b.getAddedAt() == null || b.getAddedAt() <= 0) {
                b.setAddedAt(nowInit);
            }

        }
        master.setAll(loaded);

        // Table columns bindings
        titleCol.setCellValueFactory(new PropertyValueFactory<>("title"));
        authorCol.setCellValueFactory(new PropertyValueFactory<>("author"));
        yearCol.setCellValueFactory(new PropertyValueFactory<>("year"));
        genreCol.setCellValueFactory(new PropertyValueFactory<>("genre"));
        availableCol.setCellValueFactory(new PropertyValueFactory<>("available"));
        if (readingStatusCol != null) {
            readingStatusCol.setCellValueFactory(new PropertyValueFactory<>("readingStatus"));
        }
        if (addedCol != null) {
            addedCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
                    cd.getValue() != null ? cd.getValue().getAddedAtDateOnly() : ""));
            addedCol.setComparator((a, b) -> {
                java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
                try {
                    java.time.LocalDate da = (a == null || a.isBlank() || "—".equals(a)) ? null : java.time.LocalDate.parse(a, fmt);
                    java.time.LocalDate db = (b == null || b.isBlank() || "—".equals(b)) ? null : java.time.LocalDate.parse(b, fmt);
                    if (da == null && db == null) return 0;
                    if (da == null) return -1;
                    if (db == null) return 1;
                    return da.compareTo(db);
                } catch (Exception ex) {
                    return 0;
                }
            });
        }
        //"Disponible" or "Emprunté le <date>"
        table.setEditable(false);
        availableCol.setEditable(false);
        availableCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText("");
                } else if (item) {
                    setText("Disponible");
                } else {
                    Book rowBook = (Book) getTableRow().getItem();
                    String when = (rowBook != null) ? rowBook.getBorrowedAtDateOnly() : "—";
                    setText((when == null || when.isBlank() || "—".equals(when)) ? "Emprunté" : ("Prêté le " + when));
                }
            }
        });

        // Filters
        filtered = new FilteredList<>(master, b -> true);
        table.setItems(filtered);

        searchField.textProperty().addListener((obs, o, n) -> applyFilters());
        availableFilter.selectedProperty().addListener((obs, o, n) -> applyFilters());
        genreFilter.valueProperty().addListener((obs, o, n) -> applyFilters());
        if (readingStatusFilter != null) {
            readingStatusFilter.setItems(FXCollections.observableArrayList("Tous","Non lu","En cours de lecture","Lu"));
            readingStatusFilter.valueProperty().addListener((obs, o, n) -> applyFilters());
            // Default
            readingStatusFilter.getSelectionModel().select("Tous");
        }

        // Populate genre filter from existing data
        refreshGenreFilterItems();

        // Selection listener to update details
        table.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (bookDetailController != null) bookDetailController.setBook(n);
        });

        // Sort table by title initially
        table.getSortOrder().add(titleCol);
        titleCol.setSortType(TableColumn.SortType.ASCENDING);
    }

    /** Récupère les genres présents dans les données et alimente le filtre Genre. */
    private void refreshGenreFilterItems() {
        Set<String> genres = new HashSet<>();
        for (Book b : master) { if (b.getGenre() != null && !b.getGenre().isBlank()) genres.add(b.getGenre()); }
        List<String> items = genres.stream().sorted(Comparator.naturalOrder()).toList();
        genreFilter.setItems(FXCollections.observableArrayList(items));
    }

    /** Réinitialise tous les filtres et relance l'application des filtres. */
    private void resetFilters() {
        if (searchField != null) searchField.clear();
        if (availableFilter != null) availableFilter.setSelected(false);
        if (genreFilter != null) genreFilter.getSelectionModel().clearSelection();
        if (readingStatusFilter != null) readingStatusFilter.getSelectionModel().clearSelection();
        applyFilters();
    }

    /** Applique les filtres de recherche/genre/disponibilité à la table. */
    private void applyFilters() {
        String query = searchField.getText() == null ? "" : searchField.getText().toLowerCase().trim();
        String genre = genreFilter.getValue();
        boolean onlyAvailable = availableFilter.isSelected();
        String rs = readingStatusFilter != null ? readingStatusFilter.getValue() : null;

        Predicate<Book> p = b -> {
            boolean matchesTitle = query.isEmpty() || (b.getTitle() != null && b.getTitle().toLowerCase().contains(query));
            boolean matchesGenre = (genre == null || genre.isBlank()) || (genre.equals(b.getGenre()));
            boolean matchesAvail = !onlyAvailable || b.isAvailable();
            boolean matchesReading = (rs == null || rs.isBlank() || "Tous".equals(rs)) || (b.getReadingStatus() != null && rs.equalsIgnoreCase(b.getReadingStatus()));
            return matchesTitle && matchesGenre && matchesAvail && matchesReading;
        };
        filtered.setPredicate(p);
    }

    /** Ouvre la boîte de dialogue d'ajout et ajoute le livre si validé. */
    @FXML
    private void onAdd() {
        Book created = openAddEditDialog(null, "addBook.fxml", "Ajouter un livre");
        if (created != null) {
            master.add(created);
            refreshGenreFilterItems();
            repository.save(master);
        }
    }

    /** Ouvre la boîte de dialogue d'édition pour le livre sélectionné et applique les modifications. */
    @FXML
    private void onEdit() {
        Book selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        Book updated = openAddEditDialog(selected, "editBook.fxml", "Modifier le livre");
        if (updated != null) {
            selected.setTitle(updated.getTitle());
            selected.setAuthor(updated.getAuthor());
            selected.setYear(updated.getYear());
            selected.setGenre(updated.getGenre());
            selected.setSummary(updated.getSummary());
            selected.setCoverUrl(updated.getCoverUrl());
            selected.setReadingStatus(updated.getReadingStatus());
            boolean wasAvailable = selected.isAvailable();
            boolean nowAvailable = updated.isAvailable();
            selected.setAvailable(nowAvailable);
            if (wasAvailable && !nowAvailable) {
                selected.setBorrowedAt(System.currentTimeMillis());
            } else if (!wasAvailable && nowAvailable) {
                selected.setBorrowedAt(null);
            }
            table.refresh();
            if (bookDetailController != null) bookDetailController.setBook(selected);
            refreshGenreFilterItems();
            repository.save(master);
        }
    }

    /** Supprime le livre sélectionné après confirmation. */
    @FXML
    private void onDelete() {
        Book selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        master.remove(selected);
        if (bookDetailController != null) bookDetailController.setBook(null);
        refreshGenreFilterItems();
        repository.save(master);
    }

    /** Force une sauvegarde immédiate de la bibliothèque courante. */
    @FXML
    private void onSave() {
        repository.save(master);
    }

    /** Recharge les données de la bibliothèque courante depuis le stockage. */
    @FXML
    private void onLoad() {
        master.setAll(repository.load());
        refreshGenreFilterItems();
        applyFilters();
    }

    /**
     * Ouvre un dialogue modal pour créer/éditer un livre.
     * @param initial livre initial (null pour une création)
     * @param fxml    ressource FXML de la boîte de dialogue
     * @param title   titre de la fenêtre
     * @return le livre saisi/modifié, ou null si annulé
     */
    private Book openAddEditDialog(Book initial, String fxml, String title) {
        try {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource(fxml));
            Region root = loader.load();
            AddEditBookController ctrl = loader.getController();
            ctrl.setInitial(initial);

            Stage dialog = new Stage();
            dialog.setTitle(title);
            dialog.initModality(Modality.WINDOW_MODAL);
            Stage owner = (Stage) table.getScene().getWindow();
            if (owner != null) dialog.initOwner(owner);
            Scene scene = new Scene(root);
            scene.getStylesheets().add(Objects.requireNonNull(Main.class.getResource("styles.css")).toExternalForm());
            dialog.setScene(scene);
            dialog.sizeToScene();
            dialog.showAndWait();
            return ctrl.getResult();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Bascule vers une autre bibliothèque: sauvegarde l'actuelle, charge la nouvelle et met à jour l'UI.
     * @param oldName nom précédent
     * @param newName nouveau nom sélectionné
     */
    private void onSwitchLibrary(String oldName, String newName) {
        if (newName == null || Objects.equals(newName, currentLibrary)) return;
        // Save current library before switching
        repository.save(currentLibrary, master);
        // Load new library
        List<Book> loaded = repository.load(newName);
        long now = System.currentTimeMillis();
        for (Book b : loaded) {
            if (b.getAddedAt() == null || b.getAddedAt() <= 0) b.setAddedAt(now);
        }
        currentLibrary = newName;
        repository.setCurrentLibrary(newName);
        master.setAll(loaded);
        if (bookDetailController != null) bookDetailController.setBook(null);
        refreshGenreFilterItems();
        resetFilters();
    }

    /** Demande un nom et crée une nouvelle bibliothèque, puis y bascule. */
    @FXML
    private void onNewLibrary() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Nouvelle bibliothèque");
        dialog.setHeaderText("Créer une nouvelle bibliothèque");
        dialog.setContentText("Nom:");
        dialog.getEditor().setPromptText("Nom de la bibliothèque");
        if (libraryCombo != null && libraryCombo.getScene() != null) {
            dialog.initOwner(libraryCombo.getScene().getWindow());
        }
        dialog.showAndWait().ifPresent(name -> {
            String trimmed = name.trim();
            if (trimmed.isEmpty()) return;
            if (!repository.createLibrary(trimmed)) {
                Alert warn = new Alert(Alert.AlertType.WARNING, "Impossible de créer cette bibliothèque (nom déjà utilisé ?)");
                if (libraryCombo != null && libraryCombo.getScene() != null) {
                    warn.initOwner(libraryCombo.getScene().getWindow());
                }
                warn.showAndWait();
                return;
            }

            libraryCombo.setItems(FXCollections.observableArrayList(repository.listLibraries()));
            libraryCombo.getSelectionModel().select(trimmed);
        });
    }

    /** Renomme la bibliothèque courante après saisie utilisateur. */
    @FXML
    private void onRenameLibrary() {
        if (currentLibrary == null) return;
        TextInputDialog dialog = new TextInputDialog(currentLibrary);
        dialog.setTitle("Renommer la bibliothèque");
        dialog.setHeaderText("Nouveau nom pour la bibliothèque");
        dialog.setContentText("Nom:");
        if (libraryCombo != null && libraryCombo.getScene() != null) {
            dialog.initOwner(libraryCombo.getScene().getWindow());
        }
        dialog.showAndWait().ifPresent(newName -> {
            String trimmed = newName.trim();
            if (trimmed.isEmpty() || Objects.equals(trimmed, currentLibrary)) return;
            if (!repository.renameLibrary(currentLibrary, trimmed)) {
                Alert warn = new Alert(Alert.AlertType.WARNING, "Impossible de renommer (nom déjà utilisé ?)");
                if (libraryCombo != null && libraryCombo.getScene() != null) {
                    warn.initOwner(libraryCombo.getScene().getWindow());
                }
                warn.showAndWait();
                return;
            }
            currentLibrary = trimmed;
            libraryCombo.setItems(FXCollections.observableArrayList(repository.listLibraries()));
            libraryCombo.getSelectionModel().select(currentLibrary);
        });
    }

    /** Supprime la bibliothèque courante après confirmation et bascule sur une restante. */
    @FXML
    private void onDeleteLibrary() {
        if (currentLibrary == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Supprimer la bibliothèque '" + currentLibrary + "' ?", ButtonType.OK, ButtonType.CANCEL);
        confirm.setHeaderText("Confirmation de suppression");
        if (libraryCombo != null && libraryCombo.getScene() != null) {
            confirm.initOwner(libraryCombo.getScene().getWindow());
        }
        var res = confirm.showAndWait();
        if (res.isPresent() && res.get() == ButtonType.OK) {
            if (!repository.deleteLibrary(currentLibrary)) {
                Alert warn = new Alert(Alert.AlertType.WARNING, "Impossible de supprimer (au moins une bibliothèque doit rester)");
                if (libraryCombo != null && libraryCombo.getScene() != null) {
                    warn.initOwner(libraryCombo.getScene().getWindow());
                }
                warn.showAndWait();
                return;
            }
            // Après la suppression, le repo a automatiquement sélectionné une bibliothèque actuelle si nécessaire.
            currentLibrary = repository.getCurrentLibrary();
            libraryCombo.setItems(FXCollections.observableArrayList(repository.listLibraries()));
            libraryCombo.getSelectionModel().select(currentLibrary);
            master.setAll(repository.load(currentLibrary));
            if (bookDetailController != null) bookDetailController.setBook(null);
            refreshGenreFilterItems();
            resetFilters();
        }
    }
}
