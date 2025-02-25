module com.udacity.catpoint.security {
    requires com.udacity.catpoint.image;
    requires com.miglayout.swing;
    requires java.desktop;
    requires com.google.common;
    requires com.google.gson;
    requires java.prefs;
    opens com.udacity.catpoint.security.data to com.google.gson;
}