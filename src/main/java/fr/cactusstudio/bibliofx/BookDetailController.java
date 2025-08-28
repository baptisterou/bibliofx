package fr.cactusstudio.bibliofx;

import fr.cactusstudio.bibliofx.model.Book;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.io.File;

/**
 * Contrôleur chargé d'afficher les détails d'un livre (vue book.fxml incluse).
 */
public class BookDetailController {
    @FXML private Label titleLabel;
    @FXML private Label authorLabel;
    @FXML private Label yearLabel;
    @FXML private Label genreLabel;
    @FXML private Label availableLabel;
    @FXML private Label addedAtLabel;
    @FXML private Label borrowedAtLabel;
    @FXML private Label readingStatusLabel;
    @FXML private TextArea summaryArea;
    @FXML private ImageView coverImageView;

    /**
     * Alimente la vue avec les informations du livre fourni.
     * Si le livre est null, réinitialise l'affichage.
     *
     * @param book le livre sélectionné ou null
     */
    public void setBook(Book book) {
        if (book == null) {
            titleLabel.setText("Titre: -");
            authorLabel.setText("Auteur: -");
            yearLabel.setText("Année: -");
            genreLabel.setText("Genre: -");
            availableLabel.setText("Disponibilité: -");
            addedAtLabel.setText("Ajouté le: -");
            borrowedAtLabel.setText("Prêté le: -");
            if (readingStatusLabel != null) readingStatusLabel.setText("Statut de lecture: -");
            if (summaryArea != null) summaryArea.setText("");
            if (coverImageView != null) coverImageView.setImage(null);
        } else {
            titleLabel.setText("Titre: " + book.getTitle());
            authorLabel.setText("Auteur: " + book.getAuthor());
            yearLabel.setText("Année: " + book.getYear());
            genreLabel.setText("Genre: " + book.getGenre());
            availableLabel.setText("Disponibilité: " + (book.isAvailable() ? "Disponible" : "Emprunté"));
            addedAtLabel.setText("Ajouté le: " + (book.getAddedAtDateOnly()));
            String borrowed = book.isAvailable() ? "—" : book.getBorrowedAtFormatted();
            borrowedAtLabel.setText("Prêté le: " + borrowed);
            if (readingStatusLabel != null) {
                readingStatusLabel.setText("Statut de lecture: " + book.getReadingStatus());
            }
            // Résumé
            if (summaryArea != null) {
                String sum = book.getSummary();
                summaryArea.setText(sum == null || sum.isBlank() ? "" : sum);
            }
            // Image de couverture
            if (coverImageView != null) {
                String cu = book.getCoverUrl();
                if (cu == null || cu.isBlank()) {
                    coverImageView.setImage(null);
                } else {
                    try {
                        Image img;
                        if (cu.startsWith("http://") || cu.startsWith("https://") || cu.startsWith("file:")) {
                            img = new Image(cu, true);
                        } else {
                            File f = new File(cu);
                            String uri = f.exists() ? f.toURI().toString() : cu;
                            img = new Image(uri, true);
                        }
                        coverImageView.setImage(img);
                    } catch (Exception e) {
                        coverImageView.setImage(null);
                    }
                }
            }
        }
    }
}