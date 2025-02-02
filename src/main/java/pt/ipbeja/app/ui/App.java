package pt.ipbeja.app.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;
import pt.ipbeja.app.model.*;
import pt.ipbeja.app.model.message_to_ui.MessageToUI;
import pt.ipbeja.app.model.throwables.InvalidInGameChangeException;
import pt.ipbeja.app.model.words_provider.DBWordsProvider;
import pt.ipbeja.app.model.words_provider.ManualWordsProvider;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.*;
import java.util.Optional;

import static pt.ipbeja.app.ui.StartWordSearch.TITLE;

public class App extends VBox implements WSView {
    private final @NotNull WSModel model;

    private final @NotNull MenuBar bar;
    private final @NotNull Game game;
    private final @NotNull Menu menu;

    public App(Stage stage) {
        this.model = new WSModel();
        this.model.registerView(this);

        this.game = new Game(this, this.model);
        // https://stackoverflow.com/questions/28558165/javafx-setvisible-hides-the-element-but-doesnt-rearrange-adjacent-nodes
        this.game.managedProperty().bind(this.game.visibleProperty());
        this.game.setVisible(false);
        this.menu = new Menu((lines, cols, mode, max, min) -> {
            try {
                this.model.setDimensions(lines, cols);
            } catch (InvalidInGameChangeException e) {
                // TODO: handle the error better.
                return;
            }

            this.model.setMaxWords(max);
            this.model.setMinWordSize(min);

            switch (mode) {
                case DB -> this.model.setWords(new DBWordsProvider(new FileChooser(stage).choose()));
                case MANUAL -> {
                    ManualWordsProvider provider = new ManualWordsProvider();

                    while (!provider.isClosed()) {
                        ProvideWordDialog dialog = new ProvideWordDialog();
                        Optional<String> result = dialog.showAndWait();
                        result.ifPresentOrElse(provider::provide, provider::close);
                    }

                    this.model.setWords(provider);
                }
                default -> throw new RuntimeException();
            }

            try {
                this.model.startGame();
            } catch (RuntimeException e) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle(TITLE);
                alert.setHeaderText("ERROR");
                alert.setContentText(e.toString());

                alert.showAndWait();
            }
        });
        this.menu.managedProperty().bind(this.menu.visibleProperty());

        this.bar = new MenuBar(stage);

        HBox centerH = new HBox(this.game, this.menu);
        VBox centerV = new VBox(centerH);
        centerH.setAlignment(Pos.CENTER);
        centerV.setAlignment(Pos.CENTER);
        this.getChildren().addAll(this.bar, centerV);
        // https://docs.oracle.com/javase/8/javafx/api/javafx/scene/layout/VBox.html
        VBox.setVgrow(centerV, Priority.ALWAYS);

        model.setSaver(res -> {
            Path dir = this.bar.getScoreDir();
            try (BufferedWriter writer = Files.newBufferedWriter(
                    dir.resolve("scores.txt"),
                    StandardOpenOption.APPEND
            )) {
                writer.write(String.format("%.2f%%\n", 100.0 * res.words_found().size() / res.words().size()));
            } catch (NoSuchFileException e) {
                try (BufferedWriter writer = Files.newBufferedWriter(dir.resolve("scores.txt"))) {
                    writer.write(String.format("%.2f%%\n", 100.0 * res.words_found().size() / res.words().size()));
                } catch (IOException ignored) {
                }
            } catch (IOException ignored) {
            }
        });
    }

    //play
    //options
    //quit
    //language
    //help

    /**
     * Simply updates the text for the buttons in the received positions
     *
     * @param messageToUI the WS model
     */
    @Override
    public void update(@NotNull MessageToUI messageToUI) {
        this.game.log(messageToUI.getMessage() + "\n");
    }

    @Override
    public void gameStarted() {
        this.game.getBoard().buildGUI();
        this.game.setVisible(true);
        this.menu.setVisible(false);
        this.game.resetGameLog();
    }

    @Override
    public void wordFound(@NotNull Position start, @NotNull Position end) {
        if (start.line() == end.line()) {
            int start_pos = Math.min(start.col(), end.col());
            int end_pos = Math.max(start.col(), end.col());
            for (int i = start_pos; i <= end_pos; i++) {
                Button btn = this.game.getBoard().getButton(end.line(), i);
                btn.setStyle("-fx-background-color: green");
            }
        } else if (start.col() == end.col()) {
            int start_pos = Math.min(start.line(), end.line());
            int end_pos = Math.max(start.line(), end.line());
            for (int i = start_pos; i <= end_pos; i++) {
                Button btn = this.game.getBoard().getButton(i, end.col());
                btn.setStyle("-fx-background-color: green");
            }
        }

        this.model.gameEnded();
    }

    @Override
    public void gameEnded(@NotNull GameResults res) {
        assert res.words_found() != null && res.words() != null;

        this.game.log("\twords found:\t" +
                res.words_found().size() +
                "\n\ttotal of words:\t" +
                res.words().size() +
                "\n\tpercentage of words found:\t" +
                String.format("%.2f%%\n", 100.0 * res.words_found().size() / res.words().size()) +
                "\n");

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(TITLE);
        alert.setHeaderText("Game ended");
        alert.setContentText("Do you want to play again (same configurations)?");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            this.model.startGame();
        } else {
            this.game.setVisible(false);
            this.menu.setVisible(true);
        }
    }

    public Path getLogDir() {
        return this.bar.getLogDir();
    }

    public String matrixToString() {
        return this.model.matrixToString();
    }
}
