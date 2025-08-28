package fr.cactusstudio.bibliofx;

import javafx.application.Application;

/**
 * Lanceur standard pour exécuter l'application JavaFX depuis un environnement
 * qui ne supporte pas directement l'exécution d'une sous-classe d'Application.
 */
public class Launcher {
    /**
     * Méthode main qui délègue au runtime JavaFX.
     *
     * @param args arguments de ligne de commande
     */
    public static void main(String[] args) {
        Application.launch(Main.class, args);
    }
}
