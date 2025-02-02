package pt.ipbeja.app.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.jetbrains.annotations.NotNull;

import static pt.ipbeja.app.model.WSModel.MAX_SIDE_LEN;
import static pt.ipbeja.app.model.WSModel.MIN_SIDE_LEN;

public class Menu extends VBox {
    public Menu(@NotNull OnStartHandler handler) {
        Button btn = new Button("Start");
        NumberInput lines = new NumberInput("lines:\t", MIN_SIDE_LEN);
        lines.setMin(MIN_SIDE_LEN);
        lines.setMax(MAX_SIDE_LEN);
        NumberInput columns = new NumberInput("columns:\t", MIN_SIDE_LEN);
        columns.setMin(MIN_SIDE_LEN);
        columns.setMax(MAX_SIDE_LEN);

        NumberInput max = new NumberInput("maximum amount of words:\t", 4);
        max.setMin(1);

        NumberInput min = new NumberInput("minimum length of an word:\t", 1);
        max.setMin(1);

        // https://docs.oracle.com/javafx/2/ui_controls/radio-button.htm
        final ToggleGroup group = new ToggleGroup();
        RadioButton manual = new RadioButton("Manual");
        manual.setUserData(ProviderMode.MANUAL);
        RadioButton db = new RadioButton("Database");
        db.setUserData(ProviderMode.DB);
        manual.setToggleGroup(group);
        db.setToggleGroup(group);
        db.setSelected(true);

        btn.setOnAction(event -> handler.onStart(
                lines.getVal(),
                columns.getVal(),
                (ProviderMode) group.getSelectedToggle().getUserData(),
                max.getVal(),
                min.getVal()
        ));

        this.getChildren().addAll(lines, columns, new HBox(manual, db), btn, max, min);

        this.setAlignment(Pos.CENTER);
    }

    public interface OnStartHandler {
        void onStart(int lines, int cols, @NotNull ProviderMode mode, int max, int min);
    }

    public enum ProviderMode { MANUAL, DB }
}
