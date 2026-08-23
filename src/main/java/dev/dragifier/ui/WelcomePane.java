package dev.dragifier.ui;

import dev.dragifier.io.RecentProjects;
import dev.dragifier.model.Templates;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.nio.file.Path;
import java.util.function.Consumer;

/** Start screen: new project, template gallery, and recent projects. */
public class WelcomePane extends VBox {

    private final VBox recentBox = new VBox(4);
    private final Consumer<Path> onOpenRecent;

    public WelcomePane(Runnable onNewProject, Runnable onOpenProject,
                       Consumer<Templates.Template> onTemplate, Consumer<Path> onOpenRecent) {
        this.onOpenRecent = onOpenRecent;
        setAlignment(Pos.CENTER);
        setSpacing(18);
        setPadding(new Insets(40));

        Label title = new Label("Dragifier");
        title.getStyleClass().add("welcome-title");
        Label subtitle = new Label("Design JavaFX desktop apps by drag and drop");
        subtitle.getStyleClass().add("welcome-subtitle");

        Button newProject = new Button("New Project", new FontIcon(Feather.FILE_PLUS));
        newProject.setDefaultButton(true);
        newProject.setOnAction(e -> onNewProject.run());
        Button openProject = new Button("Open Project…", new FontIcon(Feather.FOLDER));
        openProject.setOnAction(e -> onOpenProject.run());
        HBox actions = new HBox(10, newProject, openProject);
        actions.setAlignment(Pos.CENTER);

        Label templatesHeader = new Label("START FROM A TEMPLATE");
        templatesHeader.getStyleClass().add("welcome-section");
        HBox templateCards = new HBox(10);
        templateCards.setAlignment(Pos.CENTER);
        for (Templates.Template template : Templates.all()) {
            Button card = new Button(template.name());
            card.getStyleClass().add("template-card");
            card.setOnAction(e -> onTemplate.accept(template));
            templateCards.getChildren().add(card);
        }

        Label recentHeader = new Label("RECENT PROJECTS");
        recentHeader.getStyleClass().add("welcome-section");
        recentBox.setAlignment(Pos.CENTER);
        refreshRecents();

        getChildren().addAll(title, subtitle, actions,
                templatesHeader, templateCards, recentHeader, recentBox);
    }

    public void refreshRecents() {
        recentBox.getChildren().clear();
        var recents = RecentProjects.list();
        if (recents.isEmpty()) {
            Label none = new Label("Nothing yet — projects you open or save appear here");
            none.getStyleClass().add("hint-text");
            recentBox.getChildren().add(none);
            return;
        }
        for (Path path : recents) {
            Hyperlink link = new Hyperlink(path.getFileName() + "  —  " + path.getParent());
            link.setOnAction(e -> onOpenRecent.accept(path));
            recentBox.getChildren().add(link);
        }
    }
}
