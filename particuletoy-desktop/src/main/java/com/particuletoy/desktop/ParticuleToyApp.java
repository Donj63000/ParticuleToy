package com.particuletoy.desktop;

import com.particuletoy.core.DebugLog;
import com.particuletoy.core.ElementType;
import com.particuletoy.core.ThermoConstants;
import com.particuletoy.core.World;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.Locale;

/**
 * Desktop UI (JavaFX).
 *
 * Goals:
 * - Show the simulation as a scaled pixel canvas.
 * - Allow painting materials and temperature.
 * - Provide temperature controls and heatmap view.
 * - Basic controls: play/pause, step, clear, reset.
 */
public final class ParticuleToyApp extends Application {

    // --- Simulation settings (MVP defaults) ---
    private static final int WORLD_W = 320;
    private static final int WORLD_H = 200;
    private static final int VIEW_SCALE = 3;
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
    private PaintMode paintMode = PaintMode.MATERIAL;
    private boolean showTemperature = false;
    private float temperatureBrushC = 20.0f;
    private Label tempHoverLabel;
    private boolean hoverInBounds = false;
    private int hoverX = -1;
    private int hoverY = -1;

    // Stats
    private long lastFrameNs = 0L;
    private double accumulator = 0.0;
    private long fpsLastNs = 0L;
    private int fpsFrames = 0;
    private double lastFps = 0.0;
    private int lastSteps = 0;

    private static int debugPaintSeq = 0;
    private static boolean debugPaint = true;

    private enum PaintMode {
        MATERIAL,
        TEMPERATURE
    }

    @Override
    public void start(Stage stage) {
        // --- World init ---
        this.world = new World(WORLD_W, WORLD_H, 1337L);
        this.world.clear();
        this.world.fillBorder(ElementType.BEDROCK);
        this.temperatureBrushC = world.ambientTemperatureC();

        // --- Rendering init ---
        this.pixels = new int[WORLD_W * WORLD_H];
        this.image = new WritableImage(WORLD_W, WORLD_H);
        this.pixelWriter = image.getPixelWriter();

        this.imageView = new ImageView(image);
        this.imageView.setSmooth(false);
        this.imageView.setPreserveRatio(false);
        this.imageView.setScaleX(VIEW_SCALE);
        this.imageView.setScaleY(VIEW_SCALE);

        this.tempHoverLabel = new Label("T: -- C");
        this.tempHoverLabel.setVisible(false);
        this.tempHoverLabel.setStyle("-fx-background-color: rgba(11, 15, 25, 0.75);"
                + "-fx-text-fill: #e2e8f0;"
                + "-fx-padding: 4 8;"
                + "-fx-background-radius: 6;"
                + "-fx-font-family: 'Consolas';"
                + "-fx-font-size: 12px;");
        this.tempHoverLabel.setMouseTransparent(true);

        StackPane viewport = new StackPane(imageView, tempHoverLabel);
        viewport.setAlignment(Pos.CENTER);
        StackPane.setAlignment(tempHoverLabel, Pos.TOP_RIGHT);
        StackPane.setMargin(tempHoverLabel, new Insets(8));
        viewport.setPadding(new Insets(10));
        viewport.setStyle("-fx-background-color: linear-gradient(to bottom, #0b0f17, #151b2a);");

        // --- Controls panel ---
        String panelStyle = ""
                + "-fx-background-color: linear-gradient(to bottom, #111827, #0b1220 45%, #111a2e);"
                + "-fx-border-color: rgba(255,255,255,0.06);"
                + "-fx-border-width: 0 0 0 1;";
        String cardStyle = ""
                + "-fx-background-color: rgba(17, 24, 39, 0.85);"
                + "-fx-background-radius: 12;"
                + "-fx-border-color: rgba(255,255,255,0.07);"
                + "-fx-border-radius: 12;"
                + "-fx-padding: 10;";
        String headerBase = "-fx-font-family: 'Palatino Linotype'; -fx-font-size: 13px; -fx-font-weight: bold;";
        String labelStyle = "-fx-font-family: 'Trebuchet MS'; -fx-text-fill: #d6d9e6;";
        String minorLabelStyle = "-fx-font-family: 'Trebuchet MS'; -fx-text-fill: #aab3c7; -fx-font-size: 11px;";
        String choiceStyle = ""
                + "-fx-background-color: #0f172a;"
                + "-fx-mark-color: #7dd3fc;"
                + "-fx-text-fill: #e2e8f0;"
                + "-fx-background-radius: 8;"
                + "-fx-padding: 4 6;";
        String sliderStyle = ""
                + "-fx-control-inner-background: #0b1120;"
                + "-fx-accent: #7dd3fc;"
                + "-fx-focus-color: #34d399;"
                + "-fx-faint-focus-color: transparent;";
        String buttonBase = "-fx-background-radius: 8; -fx-text-fill: #0b0f19; -fx-font-weight: bold; -fx-padding: 8 10;";

        Label title = new Label("ParticuleToy");
        title.setStyle("-fx-font-family: 'Palatino Linotype'; -fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #f8fafc;");

        Region accentBar = new Region();
        accentBar.setPrefHeight(6);
        accentBar.setMaxWidth(Double.MAX_VALUE);
        accentBar.setStyle("-fx-background-color: linear-gradient(to right, #ff7a59, #ffd15c, #2dd4bf, #60a5fa, #f472b6); -fx-background-radius: 10;");

        // Paint mode controls
        Label modeHeader = new Label("Paint mode");
        modeHeader.setStyle(headerBase + " -fx-text-fill: #93c5fd;");

        ToggleGroup modeGroup = new ToggleGroup();
        RadioButton materialMode = new RadioButton("Material");
        materialMode.setToggleGroup(modeGroup);
        materialMode.setSelected(true);
        materialMode.setStyle(labelStyle);
        RadioButton temperatureMode = new RadioButton("Temperature");
        temperatureMode.setToggleGroup(modeGroup);
        temperatureMode.setStyle(labelStyle);

        modeGroup.selectedToggleProperty().addListener((obs, oldT, newT) -> {
            if (newT == temperatureMode) {
                paintMode = PaintMode.TEMPERATURE;
            } else {
                paintMode = PaintMode.MATERIAL;
            }
        });

        HBox modeRow = new HBox(12, materialMode, temperatureMode);

        ChoiceBox<ElementType> elementChoice = new ChoiceBox<>(FXCollections.observableArrayList(ElementType.palette()));
        elementChoice.setValue(selectedElement);
        elementChoice.setStyle(choiceStyle);
        elementChoice.setMaxWidth(Double.MAX_VALUE);
        elementChoice.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) selectedElement = newV;
        });

        Label elementHeader = new Label("Element");
        elementHeader.setStyle(headerBase + " -fx-text-fill: #ffb347;");

        Slider brushSlider = new Slider(1, 25, brushRadius);
        brushSlider.setMajorTickUnit(6);
        brushSlider.setMinorTickCount(5);
        brushSlider.setShowTickLabels(true);
        brushSlider.setShowTickMarks(true);
        brushSlider.setSnapToTicks(true);
        brushSlider.setStyle(sliderStyle);
        brushSlider.setMaxWidth(Double.MAX_VALUE);

        Label brushLabel = new Label();
        brushLabel.setStyle(labelStyle);
        Runnable refreshBrushLabel = () -> brushLabel.setText("Brush radius: " + brushRadius + " px");
        refreshBrushLabel.run();

        brushSlider.valueProperty().addListener((obs, oldV, newV) -> {
            brushRadius = (int) Math.round(newV.doubleValue());
            refreshBrushLabel.run();
        });

        Label brushHeader = new Label("Brush");
        brushHeader.setStyle(headerBase + " -fx-text-fill: #5eead4;");

        Label tempHeader = new Label("Temperature");
        tempHeader.setStyle(headerBase + " -fx-text-fill: #fca5a5;");

        Slider tempBrushSlider = new Slider(ThermoConstants.MIN_TEMP_C, ThermoConstants.MAX_TEMP_C, temperatureBrushC);
        tempBrushSlider.setShowTickLabels(true);
        tempBrushSlider.setShowTickMarks(true);
        tempBrushSlider.setMajorTickUnit(2000);
        tempBrushSlider.setMinorTickCount(9);
        tempBrushSlider.setStyle(sliderStyle);
        tempBrushSlider.setMaxWidth(Double.MAX_VALUE);

        Label tempBrushLabel = new Label();
        tempBrushLabel.setStyle(labelStyle);
        Runnable refreshTempBrushLabel = () -> tempBrushLabel.setText(String.format(Locale.US, "Temp brush: %.0f C", temperatureBrushC));
        refreshTempBrushLabel.run();

        tempBrushSlider.valueProperty().addListener((obs, oldV, newV) -> {
            temperatureBrushC = newV.floatValue();
            refreshTempBrushLabel.run();
        });

        Slider ambientSlider = new Slider(ThermoConstants.MIN_TEMP_C, ThermoConstants.MAX_TEMP_C, world.ambientTemperatureC());
        ambientSlider.setShowTickLabels(true);
        ambientSlider.setShowTickMarks(true);
        ambientSlider.setMajorTickUnit(2000);
        ambientSlider.setMinorTickCount(9);
        ambientSlider.setStyle(sliderStyle);
        ambientSlider.setMaxWidth(Double.MAX_VALUE);

        Label ambientLabel = new Label();
        ambientLabel.setStyle(labelStyle);
        Runnable refreshAmbientLabel = () -> ambientLabel.setText(String.format(Locale.US, "Ambient: %.0f C", world.ambientTemperatureC()));
        refreshAmbientLabel.run();

        ambientSlider.valueProperty().addListener((obs, oldV, newV) -> {
            world.setAmbientTemperatureC(newV.floatValue());
            refreshAmbientLabel.run();
        });

        Label pressureLabel = new Label();
        pressureLabel.setStyle(labelStyle);
        Runnable refreshPressureLabel = () -> pressureLabel.setText(
                String.format(Locale.US, "Ambient pressure: %.2f bar", world.ambientPressurePa() / 100_000.0)
        );
        refreshPressureLabel.run();

        Slider pressureSlider = new Slider(0.2, 3.0, world.ambientPressurePa() / 100_000.0);
        pressureSlider.setShowTickLabels(true);
        pressureSlider.setShowTickMarks(true);
        pressureSlider.setMajorTickUnit(0.5);
        pressureSlider.setMinorTickCount(4);
        pressureSlider.setStyle(sliderStyle);
        pressureSlider.setMaxWidth(Double.MAX_VALUE);

        pressureSlider.valueProperty().addListener((obs, oldV, newV) -> {
            world.setAmbientPressurePa(newV.floatValue() * 100_000.0f);
            refreshPressureLabel.run();
        });

        CheckBox showTempCb = new CheckBox("Show temperature heatmap");
        showTempCb.setStyle(labelStyle);
        showTempCb.selectedProperty().addListener((obs, oldV, newV) -> {
            showTemperature = newV;
            if (!showTemperature) {
                tempHoverLabel.setVisible(false);
            } else if (hoverInBounds) {
                float t = world.temperatureCAt(hoverX, hoverY);
                tempHoverLabel.setText(String.format(Locale.US, "T: %.1f C", t));
                tempHoverLabel.setVisible(true);
            }
        });

        ToggleButton playPause = new ToggleButton("Play");
        playPause.setMaxWidth(Double.MAX_VALUE);
        playPause.setStyle(buttonBase + " -fx-background-color: linear-gradient(to right, #2dd4bf, #22c55e);");
        playPause.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
            paused = !isSelected;
            playPause.setText(isSelected ? "Pause" : "Play");
        });
        playPause.setSelected(true);
        playPause.setText("Pause");

        Button stepBtn = new Button("Step (1 tick)");
        stepBtn.setMaxWidth(Double.MAX_VALUE);
        stepBtn.setStyle(buttonBase + " -fx-background-color: linear-gradient(to right, #60a5fa, #38bdf8);");
        stepBtn.setOnAction(e -> {
            world.step();
            renderNow();
        });

        Button clearBtn = new Button("Clear");
        clearBtn.setMaxWidth(Double.MAX_VALUE);
        clearBtn.setStyle(buttonBase + " -fx-background-color: linear-gradient(to right, #f59e0b, #f97316);");
        clearBtn.setOnAction(e -> {
            world.clear();
            world.fillBorder(ElementType.BEDROCK);
            renderNow();
        });

        Button resetBtn = new Button("Reset (new seed)");
        resetBtn.setMaxWidth(Double.MAX_VALUE);
        resetBtn.setStyle(buttonBase + " -fx-background-color: linear-gradient(to right, #f472b6, #fb7185);");
        resetBtn.setOnAction(e -> {
            world.reseed(System.nanoTime());
            world.clear();
            world.fillBorder(ElementType.BEDROCK);
            renderNow();
        });

        Label actionsHeader = new Label("Controls");
        actionsHeader.setStyle(headerBase + " -fx-text-fill: #a7f3d0;");

        Label help = new Label(
                "Mouse:\n" +
                "- Left: paint selected or temperature\n" +
                "- Right: erase (Material) or reset to ambient (Temp)\n" +
                "\n" +
                "Modes:\n" +
                "- Material: paints WALL / SAND / WATER\n" +
                "- Temperature: sets cell temperature\n" +
                "\n" +
                "Tips:\n" +
                "- Keep border bedrock for containment\n" +
                "- Toggle heatmap to inspect temperature"
        );
        help.setStyle(minorLabelStyle);
        help.setWrapText(true);

        Label stats = new Label();
        stats.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 11px; -fx-text-fill: #9fb4c7;");

        Label statsHeader = new Label("Stats");
        statsHeader.setStyle(headerBase + " -fx-text-fill: #7ee787;");

        Label helpHeader = new Label("Help");
        helpHeader.setStyle(headerBase + " -fx-text-fill: #facc15;");

        VBox modeCard = new VBox(6, modeHeader, modeRow);
        modeCard.setStyle(cardStyle);

        VBox elementCard = new VBox(6, elementHeader, elementChoice);
        elementCard.setStyle(cardStyle);

        VBox brushCard = new VBox(6, brushHeader, brushLabel, brushSlider);
        brushCard.setStyle(cardStyle);

        VBox tempCard = new VBox(6,
                tempHeader,
                tempBrushLabel,
                tempBrushSlider,
                new Separator(),
                ambientLabel,
                ambientSlider,
                pressureLabel,
                pressureSlider,
                showTempCb
        );
        tempCard.setStyle(cardStyle);

        VBox actionCard = new VBox(8, actionsHeader, playPause, stepBtn, clearBtn, resetBtn);
        actionCard.setStyle(cardStyle);

        VBox statsCard = new VBox(6, statsHeader, stats);
        statsCard.setStyle(cardStyle);

        VBox helpCard = new VBox(6, helpHeader, help);
        helpCard.setStyle(cardStyle);

        VBox controls = new VBox(12, accentBar, title, modeCard, elementCard, brushCard, tempCard, actionCard, statsCard, helpCard);
        controls.setPadding(new Insets(12));
        controls.setPrefWidth(320);
        controls.setFillWidth(true);
        controls.setStyle("-fx-background-color: transparent;");

        ScrollPane controlsScroll = new ScrollPane(controls);
        controlsScroll.setFitToWidth(true);
        controlsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        controlsScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        controlsScroll.setPannable(false);
        controlsScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        controlsScroll.setPrefWidth(320);

        StackPane controlsPane = new StackPane(controlsScroll);
        controlsPane.setStyle(panelStyle);
        controlsPane.setPrefWidth(320);

        BorderPane root = new BorderPane();
        root.setCenter(viewport);
        root.setRight(controlsPane);
        root.setStyle("-fx-background-color: #0b0f17;");

        Scene scene = new Scene(root);
        stage.setTitle("ParticuleToy");
        stage.setScene(scene);
        stage.setResizable(true);
        stage.setMaximized(true);
        stage.show();

        // --- Input (painting) ---
        viewport.setPickOnBounds(true);
        viewport.setOnMousePressed(this::onPaintEvent);
        viewport.setOnMouseDragged(this::onPaintEvent);
        viewport.setOnMouseMoved(this::onHoverEvent);
        viewport.setOnMouseExited(e -> {
            hoverInBounds = false;
            tempHoverLabel.setVisible(false);
        });

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

                fpsFrames++;
                if (now - fpsLastNs >= 500_000_000L) {
                    double seconds = (now - fpsLastNs) / 1_000_000_000.0;
                    lastFps = fpsFrames / seconds;
                    fpsFrames = 0;
                    fpsLastNs = now;

                    stats.setText(String.format(Locale.US,
                            "FPS: %.1f%nTick steps/frame: %d%nWorld: %dx%d%nAmbient: %.0f C%nAmbientP: %.2f bar",
                            lastFps, lastSteps, WORLD_W, WORLD_H, world.ambientTemperatureC(), world.ambientPressurePa() / 100_000.0));
                }
            }
        };
        timer.start();
    }

    private void onPaintEvent(MouseEvent e) {
        boolean primaryDown = e.isPrimaryButtonDown() || e.getButton() == MouseButton.PRIMARY;
        boolean secondaryDown = e.isSecondaryButtonDown() || e.getButton() == MouseButton.SECONDARY;
        if (!primaryDown && !secondaryDown) {
            return;
        }

        Point2D local = imageView.sceneToLocal(e.getSceneX(), e.getSceneY());
        int x = (int) Math.floor(local.getX());
        int y = (int) Math.floor(local.getY());

        debugPaintLog("onPaintEvent", e, x, y);

        if (!world.inBounds(x, y)) return;

        if (x <= 0 || x >= world.width() - 1 || y <= 0 || y >= world.height() - 1) return;

        boolean right = secondaryDown;

        if (paintMode == PaintMode.MATERIAL) {
            if (right) {
                world.paintCircle(x, y, brushRadius, ElementType.EMPTY);
            } else {
                world.paintCircleWithTemperature(x, y, brushRadius, selectedElement, temperatureBrushC);
            }
        } else {
            float t = right ? world.ambientTemperatureC() : temperatureBrushC;
            world.paintTemperatureCircle(x, y, brushRadius, t);
        }

        world.fillBorder(ElementType.BEDROCK);
        renderNow();
        e.consume();
    }

    private void debugPaintLog(String tag, MouseEvent e, int gx, int gy) {
        if (!debugPaint) return;

        int seq = ++debugPaintSeq;
        String line = String.format(Locale.US,
                "[PAINT #%d] %s type=%s source=%s target=%s button=%s primDown=%s secDown=%s "
                        + "scene=(%.1f,%.1f) local=(%.1f,%.1f) grid=(%d,%d)",
                seq,
                tag,
                e.getEventType(),
                (e.getSource() == null ? "null" : e.getSource().getClass().getSimpleName()),
                (e.getTarget() == null ? "null" : e.getTarget().getClass().getSimpleName()),
                e.getButton(),
                e.isPrimaryButtonDown(),
                e.isSecondaryButtonDown(),
                e.getSceneX(), e.getSceneY(),
                e.getX(), e.getY(),
                gx, gy
        );
        DebugLog.log(line);

        StackTraceElement[] st = Thread.currentThread().getStackTrace();
        for (int i = 2; i < Math.min(st.length, 12); i++) {
            DebugLog.log("    at " + st[i]);
        }
    }

    private void onHoverEvent(MouseEvent e) {
        if (!showTemperature) {
            tempHoverLabel.setVisible(false);
            return;
        }
        Point2D local = imageView.sceneToLocal(e.getSceneX(), e.getSceneY());
        int x = (int) Math.floor(local.getX());
        int y = (int) Math.floor(local.getY());

        if (!world.inBounds(x, y)) {
            hoverInBounds = false;
            tempHoverLabel.setVisible(false);
            return;
        }

        hoverInBounds = true;
        hoverX = x;
        hoverY = y;
        float t = world.temperatureCAt(x, y);
        tempHoverLabel.setText(String.format(Locale.US, "T: %.1f C", t));
        tempHoverLabel.setVisible(true);
    }

    private void renderNow() {
        if (showTemperature) {
            world.renderTemperatureTo(pixels);
        } else {
            world.renderTo(pixels);
        }
        pixelWriter.setPixels(
                0, 0,
                WORLD_W, WORLD_H,
                PixelFormat.getIntArgbPreInstance(),
                pixels, 0, WORLD_W
        );
    }
}
