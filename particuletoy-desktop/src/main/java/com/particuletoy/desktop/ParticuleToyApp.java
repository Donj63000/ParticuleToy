package com.particuletoy.desktop;

import com.particuletoy.core.ElementType;
import com.particuletoy.core.World;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.util.Locale;

/**
 * Desktop UI (JavaFX).
 *
 * MVP goals:
 * - Show the simulation as a scaled pixel canvas.
 * - Allow painting elements with a brush.
 * - Basic controls: play/pause, step, clear, reset.
 */
public final class ParticuleToyApp extends Application {

    // --- Simulation settings (MVP defaults) ---
    private static final int WORLD_W = 320;
    private static final int WORLD_H = 200;
    private static final int VIEW_SCALE = 3;     // integer scale for crisp pixels
    private static final double TICK_DT = 1.0 / 60.0;
    private static final int MAX_STEPS_PER_FRAME = 6;

    private World world;

    // Rendering
    private int[] pixels;
    private WritableImage image;
    private PixelWriter pixelWriter;
    private ImageView imageView;

    // UI state
    private boolean paused = false;
    private ElementType selectedElement = ElementType.SAND;
    private int brushRadius = 6;

    // Stats
    private long lastFrameNs = 0L;
    private double accumulator = 0.0;
    private long fpsLastNs = 0L;
    private int fpsFrames = 0;
    private double lastFps = 0.0;
    private int lastSteps = 0;

    @Override
    public void start(Stage stage) {
        // --- World init ---
        this.world = new World(WORLD_W, WORLD_H, 1337L);
        this.world.clear();
        this.world.fillBorder(ElementType.WALL);

        // --- Rendering init ---
        this.pixels = new int[WORLD_W * WORLD_H];
        this.image = new WritableImage(WORLD_W, WORLD_H);
        this.pixelWriter = image.getPixelWriter();

        this.imageView = new ImageView(image);
        this.imageView.setSmooth(false);
        this.imageView.setPreserveRatio(false);
        this.imageView.setScaleX(VIEW_SCALE);
        this.imageView.setScaleY(VIEW_SCALE);

        // A pane that centers the scaled image
        StackPane viewport = new StackPane(imageView);
        viewport.setAlignment(Pos.CENTER);
        viewport.setPadding(new Insets(10));
        viewport.setStyle("-fx-background-color: #1e1e1e;");

        // --- Controls panel ---
        Label title = new Label("ParticuleToy");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        ChoiceBox<ElementType> elementChoice = new ChoiceBox<>(FXCollections.observableArrayList(ElementType.palette()));
        elementChoice.setValue(selectedElement);
        elementChoice.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) selectedElement = newV;
        });

        Slider brushSlider = new Slider(1, 25, brushRadius);
        brushSlider.setMajorTickUnit(6);
        brushSlider.setMinorTickCount(5);
        brushSlider.setShowTickLabels(true);
        brushSlider.setShowTickMarks(true);
        brushSlider.setSnapToTicks(true);

        Label brushLabel = new Label();
        Runnable refreshBrushLabel = () -> brushLabel.setText("Brush radius: " + brushRadius + " px");
        refreshBrushLabel.run();

        brushSlider.valueProperty().addListener((obs, oldV, newV) -> {
            brushRadius = (int) Math.round(newV.doubleValue());
            refreshBrushLabel.run();
        });

        ToggleButton playPause = new ToggleButton("Play");
        playPause.setMaxWidth(Double.MAX_VALUE);
        playPause.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
            paused = !isSelected;
            playPause.setText(isSelected ? "Pause" : "Play");
        });
        // initial state: paused=false => selected=true
        playPause.setSelected(true);
        playPause.setText("Pause");

        Button stepBtn = new Button("Step (1 tick)");
        stepBtn.setMaxWidth(Double.MAX_VALUE);
        stepBtn.setOnAction(e -> {
            world.step();
            renderNow();
        });

        Button clearBtn = new Button("Clear");
        clearBtn.setMaxWidth(Double.MAX_VALUE);
        clearBtn.setOnAction(e -> {
            world.clear();
            world.fillBorder(ElementType.WALL);
            renderNow();
        });

        Button resetBtn = new Button("Reset (new seed)");
        resetBtn.setMaxWidth(Double.MAX_VALUE);
        resetBtn.setOnAction(e -> {
            world.reseed(System.nanoTime());
            world.clear();
            world.fillBorder(ElementType.WALL);
            renderNow();
        });

        Label help = new Label(
                "Mouse:\n" +
                "- Left: paint selected\n" +
                "- Right: erase (EMPTY)\n" +
                "\n" +
                "Tips:\n" +
                "- Keep border walls for containment\n" +
                "- Increase brush for faster painting"
        );
        help.setStyle("-fx-font-size: 11px;");

        Label stats = new Label();
        stats.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 11px;");

        VBox controls = new VBox(10,
                title,
                new Label("Element:"), elementChoice,
                new Separator(),
                brushLabel, brushSlider,
                new Separator(),
                playPause, stepBtn, clearBtn, resetBtn,
                new Separator(),
                stats,
                new Separator(),
                help
        );
        controls.setPadding(new Insets(10));
        controls.setPrefWidth(260);

        BorderPane root = new BorderPane();
        root.setCenter(viewport);
        root.setRight(controls);

        Scene scene = new Scene(root);
        stage.setTitle("ParticuleToy");
        stage.setScene(scene);
        stage.setResizable(true);
        stage.show();

        // --- Input (painting) ---
        imageView.addEventFilter(MouseEvent.MOUSE_PRESSED, this::onPaintEvent);
        imageView.addEventFilter(MouseEvent.MOUSE_DRAGGED, this::onPaintEvent);

        // --- Game loop ---
        AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (lastFrameNs == 0L) {
                    lastFrameNs = now;
                    fpsLastNs = now;
                    renderNow();
                    return;
                }

                double frameDt = (now - lastFrameNs) / 1_000_000_000.0;
                lastFrameNs = now;

                // Avoid "spiral of death" on big hiccups
                if (frameDt > 0.25) frameDt = 0.25;

                int stepsThisFrame = 0;
                if (!paused) {
                    accumulator += frameDt;
                    while (accumulator >= TICK_DT && stepsThisFrame < MAX_STEPS_PER_FRAME) {
                        world.step();
                        accumulator -= TICK_DT;
                        stepsThisFrame++;
                    }
                }
                lastSteps = stepsThisFrame;

                renderNow();

                // FPS stats
                fpsFrames++;
                if (now - fpsLastNs >= 500_000_000L) { // 0.5s
                    double seconds = (now - fpsLastNs) / 1_000_000_000.0;
                    lastFps = fpsFrames / seconds;
                    fpsFrames = 0;
                    fpsLastNs = now;

                    stats.setText(String.format(Locale.US,
                            "FPS: %.1f%nTick steps/frame: %d%nWorld: %dx%d",
                            lastFps, lastSteps, WORLD_W, WORLD_H));
                }
            }
        };
        timer.start();
    }

    private void onPaintEvent(MouseEvent e) {
        // Convert mouse position to world pixel coordinate
        Point2D local = imageView.sceneToLocal(e.getSceneX(), e.getSceneY());
        int x = (int) Math.floor(local.getX());
        int y = (int) Math.floor(local.getY());

        if (!world.inBounds(x, y)) return;

        // We keep the border as an immutable containment wall in the MVP.
        if (x <= 0 || x >= world.width() - 1 || y <= 0 || y >= world.height() - 1) return;

        ElementType typeToPaint;
        if (e.getButton() == MouseButton.SECONDARY || e.isSecondaryButtonDown()) {
            typeToPaint = ElementType.EMPTY;
        } else {
            typeToPaint = selectedElement;
        }

        world.paintCircle(x, y, brushRadius, typeToPaint);
        world.fillBorder(ElementType.WALL);
        renderNow();
        e.consume();
    }

    private void renderNow() {
        world.renderTo(pixels);
        pixelWriter.setPixels(
                0, 0,
                WORLD_W, WORLD_H,
                PixelFormat.getIntArgbPreInstance(),
                pixels, 0, WORLD_W
        );
    }
}
