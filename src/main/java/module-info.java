module fr.cactusstudio.bibliofx {
    requires javafx.controls;
    requires javafx.fxml;
    requires com.google.gson;
    requires java.net.http;

    opens fr.cactusstudio.bibliofx to javafx.fxml, com.google.gson;
    opens fr.cactusstudio.bibliofx.model to javafx.base, com.google.gson;
    exports fr.cactusstudio.bibliofx;
}