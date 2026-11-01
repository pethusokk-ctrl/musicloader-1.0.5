package com.musicloader.mixin;

import com.musicloader.MusicLibrary;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;
import java.util.List;

/** Перетащил песню в окно игры на экране паузы -> она копируется в папку musicloader. */
@Mixin(Screen.class)
public abstract class ScreenDropMixin {
    @Inject(method = "onFilesDrop", at = @At("HEAD"), cancellable = true)
    private void musicloader$onDrop(List<Path> files, CallbackInfo ci) {
        if ((Object) this instanceof PauseScreen) {
            MusicLibrary.importFiles(files);
            ci.cancel();
        }
    }
}
