package fr.cactusstudio.bibliofx;

import fr.cactusstudio.bibliofx.model.Book;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import java.util.Locale;

/**
 * Contrôleur de la boîte de dialogue d'ajout/édition d'un livre.
 * <p>
 * Valide les champs, propose des suggestions via l'API Google Books,
 * et renvoie un objet {@link Book} en résultat si l'utilisateur valide.
 */
public class AddEditBookController {
    @FXML private TextField titleField;
    @FXML private TextField authorField;
    @FXML private TextField yearField;
    @FXML private ComboBox<String> genreCombo;
    @FXML private ComboBox<String> readingStatusCombo;
    @FXML private CheckBox availableCheck;
    @FXML private TextArea summaryField;
    @FXML private TextField coverUrlField;
    @FXML private Button cancelBtn;
    @FXML private Button okBtn;

    private Book result;


    private String initialTitle = null;
    private boolean titleModified = false;

    /**
     * Initialise la boîte de dialogue: remplit la liste des genres, prépare la validation
     * des champs et la logique de suggestions.
     */
    @FXML
    private void initialize() {
        genreCombo.setItems(FXCollections.observableArrayList(
                "Roman", "Essai", "Science", "Histoire", "Biographie", "Fantastique", "Policier", "Autre"
        ));
        if (readingStatusCombo != null) {
            readingStatusCombo.setItems(FXCollections.observableArrayList(
                    "Non lu", "En cours de lecture", "Lu"
            ));
            readingStatusCombo.setValue("Non lu");
        }
        // Controle du Year field: only digits, max 4
        yearField.setTextFormatter(new javafx.scene.control.TextFormatter<String>(change -> {
            String newText = change.getControlNewText();
            if (newText.matches("\\d{0,4}")) return change; // allow up to 4 digits
            return null;
        }));

        // Validation: disable OK until required fields are valid
        okBtn.disableProperty().bind(titleField.textProperty().isEmpty()
                .or(authorField.textProperty().isEmpty())
                .or(yearField.textProperty().length().lessThan(4)));

        if (summaryField != null) summaryField.setPromptText("Résumé (facultatif)");
        if (coverUrlField != null) coverUrlField.setPromptText("URL ou chemin de l'image de couverture (facultatif)");

        // Default disponible
        availableCheck.setAllowIndeterminate(false);
        availableCheck.setIndeterminate(false);
        availableCheck.setSelected(true);

        // Mettre à jour uniquement l'état de modification; ne pas déclencher la recherche automatiquement
        titleField.textProperty().addListener((obs, oldV, newV) -> {
            String q = newV == null ? "" : newV.trim();
            if (initialTitle != null) {
                titleModified = !q.equals(initialTitle.trim());
            } else {
                titleModified = !q.isBlank();
            }
            // Si le texte est trop court, fermer le menu de suggestion éventuel
            if ((q.length() < 2) && suggestionMenu != null && suggestionMenu.isShowing()) {
                suggestionMenu.hide();
            }
        });
        titleField.focusedProperty().addListener((o, was, is) -> {
            if (!is && suggestionMenu != null && suggestionMenu.isShowing()) suggestionMenu.hide();
        });
    }

    private static class Suggestion {
        final String title;
        final String author;
        final Integer year;
        final String genre;
        final String summary;
        final String coverUrl;
        Suggestion(String t, String a, Integer y, String g, String s, String cu) { this.title = t; this.author = a; this.year = y; this.genre = g; this.summary = s; this.coverUrl = cu; }
        @Override public String toString() { return title + (author != null ? " — " + author : ""); }
    }

    private final javafx.collections.ObservableList<Suggestion> suggestions = FXCollections.observableArrayList();

    /**
     * Lance en arrière-plan la recherche de suggestions Google Books pour la requête.
     * @param query texte à rechercher (typiquement le titre)
     */
    private void fetchSuggestionsAsync(String query) {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                var uri = java.net.URI.create("https://www.googleapis.com/books/v1/volumes?q=" + java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8) + "&maxResults=5");
                var client = java.net.http.HttpClient.newHttpClient();
                var req = java.net.http.HttpRequest.newBuilder(uri).GET().build();
                var resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
                var json = resp.body();
                var parsed = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
                var items = parsed.has("items") ? parsed.getAsJsonArray("items") : new com.google.gson.JsonArray();
                java.util.List<Suggestion> list = new java.util.ArrayList<>();
                for (var el : items) {
                    var vol = el.getAsJsonObject().getAsJsonObject("volumeInfo");
                    String t = vol.has("title") ? vol.get("title").getAsString() : null;
                    String a = null;
                    if (vol.has("authors") && vol.get("authors").isJsonArray() && vol.getAsJsonArray("authors").size() > 0) {
                        a = vol.getAsJsonArray("authors").get(0).getAsString();
                    }
                    Integer y = null;
                    if (vol.has("publishedDate")) {
                        String pd = vol.get("publishedDate").getAsString();
                        // take first 4 digits if present
                        if (pd.length() >= 4 && pd.substring(0,4).chars().allMatch(Character::isDigit)) {
                            y = Integer.parseInt(pd.substring(0,4));
                        }
                    }
                    String suggestedGenre = null;
                    if (vol.has("categories") && vol.get("categories").isJsonArray() && vol.getAsJsonArray("categories").size() > 0) {
                        String rawCat = vol.getAsJsonArray("categories").get(0).getAsString();
                        suggestedGenre = mapCategoryToGenre(rawCat);
                    }

                    String summary = vol.has("description") ? vol.get("description").getAsString() : null;

                    String coverUrl = null;
                    if (vol.has("imageLinks") && vol.get("imageLinks").isJsonObject()) {
                        var imgs = vol.getAsJsonObject("imageLinks");

                        String[] keys = new String[]{"extraLarge","large","medium","small","thumbnail","smallThumbnail"};
                        for (String k : keys) {
                            if (imgs.has(k) && imgs.get(k).isJsonPrimitive()) {
                                coverUrl = imgs.get(k).getAsString();
                                break;
                            }
                        }
                    }
                    if (coverUrl != null && coverUrl.startsWith("http:")) {
                        coverUrl = "https:" + coverUrl.substring(5);
                    }
                    if (t != null) list.add(new Suggestion(t, a, y, suggestedGenre, summary, coverUrl));
                }
                javafx.application.Platform.runLater(() -> {
                    suggestions.setAll(list);
                    showSuggestionPopup();
                });
            } catch (Exception ignored) {
            }
        });
    }

    private javafx.scene.control.ContextMenu suggestionMenu;

    // Map a Google Books category string to one of our supported genres
    /**
         * Convertit une catégorie Google Books en l'un des genres supportés par l'application.
         * @param rawCategory chaîne de catégorie brute
         * @return genre mappé ou null si non reconnu
         */
        private static String mapCategoryToGenre(String rawCategory) {
        if (rawCategory == null) return null;
        String c = rawCategory.toLowerCase(Locale.ROOT);

        c = c.replace('’', '\'');

        if (c.contains("roman") || c.contains("fiction") || c.contains("novel")) return "Roman";
        if (c.contains("essai") || c.contains("essay")) return "Essai";
        if (c.contains("science") || c.contains("sciences")) return "Science";
        if (c.contains("histoire") || c.contains("history")) return "Histoire";
        if (c.contains("biograph") || c.contains("autobiograph")) return "Biographie";
        if (c.contains("fantasy") || c.contains("fantastique") || c.contains("fantaisie")) return "Fantastique";
        if (c.contains("policier") || c.contains("detective") || c.contains("crime") || c.contains("mystery") || c.contains("thriller")) return "Policier";

        if (c.contains("/")) {
            String[] parts = c.split("/");
            for (String p : parts) {
                String m = mapCategoryToGenre(p.trim());
                if (m != null) return m;
            }
        }
        return null;
    }

    @FXML
    private void onSearchSuggestions() {
        String q = titleField.getText() == null ? "" : titleField.getText().trim();
        // Met à jour le flag de modification pour le mode édition
        if (initialTitle != null) {
            titleModified = !q.equals(initialTitle.trim());
        } else {
            titleModified = !q.isBlank();
        }
        if (q.length() >= 2) {
            fetchSuggestionsAsync(q);
        } else if (suggestionMenu != null && suggestionMenu.isShowing()) {
            suggestionMenu.hide();
        }
    }

    private void showSuggestionPopup() {
        String q = titleField.getText() == null ? "" : titleField.getText().trim();
        boolean allowed = q.length() >= 2 && (initialTitle == null || titleModified);
        if (!allowed) {
            if (suggestionMenu != null && suggestionMenu.isShowing()) suggestionMenu.hide();
            return;
        }
        if (suggestionMenu == null) {
            suggestionMenu = new javafx.scene.control.ContextMenu();
        }
        suggestionMenu.getItems().clear();
        for (Suggestion s : suggestions) {
            var item = new javafx.scene.control.MenuItem(s.toString());
            item.setOnAction(e -> applySuggestion(s));
            suggestionMenu.getItems().add(item);
        }
        if (!suggestionMenu.getItems().isEmpty()) {

            var scene = titleField.getScene();
            double sceneWidth = scene != null ? scene.getWidth() : 0;

            double desiredWidth = Math.max(titleField.getWidth(), 200);
            double maxAllowed = sceneWidth > 0 ? sceneWidth : desiredWidth;
            double finalWidth = Math.min(desiredWidth, maxAllowed);


            suggestionMenu.show(titleField, javafx.geometry.Side.BOTTOM, 0, 0);
            try {
                var skinNode = suggestionMenu.getSkin() != null ? suggestionMenu.getSkin().getNode() : null;
                var popupWindow = skinNode != null && skinNode.getScene() != null ? skinNode.getScene().getWindow() : null;
                if (popupWindow != null) {
                    double target = sceneWidth > 0 ? Math.min(popupWindow.getWidth(), sceneWidth) : Math.min(popupWindow.getWidth(), finalWidth);
                    popupWindow.setWidth(target);
                }
            } catch (Exception ignored) { }
        } else if (suggestionMenu.isShowing()) {
            suggestionMenu.hide();
        }
    }

    private void applySuggestion(Suggestion s) {
        if (s.title != null) titleField.setText(s.title);
        if (s.author != null) authorField.setText(s.author);
        if (s.year != null) yearField.setText(Integer.toString(s.year));

        if (s.genre != null && !s.genre.isBlank()) {

            if (genreCombo.getValue() == null || genreCombo.getValue().isBlank() || "Autre".equals(genreCombo.getValue())) {
                genreCombo.setValue(s.genre);
            }
        } else if (genreCombo.getValue() == null || genreCombo.getValue().isBlank()) {
            genreCombo.setValue("Autre");
        }

        if (summaryField != null) {
            String current = summaryField.getText();
            if (current == null || current.isBlank()) {
                if (s.summary != null && !s.summary.isBlank()) {
                    summaryField.setText(s.summary);
                }
            }
        }

        if (coverUrlField != null) {
            String current = coverUrlField.getText();
            if ((current == null || current.isBlank()) && s.coverUrl != null && !s.coverUrl.isBlank()) {
                coverUrlField.setText(s.coverUrl);
            }
        }
        if (suggestionMenu != null) suggestionMenu.hide();
    }

    public void setInitial(Book book) {
        if (book != null) {

            initialTitle = book.getTitle() == null ? "" : book.getTitle();
            titleModified = false;

            titleField.setText(book.getTitle());
            authorField.setText(book.getAuthor());
            yearField.setText(Integer.toString(book.getYear()));
            genreCombo.setValue(book.getGenre());
            availableCheck.setAllowIndeterminate(false);
            availableCheck.setIndeterminate(false);
            availableCheck.setSelected(book.isAvailable());
            if (readingStatusCombo != null) readingStatusCombo.setValue(book.getReadingStatus());
            if (summaryField != null) summaryField.setText(book.getSummary());
            if (coverUrlField != null) coverUrlField.setText(book.getCoverUrl());
        } else {

            initialTitle = null;
            titleModified = false;
            availableCheck.setAllowIndeterminate(false);
            availableCheck.setIndeterminate(false);
            availableCheck.setSelected(true);
            if (readingStatusCombo != null) readingStatusCombo.setValue("Non lu");
        }
    }

    public Book getResult() {
        return result;
    }

    @FXML
    private void onCancel() {
        result = null;
        close();
    }

    @FXML
    private void onOk() {
        String title = titleField.getText() == null ? "" : titleField.getText().trim();
        String author = authorField.getText() == null ? "" : authorField.getText().trim();
        String yearTxt = yearField.getText() == null ? "" : yearField.getText().trim();
        String genre = genreCombo.getValue() == null ? "Autre" : genreCombo.getValue();
        boolean available = availableCheck.isSelected();

        // Basic validation
        StringBuilder errors = new StringBuilder();
        if (title.isBlank()) errors.append("- Le titre est requis.\n");
        if (author.isBlank()) errors.append("- L'auteur est requis.\n");
        int year = -1;
        if (!yearTxt.isBlank()) {
            try {
                year = Integer.parseInt(yearTxt);
                if (year < 0 || year > 9999) {
                    errors.append("- L'année doit être comprise entre 0 et 9999.\n");
                }
            } catch (NumberFormatException ex) {
                errors.append("- L'année doit être un nombre.\n");
            }
        } else {
            errors.append("- L'année est requise (4 chiffres).\n");
        }
        if (errors.length() > 0) {
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.WARNING);
            alert.setTitle("Champs invalides");
            alert.setHeaderText("Veuillez corriger les erreurs suivantes :");
            alert.setContentText(errors.toString());
            // Ensure alert is owned by dialog window so it stays on top
            if (okBtn != null && okBtn.getScene() != null) {
                alert.initOwner(okBtn.getScene().getWindow());
            }
            alert.showAndWait();
            return;
        }

        result = new Book(title, author, year, genre, available);
        // Optional fields
        String summary = summaryField != null && summaryField.getText() != null ? summaryField.getText().trim() : null;
        String cover = coverUrlField != null && coverUrlField.getText() != null ? coverUrlField.getText().trim() : null;
        if (summary != null && summary.isBlank()) summary = null;
        if (cover != null && cover.isBlank()) cover = null;
        result.setSummary(summary);
        result.setCoverUrl(cover);
        // Reading status
        String rs = (readingStatusCombo != null && readingStatusCombo.getValue() != null) ? readingStatusCombo.getValue() : "Non lu";
        result.setReadingStatus(rs);
        
        // Set dates
        long now = System.currentTimeMillis();
        result.setAddedAt(now);
        if (!available) {
            result.setBorrowedAt(now);
        } else {
            result.setBorrowedAt(null);
        }
        close();
    }

    private void close() {
        Stage stage = (Stage) okBtn.getScene().getWindow();
        stage.close();
    }
}