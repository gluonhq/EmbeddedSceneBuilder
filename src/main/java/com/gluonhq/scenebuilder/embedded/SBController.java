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
import com.oracle.javafx.scenebuilder.kit.editor.EditorPlatform;
import com.oracle.javafx.scenebuilder.kit.editor.panel.util.dialog.AbstractModalDialog;
import com.oracle.javafx.scenebuilder.kit.editor.panel.util.dialog.AlertDialog;
import com.oracle.javafx.scenebuilder.kit.fxom.FXOMDocument;
import com.oracle.javafx.scenebuilder.kit.fxom.FXOMNodes;
import com.oracle.javafx.scenebuilder.kit.fxom.FXOMObject;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.Clipboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

public class SBController {

    private static final KeyCombination.Modifier modifier;
    static {
        if (EditorPlatform.IS_MAC) {
            modifier = KeyCombination.META_DOWN;
        } else {
            modifier = KeyCombination.CONTROL_DOWN;
        }
    }
    private final ResourceBundle resources = ResourceBundle.getBundle("com.gluonhq.scenebuilder.embedded.sb");

    private final EventHandler<KeyEvent> mainKeyEventFilter;

    private final SceneBuilderPane sbPane;
    private final Node mainPane;
    private final EditorController editorController;

    SBController(SceneBuilderPane sbPane) {
        this.sbPane = sbPane;
        this.mainPane = sbPane.getChildrenUnmodifiable().getFirst();
        this.editorController = sbPane.getEditorController();

        mainKeyEventFilter = event -> {
            if (sbPane.getHierarchyPanelController().getPanelControl().isFocused() &&
                    new KeyCodeCombination(KeyCode.A, modifier).match(event)) {
                event.consume();
                if (canPerformSelectAll()) {
                    performSelectAll();
                }
            }

            if (sbPane.getHierarchyPanelController().getPanelControl().isFocused() &&
                    new KeyCodeCombination(KeyCode.A, KeyCombination.SHIFT_DOWN, modifier).match(event)) {
                event.consume();
                if (canPerformSelectNone()) {
                    performSelectNone();
                }
            }

            final Node focusOwner = mainPane.getScene().getFocusOwner();
            if (!isTextInputControlEditing(focusOwner) && KeyCode.BACK_SPACE.equals(event.getCode())) {
                if (canPerformDelete()) {
                    performDelete();
                }
                event.consume();
            }

            if (new KeyCodeCombination(KeyCode.Z, modifier).match(event)) {
                if (canPerformUndo()) {
                    performUndo();
                }
                event.consume();
            }
            
            if ((EditorPlatform.IS_MAC && new KeyCodeCombination(KeyCode.Z, KeyCombination.SHIFT_DOWN, modifier).match(event)) ||
                    new KeyCodeCombination(KeyCode.Y, modifier).match(event)) {
                if (canPerformRedo()) {
                    performRedo();
                }
                event.consume();
            }

            if (new KeyCodeCombination(KeyCode.C, modifier).match(event)) {
                if (canPerformCopy()) {
                    performCopy();
                }
                event.consume();
            }
            
            if (new KeyCodeCombination(KeyCode.X, modifier).match(event)) {
                if (canPerformCut()) {
                    performCut();
                }
                event.consume();
            }
            
            if (new KeyCodeCombination(KeyCode.V, modifier).match(event)) {
                if (canPerformPaste()) {
                    performPaste();
                }
                event.consume();
            }

            if (new KeyCodeCombination(KeyCode.D, modifier).match(event)) {
                if (canPerformDuplicate()) {
                    performDuplicate();
                }
                event.consume();
            }

            if (new KeyCodeCombination(KeyCode.N, modifier).match(event)) {
                newFXML();
                event.consume();
            }

            if (new KeyCodeCombination(KeyCode.O, modifier).match(event)) {
                openFXML();
                event.consume();
            }

            if (new KeyCodeCombination(KeyCode.S, modifier).match(event)) {
                saveFXML();
                event.consume();
            }

        };

        sbPane.addEventFilter(KeyEvent.KEY_PRESSED, mainKeyEventFilter);
    }

    void newFXML() {
        try {
            editorController.setFxmlText("", false);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    void openFXML() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(resources.getString("file.dialog.title"));
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(resources.getString("file.filter.label.fxml"), "*.fxml"));
        fileChooser.setInitialDirectory(EditorController.getNextInitialDirectory());
        File file = fileChooser.showOpenDialog(editorController.getOwnerWindow());
        if (file != null) {
            try {
                EditorController.updateNextInitialDirectory(file);
                URL url = file.toURI().toURL();
                String content = FXOMDocument.readContentFromURL(url);
                editorController.setFxmlTextAndLocation(content, url);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    void saveFXML() {
        if (editorController.canGetFxmlText()) {
            final FileChooser fileChooser = new FileChooser();
            final FileChooser.ExtensionFilter f = new FileChooser.ExtensionFilter("FXML Document", "*.fxml"); //NOI18N
            fileChooser.getExtensionFilters().add(f);
            fileChooser.setInitialDirectory(EditorController.getNextInitialDirectory());
            File fxmlFile = fileChooser.showSaveDialog(editorController.getOwnerWindow());
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

    private boolean canPerformCopy() {
        boolean result;
        final Node focusOwner = mainPane.getScene().getFocusOwner();
        if (isPopupEditing(focusOwner)) {
            return false;
        } else if (isTextInputControlEditing(focusOwner)) {
            final TextInputControl tic = getTextInputControl(focusOwner);
            result = tic.getSelectedText() != null && !tic.getSelectedText().isEmpty();
        } else {
            result = editorController.canPerformControlAction(EditorController.ControlAction.COPY);
        }
        return result;
    }

    private void performCopy() {
        final Node focusOwner = mainPane.getScene().getFocusOwner();
        if (isTextInputControlEditing(focusOwner)) {
            getTextInputControl(focusOwner).copy();
        } else {
            this.editorController.performControlAction(EditorController.ControlAction.COPY);
        }
    }

    private boolean canPerformCut() {
        boolean result;
        final Node focusOwner = mainPane.getScene().getFocusOwner();
        if (isPopupEditing(focusOwner)) {
            return false;
        } else if (isTextInputControlEditing(focusOwner)) {
            final TextInputControl tic = getTextInputControl(focusOwner);
            result = tic.getSelectedText() != null && !tic.getSelectedText().isEmpty();
        } else {
            result = editorController.canPerformEditAction(EditorController.EditAction.CUT);
        }
        return result;
    }

    private void performCut() {
        final Node focusOwner = mainPane.getScene().getFocusOwner();
        if (isTextInputControlEditing(focusOwner)) {
            final TextInputControl tic = getTextInputControl(focusOwner);
            tic.cut();
        } else {
            this.editorController.performEditAction(EditorController.EditAction.CUT);
        }
    }

    private boolean canPerformPaste() {
        boolean result;
        final Node focusOwner = mainPane.getScene().getFocusOwner();
        if (editorController.canPerformEditAction(EditorController.EditAction.PASTE)) {
            result = true;
        } else if (isTextInputControlEditing(focusOwner)) {
            result = Clipboard.getSystemClipboard().hasString();
        } else {
            result = false;
        }
        return result;
    }

    private void performPaste() {
        final Node focusOwner = mainPane.getScene().getFocusOwner();
        if (editorController.canPerformEditAction(EditorController.EditAction.PASTE)) {
            this.editorController.performEditAction(EditorController.EditAction.PASTE);
            sbPane.getContentPanelController().getGlassLayer().requestFocus();
        } else {
            assert isTextInputControlEditing(focusOwner);
            final TextInputControl tic = getTextInputControl(focusOwner);
            if (EditorPlatform.IS_MAC) {
                // https://bugs.openjdk.java.net/browse/JDK-8280057
            } else {
                tic.paste();
            }
        }
    }
    
    private boolean canPerformDelete() {
        boolean result;
        final Node focusOwner = mainPane.getScene().getFocusOwner();
        if (isTextInputControlEditing(focusOwner)) {
            final TextInputControl tic = getTextInputControl(focusOwner);
            result = tic.getCaretPosition() < tic.getLength();
        } else {
            result = editorController.canPerformEditAction(EditorController.EditAction.DELETE);
        }
        return result;
    }

    private void performDelete() {

        final Node focusOwner = mainPane.getScene().getFocusOwner();
        if (isTextInputControlEditing(focusOwner)) {
            final TextInputControl tic = getTextInputControl(focusOwner);
            tic.deleteNextChar();
        } else {
            final List<FXOMObject> selectedObjects = editorController.getSelectedObjects();
            final Map<String, FXOMObject> fxIdMap = new HashMap<>();
            for (FXOMObject selectedObject : selectedObjects) {
                fxIdMap.putAll(selectedObject.collectFxIds());
            }
            FXOMNodes.removeToggleGroups(fxIdMap);

            final boolean deleteConfirmed;
            if (fxIdMap.isEmpty()) {
                deleteConfirmed = true;
            } else {
                final String message;

                if (fxIdMap.size() == 1) {
                    if (selectedObjects.size() == 1) {
                        message = resources.getString("alert.delete.fxid1of1.message");
                    } else {
                        message = resources.getString("alert.delete.fxid1ofN.message");
                    }
                } else {
                    if (selectedObjects.size() == fxIdMap.size()) {
                        message = resources.getString("alert.delete.fxidNofN.message");
                    } else {
                        message = resources.getString("alert.delete.fxidKofN.message");
                    }
                }

                final AlertDialog d = new AlertDialog(editorController.getOwnerWindow());
                d.setMessage(message);
                d.setDetails(resources.getString("alert.delete.fxid.details"));
                d.setOKButtonTitle(resources.getString("label.delete"));

                deleteConfirmed = (d.showAndWait() == AbstractModalDialog.ButtonID.OK);
            }

            if (deleteConfirmed) {
                editorController.performEditAction(EditorController.EditAction.DELETE);
            }
        }
    }

    private boolean canPerformSelectNone() {
        boolean result;
        final Node focusOwner = mainPane.getScene().getFocusOwner();
        if (isPopupEditing(focusOwner)) {
            return false;
        } else if (isTextInputControlEditing(focusOwner)) {
            final TextInputControl tic = getTextInputControl(focusOwner);
            result = tic.getSelectedText() != null && !tic.getSelectedText().isEmpty();
        } else {
            result = editorController.canPerformControlAction(EditorController.ControlAction.SELECT_NONE);
        }
        return result;
    }

    private void performSelectNone() {
        final Node focusOwner = mainPane.getScene().getFocusOwner();
        if (isTextInputControlEditing(focusOwner)) {
            final TextInputControl tic = getTextInputControl(focusOwner);
            tic.deselect();
        } else {
            this.editorController.performControlAction(EditorController.ControlAction.SELECT_NONE);
        }
    }
    
    private boolean canPerformSelectAll() {
        final boolean result;
        final Node focusOwner = mainPane.getScene().getFocusOwner();
        if (isPopupEditing(focusOwner)) {
            return false;
        } else if (isTextInputControlEditing(focusOwner)) {
            final TextInputControl tic = getTextInputControl(focusOwner);
            final String text = tic.getText();
            final String selectedText = tic.getSelectedText();
            if (text == null || text.isEmpty()) {
                result = false;
            } else {
                result = selectedText == null || selectedText.length() < tic.getText().length();
            }
        } else {
            result = editorController.canPerformControlAction(EditorController.ControlAction.SELECT_ALL);
        }
        return result;
    }

    private void performSelectAll() {
        final Node focusOwner = mainPane.getScene().getFocusOwner();
        if (isTextInputControlEditing(focusOwner)) {
            getTextInputControl(focusOwner).selectAll();
        } else {
            this.editorController.performControlAction(EditorController.ControlAction.SELECT_ALL);
        }
    }

    private boolean canPerformUndo() {
        return editorController.getOwnerWindow().isFocused() && editorController.canUndo();
    }

    private void performUndo() {
        editorController.undo();
    }

    private boolean canPerformRedo() {
        return editorController.getOwnerWindow().isFocused() && editorController.canRedo();
    }

    private void performRedo() {
        editorController.redo();
    }

    private boolean canPerformDuplicate() {
        return editorController.getOwnerWindow().isFocused() &&
                editorController.canPerformEditAction(EditorController.EditAction.DUPLICATE);
    }

    private void performDuplicate() {
        editorController.performEditAction(EditorController.EditAction.DUPLICATE);
    }

    private boolean isPopupEditing(Node node) {
        return (node instanceof MenuButton && ((MenuButton) node).isShowing())
                || editorController.getInlineEditController().isWindowOpened();
    }

    private boolean isTextInputControlEditing(Node node) {
        return node instanceof TextInputControl || node instanceof ComboBox;
    }

    private TextInputControl getTextInputControl(Node node) {
        final TextInputControl tic;
        if (node instanceof TextInputControl n) {
            tic = n;
        } else if (node instanceof ComboBox<?> cb) {
            tic = cb.getEditor();
        } else {
            throw new RuntimeException("Unsupported node type: " + node);
        }
        return tic;
    }
}
