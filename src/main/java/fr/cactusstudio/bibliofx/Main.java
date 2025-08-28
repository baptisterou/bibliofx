package fr.cactusstudio.bibliofx;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Objects;

/**
 * Application JavaFX principale de BiblioFX.
 * <p>
 * Cette classe charge l'interface initiale (hello-view.fxml), attache la feuille de style
 * globale et affiche la fenêtre principale.
 */
public class Main extends Application {
    /**
     * Point d'entrée JavaFX. Configure et affiche la scène principale.
     *
     * @param stage la fenêtre principale fournie par le runtime JavaFX
     * @throws IOException si le chargement du FXML échoue
     */
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(Main.class.getResource("hello-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 900, 500);
        // Applique la feuille de style globale
        scene.getStylesheets().add(Objects.requireNonNull(Main.class.getResource("styles.css")).toExternalForm());
        stage.setTitle("BiblioFX - Gestion de Bibliothèque");
        stage.setScene(scene);
        stage.show();
    }
}
