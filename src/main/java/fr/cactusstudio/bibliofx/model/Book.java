package fr.cactusstudio.bibliofx.model;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Modèle représentant un livre dans BiblioFX.
 * <p>
 * Contient les informations principales (titre, auteur, année, genre, disponibilité),
 * ainsi que des métadonnées facultatives (résumé, couverture) et des horodatages
 * d'ajout et d'emprunt.
 */
public class Book implements Serializable {
    /** Titre du livre. */
    private String title;
    /** Auteur du livre. */
    private String author;
    /** Année de publication. */
    private int year;
    /** Genre ou catégorie. */
    private String genre;
    /** Indique si le livre est disponible (non emprunté). */
    private boolean available;

    /** Statut de lecture: "non lu", "en cours de lecture", ou "lu". */
    private String readingStatus;

    // Métadonnées
    /** Résumé du livre. */
    private String summary;   // résumé du livre
    /** URL ou chemin de la couverture. */
    private String coverUrl;  // URL ou chemin de la couverture

    // Horodatages
    /** Date d'ajout en millisecondes epoch. */
    private Long addedAt;     // epoch millis
    /** Date d'emprunt en millisecondes epoch, null si non emprunté. */
    private Long borrowedAt;  // epoch millis, null when not borrowed

    @Serial
    private static final long serialVersionUID = 1L;

    /** Constructeur sans argument (requis pour la sérialisation). */
    public Book() {
    }

    /**
     * Constructeur utilitaire.
     *
     * @param title      titre
     * @param author     auteur
     * @param year       année de publication
     * @param genre      genre
     * @param available  disponibilité initiale
     */
    public Book(String title, String author, int year, String genre, boolean available) {
        this.title = title;
        this.author = author;
        this.year = year;
        this.genre = genre;
        this.available = available;
    }

    /** @return le titre */
    public String getTitle() { return title; }
    /** @param title le titre à définir */
    public void setTitle(String title) { this.title = title; }

    /** @return l'auteur */
    public String getAuthor() { return author; }
    /** @param author l'auteur à définir */
    public void setAuthor(String author) { this.author = author; }

    /** @return l'année de publication */
    public int getYear() { return year; }
    /** @param year l'année à définir */
    public void setYear(int year) { this.year = year; }

    /** @return le genre */
    public String getGenre() { return genre; }
    /** @param genre le genre à définir */
    public void setGenre(String genre) { this.genre = genre; }

    /** @return true si disponible */
    public boolean isAvailable() { return available; }
    /** @param available disponibilité à définir */
    public void setAvailable(boolean available) { this.available = available; }

    /** @return le résumé */
    public String getSummary() { return summary; }
    /** @param summary le résumé à définir */
    public void setSummary(String summary) { this.summary = summary; }

    /** Statut de lecture: retourne une valeur par défaut "Non lu" si non défini. */
    public String getReadingStatus() { return (readingStatus == null || readingStatus.isBlank()) ? "Non lu" : readingStatus; }
    /** Définit le statut de lecture. Valeurs suggérées: "Non lu", "En cours de lecture", "Lu". */
    public void setReadingStatus(String readingStatus) { this.readingStatus = readingStatus; }

    /** @return l'URL/chemin de la couverture */
    public String getCoverUrl() { return coverUrl; }
    /** @param coverUrl l'URL/chemin de la couverture */
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }

    /** @return l'horodatage d'ajout (ms) */
    public Long getAddedAt() { return addedAt; }
    /** @param addedAt horodatage d'ajout (ms) */
    public void setAddedAt(Long addedAt) { this.addedAt = addedAt; }

    /** @return l'horodatage d'emprunt (ms), ou null */
    public Long getBorrowedAt() { return borrowedAt; }
    /** @param borrowedAt horodatage d'emprunt (ms), ou null */
    public void setBorrowedAt(Long borrowedAt) { this.borrowedAt = borrowedAt; }

    // Aides de formatage
    /** @return date/heure formatée de l'ajout ("dd/MM/yyyy HH:mm") ou "—" */
    public String getAddedAtFormatted() {
        return formatEpochMillis(addedAt);
    }

    /** @return date/heure formatée de l'emprunt ("dd/MM/yyyy HH:mm") ou "—" */
    public String getBorrowedAtFormatted() {
        return formatEpochMillis(borrowedAt);
    }

    // Format date sans heure
    /** @return date formatée de l'emprunt ("dd/MM/yyyy") ou "—" */
    public String getBorrowedAtDateOnly() {
        return formatEpochMillisDateOnly(borrowedAt);
    }

    /** @return date formatée de l'ajout ("dd/MM/yyyy") ou "—" */
    public String getAddedAtDateOnly() {
        return formatEpochMillisDateOnly(addedAt);
    }

    private static String formatEpochMillis(Long epochMillis) {
        if (epochMillis == null || epochMillis <= 0) return "—";
        LocalDateTime ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        return ldt.format(fmt);
    }

    private static String formatEpochMillisDateOnly(Long epochMillis) {
        if (epochMillis == null || epochMillis <= 0) return "—";
        LocalDateTime ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        return ldt.format(fmt);
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Book book = (Book) o;
        return year == book.year && available == book.available && Objects.equals(title, book.title) && Objects.equals(author, book.author) && Objects.equals(genre, book.genre) && Objects.equals(readingStatus, book.readingStatus) && Objects.equals(summary, book.summary) && Objects.equals(coverUrl, book.coverUrl) && Objects.equals(addedAt, book.addedAt) && Objects.equals(borrowedAt, book.borrowedAt);
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return Objects.hash(title, author, year, genre, available, readingStatus, summary, coverUrl, addedAt, borrowedAt);
    }

    /**
     * Représentation lisible du livre: "titre — auteur (année)".
     */
    @Override
    public String toString() {
        return title + " — " + author + " (" + year + ")";
    }
}