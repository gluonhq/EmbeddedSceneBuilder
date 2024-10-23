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
import com.oracle.javafx.scenebuilder.kit.editor.panel.hierarchy.AbstractHierarchyPanelController;
import com.oracle.javafx.scenebuilder.kit.editor.panel.hierarchy.HierarchyPanelController;
import com.oracle.javafx.scenebuilder.kit.editor.panel.info.InfoPanelController;
import com.oracle.javafx.scenebuilder.kit.editor.panel.inspector.InspectorPanelController;
import com.oracle.javafx.scenebuilder.kit.editor.panel.library.LibraryPanelController;
import com.oracle.javafx.scenebuilder.kit.editor.search.SearchController;
import com.oracle.javafx.scenebuilder.kit.library.BuiltinLibrary;
import com.oracle.javafx.scenebuilder.kit.library.Library;
import com.oracle.javafx.scenebuilder.kit.library.user.UserLibrary;
import com.oracle.javafx.scenebuilder.kit.preferences.MavenPreferences;
import com.oracle.javafx.scenebuilder.kit.selectionbar.SelectionBarController;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Accordion;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.ResourceBundle;

import static com.oracle.javafx.scenebuilder.kit.editor.EditorPlatform.IS_LINUX;
import static com.oracle.javafx.scenebuilder.kit.editor.EditorPlatform.IS_MAC;
import static com.oracle.javafx.scenebuilder.kit.editor.EditorPlatform.IS_WINDOWS;

public class SceneBuilderPane extends StackPane {

    private final EditorController editorController = new EditorController();
    private final AbstractHierarchyPanelController hierarchyPanelController = new HierarchyPanelController(editorController);
    private final ContentPanelController contentPanelController = new ContentPanelController(editorController);
    private final ResourceBundle resources = ResourceBundle.getBundle("com.gluonhq.scenebuilder.embedded.sb");
    private final SBController sbController;

    public SceneBuilderPane() {
        Node mainPane = createSBPane();
        getStyleClass().add("theme-presets");
        getStylesheets().add(SceneBuilderPane.class.getResource("sb.css").toExternalForm());
        getChildren().addAll(mainPane);
        sbController = new SBController(this);

        sbController.newFXML();
    }

    public EventHandler<ActionEvent> newFXMLHandler() {
        return e -> sbController.newFXML();
    }

    public EventHandler<ActionEvent> openFXMLHandler() {
        return e -> sbController.openFXML();
    }

    public EventHandler<ActionEvent> saveFXMLHandler() {
        return e -> sbController.saveFXML();
    }

    EditorController getEditorController() {
        return editorController;
    }

    AbstractHierarchyPanelController getHierarchyPanelController() {
        return hierarchyPanelController;
    }

    ContentPanelController getContentPanelController() {
        return contentPanelController;
    }

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

                                // custom library from module path and class path
                                List<Path> scan = DependenciesScanner.scan();
                                createCustomLibrary(scan);

                                getScene().windowProperty().removeListener(this);
                            }
                        }
                    });
                    sceneProperty().removeListener(this);
                }
            }
        });

        SplitPane mainPane = new SplitPane(createLeftSide(), createCenter(), createRightSide());
        mainPane.setDividerPositions(0.25, 0.75);
        return mainPane;
    }

    private Node createLeftSide() {
        // Library
        Label libraryLabel = new Label(resources.getString("left.library"));
        libraryLabel.setMinWidth(Double.NEGATIVE_INFINITY);
        libraryLabel.setMaxWidth(Double.NEGATIVE_INFINITY);
        HBox.setHgrow(libraryLabel, Priority.NEVER);
        SearchController librarySearchController = new SearchController(editorController);
        Parent searchPane = librarySearchController.getPanelRoot();
        HBox.setHgrow(searchPane, Priority.ALWAYS);
        Region region = new Region();
        region.getStyleClass().add("cog-shape");
        ToggleGroup libraryDisplayOptionTG = new ToggleGroup();
        MenuButton libraryMenuButton = new MenuButton(null, region);
        RadioMenuItem libraryViewAsList = new RadioMenuItem(resources.getString("library.panel.menu.view.list"));
        libraryViewAsList.setToggleGroup(libraryDisplayOptionTG);
        RadioMenuItem libraryViewAsSections = new RadioMenuItem(resources.getString("library.panel.menu.view.sections"));
        libraryViewAsSections.setToggleGroup(libraryDisplayOptionTG);
        libraryViewAsSections.setSelected(true);
        libraryMenuButton.getItems().addAll(libraryViewAsList, libraryViewAsSections);
        HBox hBoxTop = new HBox(libraryLabel, searchPane, libraryMenuButton);
        hBoxTop.getStyleClass().add("panel-header");
        hBoxTop.setAlignment(Pos.CENTER_LEFT);
        LibraryPanelController libraryPanelController = new LibraryPanelController(editorController, new MavenPreferences());
        librarySearchController.textProperty().subscribe((ov, nv) -> libraryPanelController.setSearchPattern(nv));
        libraryViewAsList.setOnAction(e -> {
            if (libraryPanelController.getDisplayMode() != LibraryPanelController.DISPLAY_MODE.SEARCH) {
                libraryPanelController.setDisplayMode(LibraryPanelController.DISPLAY_MODE.LIST);
            } else {
                libraryPanelController.setPreviousDisplayMode(LibraryPanelController.DISPLAY_MODE.LIST);
            }
        });
        libraryViewAsSections.setOnAction(e -> {
            if (libraryPanelController.getDisplayMode() != LibraryPanelController.DISPLAY_MODE.SEARCH) {
                libraryPanelController.setDisplayMode(LibraryPanelController.DISPLAY_MODE.SECTIONS);
            } else {
                libraryPanelController.setPreviousDisplayMode(LibraryPanelController.DISPLAY_MODE.SECTIONS);
            }
        });
        Node libraryView = libraryPanelController.getPanelRoot();
        VBox vBoxTop = new VBox(hBoxTop, libraryView);

        // Document
        Label documentLabel = new Label(resources.getString("left.document"));

        Region docRegion = new Region();
        docRegion.getStyleClass().add("cog-shape");
        ToggleGroup hierarchyDisplayOptionTG = new ToggleGroup();
        MenuButton hierarchyMenuButton = new MenuButton(null, docRegion);
        RadioMenuItem showInfoMenuItem = new RadioMenuItem(resources.getString("hierarchy.show.info"));
        showInfoMenuItem.setToggleGroup(hierarchyDisplayOptionTG);
        showInfoMenuItem.setSelected(true);
        RadioMenuItem showFxIdMenuItem = new RadioMenuItem(resources.getString("hierarchy.show.fxid"));
        showFxIdMenuItem.setToggleGroup(hierarchyDisplayOptionTG);
        RadioMenuItem showNodeIdMenuItem = new RadioMenuItem(resources.getString("hierarchy.show.nodeid"));
        showNodeIdMenuItem.setToggleGroup(hierarchyDisplayOptionTG);
        hierarchyMenuButton.getItems().addAll(showInfoMenuItem, showFxIdMenuItem, showNodeIdMenuItem);
        showInfoMenuItem.setOnAction(e ->
                hierarchyPanelController.setDisplayOption(AbstractHierarchyPanelController.DisplayOption.INFO));
        showFxIdMenuItem.setOnAction(e ->
                hierarchyPanelController.setDisplayOption(AbstractHierarchyPanelController.DisplayOption.FXID));
        showNodeIdMenuItem.setOnAction(e ->
                hierarchyPanelController.setDisplayOption(AbstractHierarchyPanelController.DisplayOption.NODEID));

        Region span = new Region();
        HBox.setHgrow(span, Priority.ALWAYS);
        HBox hBoxBottom = new HBox(documentLabel, span, hierarchyMenuButton);
        hBoxBottom.getStyleClass().add("panel-header");
        hBoxBottom.setAlignment(Pos.CENTER_LEFT);

        Node hierarchyView = hierarchyPanelController.getPanelRoot();
        TitledPane hierarchyPane = new TitledPane(resources.getString("left.hierarchy"), hierarchyView);
        Node controllerView = new InfoPanelController(editorController).getPanelRoot();
        TitledPane controllerPane = new TitledPane(resources.getString("left.controller"), controllerView);
        Accordion accordion = new Accordion(hierarchyPane, controllerPane);
        accordion.setExpandedPane(hierarchyPane);
        VBox vBoxBottom = new VBox(hBoxBottom, accordion);

        SplitPane leftPane = new SplitPane(vBoxTop, vBoxBottom);
        leftPane.setOrientation(Orientation.VERTICAL);
        leftPane.setDividerPositions(0.5, 0.5);
        SplitPane.setResizableWithParent(leftPane, Boolean.FALSE);
        return leftPane;
    }

    private Node createCenter() {
        Parent selectionBarPane = new SelectionBarController(editorController).getPanelRoot();
        selectionBarPane.getStyleClass().add("selection-bar-container");
        Node contentView = contentPanelController.getPanelRoot();
        VBox.setVgrow(contentView, Priority.ALWAYS);
        VBox contentBox = new VBox(selectionBarPane, contentView);
        return contentBox;
    }

    private Node createRightSide() {
        // Inspector
        Label inspectorLabel = new Label(resources.getString("right.inspector"));
        inspectorLabel.setMinWidth(Double.NEGATIVE_INFINITY);
        inspectorLabel.setMaxWidth(Double.NEGATIVE_INFINITY);
        HBox.setHgrow(inspectorLabel, Priority.NEVER);
        SearchController inspectorSearchController = new SearchController(editorController);
        Parent searchInspectorPane = inspectorSearchController.getPanelRoot();
        HBox.setHgrow(searchInspectorPane, Priority.ALWAYS);

        Region region = new Region();
        region.getStyleClass().add("cog-shape");
        ToggleGroup inspectorShowTG = new ToggleGroup();
        MenuButton inspectorMenuButton = new MenuButton(null, region);
        RadioMenuItem inspectorShowAll = new RadioMenuItem(resources.getString("inspector.show.all"));
        inspectorShowAll.setToggleGroup(inspectorShowTG);
        inspectorShowAll.setSelected(true);
        RadioMenuItem inspectorShowEdited = new RadioMenuItem(resources.getString("inspector.show.edited"));
        inspectorShowEdited.setToggleGroup(inspectorShowTG);

        ToggleGroup inspectorViewTG = new ToggleGroup();
        RadioMenuItem inspectorViewSections = new RadioMenuItem(resources.getString("inspector.view.sections"));
        inspectorViewSections.setToggleGroup(inspectorViewTG);
        inspectorViewSections.setSelected(true);
        RadioMenuItem inspectorViewByPropertyName = new RadioMenuItem(resources.getString("inspector.by.property.name"));
        inspectorViewByPropertyName.setToggleGroup(inspectorViewTG);
        RadioMenuItem inspectorViewByPropertyType = new RadioMenuItem(resources.getString("inspector.by.property.type"));
        inspectorViewByPropertyType.setToggleGroup(inspectorViewTG);
        inspectorMenuButton.getItems().addAll(inspectorShowAll, inspectorShowEdited,
                new SeparatorMenuItem(),
                inspectorViewSections, inspectorViewByPropertyName, inspectorViewByPropertyType);

        HBox hBoxInspectorTop = new HBox(inspectorLabel, searchInspectorPane, inspectorMenuButton);
        hBoxInspectorTop.getStyleClass().add("panel-header");
        hBoxInspectorTop.setAlignment(Pos.CENTER_LEFT);

        InspectorPanelController inspectorPanelController = new InspectorPanelController(editorController);
        inspectorShowAll.setOnAction(e -> inspectorPanelController.setShowMode(InspectorPanelController.ShowMode.ALL));
        inspectorShowEdited.setOnAction(e -> inspectorPanelController.setShowMode(InspectorPanelController.ShowMode.EDITED));
        inspectorViewSections.setOnAction(e -> inspectorPanelController.setViewMode(InspectorPanelController.ViewMode.SECTION));
        inspectorViewByPropertyName.setOnAction(e -> inspectorPanelController.setViewMode(InspectorPanelController.ViewMode.PROPERTY_NAME));
        inspectorViewByPropertyType.setOnAction(e -> inspectorPanelController.setViewMode(InspectorPanelController.ViewMode.PROPERTY_TYPE));
        inspectorSearchController.textProperty().subscribe((ov, nv) -> inspectorPanelController.setSearchPattern(nv));
        Node inspectorView = inspectorPanelController.getPanelRoot();
        VBox.setVgrow(inspectorView, Priority.ALWAYS);
        VBox rightBox = new VBox(hBoxInspectorTop, inspectorView);
        SplitPane.setResizableWithParent(rightBox, Boolean.FALSE);
        return rightBox;
    }

    private void createCustomLibrary(List<Path> paths) {
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