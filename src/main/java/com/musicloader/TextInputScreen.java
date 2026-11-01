package com.musicloader;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Окно "Добавить текст": введённый текст показывается в игре справа внизу. */
public class TextInputScreen extends Screen {
    private final Screen parent;
    private EditBox box;

    public TextInputScreen(Screen parent) {
        super(Component.literal("Добавить текст"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        box = new EditBox(this.font, this.width / 2 - 130, this.height / 2 - 10, 260, 20,
                Component.literal("Текст"));
        box.setMaxLength(100);
        box.setValue(MusicLibrary.loadText());
        this.addRenderableWidget(box);
        this.setInitialFocus(box);
        box.setFocused(true);

        this.addRenderableWidget(Button.builder(Component.literal("Сохранить"), b -> {
            MusicLibrary.saveText(box.getValue());
            this.onClose();
        }).bounds(this.width / 2 - 130, this.height / 2 + 20, 127, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Очистить"), b -> {
            MusicLibrary.saveText("");
            this.onClose();
        }).bounds(this.width / 2 + 3, this.height / 2 + 20, 127, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        String title = "Добавить текст";
        graphics.text(this.font, title, (this.width - this.font.width(title)) / 2,
                this.height / 2 - 42, 0xFFFFFFFF, true);
        String hint = "Он будет виден в игре справа внизу";
        graphics.text(this.font, hint, (this.width - this.font.width(hint)) / 2,
                this.height / 2 - 28, 0xFFAAAAAA, true);
    }

    @Override
    public void onClose() {
        ScreenUtil.open(parent);
    }
}
