set MODULE_NAME=EmbeddedSceneBuilderDemo
set MAIN_CLASS=com.gluonhq.scenebuilder.embedded.demo.DemoApplication

cp target\demo-%APP_VERSION%.jar target\lib\

for /F %%i in ('%JAVA_HOME%\bin\jdeps --module-path target\lib --print-module-deps --ignore-missing-deps -m %MODULE_NAME%') do SET JDEPS_MODULES=%%i

set JAVA_MODULES=javafx.fxml,javafx.media,javafx.swing,javafx.web,java.logging

%JAVA_HOME%\bin\jlink ^
--module-path target\lib ^
--add-modules %JDEPS_MODULES%,%JAVA_MODULES% ^
--output target\runtime ^
--strip-debug --compress zip-6 --no-header-files --no-man-pages

%JPACKAGE_HOME%\bin\jpackage ^
--module-path target/lib ^
--runtime-image target/runtime ^
--dest target/installer ^
--module %MODULE_NAME%/%MAIN_CLASS% ^
--name %MODULE_NAME% ^
--description "Embedded Scene Builder Demo" ^
--vendor "Gluon" ^
--copyright "Copyright © 2024 Gluon" ^
--license-file LICENSE.txt ^
--app-version %APP_VERSION% ^
--java-options '"--add-opens=javafx.fxml/javafx.fxml=com.gluonhq.scenebuilder.kit"' ^
--type msi ^
--win-dir-chooser ^
--win-menu ^
--win-menu-group "Scene Builder" ^
--win-per-user-install ^
--win-shortcut

