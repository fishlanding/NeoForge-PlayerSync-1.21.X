package net.doodlechaos.playersync.input;

import net.doodlechaos.playersync.sync.SyncTimeline;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;

import java.util.function.Supplier;

public class PlaybackModeContext implements IKeyConflictContext {

    @Override
    public boolean isActive() {
        return true; //SyncTimeline.isPlaybackEnabled();
    }

    @Override
    public boolean conflicts(IKeyConflictContext iKeyConflictContext) {
        return false;
    }
}
