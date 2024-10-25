MODULE_NAME=EmbeddedSceneBuilderDemo
MAIN_CLASS=com.gluonhq.scenebuilder.embedded.demo.DemoApplication

cp target/demo-$APP_VERSION.jar target/lib/

jdeps_modules=$($JAVA_HOME/bin/jdeps --module-path target/lib --print-module-deps --ignore-missing-deps -m $MODULE_NAME)
JAVA_MODULES=javafx.fxml,javafx.media,javafx.swing,javafx.web,java.logging

$JAVA_HOME/bin/jlink \
--module-path target/lib \
--add-modules $jdeps_modules,$JAVA_MODULES \
--output target/runtime \
--strip-debug --compress zip-6 --no-header-files --no-man-pages

$JPACKAGE_HOME/bin/jpackage \
--module-path target/lib \
--runtime-image target/runtime \
--dest target/installer \
--module $MODULE_NAME/$MAIN_CLASS \
--name $MODULE_NAME \
--description "Embedded Scene Builder Demo" \
--vendor "Gluon" \
--copyright "Copyright © 2024 Gluon" \
--license-file LICENSE.txt \
--app-version $APP_VERSION \
--java-options '"--add-opens=javafx.fxml/javafx.fxml=com.gluonhq.scenebuilder.kit"' \
"$@"