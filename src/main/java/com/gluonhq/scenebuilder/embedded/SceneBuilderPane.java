/*
 * Copyright (c) 2024, Gluon and/or its affiliates.
 * All rights reserved. Use is subject to license terms.
 *
 * This file is available and licensed under the following license:
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  - Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  - Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the distribution.
 *  - Neither the name of Gluon nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.gluonhq.scenebuilder.embedded;

import com.oracle.javafx.scenebuilder.kit.editor.EditorController;
import com.oracle.javafx.scenebuilder.kit.editor.panel.content.ContentPanelController;
import com.oracle.javafx.scenebuilder.kit.editor.panel.hierarchy.HierarchyPanelController;
import com.oracle.javafx.scenebuilder.kit.editor.panel.info.InfoPanelController;
import com.oracle.javafx.scenebuilder.kit.editor.panel.inspector.InspectorPanelController;
import com.oracle.javafx.scenebuilder.kit.editor.panel.library.LibraryPanelController;
import com.oracle.javafx.scenebuilder.kit.editor.search.SearchController;
import com.oracle.javafx.scenebuilder.kit.fxom.FXOMDocument;
import com.oracle.javafx.scenebuilder.kit.library.BuiltinLibrary;
import com.oracle.javafx.scenebuilder.kit.library.Library;
import com.oracle.javafx.scenebuilder.kit.library.user.UserLibrary;
import com.oracle.javafx.scenebuilder.kit.preferences.MavenPreferences;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Accordion;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;

import static com.oracle.javafx.scenebuilder.kit.editor.EditorPlatform.IS_LINUX;
import static com.oracle.javafx.scenebuilder.kit.editor.EditorPlatform.IS_MAC;
import static com.oracle.javafx.scenebuilder.kit.editor.EditorPlatform.IS_WINDOWS;

public class SceneBuilderPane extends StackPane {

    public SceneBuilderPane() {

        Node mainPane = createSBPane();
        getStyleClass().add("theme-presets");
        getStylesheets().add(SceneBuilderPane.class.getResource("sb.css").toExternalForm());
        getChildren().addAll(mainPane);
    }

    private final EditorController editorController = new EditorController();

    private Node createSBPane() {
        // left
        Library library = new Library() {
            {
                getItems().addAll(BuiltinLibrary.getLibrary().getItems());
            }
            @Override
            public Comparator<String> getSectionComparator() {
                return (s1, s2) -> BuiltinLibrary.getLibrary().getSectionComparator().compare(s1, s2);
            }
        };
        editorController.setLibrary(library);
        sceneProperty().addListener(new InvalidationListener() {
            @Override
            public void invalidated(Observable observable) {
                if (getScene() != null) {
                    getScene().windowProperty().addListener(new InvalidationListener() {
                        @Override
                        public void invalidated(Observable observable) {
                            if (getScene().getWindow() != null) {
                                editorController.setOwnerWindow((Stage) getScene().getWindow());
                                getScene().windowProperty().removeListener(this);
                            }
                        }
                    });
                    sceneProperty().removeListener(this);
                }
            }
        });

        Label libraryLabel = new Label("Library");
        libraryLabel.setMinWidth(Double.NEGATIVE_INFINITY);
        libraryLabel.setMaxWidth(Double.NEGATIVE_INFINITY);
        HBox.setHgrow(libraryLabel, Priority.NEVER);
        Parent searchPane = new SearchController(editorController).getPanelRoot();
        HBox.setHgrow(searchPane, Priority.ALWAYS);
        HBox hBoxTop = new HBox(libraryLabel, searchPane);
        hBoxTop.getStyleClass().add("panel-header");
        hBoxTop.setAlignment(Pos.CENTER_LEFT);
        Node libraryView = new LibraryPanelController(editorController, new MavenPreferences()).getPanelRoot();
        VBox vBoxTop = new VBox(hBoxTop, libraryView);

        Label documentLabel = new Label("Document");
        HBox hBoxBottom = new HBox(documentLabel);
        hBoxBottom.getStyleClass().add("panel-header");
        hBoxBottom.setAlignment(Pos.CENTER_LEFT);

        Node hierarchyView = new HierarchyPanelController(editorController).getPanelRoot();
        TitledPane hierarchyPane = new TitledPane("Hierarchy", hierarchyView);
        Node controllerView = new InfoPanelController(editorController).getPanelRoot();
        TitledPane controllerPane = new TitledPane("Controller", controllerView);
        Accordion accordion = new Accordion(hierarchyPane, controllerPane);
        accordion.setExpandedPane(hierarchyPane);
        VBox vBoxBottom = new VBox(hBoxBottom, accordion);
        SplitPane leftPane = new SplitPane(vBoxTop, vBoxBottom);
        leftPane.setOrientation(Orientation.VERTICAL);
        leftPane.setDividerPositions(0.5, 0.5);
        SplitPane.setResizableWithParent(leftPane, Boolean.FALSE);

        //center
        Node contentView = new ContentPanelController(editorController).getPanelRoot();

        // right
        Node inspectorView = new InspectorPanelController(editorController).getPanelRoot();
        SplitPane.setResizableWithParent(inspectorView, Boolean.FALSE);

        SplitPane mainPane = new SplitPane(leftPane, contentView, inspectorView);
        mainPane.setDividerPositions(0.25, 0.75);
        newFXML();
        return mainPane;
    }

    public void newFXML() {
        try {
            editorController.setFxmlText("", false);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void openFXML(URL url) {
        try {
            String content = FXOMDocument.readContentFromURL(url);
            editorController.setFxmlTextAndLocation(content, url);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void saveFXML(Stage stage) {
        if (editorController.canGetFxmlText()) {
            final FileChooser fileChooser = new FileChooser();
            final FileChooser.ExtensionFilter f = new FileChooser.ExtensionFilter("FXML Document", "*.fxml"); //NOI18N
            fileChooser.getExtensionFilters().add(f);
            fileChooser.setInitialDirectory(EditorController.getNextInitialDirectory());
            File fxmlFile = fileChooser.showSaveDialog(stage);
            if (fxmlFile != null) {
                final URL newLocation;
                try {
                    newLocation = fxmlFile.toURI().toURL();
                } catch (MalformedURLException e) {
                    throw new RuntimeException("Error", e);
                }
                editorController.setFxmlLocation(newLocation);
                EditorController.updateNextInitialDirectory(fxmlFile);

                final FXOMDocument fxomDocument = editorController.getFxomDocument();
                final Path fxmlPath;
                try {
                    fxmlPath = Paths.get(fxomDocument.getLocation().toURI());
                } catch (URISyntaxException e) {
                    // Should not happen
                    throw new RuntimeException("Error", e);
                }
                try {
                    final byte[] fxmlBytes = editorController.getFxmlText(true).getBytes(StandardCharsets.UTF_8);
                    Files.write(fxmlPath, fxmlBytes);
                } catch (Exception e) {
                    throw new RuntimeException("Error", e);
                }

            }
        }
    }

    public void createCustomLibrary(List<Path> paths) {
        UserLibrary userLibrary = new UserLibrary(getUserLibraryFolder(), () -> paths, List::of);
        userLibrary.setOnUpdatedJarReports(jarReports -> {});
        userLibrary.startWatching();

        editorController.setLibrary(userLibrary);
    }

    private static String applicationDataFolder;
    private static String userLibraryFolder;

    private String getApplicationDataFolder() {
        if (applicationDataFolder == null) {
            final String appName = "Scene Builder";
            if (IS_WINDOWS) {
                applicationDataFolder = System.getenv("APPDATA") + "\\" + appName;
            } else if (IS_MAC) {
                applicationDataFolder = System.getProperty("user.home") + "/Library/Application Support/" + appName;
            } else if (IS_LINUX) {
                applicationDataFolder = System.getProperty("user.home") + "/.scenebuilder";
            }
        }
        return applicationDataFolder;
    }

    private String getUserLibraryFolder() {
        if (userLibraryFolder == null) {
            userLibraryFolder = getApplicationDataFolder() + "/Library";
        }
        return userLibraryFolder;
    }
}